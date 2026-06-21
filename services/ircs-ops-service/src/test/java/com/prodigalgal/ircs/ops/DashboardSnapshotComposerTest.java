package com.prodigalgal.ircs.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DashboardSnapshotComposerTest {

    private final DashboardQueryService dashboardQueryService = org.mockito.Mockito.mock(DashboardQueryService.class);
    private final RuntimeConfigService runtimeConfig = org.mockito.Mockito.mock(RuntimeConfigService.class);
    private final DashboardSnapshotComposer composer = new DashboardSnapshotComposer(
            dashboardQueryService,
            runtimeConfig);

    @Test
    void startsNextRefreshEvenWhenPreviousBranchesIgnoreCancellation() throws Exception {
        AtomicInteger metricsCalls = new AtomicInteger();
        AtomicInteger taskRuntimeCalls = new AtomicInteger();
        AtomicInteger searchOpsCalls = new AtomicInteger();
        AtomicInteger aggregationOpsCalls = new AtomicInteger();
        when(runtimeConfig.positiveDurationValue(
                "app.ops.dashboard.snapshot.refresh-budget",
                Duration.ofSeconds(6))).thenReturn(Duration.ofMillis(100));
        when(dashboardQueryService.getMetrics()).thenAnswer(invocation -> metrics(metricsCalls));
        when(dashboardQueryService.getTaskRuntimeOverview(50)).thenAnswer(invocation -> taskRuntime(taskRuntimeCalls));
        when(dashboardQueryService.getSearchOpsStats()).thenAnswer(invocation -> searchOps(searchOpsCalls));
        when(dashboardQueryService.getAggregationOpsStats()).thenAnswer(invocation -> aggregationOps(aggregationOpsCalls));

        DashboardStreamSnapshot first = composer.compose(50);

        DashboardStreamSnapshot second = CompletableFuture.supplyAsync(() -> composer.compose(50))
                .get(1, TimeUnit.SECONDS);

        assertThat(first.partial()).isTrue();
        assertThat(first.partialErrors()).containsOnlyKeys("metrics", "taskRuntime", "searchOps", "aggregationOps");
        assertThat(second.partial()).isFalse();
        assertThat(second.partialErrors()).isEmpty();
        assertThat(second.metrics()).isNotNull();
        assertThat(second.taskRuntime()).isNotNull();
        assertThat(second.searchOps()).containsEntry("available", true);
        assertThat(second.aggregationOps()).containsEntry("available", true);
    }

    private SystemMetricsResponse metrics(AtomicInteger calls) throws Exception {
        slowFirstCall(calls);
        return DashboardSnapshotFallbacks.metrics("TEST");
    }

    private TaskRuntimeOverviewResponse taskRuntime(AtomicInteger calls) throws Exception {
        slowFirstCall(calls);
        return taskRuntime();
    }

    private Map<String, Object> searchOps(AtomicInteger calls) throws Exception {
        slowFirstCall(calls);
        return Map.of("available", true, "service", "search");
    }

    private Map<String, Object> aggregationOps(AtomicInteger calls) throws Exception {
        slowFirstCall(calls);
        return Map.of("available", true, "service", "aggregation");
    }

    private void slowFirstCall(AtomicInteger calls) throws InterruptedException {
        if (calls.incrementAndGet() == 1) {
            Thread.sleep(500);
        }
    }

    private TaskRuntimeOverviewResponse taskRuntime() {
        return new TaskRuntimeOverviewResponse(
                java.time.Instant.parse("2026-06-21T00:00:00Z"),
                50,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                List.of());
    }
}
