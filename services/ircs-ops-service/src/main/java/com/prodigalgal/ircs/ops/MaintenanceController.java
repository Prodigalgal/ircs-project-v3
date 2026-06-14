package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.maintenance.MaintenanceGateCheckKind;
import com.prodigalgal.ircs.common.maintenance.MaintenanceGateDecision;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops/maintenance")
public class MaintenanceController {

    private final MaintenanceSessionService maintenanceSessionService;
    private final MaintenanceRunnerService maintenanceRunnerService;
    private final MaintenanceSchedulerService maintenanceSchedulerService;
    private final MaintenanceGateService maintenanceGateService;

    @GetMapping("/active")
    public ResponseEntity<MaintenanceSessionInfo> active() {
        return maintenanceSessionService.activeSession()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/runners")
    public ResponseEntity<Iterable<MaintenanceRunnerMetadata>> runners() {
        return ResponseEntity.ok(maintenanceRunnerService.registry());
    }

    @GetMapping("/scheduler")
    public ResponseEntity<MaintenanceSchedulerStatusResponse> scheduler() {
        return ResponseEntity.ok(maintenanceSchedulerService.status());
    }

    @PostMapping("/scheduler/run-once")
    public ResponseEntity<MaintenanceSchedulerStatusResponse> runSchedulerOnce() {
        return ResponseEntity.ok(maintenanceSchedulerService.runOnce());
    }

    @GetMapping("/gates")
    public ResponseEntity<Iterable<MaintenanceGateResponse>> gates() {
        return ResponseEntity.ok(maintenanceGateService.active());
    }

    @PostMapping("/gates")
    public ResponseEntity<MaintenanceGateResponse> createGate(@Valid @RequestBody MaintenanceGateCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceGateService.create(request));
    }

    @PostMapping("/gates/{id}/close")
    public ResponseEntity<MaintenanceGateResponse> closeGate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) MaintenanceGateCloseRequest request) {
        return ResponseEntity.ok(maintenanceGateService.close(id, request));
    }

    @GetMapping("/gates/check")
    public ResponseEntity<MaintenanceGateDecision> checkGate(
            @RequestParam String ownerService,
            @RequestParam String resourceType,
            @RequestParam(defaultValue = "*") String resourceScope,
            @RequestParam(defaultValue = "WRITE") MaintenanceGateCheckKind checkKind) {
        return ResponseEntity.ok(maintenanceGateService.check(ownerService, resourceType, resourceScope, checkKind));
    }

    @PostMapping("/sanitize/init")
    public ResponseEntity<Map<String, String>> initSanitize() {
        return session("sanitize");
    }

    @PostMapping("/aggregation/reset-init")
    public ResponseEntity<Map<String, String>> initAggregationReset() {
        return session("aggregation-reset");
    }

    @PostMapping("/unified/recalculate-init")
    public ResponseEntity<Map<String, String>> initUnifiedRecalculate() {
        return session("unified-recalculate");
    }

    @PostMapping("/area/clean-init")
    public ResponseEntity<Map<String, String>> initAreaCleaning() {
        return session("area-clean");
    }

    @PostMapping("/language/clean-init")
    public ResponseEntity<Map<String, String>> initLanguageCleaning() {
        return session("language-clean");
    }

    @PostMapping("/llm/sanitize/init")
    public ResponseEntity<Map<String, String>> initLlmSanitize() {
        return session("llm-sanitize");
    }

    @PostMapping("/llm/area/init")
    public ResponseEntity<Map<String, String>> initLlmAreaClean() {
        return session("llm-area-clean");
    }

    @PostMapping("/llm/genre/init")
    public ResponseEntity<Map<String, String>> initLlmGenreClean() {
        return session("llm-genre-clean");
    }

    @PostMapping("/llm/category/init")
    public ResponseEntity<Map<String, String>> initLlmCategoryClean() {
        return session("llm-category-clean");
    }

    @PostMapping("/search/reindex-raw/init")
    public ResponseEntity<Map<String, String>> initRawReindex() {
        return session("search-reindex-raw");
    }

    @PostMapping("/search/reindex-unified/init")
    public ResponseEntity<Map<String, String>> initUnifiedReindex() {
        return session("search-reindex-unified");
    }

    @PostMapping("/trend/sync")
    public ResponseEntity<Void> triggerTrendSync() {
        maintenanceSessionService.initSession("trend-sync");
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID sessionId) {
        return maintenanceSessionService.stream(sessionId);
    }

    private ResponseEntity<Map<String, String>> session(String taskName) {
        MaintenanceSessionInfo session = maintenanceSessionService.initSession(taskName);
        return ResponseEntity.ok(Map.of("sessionId", session.sessionId().toString()));
    }
}
