package com.prodigalgal.ircs.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditGovernanceQueryService {

    private final AuditGovernanceRepository repository;

    public AuditArchiveSummaryResponse archiveSummary() {
        return repository.archiveSummary();
    }

    public AuditEsReplicationOutboxSummaryResponse outboxSummary() {
        return repository.outboxSummary();
    }
}
