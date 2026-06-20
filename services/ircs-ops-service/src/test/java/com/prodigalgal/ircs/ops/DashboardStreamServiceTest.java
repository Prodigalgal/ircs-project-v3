package com.prodigalgal.ircs.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;

class DashboardStreamServiceTest {

    private final DashboardSnapshotCacheService cacheService =
            org.mockito.Mockito.mock(DashboardSnapshotCacheService.class);
    private final DashboardSnapshotRefresher snapshotRefresher =
            org.mockito.Mockito.mock(DashboardSnapshotRefresher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<TaskExecutor> taskExecutorProvider = org.mockito.Mockito.mock(ObjectProvider.class);

    @Test
    void snapshotReadsCacheBeforeSynchronousRefresh() {
        DashboardStreamSnapshot snapshot = snapshot();
        when(cacheService.read(50)).thenReturn(Optional.of(snapshot));
        DashboardStreamService service = new DashboardStreamService(
                cacheService,
                snapshotRefresher,
                taskExecutorProvider,
                Duration.ofSeconds(3),
                Duration.ofMinutes(1));

        DashboardStreamSnapshot result = service.snapshot(50);

        assertThat(result).isSameAs(snapshot);
    }

    @Test
    void snapshotReturnsDegradedPartialWhenCacheMisses() {
        when(cacheService.read(50)).thenReturn(Optional.empty());
        DashboardStreamService service = new DashboardStreamService(
                cacheService,
                snapshotRefresher,
                taskExecutorProvider,
                Duration.ofSeconds(3),
                Duration.ofMinutes(1));

        DashboardStreamSnapshot result = service.snapshot(50);

        assertThat(result.partial()).isTrue();
        assertThat(result.stale()).isTrue();
        assertThat(result.partialErrors()).containsEntry("snapshot", "CACHE_MISS");
        assertThat(result.metrics()).isNotNull();
        assertThat(result.taskRuntime()).isNotNull();
        assertThat(result.aggregationOps()).containsEntry("available", false);
        verify(snapshotRefresher).refreshAsync(50);
        verify(snapshotRefresher, never()).composeSnapshot(50);
    }

    @Test
    void snapshotUsesLocalSnapshotWhenRedisCacheMissesAfterFirstRead() {
        DashboardStreamSnapshot snapshot = snapshot();
        when(cacheService.read(50)).thenReturn(Optional.of(snapshot), Optional.empty());
        DashboardStreamService service = new DashboardStreamService(
                cacheService,
                snapshotRefresher,
                taskExecutorProvider,
                Duration.ofSeconds(3),
                Duration.ofMinutes(1));

        DashboardStreamSnapshot cached = service.snapshot(50);
        DashboardStreamSnapshot result = service.snapshot(50);

        assertThat(cached).isSameAs(snapshot);
        assertThat(result.sequence()).isEqualTo(1L);
        assertThat(result.stale()).isTrue();
        verify(snapshotRefresher).refreshAsync(50);
        verify(snapshotRefresher, never()).composeSnapshot(50);
    }

    private DashboardStreamSnapshot snapshot() {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        return new DashboardStreamSnapshot(
                now,
                now,
                1L,
                false,
                false,
                Map.of(),
                null,
                null,
                Map.of(),
                Map.of());
    }
}
