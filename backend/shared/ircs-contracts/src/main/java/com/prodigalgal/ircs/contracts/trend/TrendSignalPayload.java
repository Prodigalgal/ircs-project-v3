package com.prodigalgal.ircs.contracts.trend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Source evidence; observedAt must never be used as a release or episode date. */
public record TrendSignalPayload(
        String provider,
        String board,
        String mediaType,
        String externalId,
        Integer position,
        String window,
        BigDecimal popularity,
        Long voteCount,
        Instant observedAt,
        LocalDate eventDate,
        String eventType,
        Integer season,
        Integer episode,
        String region,
        String sourceUrl,
        String categoryCode) {
}
