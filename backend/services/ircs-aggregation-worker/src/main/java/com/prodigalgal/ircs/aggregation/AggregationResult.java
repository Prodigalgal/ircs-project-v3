package com.prodigalgal.ircs.aggregation;

import java.util.UUID;
import java.util.List;

public record AggregationResult(UUID sourceRecordId, UUID catalogEntryId, String status, List<UUID> deletedCatalogEntryIds) {
    public static AggregationResult bound(UUID sourceRecordId, UUID catalogEntryId) {
        return bound(sourceRecordId, catalogEntryId, List.of());
    }

    public static AggregationResult bound(UUID sourceRecordId, UUID catalogEntryId, List<UUID> deletedCatalogEntryIds) {
        return new AggregationResult(sourceRecordId, catalogEntryId, "BOUND", List.copyOf(deletedCatalogEntryIds));
    }

    public static AggregationResult skipped(UUID sourceRecordId, String status) {
        return new AggregationResult(sourceRecordId, null, status, List.of());
    }

    public static AggregationResult pipelineFailed(
            UUID sourceRecordId,
            UUID catalogEntryId,
            List<UUID> deletedCatalogEntryIds) {
        return new AggregationResult(sourceRecordId, catalogEntryId, "PIPELINE_FAILED", List.copyOf(deletedCatalogEntryIds));
    }
}
