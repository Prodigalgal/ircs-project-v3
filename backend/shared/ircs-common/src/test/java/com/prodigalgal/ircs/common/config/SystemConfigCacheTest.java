package com.prodigalgal.ircs.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SystemConfigCacheTest {

    private static final String PREFIX = "ircs:system-config:test";
    private static final Duration REMOTE_TTL = Duration.ofHours(12);
    private static final Duration LOCAL_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final SystemConfigCache cache = new SystemConfigCache(
            provider(redisTemplate),
            PREFIX,
            REMOTE_TTL,
            LOCAL_TTL);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void readsFromLocalBeforeValkeyAndDatabase() {
        when(valueOperations.get(PREFIX + ":worker.enabled")).thenReturn("true");
        AtomicInteger dbLoads = new AtomicInteger();

        assertThat(cache.findValue("worker.enabled", () -> {
            dbLoads.incrementAndGet();
            return Optional.of("false");
        })).contains("true");
        assertThat(cache.findValue("worker.enabled", () -> {
            dbLoads.incrementAndGet();
            return Optional.of("false");
        })).contains("true");

        assertThat(dbLoads).hasValue(0);
        verify(valueOperations, times(1)).get(PREFIX + ":worker.enabled");
    }

    @Test
    void valkeyMissLoadsDatabaseAndBackfillsValkey() {
        when(valueOperations.get(PREFIX + ":mail.enabled")).thenReturn(null);

        assertThat(cache.findValue("mail.enabled", () -> Optional.of("false"))).contains("false");
        assertThat(cache.findValue("mail.enabled", Optional::empty)).contains("false");

        verify(valueOperations, times(1)).get(PREFIX + ":mail.enabled");
        verify(valueOperations).setIfAbsent(PREFIX + ":mail.enabled", "false", REMOTE_TTL);
    }

    @Test
    void localLayerCachesMissingDatabaseValuesWithoutWritingValkey() {
        when(valueOperations.get(PREFIX + ":missing")).thenReturn(null);
        AtomicInteger dbLoads = new AtomicInteger();

        assertThat(cache.findValue("missing", () -> {
            dbLoads.incrementAndGet();
            return Optional.empty();
        })).isEmpty();
        assertThat(cache.findValue("missing", () -> {
            dbLoads.incrementAndGet();
            return Optional.of("late");
        })).isEmpty();

        assertThat(dbLoads).hasValue(1);
        verify(valueOperations, times(1)).get(PREFIX + ":missing");
        verify(valueOperations, never()).set(eq(PREFIX + ":missing"), eq("late"), eq(REMOTE_TTL));
    }

    @Test
    void evictLocalKeepsValkeyLayerAuthoritative() {
        cache.put("scraper.base-url", "http://old");
        cache.evictLocal("scraper.base-url");
        when(valueOperations.get(PREFIX + ":scraper.base-url")).thenReturn("http://new");

        assertThat(cache.findValue("scraper.base-url", () -> Optional.of("http://db"))).contains("http://new");

        verify(redisTemplate, never()).delete(PREFIX + ":scraper.base-url");
    }

    @Test
    void putAfterCommitWritesOnlyAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();

        cache.putAfterCommit("r2.enabled", "true");

        verify(valueOperations, never()).set(PREFIX + ":r2.enabled", "true", REMOTE_TTL);
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());

        verify(valueOperations).set(PREFIX + ":r2.enabled", "true", REMOTE_TTL);
    }

    @Test
    void concurrentColdReadsLoadTheDatabaseOnce() throws Exception {
        AtomicInteger dbLoads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reads = new ArrayList<Future<Optional<String>>>();
            for (int index = 0; index < 16; index++) {
                reads.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return cache.findValue("cold", () -> {
                        dbLoads.incrementAndGet();
                        loading.countDown();
                        await(release);
                        return Optional.of("loaded");
                    });
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (var read : reads) {
                assertThat(read.get(5, TimeUnit.SECONDS)).contains("loaded");
            }
        } finally {
            start.countDown();
            release.countDown();
        }
        assertThat(dbLoads).hasValue(1);
    }

    @Test
    void sharedCacheOutageStillUsesTheLocalFallback() {
        when(valueOperations.get(PREFIX + ":offline")).thenThrow(new IllegalStateException("unavailable"));
        when(valueOperations.setIfAbsent(PREFIX + ":offline", "db", REMOTE_TTL))
                .thenThrow(new IllegalStateException("unavailable"));
        AtomicInteger dbLoads = new AtomicInteger();
        for (int index = 0; index < 10; index++) {
            assertThat(cache.findValue("offline", () -> {
                dbLoads.incrementAndGet();
                return Optional.of("db");
            })).contains("db");
        }
        assertThat(dbLoads).hasValue(1);
        verify(valueOperations).get(PREFIX + ":offline");
    }

    @Test
    void databaseBackfillPreservesANewerSharedValue() {
        when(valueOperations.get(PREFIX + ":updated")).thenReturn(null, "new");
        when(valueOperations.setIfAbsent(PREFIX + ":updated", "old", REMOTE_TTL)).thenReturn(false);

        assertThat(cache.findValue("updated", () -> Optional.of("old"))).contains("new");
        assertThat(cache.findValue("updated", Optional::empty)).contains("new");
        verify(valueOperations, never()).set(PREFIX + ":updated", "old", REMOTE_TTL);
    }

    @Test
    void tokenRevocationReadsAlwaysObserveTheSharedValue() {
        when(valueOperations.get(PREFIX + ":security.jwt.iat-floor")).thenReturn("10", "20");

        assertThat(cache.findValue("security.jwt.iat-floor", Optional::empty)).contains("10");
        assertThat(cache.findValue("security.jwt.iat-floor", Optional::empty)).contains("20");
        verify(valueOperations, times(2)).get(PREFIX + ":security.jwt.iat-floor");
    }

    @Test
    void tokenRevocationWritesUseABoundedSharedTtl() {
        cache.put("security.jwt.iat-floor", "20");

        verify(valueOperations).set(PREFIX + ":security.jwt.iat-floor", "20", Duration.ofSeconds(30));
    }

    @Test
    void failedTokenRevocationWriteIsNotReportedAsSuccessful() {
        doThrow(new IllegalStateException("unavailable")).when(valueOperations)
                .set(PREFIX + ":security.jwt.iat-floor", "20", Duration.ofSeconds(30));

        assertThatThrownBy(() -> cache.put("security.jwt.iat-floor", "20"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not acknowledged");
        verify(redisTemplate).delete(PREFIX + ":security.jwt.iat-floor");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for cache test latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redisTemplate) {
        return new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject() {
                return redisTemplate;
            }
        };
    }
}
