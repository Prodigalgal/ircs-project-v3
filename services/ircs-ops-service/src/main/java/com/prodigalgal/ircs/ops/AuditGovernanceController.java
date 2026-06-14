package com.prodigalgal.ircs.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops/audit-governance")
public class AuditGovernanceController {

    private final AuditGovernanceQueryService queryService;

    @GetMapping("/archive/summary")
    public ResponseEntity<AuditArchiveSummaryResponse> archiveSummary() {
        return ResponseEntity.ok(queryService.archiveSummary());
    }

    @GetMapping("/es-outbox/summary")
    public ResponseEntity<AuditEsReplicationOutboxSummaryResponse> outboxSummary() {
        return ResponseEntity.ok(queryService.outboxSummary());
    }
}
