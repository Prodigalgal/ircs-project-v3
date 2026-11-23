package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.lease.ClusterLeaseGuard;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyRequest;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyResponse;
import com.prodigalgal.ircs.contracts.trend.TrendSyncRunResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
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
        long fetchedItems = boards.stream().filter(board -> "SUCCESS".equals(board.status()))
                .mapToLong(board -> board.items().size()).sum();
        List<TrendSyncApplyResponse> results = new ArrayList<>();
        for (TrendBoardPayload board : boards) {
            ClusterLeaseGuard.checkCurrentOwnership();
            try {
                // A board owns its complete snapshot. Each call has its own deadline and is sent once.
                results.add(contentClient.apply(new TrendSyncApplyRequest(List.of(), List.of(board)), correlationId));
            } catch (RuntimeException ex) {
                if (Thread.currentThread().isInterrupted()) throw ex;
                providerErrors.add(board.provider() + "/" + board.board() + ": CONTENT_APPLY_FAILED");
                log.warn("Trend board apply deferred: provider={}, board={}, mediaType={}, errorType={}",
                        board.provider(), board.board(), board.mediaType(), ex.getClass().getSimpleName());
            }
        }
        if (!boards.isEmpty() && results.isEmpty()) {
            throw new IllegalStateException("No trend board was accepted by content-service");
        }
        // Discovery is persisted atomically with catalog entries and dispatched by task-service.
        return new TrendSyncRunResponse("trend-sync", providers.size(), fetchedItems, aggregate(results),
                List.copyOf(providerErrors), TrendDiscoveryScheduleResponse.empty("trend-discovery"));
    }

    private static TrendSyncApplyResponse aggregate(List<TrendSyncApplyResponse> results) {
        var created = new LinkedHashSet<UUID>();
        var updated = new LinkedHashSet<UUID>();
        long candidates = 0;
        long titleMatches = 0;
        long duplicates = 0;
        for (TrendSyncApplyResponse result : results) {
            candidates += result.candidates();
            titleMatches += result.updatedByTitleMatch();
            duplicates += result.skippedDuplicates();
            updated.addAll(result.updatedCatalogEntryIds());
            created.addAll(result.createdCatalogEntryIds());
        }
        updated.removeAll(created);
        return new TrendSyncApplyResponse("trend-sync", candidates, updated.size(), titleMatches, created.size(),
                duplicates, List.copyOf(updated), List.copyOf(created), List.of());
    }
}
