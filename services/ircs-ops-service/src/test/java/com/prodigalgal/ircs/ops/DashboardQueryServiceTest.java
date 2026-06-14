package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardQueryServiceTest {

    private final JdbcDashboardRepository repository = org.mockito.Mockito.mock(JdbcDashboardRepository.class);
    private final SystemMetricsService metricsService = org.mockito.Mockito.mock(SystemMetricsService.class);
    private final TaskRuntimeOverviewService taskRuntimeOverviewService =
            org.mockito.Mockito.mock(TaskRuntimeOverviewService.class);
    private final DashboardQueryService service =
            new DashboardQueryService(repository, metricsService, taskRuntimeOverviewService);

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
    void refreshResetsMetricsWindowOnly() {
        service.refresh(14);

        verify(metricsService).resetThroughputWindow();
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
}
