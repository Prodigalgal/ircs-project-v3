package com.prodigalgal.ircs.contracts.ingestion;

import java.time.Instant;

public record IngestionItem(
        IngestionVideoDTO video,
        boolean forceIngest,
        Instant observedAt
) {
    public IngestionItem(IngestionVideoDTO video, boolean forceIngest) {
        this(video, forceIngest, null);
    }
}
