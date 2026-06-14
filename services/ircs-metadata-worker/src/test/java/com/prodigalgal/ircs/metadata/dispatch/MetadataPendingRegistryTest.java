package com.prodigalgal.ircs.metadata.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class MetadataPendingRegistryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Test
    void registerAddsProvidersAndTtlAtomically() {
        UUID id = UUID.randomUUID();
        MetadataPendingRegistry registry = new MetadataPendingRegistry(redisTemplate);

        registry.register(id, List.of(ProviderType.DOUBAN, ProviderType.TMDB), Duration.ofMinutes(30));

        String key = registry.key(id);
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(key)),
                eq("DOUBAN"),
                eq("TMDB"),
                eq("1800000"));
        verify(redisTemplate, never()).expire(eq(key), any(Duration.class));
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    void completeRemovesProviderAndReturnsRemainingCountAtomically() {
        UUID id = UUID.randomUUID();
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("enrich:pending:" + id)),
                eq("TMDB")))
                .thenReturn(1L);
        MetadataPendingRegistry registry = new MetadataPendingRegistry(redisTemplate);

        MetadataPendingRegistry.PendingCompletion result = registry.complete(id, ProviderType.TMDB);

        assertEquals(true, result.tracked());
        assertEquals(1, result.remaining());
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("enrich:pending:" + id)),
                eq("TMDB"));
        verify(redisTemplate, never()).hasKey("enrich:pending:" + id);
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    void completeReturnsUntrackedWhenPendingKeyDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("enrich:pending:" + id)),
                eq("TMDB")))
                .thenReturn(-1L);
        MetadataPendingRegistry registry = new MetadataPendingRegistry(redisTemplate);

        MetadataPendingRegistry.PendingCompletion result = registry.complete(id, ProviderType.TMDB);

        assertEquals(false, result.tracked());
        assertEquals(0, result.remaining());
        verify(redisTemplate, never()).hasKey("enrich:pending:" + id);
        verify(redisTemplate, never()).opsForSet();
    }
}
