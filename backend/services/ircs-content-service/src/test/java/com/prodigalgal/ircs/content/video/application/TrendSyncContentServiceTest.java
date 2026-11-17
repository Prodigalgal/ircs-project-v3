package com.prodigalgal.ircs.content.video.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.content.video.infrastructure.TrendSnapshotRepository;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class TrendSyncContentServiceTest {

    private final Instant observedAt = Instant.now().minusSeconds(60);
    private final TrendCatalogEntryService catalog = Mockito.mock(TrendCatalogEntryService.class);
    private final TrendSnapshotRepository snapshots = Mockito.mock(TrendSnapshotRepository.class);
    private final TrendSyncContentService service = new TrendSyncContentService(catalog, snapshots);

    @Test
    void resolvesNewAndExistingCatalogEntriesOnceAndStoresSourceEvidence() {
        UUID existing = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        var known = item("Known", "42", "trending-day", "day", 1, null);
        var duplicate = item("Known", "42", "trending-day", "day", 2, null);
        var discovered = item("New", "99", "trending-day", "day", 3, null);
        var board = board("trending-day", "day", "SUCCESS", List.of(known, duplicate, discovered));
        when(catalog.resolve(any())).thenAnswer(call -> "42".equals(call.getArgument(0,
                com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate.class).externalId())
                ? new TrendCatalogEntryService.Resolution(existing, false)
                : new TrendCatalogEntryService.Resolution(created, true));

        var result = service.apply(new TrendSyncApplyRequest(List.of(), List.of(board)));

        assertThat(result.candidates()).isEqualTo(3);
        assertThat(result.createdGhosts()).isEqualTo(1);
        assertThat(result.updatedByExternalId()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isEqualTo(1);
        assertThat(result.createdUnifiedVideoIds()).containsExactly(created);
        assertThat(result.updatedUnifiedVideoIds()).containsExactly(existing);
        assertThat(result.discoveryKeywords()).isEmpty();
        verify(catalog, times(2)).resolve(any());
        verify(snapshots).save(eq(board), argThat(observations -> observations.size() == 2
                && observations.stream().map(TrendSnapshotRepository.Observation::id).toList().containsAll(List.of(existing, created))));
    }

    @Test
    void dayAndWeekKeepSeparateObservationsWithoutRepeatedCatalogOrDiscoveryCreation() {
        UUID canonical = UUID.randomUUID();
        when(catalog.resolve(any())).thenReturn(new TrendCatalogEntryService.Resolution(canonical, true));
        var day = board("trending-day", "day", "SUCCESS", List.of(item("Known", "42", "trending-day", "day", 1, null)));
        var week = board("trending-week", "week", "SUCCESS", List.of(item("Known", "42", "trending-week", "week", 4, null)));

        var result = service.apply(new TrendSyncApplyRequest(List.of(), List.of(day, week)));

        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.createdUnifiedVideoIds()).containsExactly(canonical);
        verify(catalog).resolve(any());
        verify(snapshots).save(eq(day), argThat(observations -> observations.size() == 1));
        verify(snapshots).save(eq(week), argThat(observations -> observations.size() == 1));
    }

    @Test
    void failedBoardOnlyRecordsFailureAndDoesNotTurnAnOutageIntoAnEmptySuccess() {
        var failed = board("trending-day", "day", "FAILED", List.of());

        var result = service.apply(new TrendSyncApplyRequest(List.of(), List.of(failed)));

        assertThat(result.candidates()).isZero();
        verifyNoInteractions(catalog);
        verify(snapshots).save(failed, List.of());
    }

    @Test
    void validatesWholeBoardBeforeAnyCatalogOrSnapshotWrite() {
        var valid = item("Valid", "42", "trending-day", "day", 1, null);
        var wrongBoard = item("Wrong source board", "99", "trending-week", "week", 1, null);

        assertThatThrownBy(() -> service.apply(new TrendSyncApplyRequest(List.of(),
                List.of(board("trending-day", "day", "SUCCESS", List.of(valid, wrongBoard))))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("INVALID_TREND_SIGNAL");
        verifyNoInteractions(catalog, snapshots);
    }

    @Test
    void unrankedTrendIsRejectedWhileARealReleaseEventDoesNotInventARank() {
        var unranked = item("No rank", "42", "trending-day", "day", null, null);
        assertThatThrownBy(() -> service.apply(new TrendSyncApplyRequest(List.of(),
                List.of(board("trending-day", "day", "SUCCESS", List.of(unranked))))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(catalog, snapshots);

        UUID id = UUID.randomUUID();
        when(catalog.resolve(any())).thenReturn(new TrendCatalogEntryService.Resolution(id, true));
        var event = item("Released", "99", "recent-release", "event", null, LocalDate.now().minusDays(1));
        var eventBoard = board("recent-release", "event", "SUCCESS", List.of(event));

        var response = service.apply(new TrendSyncApplyRequest(List.of(), List.of(eventBoard)));

        assertThat(response.createdGhosts()).isEqualTo(1);
        verify(snapshots).save(eq(eventBoard), argThat(observations -> observations.size() == 1
                && observations.getFirst().candidate().item().signal().position() == null));
    }

    @Test
    void legacyTypedItemsRemainCatalogOnlyAndInvalidPlaceholderIdsAreIgnored() {
        UUID id = UUID.randomUUID();
        when(catalog.resolve(any())).thenReturn(new TrendCatalogEntryService.Resolution(id, true));
        var legacy = new TrendItemPayload("Legacy", null, null, "2026", null, null, null,
                "42", null, null, "movie");
        var zero = new TrendItemPayload("Placeholder", null, null, "2026", null, null, null,
                "0", "0", null, "movie");

        var result = service.apply(new TrendSyncApplyRequest(List.of(legacy, legacy, zero)));

        assertThat(result.candidates()).isEqualTo(2);
        assertThat(result.createdGhosts()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isEqualTo(1);
        verify(catalog).resolve(any());
        verifyNoInteractions(snapshots);
    }

    @Test
    void oversizedBatchIsRejectedBeforeWrites() {
        var single = board("trending-day", "day", "SUCCESS", List.of());

        assertThatThrownBy(() -> service.apply(new TrendSyncApplyRequest(List.of(), Collections.nCopies(33, single))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("TREND_BATCH_TOO_LARGE");
        verifyNoInteractions(catalog, snapshots);
    }

    @Test
    void snapshotFailureIsPropagatedForDurableRetry() {
        when(catalog.resolve(any())).thenReturn(new TrendCatalogEntryService.Resolution(UUID.randomUUID(), true));
        when(snapshots.save(any(), any())).thenThrow(new IllegalStateException("fixture database unavailable"));
        var board = board("trending-day", "day", "SUCCESS", List.of(item("Known", "42", "trending-day", "day", 1, null)));

        assertThatThrownBy(() -> service.apply(new TrendSyncApplyRequest(List.of(), List.of(board))))
                .hasMessage("fixture database unavailable");
    }

    @Test
    void absentRequestHasNoSideEffects() {
        assertThat(service.apply(null).candidates()).isZero();
        verifyNoInteractions(catalog, snapshots);
    }

    private TrendBoardPayload board(String name, String window, String status, List<TrendItemPayload> items) {
        return new TrendBoardPayload("TMDB", name, "movie", window, observedAt, status,
                "FAILED".equals(status) ? "HTTP_503" : null, items);
    }

    private TrendItemPayload item(String title, String id, String board, String window, Integer rank, LocalDate eventDate) {
        return new TrendItemPayload(title, null, null, "2026", null, null, null, id, null, null, "movie",
                new TrendSignalPayload("TMDB", board, "movie", id, rank, window, null, null, observedAt,
                        eventDate, eventDate == null ? null : "RELEASE", null, null, null,
                        "https://www.themoviedb.org/movie/" + id, "movie"));
    }
}
