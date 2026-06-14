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
        return ResponseEntity.ok(dashboardQueryService.getMetrics());
    }

    @GetMapping("/task-runtime")
    public ResponseEntity<TaskRuntimeOverviewResponse> taskRuntime(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(dashboardQueryService.getTaskRuntimeOverview(limit));
    }

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter metricsStream() {
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            emitter.send(dashboardQueryService.getMetrics());
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @GetMapping("/source-quality")
    public ResponseEntity<List<SourceQualityResponse>> sourceQuality() {
        return ResponseEntity.ok(dashboardQueryService.getSourceQuality());
    }
}
