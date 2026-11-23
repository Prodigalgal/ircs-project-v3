package com.prodigalgal.ircs.contracts.trend;

import java.util.List;
import java.util.UUID;

public record TrendSyncApplyResponse(
        String taskName,
        long candidates,
        long updatedByExternalId,
        long updatedByTitleMatch,
        long createdGhosts,
        long skippedDuplicates,
        List<UUID> updatedCatalogEntryIds,
        List<UUID> createdCatalogEntryIds,
        List<String> discoveryKeywords) {

    public TrendSyncApplyResponse {
        updatedCatalogEntryIds = updatedCatalogEntryIds == null ? List.of() : List.copyOf(updatedCatalogEntryIds);
        createdCatalogEntryIds = createdCatalogEntryIds == null ? List.of() : List.copyOf(createdCatalogEntryIds);
        discoveryKeywords = discoveryKeywords == null ? List.of() : List.copyOf(discoveryKeywords);
    }
}
