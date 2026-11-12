package com.prodigalgal.ircs.common.config;

import io.micrometer.core.instrument.Metrics;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
public class SystemConfigCache {

    public static final String DEFAULT_KEY_PREFIX = "ircs:system-config:v1";
    public static final Duration DEFAULT_TTL = Duration.ofHours(12);
    public static final Duration DEFAULT_LOCAL_TTL = Duration.ofMinutes(5);
    private static final String TOKEN_REVOCATION_KEY = "security.jwt.iat-floor";
    private static final Duration REVOCATION_TTL = Duration.ofSeconds(30);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final String keyPrefix;
    private final Duration ttl;
    private final long localTtlNanos;
    private final ConcurrentMap<String, LocalEntry> localCache = new ConcurrentHashMap<>();
    private final ReentrantLock[] loadLocks = IntStream.range(0, 64)
            .mapToObj(index -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public SystemConfigCache(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            String keyPrefix,
            Duration ttl) {
        this(redisTemplateProvider, keyPrefix, ttl, DEFAULT_LOCAL_TTL);
    }

    public SystemConfigCache(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            String keyPrefix,
            Duration ttl,
            Duration localTtl) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : DEFAULT_KEY_PREFIX;
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
        Duration resolvedLocalTtl = localTtl == null || localTtl.isZero() || localTtl.isNegative()
                ? DEFAULT_LOCAL_TTL
                : localTtl;
        this.localTtlNanos = resolvedLocalTtl.toNanos();
    }

    public Optional<String> findValue(String key, Supplier<Optional<String>> loader) {
        // L1 local cache -> L2 Valkey -> L3 database loader.
        if (key == null) {
            return loader.get();
        }
        Optional<String> local = getLocal(key);
        if (local != null) {
            record(key, "L1", "hit");
            return local;
        }
        // All gateway replicas observe successful revocations through the shared
        // value, without waiting for a load-balanced configuration event or L1 TTL.
        RemoteLookup initial = TOKEN_REVOCATION_KEY.equals(key) ? getRemote(key) : null;
        if (initial != null && initial.value().isPresent()) {
            return initial.value();
        }
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            local = getLocal(key);
            if (local != null) {
                record(key, "L1", "coalesced");
                return local;
            }
            RemoteLookup remote = initial != null && !initial.available() ? initial : getRemote(key);
            if (remote.value().isPresent()) {
                putLocal(key, remote.value());
                return remote.value();
            }
            record(key, "L3", "load");
            Optional<String> loaded;
            try {
                loaded = loader.get();
            } catch (RuntimeException ex) {
                record(key, "L3", "error");
                throw ex;
            }
            Optional<String> resolved = loaded.map(value -> backfill(key, value)).orElseGet(Optional::empty);
            putLocal(key, resolved);
            return resolved;
        } finally {
            lock.unlock();
        }
    }

    public void putAfterCommit(String key, String value) {
        afterCommit(() -> put(key, value));
    }

    public void evictAfterCommit(String key) {
        afterCommit(() -> evict(key));
    }

    public void put(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            putRemote(key, value);
            putLocal(key, Optional.of(value));
        } finally {
            lock.unlock();
        }
    }

    public void evict(String key) {
        if (key == null) {
            return;
        }
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            localCache.remove(key);
            evictRemote(key);
        } finally {
            lock.unlock();
        }
    }

    public void evictLocal(String key) {
        if (key != null) {
            ReentrantLock lock = lockFor(key);
            lock.lock();
            try {
                localCache.remove(key);
            } finally {
                lock.unlock();
            }
        }
    }

    private Optional<String> backfill(String key, String value) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null) {
            return Optional.of(value);
        }
        try {
            // A read that started before a committed update must not overwrite
            // the writer's newer shared value when its database query finishes.
            if (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(cacheKey(key), value, ttlFor(key)))) {
                Optional<String> current = getRemote(key).value();
                if (current.isPresent()) {
                    return current;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Unable to backfill system config cache: key={}, error={}", key, ex.getMessage());
        }
        return Optional.of(value);
    }

    private void putRemote(String key, String value) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null || key == null || value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(key), value, ttlFor(key));
        } catch (RuntimeException ex) {
            log.warn("Unable to write system config Valkey cache: key={}, error={}", key, ex.getMessage());
            try {
                redisTemplate.delete(cacheKey(key));
            } catch (RuntimeException evictionFailure) {
                ex.addSuppressed(evictionFailure);
            }
            if (TOKEN_REVOCATION_KEY.equals(key)) {
                throw new IllegalStateException("Token revocation cache update was not acknowledged", ex);
            }
        }
    }

    private void evictRemote(String key) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null || key == null) {
            return;
        }
        try {
            redisTemplate.delete(cacheKey(key));
        } catch (RuntimeException ex) {
            log.warn("Unable to evict system config Valkey cache: key={}, error={}", key, ex.getMessage());
            if (TOKEN_REVOCATION_KEY.equals(key)) {
                throw new IllegalStateException("Token revocation cache eviction was not acknowledged", ex);
            }
        }
    }

    private RemoteLookup getRemote(String key) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null || key == null) {
            return new RemoteLookup(Optional.empty(), false);
        }
        try {
            String value = redisTemplate.opsForValue().get(cacheKey(key));
            record(key, "L2", value == null ? "miss" : "hit");
            return new RemoteLookup(Optional.ofNullable(value), true);
        } catch (RuntimeException ex) {
            record(key, "L2", "error");
            log.warn("Unable to read system config Valkey cache: key={}, error={}", key, ex.getMessage());
            return new RemoteLookup(Optional.empty(), false);
        }
    }

    private Optional<String> getLocal(String key) {
        if (key == null || TOKEN_REVOCATION_KEY.equals(key)) {
            return null;
        }
        LocalEntry entry = localCache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired(System.nanoTime(), localTtlNanos)) {
            localCache.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    private void putLocal(String key, Optional<String> value) {
        if (key != null && value != null && !TOKEN_REVOCATION_KEY.equals(key)) {
            localCache.put(key, new LocalEntry(value, System.nanoTime()));
        }
    }

    private StringRedisTemplate redisTemplate() {
        return redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
    }

    private String cacheKey(String key) {
        return keyPrefix + ":" + key;
    }

    private Duration ttlFor(String key) {
        return TOKEN_REVOCATION_KEY.equals(key) && ttl.compareTo(REVOCATION_TTL) > 0 ? REVOCATION_TTL : ttl;
    }

    private ReentrantLock lockFor(String key) {
        return loadLocks[(key.hashCode() & Integer.MAX_VALUE) % loadLocks.length];
    }

    private void record(String key, String level, String outcome) {
        Metrics.counter("ircs.system.config.cache.requests", "category", key.startsWith("security.") ? "security" : "runtime",
                "level", level, "outcome", outcome).increment();
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record LocalEntry(Optional<String> value, long loadedAtNanos) {

        boolean expired(long nowNanos, long ttlNanos) {
            return nowNanos - loadedAtNanos >= ttlNanos;
        }
    }

    private record RemoteLookup(Optional<String> value, boolean available) {
    }
}
