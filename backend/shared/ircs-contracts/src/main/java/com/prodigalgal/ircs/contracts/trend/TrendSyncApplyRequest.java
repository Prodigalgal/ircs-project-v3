package com.prodigalgal.ircs.contracts.trend;

import java.util.List;

public record TrendSyncApplyRequest(List<TrendItemPayload> items, List<TrendBoardPayload> boards) {

    public TrendSyncApplyRequest {
        items = items == null ? List.of() : List.copyOf(items);
        boards = boards == null ? List.of() : List.copyOf(boards);
    }

    public TrendSyncApplyRequest(List<TrendItemPayload> items) {
        this(items, List.of());
    }
}
