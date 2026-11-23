package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyRequest;
import com.prodigalgal.ircs.contracts.trend.TrendSyncApplyResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrendSyncServiceTest {
    private final TrendSyncContentClient content = mock(TrendSyncContentClient.class);

    @Test
    void appliesAllSourceBoardsAndLeavesDiscoveryToDurableRequests() {
        UUID id = UUID.randomUUID();
        var item = new TrendItemPayload("Trend", null, null, "2026", null, null, null, "42", null, null, "movie");
        var board = new TrendBoardPayload("TMDB", "trending-day", "movie", "day", Instant.now(), "SUCCESS", null, List.of(item));
        var provider = mock(TrendListProvider.class);
        when(provider.fetchBoards()).thenReturn(List.of(board));
        when(content.apply(any(), eq("corr"))).thenReturn(new TrendSyncApplyResponse("trend-sync", 1, 0, 0, 1, 0,
                List.of(), List.of(id), List.of()));
        var result = new TrendSyncService(List.of(provider), content).syncTrends("corr");
        var captured = ArgumentCaptor.forClass(TrendSyncApplyRequest.class);
        verify(content).apply(captured.capture(), eq("corr"));
        assertThat(captured.getValue().items()).isEmpty();
        assertThat(captured.getValue().boards()).containsExactly(board);
        assertThat(result.fetchedItems()).isEqualTo(1);
        assertThat(result.applyResult().createdCatalogEntryIds()).containsExactly(id);
        assertThat(result.discoveryResult().queuedTasks()).isZero();
    }

    @Test
    void persistsFailedSourceStateEvenWhenNoSourceReturnsItemsAndRedactsErrors() {
        var failing = mock(TrendListProvider.class);
        when(failing.name()).thenReturn("Douban");
        when(failing.fetchBoards()).thenThrow(new IllegalStateException("https://provider.example/?api_key=secret"));
        when(content.apply(any(), eq("corr"))).thenReturn(empty());
        var result = new TrendSyncService(List.of(failing), content).syncTrends("corr");
        var captured = ArgumentCaptor.forClass(TrendSyncApplyRequest.class);
        verify(content).apply(captured.capture(), eq("corr"));
        assertThat(captured.getValue().boards()).singleElement().satisfies(board -> {
            assertThat(board.status()).isEqualTo("FAILED");
            assertThat(board.errorCode()).isEqualTo("PROVIDER_FETCH_FAILED");
        });
        assertThat(result.fetchedItems()).isZero();
        assertThat(result.providerErrors()).containsExactly("Douban: PROVIDER_FETCH_FAILED");
    }

    @Test
    void unavailableSourceDoesNotDiscardAnotherSourcesSuccessfulBoard() {
        var unavailable = new TrendBoardPayload("IMDb", "popular", "movie", "popular", Instant.now(),
                "UNAVAILABLE", "OFFICIAL_TREND_ACCESS_NOT_CONFIGURED", List.of());
        var success = new TrendBoardPayload("TMDB", "trending-day", "movie", "day", Instant.now(), "SUCCESS", null, List.of());
        var first = mock(TrendListProvider.class);
        var second = mock(TrendListProvider.class);
        when(first.fetchBoards()).thenReturn(List.of(unavailable));
        when(second.fetchBoards()).thenReturn(List.of(success));
        when(content.apply(any(), any())).thenReturn(empty());
        var result = new TrendSyncService(List.of(first, second), content).syncTrends("corr");
        var captured = ArgumentCaptor.forClass(TrendSyncApplyRequest.class);
        verify(content, times(2)).apply(captured.capture(), eq("corr"));
        assertThat(captured.getAllValues()).extracting(TrendSyncApplyRequest::boards)
                .containsExactly(List.of(unavailable), List.of(success));
        assertThat(result.providerErrors()).hasSize(1);
    }

    @Test
    void aTimedOutBoardIsNotSentAgainAndHealthyBoardsStillReachContent() {
        var slow = new TrendBoardPayload("Douban", "chart", "movie", "chart", Instant.now(),
                "SUCCESS", null, List.of());
        var day = new TrendBoardPayload("TMDB", "trending-day", "movie", "day", Instant.now(),
                "SUCCESS", null, List.of());
        var week = new TrendBoardPayload("TMDB", "trending-week", "movie", "week", Instant.now(),
                "SUCCESS", null, List.of());
        var provider = mock(TrendListProvider.class);
        when(provider.fetchBoards()).thenReturn(List.of(slow, day, week));
        UUID id = UUID.randomUUID();
        when(content.apply(any(), eq("corr")))
                .thenThrow(new IllegalStateException("request timeout"))
                .thenReturn(new TrendSyncApplyResponse("trend-sync", 1, 0, 0, 1, 0,
                        List.of(), List.of(id), List.of()))
                .thenReturn(new TrendSyncApplyResponse("trend-sync", 1, 1, 0, 0, 0,
                        List.of(id), List.of(), List.of()));

        var result = new TrendSyncService(List.of(provider), content).syncTrends("corr");

        var captured = ArgumentCaptor.forClass(TrendSyncApplyRequest.class);
        verify(content, times(3)).apply(captured.capture(), eq("corr"));
        assertThat(captured.getAllValues()).extracting(TrendSyncApplyRequest::boards)
                .containsExactly(List.of(slow), List.of(day), List.of(week));
        assertThat(result.providerErrors()).containsExactly("Douban/chart: CONTENT_APPLY_FAILED");
        assertThat(result.applyResult().createdCatalogEntryIds()).containsExactly(id);
        assertThat(result.applyResult().updatedCatalogEntryIds()).isEmpty();
    }

    @Test
    void totalContentFailureLetsTheSchedulerDeferTheRound() {
        var provider = mock(TrendListProvider.class);
        when(provider.fetchBoards()).thenReturn(List.of(new TrendBoardPayload("Douban", "chart", "movie",
                "chart", Instant.now(), "SUCCESS", null, List.of())));
        when(content.apply(any(), eq("corr"))).thenThrow(new IllegalStateException("request timeout"));

        assertThatThrownBy(() -> new TrendSyncService(List.of(provider), content).syncTrends("corr"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No trend board was accepted");
        verify(content).apply(any(), eq("corr"));
    }

    private TrendSyncApplyResponse empty() {
        return new TrendSyncApplyResponse("trend-sync", 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }
}
