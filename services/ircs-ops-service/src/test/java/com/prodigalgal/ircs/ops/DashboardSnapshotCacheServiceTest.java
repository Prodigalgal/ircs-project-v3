package com.prodigalgal.ircs.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class DashboardSnapshotCacheServiceTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);

    @Test
    void readsFreshStaleAndExpiredSnapshots() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Instant base = Instant.parse("2026-06-20T00:00:00Z");
        DashboardSnapshotCacheService writer = service(base);
        DashboardStreamSnapshot snapshot = snapshot(base);

        writer.write(50, snapshot);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(
                eq(DashboardSnapshotCacheService.DEFAULT_PREFIX + ":limit:50"),
                payload.capture(),
                any(Duration.class));

        when(valueOps.get(DashboardSnapshotCacheService.DEFAULT_PREFIX + ":limit:50"))
                .thenReturn(payload.getValue());
        assertThat(service(base.plusSeconds(5)).read(50))
                .hasValueSatisfying(value -> assertThat(value.stale()).isFalse());
        assertThat(service(base.plusSeconds(7)).read(50))
                .hasValueSatisfying(value -> assertThat(value.stale()).isTrue());
        assertThat(service(base.plusSeconds(61)).read(50)).isEmpty();
    }

    private DashboardSnapshotCacheService service(Instant now) throws Exception {
        RuntimeConfigService runtimeConfig = org.mockito.Mockito.mock(RuntimeConfigService.class);
        when(runtimeConfig.stringValue(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(runtimeConfig.positiveDurationValue(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        DashboardSnapshotCacheService service = new DashboardSnapshotCacheService(
                JsonMapper.builder().findAndAddModules().build(),
                redisTemplate,
                runtimeConfig);
        service.setClockForTests(Clock.fixed(now, ZoneOffset.UTC));
        return service;
    }

    private DashboardStreamSnapshot snapshot(Instant now) {
        return new DashboardStreamSnapshot(
                now,
                now,
                1L,
                false,
                false,
                Map.of(),
                null,
                null,
                Map.of("available", true),
                Map.of("available", true));
    }
}
