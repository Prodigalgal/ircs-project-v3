package com.prodigalgal.ircs.common.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class MetadataNameCacheIntegrationTest {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate store;
    private MetadataNameCache cache;
    private String prefix;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        store = new StringRedisTemplate(connectionFactory);
        prefix = "ircs-test:metadata:" + UUID.randomUUID();
        cache = new MetadataNameCache(new ObjectProvider<>() {
            @Override public StringRedisTemplate getObject() { return store; }
        }, prefix, Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        try {
            if (cache != null) cache.evictAll();
        } finally {
            if (connectionFactory != null) connectionFactory.destroy();
        }
    }

    @Test
    void mergeAndReplacementExpireAndRemovePreviousOwners() {
        UUID oldOwner = UUID.randomUUID();
        UUID currentOwner = UUID.randomUUID();
        cache.putNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS,
                Map.of(oldOwner, Set.of("Old")));
        cache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS,
                Map.of(currentOwner, Set.of("Current")));

        assertThat(cache.findNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS,
                List.of(oldOwner, currentOwner))).containsOnlyKeys(currentOwner);
        assertThat(store.getExpire(prefix + ":raw:actors", TimeUnit.MILLISECONDS)).isBetween(1L, 30000L);
        cache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS, Map.of());
        assertThat(store.hasKey(prefix + ":raw:actors")).isFalse();
    }

    @Test
    void concurrentReadersOnlyObserveCompleteReplacements() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<UUID, Set<String>> before = Map.of(first, Set.of("Before"), second, Set.of("Before"));
        Map<UUID, Set<String>> after = Map.of(first, Set.of("After"), second, Set.of("After"));
        cache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS, before);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var writer = executor.submit(() -> {
                for (int attempt = 0; attempt < 100; attempt++) {
                    cache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS,
                            attempt % 2 == 0 ? before : after);
                }
            });
            for (int attempt = 0; attempt < 100; attempt++) {
                assertThat(cache.findNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS,
                        List.of(first, second))).isIn(before, after);
            }
            writer.get(10, TimeUnit.SECONDS);
        }
    }
}
