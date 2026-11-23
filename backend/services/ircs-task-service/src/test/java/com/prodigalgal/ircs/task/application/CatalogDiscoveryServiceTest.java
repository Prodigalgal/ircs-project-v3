package com.prodigalgal.ircs.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduledTask;
import com.prodigalgal.ircs.task.config.CatalogDiscoveryConfigValues;
import com.prodigalgal.ircs.task.domain.CatalogDiscoveryCandidate;
import com.prodigalgal.ircs.task.infrastructure.CatalogDiscoveryRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogDiscoveryServiceTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private final CatalogDiscoveryRepository repository = mock(CatalogDiscoveryRepository.class);
    private final TrendDiscoveryTaskService discovery = mock(TrendDiscoveryTaskService.class);
    private final CatalogDiscoveryConfigValues config = mock(CatalogDiscoveryConfigValues.class);
    private final CatalogDiscoveryService service = new CatalogDiscoveryService(repository, discovery, config);
    private final CatalogDiscoveryCandidate candidate =
            new CatalogDiscoveryCandidate(UUID.randomUUID(), 2, 12, "Example", 1, UUID.randomUUID());

    @BeforeEach
    void configure() {
        when(config.enabled()).thenReturn(true);
        when(config.batchSize()).thenReturn(5);
        when(config.maxDataSources()).thenReturn(3);
        when(config.maxActiveTasks()).thenReturn(24);
        when(config.leaseDuration()).thenReturn(LEASE);
        when(repository.claimDue(5, LEASE)).thenReturn(List.of(candidate));
        when(repository.renew(candidate, LEASE)).thenReturn(true);
    }

    @Test
    void onlyMarksSubmittedWhenAtLeastOneCollectionTaskAcceptedTheRequest() {
        when(discovery.scheduleCatalog(eq(candidate), eq(3), any())).thenReturn(result("QUEUED"));

        service.dispatchDue();

        verify(repository).markDispatchAccepted(candidate);
        verify(repository, never()).markSubmitted(candidate);
        verify(repository, never()).retry(any(), any(), any());
    }

    @Test
    void alreadyActiveRequestRecoversLostAcknowledgementWithoutStartingAnotherTask() {
        when(repository.hasActiveTasks(candidate)).thenReturn(true);

        service.dispatchDue();

        verify(repository).markSubmitted(candidate);
        verify(repository, never()).markDispatchAccepted(candidate);
        verifyNoInteractions(discovery);
    }

    @Test
    void fulfilledEpisodeRequestDoesNotStartAnotherSearch() {
        when(repository.hasRequestedResources(candidate)).thenReturn(true);

        service.dispatchDue();

        verify(repository).markFulfilled(candidate);
        verifyNoInteractions(discovery);
    }

    @Test
    void operatorPausePreventsCreatingReplacementTasksForTheSameTarget() {
        when(repository.isPausedByOperator(candidate)).thenReturn(true);

        service.dispatchDue();

        verify(repository).retry(candidate, Duration.ofHours(6), "OPERATOR_PAUSED");
        verifyNoInteractions(discovery);
    }

    @Test
    void activeCollectionBacklogDefersRequestWithoutStartingSearches() {
        when(repository.activeTaskCount(24)).thenReturn(24);

        service.dispatchDue();

        verify(repository).retry(candidate, Duration.ofMinutes(5), "COLLECTION_BACKPRESSURE");
        verify(repository, never()).markSubmitted(any());
        verifyNoInteractions(discovery);
    }

    @Test
    void sourceFanoutNeverExceedsRemainingCapacity() {
        when(repository.activeTaskCount(24)).thenReturn(23);
        when(discovery.scheduleCatalog(eq(candidate), eq(1), any())).thenReturn(result("ACTIVE"));

        service.dispatchDue();

        verify(discovery).scheduleCatalog(eq(candidate), eq(1), any());
        verify(repository).markDispatchAccepted(candidate);
    }

    @Test
    void unavailableSourcesUseTheCooldownWithoutCountingADispatchAttempt() {
        CatalogDiscoveryCandidate delayed = new CatalogDiscoveryCandidate(
                candidate.catalogEntryId(), 2, 12, "Example", 37, candidate.leaseToken());
        when(repository.claimDue(5, LEASE)).thenReturn(List.of(delayed));
        when(repository.renew(delayed, LEASE)).thenReturn(true);
        when(discovery.scheduleCatalog(eq(delayed), anyInt(), any()))
                .thenReturn(TrendDiscoveryScheduleResponse.empty("trend-discovery"));

        service.dispatchDue();

        verify(repository).retry(delayed, Duration.ofMinutes(30), "NO_ELIGIBLE_SOURCE");
        verify(repository, never()).markSubmitted(any());
    }

    @Test
    void upstreamExceptionTextIsNotPersistedInRetryState() {
        when(discovery.scheduleCatalog(eq(candidate), anyInt(), any()))
                .thenThrow(new IllegalStateException("https://source.example/?token=do-not-store"));

        service.dispatchDue();

        verify(repository).retry(candidate, Duration.ofMinutes(10), "DISCOVERY_DISPATCH_FAILED");
        verify(repository, never()).markSubmitted(any());
    }

    @Test
    void precheckFailureUsesStableShortRetryWithoutSourceDispatch() {
        when(repository.hasRequestedResources(candidate)).thenThrow(new IllegalStateException("database unavailable"));

        service.dispatchDue();

        verify(repository).retry(candidate, Duration.ofMinutes(5), "DISCOVERY_PRECHECK_FAILED");
        verifyNoInteractions(discovery);
    }

    @Test
    void nonAcceptedDispatchUsesTheNextAttemptBackoffStep() {
        when(discovery.scheduleCatalog(eq(candidate), anyInt(), any())).thenReturn(result("SKIPPED"));

        service.dispatchDue();

        verify(repository).retry(candidate, Duration.ofMinutes(10), "DISCOVERY_NOT_ACCEPTED");
    }

    @Test
    void lostClaimCannotStartSearches() {
        when(repository.renew(candidate, LEASE)).thenReturn(false);

        service.dispatchDue();

        verifyNoInteractions(discovery);
        verify(repository, never()).markSubmitted(any());
    }

    @Test
    void runtimeDisableStopsClaimingWork() {
        when(config.enabled()).thenReturn(false);

        service.dispatchDue();

        verifyNoInteractions(repository, discovery);
    }

    @Test
    void retryBackoffIsBoundedAndNeverCreatesAHigherFrequencyHotLoop() {
        assertThat(CatalogDiscoveryService.retryDelay(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(CatalogDiscoveryService.retryDelay(2)).isEqualTo(Duration.ofMinutes(10));
        assertThat(CatalogDiscoveryService.retryDelay(1000000)).isEqualTo(Duration.ofHours(6));
    }

    private TrendDiscoveryScheduleResponse result(String status) {
        return new TrendDiscoveryScheduleResponse("trend-discovery", 1, 1, 1, 0,
                "QUEUED".equals(status) ? 1 : 0,
                List.of(new TrendDiscoveryScheduledTask(UUID.randomUUID(), UUID.randomUUID(), "Source", "Example", status)),
                List.of());
    }
}
