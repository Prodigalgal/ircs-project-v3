package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import java.time.Instant;
import java.util.List;

interface TrendListProvider {

    String name();

    List<TrendItemPayload> fetchTrending();

    default List<TrendBoardPayload> fetchBoards() {
        return List.of(new TrendBoardPayload(name(), "legacy", "all", "legacy",
                Instant.now(), "UNAVAILABLE", "LEGACY_PROVIDER_UNRANKED", List.of()));
    }
}
