package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
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
                PREFIX + "Global:Scraper:Ip:203.0.113.10",
                PREFIX + "cred:" + credentialId,
                PREFIX + "Domain:stale.example");
        when(valueOps.get(PREFIX + "Provider:TMDB")).thenReturn(String.valueOf(now + 2500L));
        when(valueOps.get(PREFIX + "Global:Scraper:Ip:203.0.113.10")).thenReturn(String.valueOf(now + 1500L));
        when(valueOps.get(PREFIX + "cred:" + credentialId)).thenReturn(String.valueOf(now + 35_000L));
        when(valueOps.get(PREFIX + "Domain:stale.example")).thenReturn(String.valueOf(now - 120_000L));
        when(credentialLabelRepository.findLabel(credentialId)).thenReturn(Optional.of("[TMDB] main"));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertEquals(2, status.globalLimiters().size());
        assertEquals("Global:Scraper:Ip:203.0.113.10", status.globalLimiters().getFirst().key());
        assertEquals("全局锁: Scraper / IP 203.0.113.10", status.globalLimiters().getFirst().label());
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
    void exposesCredentialTokenBucketsStoredAsRedisHashes() {
        UUID credentialId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String key = PREFIX + "cred:" + credentialId;
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        HashOperations<String, Object, Object> hashOps = org.mockito.Mockito.mock(HashOperations.class);

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.type(key)).thenReturn(DataType.HASH);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(key);
        when(hashOps.entries(key)).thenReturn(Map.of(
                "tokens", "17",
                "capacity", "30",
                "permits", "1",
                "retry_after_ms", "0"));
        when(credentialLabelRepository.findLabel(credentialId)).thenReturn(Optional.of("[TMDB] main"));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertEquals(1, status.credentialLimiters().size());
        TrafficSlotResponse slot = status.credentialLimiters().getFirst();
        assertEquals("[TMDB] main", slot.label());
        assertEquals("TOKEN_BUCKET", slot.limiterType());
        assertEquals(17L, slot.remainingPermits());
        assertEquals(30L, slot.capacity());
        assertEquals(0L, slot.waitingTasks());
        assertFalse(slot.isBlocked());
        assertEquals(0.43, slot.congestionRate());
    }

    @Test
    void includesEnabledTmdbCredentialsWithoutRuntimeBucket() {
        UUID firstCredentialId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID secondCredentialId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(credentialLabelRepository.findEnabledProviderLabels("TMDB", "api_key", 100))
                .thenReturn(List.of(
                        new CredentialLabelRepository.CredentialTrafficLabel(
                                firstCredentialId,
                                "TMDB",
                                "Default TMDB Key 1",
                                40),
                        new CredentialLabelRepository.CredentialTrafficLabel(
                                secondCredentialId,
                                "TMDB",
                                "Default TMDB Key 2",
                                40)));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertEquals(2, status.credentialLimiters().size());
        TrafficSlotResponse first = status.credentialLimiters().getFirst();
        assertEquals("cred:" + firstCredentialId, first.key());
        assertEquals("[TMDB] Default TMDB Key 1", first.label());
        assertEquals("TOKEN_BUCKET", first.limiterType());
        assertEquals(40L, first.remainingPermits());
        assertEquals(40L, first.capacity());
        assertFalse(first.isBlocked());
    }

    @Test
    void labelsDatasourceIpLimiter() {
        UUID sourceId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        long now = System.currentTimeMillis();
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(PREFIX + "DataSource:Ip:203.0.113.10:" + sourceId);
        when(valueOps.get(PREFIX + "DataSource:Ip:203.0.113.10:" + sourceId))
                .thenReturn(String.valueOf(now + 1200L));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertEquals(1, status.globalLimiters().size());
        assertEquals("数据源锁: " + sourceId + " / IP 203.0.113.10", status.globalLimiters().getFirst().label());
    }

    @Test
    void labelsProviderAndDomainIpLimiters() {
        long now = System.currentTimeMillis();
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                PREFIX + "Provider:Ip:2001_db8__10:DOUBAN",
                PREFIX + "Domain:Ip:203.0.113.10:img.example.com");
        when(valueOps.get(PREFIX + "Provider:Ip:2001_db8__10:DOUBAN"))
                .thenReturn(String.valueOf(now + 1200L));
        when(valueOps.get(PREFIX + "Domain:Ip:203.0.113.10:img.example.com"))
                .thenReturn(String.valueOf(now + 800L));

        TrafficStatusResponse status = service.getTrafficStatus();

        assertEquals(2, status.globalLimiters().size());
        assertEquals("域名锁: img.example.com / IP 203.0.113.10", status.globalLimiters().getFirst().label());
        assertEquals("全局锁: DOUBAN / IP 2001_db8__10", status.globalLimiters().get(1).label());
    }

    @Test
    void resetDeletesNamespacedTrafficKey() {
        service.resetKey("Provider:TMDB");

        verify(redisTemplate).delete(PREFIX + "Provider:TMDB");
    }
}
