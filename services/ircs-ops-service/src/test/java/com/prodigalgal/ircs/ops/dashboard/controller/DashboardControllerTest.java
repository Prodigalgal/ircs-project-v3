package com.prodigalgal.ircs.ops.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.prodigalgal.ircs.ops.dashboard.application.DashboardQueryService;
import com.prodigalgal.ircs.ops.dashboard.application.DashboardStreamService;
import com.prodigalgal.ircs.ops.dashboard.dto.ChartDataPoint;
import com.prodigalgal.ircs.ops.dashboard.dto.DashboardAnalysisResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.DashboardStatsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.RateMetricResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.RedisMetricResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.SystemMetricsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.TaskRuntimeOverviewResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

class DashboardControllerTest {

    private final DashboardQueryService dashboardQueryService = org.mockito.Mockito.mock(DashboardQueryService.class);
    private final DashboardStreamService dashboardStreamService = org.mockito.Mockito.mock(DashboardStreamService.class);
    private final DashboardController controller = new DashboardController(dashboardQueryService, dashboardStreamService);

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
    void returnsMetricsDirectlyFromQueryService() {
        SystemMetricsResponse metrics = emptyMetricsResponse();
        when(dashboardQueryService.getMetrics()).thenReturn(metrics);

        assertEquals(metrics, controller.metrics().getBody());
        verify(dashboardQueryService).getMetrics();
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
    void returnsSearchOpsStats() {
        Map<String, Object> stats = Map.of("worker", Map.of("state", "RUNNING"));
        when(dashboardQueryService.getSearchOpsStats()).thenReturn(stats);

        assertEquals(stats, controller.searchOps().getBody());
        verify(dashboardQueryService).getSearchOpsStats();
    }

    @Test
    void returnsAggregationOpsStats() {
        Map<String, Object> stats = Map.of("worker", Map.of("state", "IDLE"));
        when(dashboardQueryService.getAggregationOpsStats()).thenReturn(stats);

        assertEquals(stats, controller.aggregationOps().getBody());
        verify(dashboardQueryService).getAggregationOpsStats();
    }

    @Test
    void returnsTopicStreamEmitter() {
        SseEmitter emitter = new SseEmitter();
        when(dashboardStreamService.stream("metrics", 25)).thenReturn(emitter);

        assertEquals(emitter, controller.topicStream("metrics", 25));
        verify(dashboardStreamService).stream("metrics", 25);
    }

    @Test
    void rejectsUnknownTopicStreamAsBadRequest() {
        doThrow(new IllegalArgumentException("Unsupported dashboard stream topic: unknown"))
                .when(dashboardStreamService).stream("unknown", 25);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.topicStream("unknown", 25));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private SystemMetricsResponse emptyMetricsResponse() {
        return new SystemMetricsResponse(
                RateMetricResponse.empty("collection.page.discovered", "PT 发现速率", 60, 300),
                RateMetricResponse.empty("collection.detail.completed", "DT 采集速率", 60, 300),
                RateMetricResponse.empty("runtime.aggregation.completed", "清洗速率", 60, 300),
                RateMetricResponse.empty("pipeline.metadata_provider.completed", "元数据速率", 60, 300),
                0,
                0,
                0,
                0,
                List.of(),
                0,
                new RedisMetricResponse(0, 0, 0, "unknown"),
                0,
                0,
                false,
                0,
                Map.of());
    }
}
