package com.prodigalgal.ircs.portal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortalMovieCard(
        UUID id,
        String title,
        String aliasTitle,
        Integer season,
        String subtitle,
        String posterUrl,
        BigDecimal rating,
        String releaseYear,
        String categoryName,
        String totalEpisodes,
        String duration,
        String remarks,
        String area,
        String description,
        List<String> genres,
        Instant lastTrendAt,
        PortalRankingInfo ranking) {

    public PortalMovieCard(UUID id, String title, String aliasTitle, Integer season, String subtitle,
            String posterUrl, BigDecimal rating, String releaseYear, String categoryName, String totalEpisodes,
            String duration, String remarks, String area, String description, List<String> genres, Instant lastTrendAt) {
        this(id, title, aliasTitle, season, subtitle, posterUrl, rating, releaseYear, categoryName, totalEpisodes,
                duration, remarks, area, description, genres, lastTrendAt, null);
    }

    public PortalMovieCard withRanking(PortalRankingInfo info) {
        return new PortalMovieCard(id, title, aliasTitle, season, subtitle, posterUrl, rating, releaseYear,
                categoryName, totalEpisodes, duration, remarks, area, description, genres, lastTrendAt, info);
    }
}
