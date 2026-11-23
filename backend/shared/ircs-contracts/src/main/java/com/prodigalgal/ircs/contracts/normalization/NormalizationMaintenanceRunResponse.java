package com.prodigalgal.ircs.contracts.normalization;

import java.util.List;
import java.util.UUID;

public record NormalizationMaintenanceRunResponse(
        String taskName,
        long sourceRecordCount,
        long changedRows,
        long enqueuedRows,
        List<UUID> sampleSourceRecordIds
) {
    public NormalizationMaintenanceRunResponse {
        sampleSourceRecordIds = sampleSourceRecordIds == null ? List.of() : List.copyOf(sampleSourceRecordIds);
    }
}
