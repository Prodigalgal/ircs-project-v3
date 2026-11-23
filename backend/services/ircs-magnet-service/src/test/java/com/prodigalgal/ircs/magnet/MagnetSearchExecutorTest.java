package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MagnetSearchExecutorTest {
    private final UUID videoId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final JdbcMagnetRepository repository = mock(JdbcMagnetRepository.class);
    private final MagnetSearchJobCoordinator coordinator = mock(MagnetSearchJobCoordinator.class);
    private final MagnetProviderSearchRunner runner = mock(MagnetProviderSearchRunner.class);
    private final MagnetWorkPublisher publisher = mock(MagnetWorkPublisher.class);
    private final List<MagnetProviderRunSummary> ledger = new ArrayList<>();
    private final MagnetSearchExecutor executor = new MagnetSearchExecutor(repository, coordinator, runner,
            MagnetReadModelCache.disabled(), supplied(publisher), null, Duration.ofMinutes(2));

    @BeforeEach
    void setUp() {
        when(repository.findCatalogEntrySearchTarget(videoId)).thenReturn(Optional.of(target("Example", "2026", null)));
        when(repository.listEnabledProviders()).thenReturn(List.of(provider("source", "TEST", List.of("IMDB"))));
        when(coordinator.enqueue(eq(videoId), anyString(), anyList()))
                .thenReturn(new MagnetSearchJobCoordinator.Enqueued(job("PENDING", 0, 0), true));
        when(coordinator.claim(eq(jobId), eq(videoId), anyList(), any()))
                .thenReturn(new MagnetSearchJobCoordinator.Claim(job("RUNNING", 0, 0), 1L, true));
        when(coordinator.renew(any())).thenReturn(true);
        when(coordinator.withinLease(any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        when(repository.findProviderRuns(jobId)).thenAnswer(call -> List.copyOf(ledger));
        doAnswer(call -> {
            MagnetProviderSummary provider = call.getArgument(1);
            MagnetExternalIdQuery query = call.getArgument(2);
            ledger.add(new MagnetProviderRunSummary(UUID.randomUUID(), jobId, provider.id(), provider.code(),
                    query.type(), query.value(), call.getArgument(3), call.getArgument(4), call.getArgument(5),
                    call.getArgument(6), call.getArgument(7), call.getArgument(8), call.getArgument(9), Instant.now(), Instant.now()));
            return null;
        }).when(repository).createProviderRun(eq(jobId), any(), any(), anyString(), nullable(String.class),
                nullable(Integer.class), anyInt(), anyInt(), anyLong(), nullable(String.class));
        when(repository.finishSearchJob(eq(jobId), anyList(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int failed = call.getArgument(4);
            return job(failed == 0 ? "SUCCESS" : ledger.stream().anyMatch(run -> run.status().equals("SUCCESS"))
                    ? "PARTIAL_SUCCESS" : "FAILED", call.getArgument(2), call.getArgument(3));
        });
        when(repository.upsertLink(eq(videoId), any(), any(), anyBoolean())).thenAnswer(call -> {
            MagnetProviderCandidate candidate = call.getArgument(2);
            boolean approved = call.getArgument(3);
            return new MagnetLinkSummary(UUID.randomUUID(), videoId, "source", candidate.infoHash(), candidate.magnetUri(),
                    candidate.title(), null, null, null, null, null, null, null, candidate.matchedExternalIdType(),
                    candidate.matchedExternalIdValue(), candidate.matchScore(), approved ? "APPROVED" : "CANDIDATE",
                    null, List.of(), Instant.now(), Instant.now());
        });
    }

    @Test
    void providerRunsOutsideTransactionAndStrongEvidenceCanApprove() {
        when(runner.search(any(), any(), eq(videoId))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return response(candidate("Example 2026 1080p WEB-DL", Map.of("titleSource", "CANDIDATE")));
        });
        var result = executor.execute(jobId, videoId, "ADMIN_MANUAL", true);
        assertEquals("SUCCESS", result.status());
        assertEquals("APPROVED", result.links().getFirst().status());
        assertEquals(95, result.links().getFirst().matchScore());
        verify(repository).updateProviderHealth(any(), eq("SUCCESS"), isNull());
    }

    @Test
    void queryEchoDoesNotApproveEvenWithAnImdbQueryScore() {
        when(runner.search(any(), any(), any())).thenReturn(response(candidate("Example 2026", Map.of("queryValue", "tt1234567"))));
        var result = executor.execute(jobId, videoId, "ADMIN_MANUAL", true);
        assertEquals("CANDIDATE", result.links().getFirst().status());
    }

    @Test
    void wrongSeasonIsRejectedBeforeUpsert() {
        when(repository.findCatalogEntrySearchTarget(videoId)).thenReturn(Optional.of(target("Example", "2026", 1)));
        when(runner.search(any(), any(), any())).thenReturn(response(candidate("Example S02E03 1080p", Map.of("titleSource", "CANDIDATE"))));
        var result = executor.execute(jobId, videoId, "ADMIN_MANUAL", true);
        assertEquals(1, result.rejectedCount());
        verify(repository, never()).upsertLink(any(), any(), any(), anyBoolean());
    }

    @Test
    void mismatchedHashAndUriAreRejected() {
        var original = candidate("Example 2026", Map.of("titleSource", "CANDIDATE"));
        var invalid = new MagnetProviderCandidate(original.infoHash(), "magnet:?xt=urn:btih:" + "1".repeat(40), original.title(),
                null, null, null, null, null, null, null, "IMDB", "tt1234567", 100, null, List.of(), original.providerEvidence());
        when(runner.search(any(), any(), any())).thenReturn(response(invalid));
        assertEquals(0, executor.execute(jobId, videoId, "ADMIN_MANUAL", true).acceptedCount());
        verify(repository, never()).upsertLink(any(), any(), any(), anyBoolean());
    }

    @Test
    void onlyFailedProviderIsRetriedAndSuccessfulEmptyResultsAreNotRepeated() {
        var healthy = provider("healthy", "TEST", List.of("IMDB"));
        var flaky = provider("flaky", "TEST", List.of("IMDB"));
        when(repository.listEnabledProviders()).thenReturn(List.of(healthy, flaky));
        when(runner.search(eq(healthy), any(), any())).thenReturn(new MagnetProviderSearchResult(null, 200, List.of()));
        when(runner.search(eq(flaky), any(), any()))
                .thenThrow(new MagnetProviderRunnerException("GENERIC_MAGNET_TIMEOUT", null, null))
                .thenReturn(new MagnetProviderSearchResult(null, 200, List.of()));
        var failure = assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(jobId, videoId, "AUTO_SCHEDULED", true));
        assertTrue(failure.retryable());
        verify(coordinator).retry(any(), eq("MAGNET_PROVIDER_RETRY_PENDING"));
        verify(repository, never()).finishSearchJob(any(), anyList(), anyInt(), anyInt(), anyInt());
        assertEquals("SUCCESS", executor.execute(jobId, videoId, "AUTO_SCHEDULED", true).status());
        verify(runner, times(1)).search(eq(healthy), any(), any());
        verify(runner, times(2)).search(eq(flaky), any(), any());
    }

    @Test
    void retriesCommittedLinkProjectionWithoutRefetchingSuccessfulProvider() {
        var cache = spy(MagnetReadModelCache.disabled());
        var searchSync = mock(SearchSyncWorkPublisher.class);
        var publishingExecutor = new MagnetSearchExecutor(repository, coordinator, runner, cache, supplied(publisher),
                supplied(searchSync), Duration.ofMinutes(2));
        when(runner.search(any(), any(), eq(videoId))).thenReturn(
                response(candidate("Example 2026 1080p WEB-DL", Map.of("titleSource", "CANDIDATE"))));
        when(searchSync.enqueue(videoId, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX))
                .thenThrow(new IllegalStateException("temporary index submission failure"))
                .thenReturn(1);

        assertThrows(IllegalStateException.class, () -> publishingExecutor.execute(jobId, videoId, "AUTO_SCHEDULED", true));
        verify(coordinator).retry(any(), eq("MAGNET_EXECUTION_RETRY_PENDING"));
        assertEquals("SUCCESS", publishingExecutor.execute(jobId, videoId, "AUTO_SCHEDULED", true).status());
        verify(runner).search(any(), any(), eq(videoId));
        verify(repository).upsertLink(eq(videoId), any(), any(), anyBoolean());
        verify(cache, times(2)).evictApprovedLinks(videoId);
        verify(searchSync, times(2)).enqueue(videoId, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
    }

    @Test
    void partialResultWithoutAcceptedLinksRemainsATerminalFailure() {
        var healthy = provider("healthy", "TEST", List.of("IMDB"));
        var denied = provider("denied", "TEST", List.of("IMDB"));
        when(repository.listEnabledProviders()).thenReturn(List.of(healthy, denied));
        when(runner.search(eq(healthy), any(), any())).thenReturn(new MagnetProviderSearchResult(null, 200, List.of()));
        when(runner.search(eq(denied), any(), any())).thenThrow(new MagnetProviderRunnerException("GENERIC_MAGNET_ACCESS_RESTRICTED", null, 403));
        var failure = assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(jobId, videoId, "AUTO_SCHEDULED", true));
        assertFalse(failure.retryable());
        verify(repository).finishSearchJob(jobId, List.of("healthy", "denied"), 0, 0, 1);
        verify(coordinator, never()).retry(any(), any());
    }

    @Test
    void queuedPartialWithAcceptedLinksCompletesAfterProviderRetryBudgetIsExhausted() {
        var healthy = provider("healthy", "TEST", List.of("IMDB"));
        var flaky = provider("flaky", "TEST", List.of("IMDB"));
        when(repository.listEnabledProviders()).thenReturn(List.of(healthy, flaky));
        when(runner.search(eq(healthy), any(), any())).thenReturn(
                response(candidate("Example 2026 1080p WEB-DL", Map.of("titleSource", "CANDIDATE"))));
        when(runner.search(eq(flaky), any(), any())).thenThrow(
                new MagnetProviderRunnerException("GENERIC_MAGNET_HTTP_ERROR", null, 503));

        var failure = assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(jobId, videoId, "AUTO_SCHEDULED", true));
        assertTrue(failure.retryable());
        verify(coordinator).retry(any(), eq("MAGNET_PROVIDER_RETRY_PENDING"));
        verify(repository, never()).finishSearchJob(any(), anyList(), anyInt(), anyInt(), anyInt());

        var result = executor.execute(jobId, videoId, "AUTO_SCHEDULED", false);

        assertEquals("PARTIAL_SUCCESS", result.status());
        assertEquals(1, result.acceptedCount());
        assertEquals(2, ledger.stream().filter(run -> "FAILED".equals(run.status()) && "flaky".equals(run.providerCode())).count());
        verify(repository).finishSearchJob(jobId, List.of("healthy", "flaky"), 1, 1, 1);
        verify(runner).search(eq(healthy), any(), any());
        verify(runner, times(2)).search(eq(flaky), any(), any());
    }

    @Test
    void immediateAdminSearchKeepsItsTerminalProviderFailureContract() {
        var healthy = provider("healthy", "TEST", List.of("IMDB"));
        var denied = provider("denied", "TEST", List.of("IMDB"));
        when(repository.listEnabledProviders()).thenReturn(List.of(healthy, denied));
        when(runner.search(eq(healthy), any(), any())).thenReturn(
                response(candidate("Example 2026 1080p WEB-DL", Map.of("titleSource", "CANDIDATE"))));
        when(runner.search(eq(denied), any(), any())).thenThrow(
                new MagnetProviderRunnerException("GENERIC_MAGNET_ACCESS_RESTRICTED", null, 403));

        var failure = assertThrows(MagnetSearchExecutionException.class, () -> executor.searchNow(videoId, "ADMIN_MANUAL"));

        assertFalse(failure.retryable());
        assertEquals("MAGNET_PROVIDER_PERMANENT_FAILURE", failure.getMessage());
        verify(repository).finishSearchJob(jobId, List.of("healthy", "denied"), 1, 1, 1);
    }

    @Test
    void lastAttemptPersistsFailureInsteadOfReopeningPending() {
        when(runner.search(any(), any(), any())).thenThrow(new MagnetProviderRunnerException("GENERIC_MAGNET_HTTP_ERROR", null, 503));
        assertFalse(assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(jobId, videoId, "ADMIN_MANUAL", false)).retryable());
        verify(repository).finishSearchJob(jobId, List.of("source"), 0, 0, 1);
        verify(coordinator, never()).retry(any(), any());
    }

    @Test
    void terminalFailureReplayWithoutAcceptedLinksCannotBeAcknowledgedAsSuccess() {
        when(coordinator.claim(any(), any(), anyList(), any())).thenReturn(
                new MagnetSearchJobCoordinator.Claim(job("PARTIAL_SUCCESS", 2, 0), null, false),
                new MagnetSearchJobCoordinator.Claim(job("FAILED", 0, 0), null, false));
        assertFalse(assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(jobId, videoId, "ADMIN_MANUAL", true)).retryable());
        assertFalse(assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(jobId, videoId, "ADMIN_MANUAL", true)).retryable());
        verifyNoInteractions(runner);
    }

    @Test
    void queuedReplayAcknowledgesDeliveredPartialWithoutCallingProvidersAgain() {
        var delivered = job("PARTIAL_SUCCESS", 2, 1);
        when(coordinator.claim(any(), any(), anyList(), any()))
                .thenReturn(new MagnetSearchJobCoordinator.Claim(delivered, null, false));

        assertSame(delivered, executor.execute(jobId, videoId, "ADMIN_MANUAL", true));

        verifyNoInteractions(runner);
        verify(repository, never()).upsertLink(any(), any(), any(), anyBoolean());
        verify(repository, never()).finishSearchJob(any(), anyList(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void terminalDuplicateSuccessDoesNoNetworkOrWrites() {
        var success = job("SUCCESS", 1, 1);
        when(coordinator.claim(any(), any(), anyList(), any())).thenReturn(new MagnetSearchJobCoordinator.Claim(success, null, false));
        assertSame(success, executor.execute(jobId, videoId, "ADMIN_MANUAL", true));
        verifyNoInteractions(runner);
        verify(coordinator, never()).renew(any());
    }

    @Test
    void unexpectedProviderExceptionReleasesLeaseForRetry() {
        when(runner.search(any(), any(), any())).thenThrow(new IllegalStateException("transport unavailable"));
        assertThrows(IllegalStateException.class, () -> executor.execute(jobId, videoId, "ADMIN_MANUAL", true));
        verify(coordinator).retry(any(), eq("MAGNET_EXECUTION_RETRY_PENDING"));
        verify(repository, never()).createProviderRun(any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    void lostExecutionLeasePreventsNetworkAndPersistence() {
        when(coordinator.renew(any())).thenReturn(false);
        assertThrows(MagnetSearchJobCoordinator.MagnetLeaseLostException.class,
                () -> executor.execute(jobId, videoId, "ADMIN_MANUAL", true));
        verifyNoInteractions(runner);
        verify(repository, never()).upsertLink(any(), any(), any(), anyBoolean());
    }

    @Test
    void transactionOnEntryIsRejectedBeforeAnyNetwork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> executor.execute(jobId, videoId, "ADMIN_MANUAL", true));
            verifyNoInteractions(runner, coordinator);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test
    void imdbOnlyProviderDoesNotReceiveTitleFallback() {
        when(repository.findCatalogEntrySearchTarget(videoId)).thenReturn(Optional.of(new MagnetCatalogEntrySearchTarget(
                videoId, "Example", null, "2026", null, null, null, "SYNCED", null, null, "movie")));
        when(repository.listEnabledProviders()).thenReturn(List.of(provider("yts", "YTS_BZ", List.of("IMDB"))));
        when(repository.markSearchJobSkipped(eq(jobId), any(), anyList(), anyList())).thenReturn(Optional.of(job("SKIPPED", 0, 0)));
        assertEquals("SKIPPED", executor.execute(jobId, videoId, "ADMIN_MANUAL", true).status());
        verifyNoInteractions(runner);
    }

    @Test
    void duplicateEnqueueUsesExistingJobAndSafePublisher() {
        executor.enqueue(videoId, "ADMIN_MANUAL");
        executor.enqueue(videoId, "ADMIN_MANUAL");
        verify(publisher, times(2)).enqueue(argThat(job -> job.id().equals(jobId)));
        verify(repository, never()).createPendingSearchJob(any(), any(), anyList());
    }

    @Test
    void queryPlanIgnoresPlaceholderIdsAndKeepsSeasonAndAliasesSeparate() {
        var target = new MagnetCatalogEntrySearchTarget(videoId, "Example", "Alias One|Alias Two", "2026",
                "tt0000000", "0", "0", "SYNCED", "Final", 2, "series");
        var queries = MagnetSearchExecutor.queries(target);
        assertTrue(queries.stream().noneMatch(query -> List.of("IMDB", "TMDB", "DOUBAN").contains(query.type())));
        assertTrue(queries.stream().anyMatch(query -> query.value().equals("Example Final S02 2026")));
        assertTrue(queries.stream().anyMatch(query -> query.value().equals("Alias One")));
        assertTrue(queries.stream().noneMatch(query -> query.value().contains("|")));
    }

    private MagnetCatalogEntrySearchTarget target(String title, String year, Integer season) {
        return new MagnetCatalogEntrySearchTarget(videoId, title, null, year, "tt1234567", null, null, "SYNCED",
                null, season, season == null ? "movie" : "series");
    }
    private MagnetProviderSummary provider(String code, String type, List<String> supported) {
        return new MagnetProviderSummary(UUID.randomUUID(), code, code, type, "https://example.invalid", true,
                1, "HIGH", supported, 0, 0, 1000, 20, true, null, null, null, null, Instant.now(), Instant.now());
    }
    private MagnetProviderCandidate candidate(String title, Map<String, Object> evidence) {
        String hash = "ABCDEF1234567890ABCDEF1234567890ABCDEF12";
        return new MagnetProviderCandidate(hash, "magnet:?xt=urn:btih:" + hash, title, null, null, null, null, null,
                null, null, "IMDB", "tt1234567", 100, null, List.of(), evidence);
    }
    private MagnetProviderSearchResult response(MagnetProviderCandidate candidate) { return new MagnetProviderSearchResult(null, 200, List.of(candidate)); }
    private MagnetSearchJobSummary job(String status, int total, int accepted) {
        return new MagnetSearchJobSummary(jobId, videoId, "ADMIN_MANUAL", status, List.of(), List.of(),
                Instant.now(), Instant.now(), total, accepted, total - accepted, null, null, List.of(), Instant.now(), Instant.now());
    }
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> supplied(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
