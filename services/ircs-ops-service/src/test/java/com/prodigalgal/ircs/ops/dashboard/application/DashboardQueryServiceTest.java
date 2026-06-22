package com.prodigalgal.ircs.ops.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.ops.dashboard.dto.DashboardAnalysisResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.DashboardStatsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.TaskRuntimeOverviewResponse;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.AggregationOpsStatsClient;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.JdbcDashboardRepository;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.SearchOpsStatsClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardQueryServiceTest {

    private final JdbcDashboardRepository repository = org.mockito.Mockito.mock(JdbcDashboardRepository.class);
    private final SystemMetricsService metricsService = org.mockito.Mockito.mock(SystemMetricsService.class);
    private final TaskRuntimeOverviewService taskRuntimeOverviewService =
            org.mockito.Mockito.mock(TaskRuntimeOverviewService.class);
    private final SearchOpsStatsClient searchOpsStatsClient = org.mockito.Mockito.mock(SearchOpsStatsClient.class);
    private final AggregationOpsStatsClient aggregationOpsStatsClient =
            org.mockito.Mockito.mock(AggregationOpsStatsClient.class);
    private final DashboardQueryService service =
            new DashboardQueryService(
                    repository,
                    metricsService,
                    taskRuntimeOverviewService,
                    searchOpsStatsClient,
                    aggregationOpsStatsClient);

    @Test
    void analysisBoundsDaysAndTurnsNullListsIntoEmptyLists() {
        DashboardStatsResponse stats = new DashboardStatsResponse(1, 0, 2, 0, 3, 0, 0, 0, 0, 0, 0, 0);
        when(repository.loadStats()).thenReturn(stats);
        when(repository.weeklyTrend(90)).thenReturn(null);
        when(repository.categoryDistribution()).thenReturn(null);
        when(repository.sourceDistribution()).thenReturn(null);
        when(repository.enrichmentStatusDistribution()).thenReturn(null);
        when(repository.idCoverage()).thenReturn(Map.of("Both", 1L, "None", 0L));
        when(repository.sourceEfficiency()).thenReturn(null);
        when(repository.categoryEfficiency()).thenReturn(List.of());

        DashboardAnalysisResponse analysis = service.getAnalysis(500);

        assertEquals(stats, analysis.stats());
        assertTrue(analysis.weeklyTrend().isEmpty());
        assertTrue(analysis.categoryDistribution().isEmpty());
        assertEquals(Map.of("Both", 1L, "None", 0L), analysis.idCoverage());
        verify(repository).weeklyTrend(90);
    }

    @Test
    void refreshDelegatesToRateMetricsRefresh() {
        service.refresh(14);

        verify(metricsService).refreshRateMetrics();
    }

    @Test
    void taskRuntimeOverviewDelegatesToOverviewService() {
        TaskRuntimeOverviewResponse response = new TaskRuntimeOverviewResponse(
                java.time.Instant.parse("2026-06-13T00:00:00Z"),
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
        when(taskRuntimeOverviewService.currentOverview(25)).thenReturn(response);

        assertEquals(response, service.getTaskRuntimeOverview(25));
        verify(taskRuntimeOverviewService).currentOverview(25);
    }

    @Test
    void opsStatsFallBackToEmptyMapWhenClientFails() {
        when(searchOpsStatsClient.currentStats()).thenThrow(new IllegalStateException("search unavailable"));
        when(aggregationOpsStatsClient.currentStats()).thenThrow(new IllegalStateException("aggregation unavailable"));

        assertEquals(
                Map.of("available", false, "unavailableReason", "SEARCH_OPS_UNAVAILABLE"),
                service.getSearchOpsStats());
        assertEquals(
                Map.of("available", false, "unavailableReason", "AGGREGATION_OPS_UNAVAILABLE"),
                service.getAggregationOpsStats());
    }
}
