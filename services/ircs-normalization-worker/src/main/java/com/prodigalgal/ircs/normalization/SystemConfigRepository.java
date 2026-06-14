package com.prodigalgal.ircs.normalization;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SystemConfigRepository {

    static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final long cacheTtlNanos;
    private LongSupplier ticker;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    SystemConfigRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${app.normalization.system-config.cache-ttl:30s}") Duration cacheTtl) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheTtlNanos = Math.max(0L, cacheTtl.toNanos());
        this.ticker = System::nanoTime;
    }

    static SystemConfigRepository forTest(JdbcTemplate jdbcTemplate, Duration cacheTtl, LongSupplier ticker) {
        SystemConfigRepository repository = new SystemConfigRepository(jdbcTemplate, cacheTtl);
        repository.ticker = ticker;
        return repository;
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
