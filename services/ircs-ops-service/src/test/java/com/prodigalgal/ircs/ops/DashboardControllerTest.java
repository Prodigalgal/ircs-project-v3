package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DashboardControllerTest {

    private final DashboardQueryService dashboardQueryService = org.mockito.Mockito.mock(DashboardQueryService.class);
    private final DashboardController controller = new DashboardController(dashboardQueryService);

    @Test
    void returnsStatistics() {
        DashboardStatsResponse stats = new DashboardStatsResponse(1, 0, 2, 0, 3, 4, 5, 6, 7, 8, 9, 10);
        when(dashboardQueryService.getStatistics()).thenReturn(stats);

        assertEquals(stats, controller.statistics().getBody());
        verify(dashboardQueryService).getStatistics();
    }

    @Test
    void returnsAnalysis() {
        DashboardAnalysisResponse analysis = new DashboardAnalysisResponse(
                new DashboardStatsResponse(1, 0, 2, 0, 3, 0, 0, 0, 0, 0, 0, 0),
                List.of(new ChartDataPoint("2026-06-04", 1)),
                List.of(),
                List.of(),
                List.of(),
                Map.of("Both", 1L),
                List.of(),
                List.of());
        when(dashboardQueryService.getAnalysis(14)).thenReturn(analysis);

        assertEquals(analysis, controller.analysis(14).getBody());
        verify(dashboardQueryService).getAnalysis(14);
    }

    @Test
    void refreshReturnsNoContent() {
        assertEquals(HttpStatus.NO_CONTENT, controller.refresh(14).getStatusCode());
        verify(dashboardQueryService).refresh(14);
    }

    @Test
    void returnsTaskRuntimeOverview() {
        TaskRuntimeOverviewResponse overview = new TaskRuntimeOverviewResponse(
                java.time.Instant.parse("2026-06-13T00:00:00Z"),
                25,
                1,
                1,
                0,
                3,
                1,
                0,
                0,
                10,
                4,
                4,
                0,
                6,
                Map.of("RUNNING", 1L),
                Map.of("RUNNING", 1L),
                List.of());
        when(dashboardQueryService.getTaskRuntimeOverview(25)).thenReturn(overview);

        assertEquals(overview, controller.taskRuntime(25).getBody());
        verify(dashboardQueryService).getTaskRuntimeOverview(25);
    }

    @Test
    void returnsMetricsStreamEmitter() {
        when(dashboardQueryService.getMetrics()).thenReturn(new SystemMetricsResponse(
                0, 0, 0, 0, 0, 0, 0, List.of(), 0,
                null, 0, 0, true, 1, Map.of()));

        assertNotNull(controller.metricsStream());
        verify(dashboardQueryService).getMetrics();
    }
}
