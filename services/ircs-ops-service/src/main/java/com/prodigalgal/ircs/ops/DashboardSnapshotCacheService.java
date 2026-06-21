package com.prodigalgal.ircs.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class DashboardSnapshotCacheService {

    static final String DEFAULT_PREFIX = "ircs:ops:dashboard:snapshot:v1";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final RuntimeConfigService runtimeConfig;
    private Clock clock = Clock.systemUTC();

    DashboardSnapshotCacheService(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            RuntimeConfigService runtimeConfig) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.runtimeConfig = runtimeConfig;
    }

    void setClockForTests(Clock clock) {
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

    boolean write(int taskRuntimeLimit, DashboardStreamSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        try {
            Instant now = Instant.now(clock);
            Duration effectiveFreshTtl = freshTtl();
            Duration effectiveStaleGrace = staleGrace();
            Duration effectiveRedisTtl = redisTtl();
            CachedDashboardSnapshot cached = new CachedDashboardSnapshot(
                    snapshot.withStale(false),
                    now,
                    now.plus(effectiveFreshTtl),
                    now.plus(effectiveStaleGrace));
            redisTemplate.opsForValue().set(
                    key(taskRuntimeLimit),
                    objectMapper.writeValueAsString(cached),
                    effectiveRedisTtl);
            return true;
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Dashboard snapshot cache write failed: {}", ex.getMessage());
            return false;
        }
    }

    private String key(int taskRuntimeLimit) {
        return prefix() + ":limit:" + Math.max(1, taskRuntimeLimit);
    }

    private String prefix() {
        return runtimeConfig == null
                ? DEFAULT_PREFIX
                : runtimeConfig.stringValue("app.ops.dashboard.snapshot.key-prefix", DEFAULT_PREFIX);
    }

    private Duration freshTtl() {
        Duration fallback = Duration.ofSeconds(6);
        return runtimeConfig == null
                ? fallback
                : runtimeConfig.positiveDurationValue("app.ops.dashboard.snapshot.fresh-ttl", fallback);
    }

    private Duration staleGrace() {
        Duration fallback = Duration.ofSeconds(60);
        return runtimeConfig == null
                ? fallback
                : runtimeConfig.positiveDurationValue("app.ops.dashboard.snapshot.stale-grace", fallback);
    }

    private Duration redisTtl() {
        Duration fallback = Duration.ofMinutes(2);
        return runtimeConfig == null
                ? fallback
                : runtimeConfig.positiveDurationValue("app.ops.dashboard.snapshot.redis-ttl", fallback);
    }

    private record CachedDashboardSnapshot(
            DashboardStreamSnapshot snapshot,
            Instant cachedAt,
            Instant freshUntil,
            Instant staleUntil) {
    }
}
