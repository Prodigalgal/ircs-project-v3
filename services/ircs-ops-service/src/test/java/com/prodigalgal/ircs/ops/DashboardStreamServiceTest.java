package com.prodigalgal.ircs.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        verify(snapshotRefresher, never()).refresh(50);
    }

    @Test
    void snapshotRefreshesAndWritesWhenCacheMisses() {
        DashboardStreamSnapshot snapshot = snapshot();
        when(cacheService.read(50)).thenReturn(Optional.empty());
        when(snapshotRefresher.refresh(50)).thenReturn(snapshot);
        DashboardStreamService service = new DashboardStreamService(
                cacheService,
                snapshotRefresher,
                taskExecutorProvider,
                Duration.ofSeconds(3),
                Duration.ofMinutes(1));

        DashboardStreamSnapshot result = service.snapshot(50);

        assertThat(result).isSameAs(snapshot);
        verify(snapshotRefresher).refresh(50);
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
