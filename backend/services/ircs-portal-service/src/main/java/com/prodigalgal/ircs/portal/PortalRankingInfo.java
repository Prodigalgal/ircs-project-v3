package com.prodigalgal.ircs.portal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PortalRankingInfo(List<String> sources, Instant observedAt, boolean stale,
        LocalDate eventDate, String eventType, Integer latestSeason, Integer latestEpisode,
        boolean resourceAvailable, Integer availableEpisode, BigDecimal heat) {
}
