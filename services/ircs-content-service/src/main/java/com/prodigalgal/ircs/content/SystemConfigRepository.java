package com.prodigalgal.ircs.content;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SystemConfigRepository {

    static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final long cacheTtlNanos;
    private final LongSupplier ticker;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    SystemConfigRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${app.content.system-config.cache-ttl:30s}") Duration cacheTtl,
            ObjectProvider<LongSupplier> ticker) {
        this.jdbcTemplate = jdbcTemplate;
        Duration ttl = cacheTtl == null ? DEFAULT_CACHE_TTL : cacheTtl;
        LongSupplier resolvedTicker = ticker == null ? null : ticker.getIfAvailable();
        this.cacheTtlNanos = Math.max(0L, ttl.toNanos());
        this.ticker = resolvedTicker == null ? System::nanoTime : resolvedTicker;
    }

    static SystemConfigRepository forTest(JdbcTemplate jdbcTemplate) {
        return forTest(jdbcTemplate, DEFAULT_CACHE_TTL, System::nanoTime);
    }

    static SystemConfigRepository forTest(JdbcTemplate jdbcTemplate, Duration cacheTtl, LongSupplier ticker) {
        return new SystemConfigRepository(jdbcTemplate, cacheTtl, new ObjectProvider<>() {
            @Override
            public LongSupplier getObject() {
                return ticker;
            }
        });
    }

    Optional<String> findValue(String key) {
        long now = ticker.getAsLong();
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.expired(now, cacheTtlNanos)) {
            return cached.value();
        }
        CacheEntry refreshed = new CacheEntry(findValueFromDatabase(key), now);
        cache.put(key, refreshed);
        return refreshed.value();
    }

    void evict(String key) {
        cache.remove(key);
    }

    private Optional<String> findValueFromDatabase(String key) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select config_value from system_configs where config_key = ?",
                    String.class,
                    key));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private record CacheEntry(Optional<String> value, long loadedAtNanos) {

        boolean expired(long nowNanos, long ttlNanos) {
            if (ttlNanos == 0L) {
                return true;
            }
            return nowNanos - loadedAtNanos >= ttlNanos;
        }
    }
}
