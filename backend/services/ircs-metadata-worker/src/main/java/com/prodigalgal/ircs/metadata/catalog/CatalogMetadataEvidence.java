package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;

/** Identity and season verified from actual authority responses before any catalog mutation. */
public record CatalogMetadataEvidence(String tmdbId, String mediaType, int season, EnrichedMetadataDTO metadata) {
}
