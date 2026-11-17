package com.prodigalgal.ircs.metadata.catalog;

import java.util.UUID;

/** The queue's expected identity is a constraint, never returned provider evidence. */
public record CatalogMetadataTarget(UUID id, UUID leaseToken, String provider, String mediaType,
        String externalId, int season, int attempts, String title, String aliasTitle, String year, String tmdbId) {
}
