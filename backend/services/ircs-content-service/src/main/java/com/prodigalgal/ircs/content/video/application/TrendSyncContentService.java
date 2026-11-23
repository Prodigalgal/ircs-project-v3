package com.prodigalgal.ircs.content.video.application;

import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.content.video.infrastructure.TrendSnapshotRepository;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyRequest;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TrendSyncContentService {
    private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED", "DISABLED", "UNAVAILABLE", "DEFERRED");
    private final TrendCatalogEntryService catalog;
    private final TrendSnapshotRepository snapshots;

    // Entries commit in short transactions; a whole board becomes visible atomically after resolution.
    public TrendSyncApplyResponse apply(TrendSyncApplyRequest request) {
        if (request == null) return empty();
        if (request.boards().size() > 32 || request.items().size() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TREND_BATCH_TOO_LARGE");
        }
        Set<UUID> created = new LinkedHashSet<>();
        Set<UUID> updated = new LinkedHashSet<>();
        Map<String, TrendCatalogEntryService.Resolution> resolved = new HashMap<>();
        long count = 0;
        long duplicateCount = 0;
        if (request.boards().isEmpty()) {
            // Old callers retain typed catalog ingestion, without manufacturing global ranks or dates.
            for (TrendItemPayload item : request.items()) {
                TrendCatalogCandidate candidate;
                try {
                    candidate = TrendCatalogCandidate.from(item);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                count++;
                String key = candidate.identityKey() + ":" + candidate.episode();
                if (resolved.containsKey(key)) { duplicateCount++; continue; }
                remember(candidate, resolved, created, updated);
            }
        } else {
            for (TrendBoardPayload board : request.boards()) {
                validate(board);
                if (!"SUCCESS".equals(board.status())) {
                    snapshots.save(board, List.of());
                    continue;
                }
                List<TrendSnapshotRepository.Observation> observations = new ArrayList<>();
                Set<String> boardIds = new LinkedHashSet<>();
                // Validate before writes: malformed/challenge output cannot clear the last good snapshot.
                List<TrendCatalogCandidate> candidates = board.items().stream().map(TrendCatalogCandidate::from).toList();
                for (TrendCatalogCandidate candidate : candidates) validateSignal(board, candidate);
                for (TrendCatalogCandidate candidate : candidates) {
                    count++;
                    if (!boardIds.add(candidate.identityKey())) { duplicateCount++; continue; }
                    var resolution = remember(candidate, resolved, created, updated);
                    if (resolution.id() != null) {
                        observations.add(new TrendSnapshotRepository.Observation(candidate, resolution.id()));
                    }
                }
                // If all observed works are awaiting details, keep the previous verified board.
                if (candidates.isEmpty() || !observations.isEmpty()) snapshots.save(board, observations);
            }
        }
        updated.removeAll(created);
        return new TrendSyncApplyResponse("trend-sync", count, updated.size(), 0, created.size(), duplicateCount,
                List.copyOf(updated), List.copyOf(created), List.of());
    }

    private TrendCatalogEntryService.Resolution remember(TrendCatalogCandidate candidate,
            Map<String, TrendCatalogEntryService.Resolution> resolved, Set<UUID> created, Set<UUID> updated) {
        String key = candidate.identityKey() + ":" + candidate.episode();
        var resolution = resolved.get(key);
        if (resolution == null) {
            resolution = catalog.resolve(candidate);
            resolved.put(key, resolution);
            if (resolution.id() != null) {
                if (resolution.created()) created.add(resolution.id()); else updated.add(resolution.id());
            }
        }
        return resolution;
    }

    private void validate(TrendBoardPayload board) {
        if (board == null || board.provider() == null || !board.provider().matches("[A-Za-z0-9_-]{1,32}")
                || board.board() == null || !board.board().matches("[A-Za-z0-9_-]{1,64}")
                || board.mediaType() == null || !Set.of("movie", "tv", "all").contains(board.mediaType())
                || board.window() == null || !board.window().matches("[A-Za-z0-9_-]{1,24}")
                || !STATUSES.contains(board.status() == null ? "" : board.status())
                || board.collectedAt() == null || board.collectedAt().isAfter(Instant.now().plusSeconds(300))
                || board.items().size() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TREND_BOARD");
        }
    }

    private void validateSignal(TrendBoardPayload board, TrendCatalogCandidate candidate) {
        var signal = candidate.item().signal();
        if (signal == null || !board.provider().equals(signal.provider()) || !board.board().equals(signal.board())
                || !board.mediaType().equals(signal.mediaType()) || !board.window().equals(signal.window())
                || (!"event".equals(board.window()) && signal.position() == null)
                || (signal.position() != null && (signal.position() < 1 || signal.position() > 1000))
                || ("event".equals(board.window()) && (signal.eventDate() == null || signal.eventType() == null))
                || signal.observedAt() == null || !board.collectedAt().equals(signal.observedAt())
                || (signal.sourceUrl() != null && signal.sourceUrl().length() > 1000)
                || (signal.region() != null && signal.region().length() > 32)
                || (signal.eventType() != null && !signal.eventType().matches("[A-Za-z0-9_-]{1,32}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TREND_SIGNAL");
        }
    }

    private TrendSyncApplyResponse empty() {
        return new TrendSyncApplyResponse("trend-sync", 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }
}
