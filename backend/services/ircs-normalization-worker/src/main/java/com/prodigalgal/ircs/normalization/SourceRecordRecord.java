package com.prodigalgal.ircs.normalization;

import java.math.BigDecimal;
import java.util.UUID;

public record SourceRecordRecord(
        UUID id,
        String normalizationStatus,
        Integer normalizationRetryCount,
        String rawMetadata,
        String lockedFields,
        String title,
        String aliasTitle,
        Integer season,
        String subtitle,
        String description,
        String year,
        String area,
        String rawLanguageStr,
        String remarks,
        BigDecimal score,
        String totalEpisodes,
        String duration,
        String doubanId,
        String tmdbId,
        String imdbId,
        String rottenTomatoesId,
        UUID dataSourceId,
        String dataHash,
        String categoryCode,
        String sourceCategoryName
) {
    // Preserve callers that do not yet supply the persisted classification context.
    public SourceRecordRecord(
            UUID id, String normalizationStatus, Integer normalizationRetryCount, String rawMetadata,
            String lockedFields, String title, String aliasTitle, Integer season, String subtitle,
            String description, String year, String area, String rawLanguageStr, String remarks,
            BigDecimal score, String totalEpisodes, String duration, String doubanId, String tmdbId,
            String imdbId, String rottenTomatoesId, UUID dataSourceId, String dataHash) {
        this(id, normalizationStatus, normalizationRetryCount, rawMetadata, lockedFields, title,
                aliasTitle, season, subtitle, description, year, area, rawLanguageStr, remarks,
                score, totalEpisodes, duration, doubanId, tmdbId, imdbId, rottenTomatoesId,
                dataSourceId, dataHash, null, null);
    }
}
