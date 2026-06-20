package com.prodigalgal.ircs.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class DashboardSnapshotCacheService {

    static final String DEFAULT_PREFIX = "ircs:ops:dashboard:snapshot:v1";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Value("${app.ops.dashboard.snapshot.key-prefix:" + DEFAULT_PREFIX + "}")
    private String keyPrefix;

    @Value("${app.ops.dashboard.snapshot.fresh-ttl:PT6S}")
    private Duration freshTtl;

    @Value("${app.ops.dashboard.snapshot.stale-grace:PT60S}")
    private Duration staleGrace;

    @Value("${app.ops.dashboard.snapshot.redis-ttl:PT2M}")
    private Duration redisTtl;

    DashboardSnapshotCacheService(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this(objectMapper, redisTemplate, Clock.systemUTC());
    }

    DashboardSnapshotCacheService(ObjectMapper objectMapper, StringRedisTemplate redisTemplate, Clock clock) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    Optional<DashboardStreamSnapshot> read(int taskRuntimeLimit) {
        try {
            String raw = redisTemplate.opsForValue().get(key(taskRuntimeLimit));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            CachedDashboardSnapshot cached = objectMapper.readValue(raw, CachedDashboardSnapshot.class);
            Instant now = Instant.now(clock);
            if (cached.staleUntil() == null || !cached.staleUntil().isAfter(now)) {
                return Optional.empty();
            }
            boolean stale = cached.freshUntil() == null || !cached.freshUntil().isAfter(now);
            DashboardStreamSnapshot snapshot = cached.snapshot();
            return snapshot == null ? Optional.empty() : Optional.of(snapshot.withStale(stale));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Dashboard snapshot cache read failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    void write(int taskRuntimeLimit, DashboardStreamSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            Instant now = Instant.now(clock);
            Duration effectiveFreshTtl = positiveOr(freshTtl, Duration.ofSeconds(6));
            Duration effectiveStaleGrace = positiveOr(staleGrace, Duration.ofSeconds(60));
            Duration effectiveRedisTtl = positiveOr(redisTtl, Duration.ofMinutes(2));
            CachedDashboardSnapshot cached = new CachedDashboardSnapshot(
                    snapshot.withStale(false),
                    now,
                    now.plus(effectiveFreshTtl),
                    now.plus(effectiveStaleGrace));
            redisTemplate.opsForValue().set(
                    key(taskRuntimeLimit),
                    objectMapper.writeValueAsString(cached),
                    effectiveRedisTtl);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Dashboard snapshot cache write failed: {}", ex.getMessage());
        }
    }

    private String key(int taskRuntimeLimit) {
        return prefix() + ":limit:" + Math.max(1, taskRuntimeLimit);
    }

    private String prefix() {
        return keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_PREFIX : keyPrefix.trim();
    }

    private Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private record CachedDashboardSnapshot(
            DashboardStreamSnapshot snapshot,
            Instant cachedAt,
            Instant freshUntil,
            Instant staleUntil) {
    }
}
