package com.prodigalgal.ircs.ops;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final DashboardStreamService dashboardStreamService;

    @GetMapping("/statistics")
    public ResponseEntity<DashboardStatsResponse> statistics() {
        return ResponseEntity.ok(dashboardQueryService.getStatistics());
    }

    @GetMapping("/analysis")
    public ResponseEntity<DashboardAnalysisResponse> analysis(@RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(dashboardQueryService.getAnalysis(days));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@RequestParam(defaultValue = "14") int days) {
        dashboardQueryService.refresh(days);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    public ResponseEntity<SystemMetricsResponse> metrics() {
        DashboardStreamSnapshot snapshot = dashboardStreamService.snapshot(50);
        SystemMetricsResponse metrics = snapshot == null ? null : snapshot.metrics();
        return ResponseEntity.ok(metrics == null ? dashboardQueryService.getMetrics() : metrics);
    }

    @GetMapping("/task-runtime")
    public ResponseEntity<TaskRuntimeOverviewResponse> taskRuntime(@RequestParam(defaultValue = "50") int limit) {
        DashboardStreamSnapshot snapshot = dashboardStreamService.snapshot(limit);
        TaskRuntimeOverviewResponse taskRuntime = snapshot == null ? null : snapshot.taskRuntime();
        return ResponseEntity.ok(taskRuntime == null ? dashboardQueryService.getTaskRuntimeOverview(limit) : taskRuntime);
    }

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter metricsStream(@RequestParam(defaultValue = "50") int limit) {
        return dashboardStreamService.stream(limit);
    }

    @GetMapping("/source-quality")
    public ResponseEntity<List<SourceQualityResponse>> sourceQuality() {
        return ResponseEntity.ok(dashboardQueryService.getSourceQuality());
    }
}
