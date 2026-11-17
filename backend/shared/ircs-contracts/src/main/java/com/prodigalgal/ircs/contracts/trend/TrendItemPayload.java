package com.prodigalgal.ircs.contracts.trend;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendItemPayload(
        String title,
        String originalTitle,
        String description,
        String year,
        LocalDate publishedAt,
        BigDecimal score,
        String posterUrl,
        String tmdbId,
        String doubanId,
        String imdbId,
        String type,
        TrendSignalPayload signal) {

    public TrendItemPayload(String title, String originalTitle, String description, String year,
            LocalDate publishedAt, BigDecimal score, String posterUrl, String tmdbId,
            String doubanId, String imdbId, String type) {
        this(title, originalTitle, description, year, publishedAt, score, posterUrl,
                tmdbId, doubanId, imdbId, type, null);
    }
}
