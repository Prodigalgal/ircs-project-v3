package com.prodigalgal.ircs.task.domain;

import java.util.UUID;

public record CatalogDiscoveryCandidate(
        UUID catalogEntryId,
        int targetSeason,
        int targetEpisode,
        String keyword,
        int attempts,
        UUID leaseToken) {

    public String requestKey() {
        return catalogEntryId + ":" + targetSeason + ":" + targetEpisode;
    }
}
