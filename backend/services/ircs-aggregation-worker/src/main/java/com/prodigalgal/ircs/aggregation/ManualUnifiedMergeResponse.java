package com.prodigalgal.ircs.aggregation;

import java.util.List;
import java.util.UUID;

public record ManualUnifiedMergeResponse(
        UUID rootCatalogEntryId,
        List<UUID> victimCatalogEntryIds,
        List<UUID> sourceRecordIds,
        String status,
        List<AggregationPipelineStageFailure> failures) {
}
