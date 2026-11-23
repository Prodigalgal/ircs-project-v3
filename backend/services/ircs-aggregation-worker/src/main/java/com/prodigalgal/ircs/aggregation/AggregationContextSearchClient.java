package com.prodigalgal.ircs.aggregation;

import java.util.List;
import java.util.UUID;

interface AggregationContextSearchClient {

    ContextSearchResult findCandidateCatalogEntryIds(String title, String year);

    record ContextSearchResult(boolean attempted, List<UUID> catalogEntryIds) {

        public ContextSearchResult {
            catalogEntryIds = catalogEntryIds == null ? List.of() : List.copyOf(catalogEntryIds);
        }

        static ContextSearchResult attempted(List<UUID> catalogEntryIds) {
            return new ContextSearchResult(true, catalogEntryIds);
        }

        static ContextSearchResult notAttempted() {
            return new ContextSearchResult(false, List.of());
        }
    }
}
