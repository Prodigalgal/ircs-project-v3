package com.prodigalgal.ircs.contracts.aggregation;

import java.util.List;
import java.util.UUID;

public record AggregationResetStepResponse(
        String taskName,
        String stepName,
        long sourceRecordCount,
        long catalogEntryCount,
        long bindingCount,
        long changedRows,
        List<UUID> sampleSourceRecordIds
) {
    public AggregationResetStepResponse {
        sampleSourceRecordIds = sampleSourceRecordIds == null ? List.of() : List.copyOf(sampleSourceRecordIds);
    }
}
