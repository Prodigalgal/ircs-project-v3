package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TrafficMonitorServiceTest {

    private static final String PREFIX = "traffic:limit:";

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final CredentialLabelRepository credentialLabelRepository =
            org.mockito.Mockito.mock(CredentialLabelRepository.class);
    private final TrafficMonitorService service = new TrafficMonitorService(redisTemplate, credentialLabelRepository);

    @Test
    void groupsGlobalAndCredentialLimitersAndSkipsStaleKeys() {
        UUID credentialId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        long now = System.currentTimeMillis();
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(cursor.hasNext()).thenReturn(true, true, true, true, false);
        when(cursor.next()).thenReturn(
                PREFIX + "Provider:TMDB",
                PREFIX + "Global:Scraper",
                PREFIX + "cred:" + credentialId,
                PREFIX + "Domain:stale.example");
        when(valueOps.get(PREFIX + "Provider:TMDB")).thenReturn(String.valueOf(now + 2500L));
        when(valueOps.get(PREFIX + "Global:Scraper")).thenReturn(String.valueOf(now + 1500L));
        when(valueOps.get(PREFIX + "cred:" + credentialId)).thenReturn(String.valueOf(now + 35_000L));
        when(valueOps.get(PREFIX + "Domain:stale.example")).thenReturn(String.valueOf(now - 120_000L));
        when(credentialLabelRepository.findLabel(credentialId)).thenReturn(Optional.of("[TMDB] main"));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertEquals(2, status.globalLimiters().size());
        assertEquals("Global:Scraper", status.globalLimiters().getFirst().key());
        assertEquals("全局锁: Scraper", status.globalLimiters().getFirst().label());
        assertEquals("Provider:TMDB", status.globalLimiters().get(1).key());
        assertFalse(status.globalLimiters().get(1).isBlocked());
        assertEquals(1, status.credentialLimiters().size());
        assertEquals("[TMDB] main", status.credentialLimiters().getFirst().label());
        assertTrue(status.credentialLimiters().getFirst().isBlocked());
    }

    @Test
    void returnsEmptyStatusWhenRedisScanFails() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenThrow(new IllegalStateException("redis down"));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertTrue(status.globalLimiters().isEmpty());
        assertTrue(status.credentialLimiters().isEmpty());
    }

    @Test
    void resetDeletesNamespacedTrafficKey() {
        service.resetKey("Provider:TMDB");

        verify(redisTemplate).delete(PREFIX + "Provider:TMDB");
    }
}
