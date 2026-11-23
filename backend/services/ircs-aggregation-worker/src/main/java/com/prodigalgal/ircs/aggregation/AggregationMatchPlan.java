package com.prodigalgal.ircs.aggregation;

import java.util.List;
import java.util.UUID;

public record AggregationMatchPlan(UUID rootCatalogEntryId, List<UUID> victimCatalogEntryIds, String resolutionReason) {

    public AggregationMatchPlan {
        victimCatalogEntryIds = List.copyOf(victimCatalogEntryIds);
    }

    public AggregationMatchPlan(UUID rootCatalogEntryId, List<UUID> victimCatalogEntryIds) {
        this(rootCatalogEntryId, victimCatalogEntryIds, null);
    }

    public static AggregationMatchPlan ambiguous() {
        return new AggregationMatchPlan(null, List.of(), "MULTIPLE_IDENTITY_CANDIDATES");
    }

    public boolean isAmbiguous() {
        return resolutionReason != null;
    }

    public static AggregationMatchPlan none() {
        return new AggregationMatchPlan(null, List.of());
    }

    public static AggregationMatchPlan rootOnly(UUID rootCatalogEntryId) {
        return new AggregationMatchPlan(rootCatalogEntryId, List.of());
    }

    public static AggregationMatchPlan rootWithVictims(UUID rootCatalogEntryId, List<UUID> victimCatalogEntryIds) {
        return new AggregationMatchPlan(rootCatalogEntryId, List.copyOf(victimCatalogEntryIds));
    }

    public boolean hasRoot() {
        return rootCatalogEntryId != null;
    }

    public boolean hasVictims() {
        return !victimCatalogEntryIds.isEmpty();
    }
}
