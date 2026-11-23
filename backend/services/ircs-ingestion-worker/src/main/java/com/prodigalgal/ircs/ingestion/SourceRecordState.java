package com.prodigalgal.ircs.ingestion;

import java.time.Instant;
import java.util.UUID;

public record SourceRecordState(
        UUID id,
        String dataHash,
        String normalizationStatus,
        UUID currentVersionId,
        UUID playbackVersionId,
        Instant observedAt
) {
    boolean playbackCurrent() {
        return currentVersionId != null && currentVersionId.equals(playbackVersionId);
    }
}
