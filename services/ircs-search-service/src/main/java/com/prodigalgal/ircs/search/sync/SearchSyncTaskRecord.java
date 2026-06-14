package com.prodigalgal.ircs.search.sync;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.time.Instant;
import java.util.UUID;

record SearchSyncTaskRecord(
        long id,
        SearchEntityType entityType,
        UUID entityId,
        SyncOperation operation,
        String status,
        int retryCount,
        Instant createdAt,
        Instant nextRetryAt,
        String sourceService,
        String correlationId) {
}
