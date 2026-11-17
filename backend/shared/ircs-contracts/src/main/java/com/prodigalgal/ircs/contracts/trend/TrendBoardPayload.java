package com.prodigalgal.ircs.contracts.trend;

import java.time.Instant;
import java.util.List;

/** A failed/disabled sample does not replace a board's last successful snapshot. */
public record TrendBoardPayload(
        String provider,
        String board,
        String mediaType,
        String window,
        Instant collectedAt,
        String status,
        String errorCode,
        List<TrendItemPayload> items) {

    public TrendBoardPayload {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
