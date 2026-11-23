package com.prodigalgal.ircs.aggregation;

import java.time.LocalDate;
import java.util.UUID;

record AuthorityCatalogProjection(
        UUID workId,
        String mediaType,
        Long tmdbId,
        String imdbId,
        String titleKey,
        String originalTitleKey,
        Integer releaseYear,
        LocalDate workReleasedOn,
        Integer regularSeasonCount,
        int seasonNumber,
        LocalDate seasonReleasedOn,
        UUID unifiedVideoId) {
}
