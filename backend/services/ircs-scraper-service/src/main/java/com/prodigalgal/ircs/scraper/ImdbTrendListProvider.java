package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** IMDb's free ratings datasets do not provide MOVIEmeter ranks. */
@Component
@RequiredArgsConstructor
class ImdbTrendListProvider implements TrendListProvider {

    private final ScraperTrendConfigValues configValues;

    @Override
    public String name() {
        return "IMDb";
    }

    @Override
    public List<TrendItemPayload> fetchTrending() {
        return List.of();
    }

    @Override
    public List<TrendBoardPayload> fetchBoards() {
        Instant at = Instant.now();
        String status = configValues.imdbEnabled() ? "UNAVAILABLE" : "DISABLED";
        String code = configValues.imdbEnabled() ? "OFFICIAL_TREND_ACCESS_NOT_CONFIGURED" : "SOURCE_DISABLED";
        return List.of(TrendSourceValues.unavailable(name(), "popular", "movie", "popular", at, status, code),
                TrendSourceValues.unavailable(name(), "popular", "tv", "popular", at, status, code));
    }
}
