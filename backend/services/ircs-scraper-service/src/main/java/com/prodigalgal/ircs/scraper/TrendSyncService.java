package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.lease.ClusterLeaseGuard;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyRequest;
import com.prodigalgal.ircs.contracts.trend.TrendSyncRunResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TrendSyncService {
    private final List<TrendListProvider> providers;
    private final TrendSyncContentClient contentClient;

    TrendSyncRunResponse syncTrends(String correlationId) {
        List<TrendBoardPayload> boards = new ArrayList<>();
        List<String> providerErrors = new ArrayList<>();
        for (TrendListProvider provider : providers) {
            ClusterLeaseGuard.checkCurrentOwnership();
            try {
                for (TrendBoardPayload board : provider.fetchBoards()) {
                    boards.add(board);
                    if (!"SUCCESS".equals(board.status()) && !"DISABLED".equals(board.status())) {
                        providerErrors.add(board.provider() + "/" + board.board() + ": " + board.status()
                                + (board.errorCode() == null ? "" : " (" + board.errorCode() + ")"));
                    }
                }
            } catch (RuntimeException ex) {
                // No exception messages: upstream exceptions may include credential-bearing URLs.
                boards.add(new TrendBoardPayload(provider.name(), "provider", "all", "day", Instant.now(),
                        "FAILED", "PROVIDER_FETCH_FAILED", List.of()));
                providerErrors.add(provider.name() + ": PROVIDER_FETCH_FAILED");
            }
        }
        List<TrendItemPayload> items = boards.stream().filter(board -> "SUCCESS".equals(board.status()))
                .flatMap(board -> board.items().stream()).toList();
        ClusterLeaseGuard.checkCurrentOwnership();
        var applied = contentClient.apply(new TrendSyncApplyRequest(items, boards), correlationId);
        // Discovery is persisted atomically with catalog entries and dispatched by task-service.
        return new TrendSyncRunResponse("trend-sync", providers.size(), items.size(), applied,
                List.copyOf(providerErrors), TrendDiscoveryScheduleResponse.empty("trend-discovery"));
    }
}
