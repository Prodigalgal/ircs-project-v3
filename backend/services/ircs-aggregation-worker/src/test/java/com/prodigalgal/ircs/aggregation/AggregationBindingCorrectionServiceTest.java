package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class AggregationBindingCorrectionServiceTest {

    private final JdbcAggregationRepository repository = mock(JdbcAggregationRepository.class);
    private final AggregationMatchingStrategy matcher = mock(AggregationMatchingStrategy.class);
    private final AggregationSearchSyncPublisher publisher = mock(AggregationSearchSyncPublisher.class);
    private final AggregationBindingCorrectionService service =
            new AggregationBindingCorrectionService(repository, matcher, publisher);

    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final UUID rawId = UUID.randomUUID();
    private final RawVideoAggregationRecord raw = raw(rawId);
    private final UnifiedVideoAggregationCandidate source = candidate(sourceId, "Source Work");
    private final UnifiedVideoAggregationCandidate target = candidate(targetId, "Target Work");

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void previewBuildsReadyPlanWithoutMutatingBindingsOrPublishing() {
        arrangeReadyPreview();

        var plan = service.preview(sourceId, targetId, List.of(rawId));

        assertThat(plan.status()).isEqualTo("READY");
        assertThat(plan.snapshot().fingerprint()).isEqualTo("snapshot-1");
        assertThat(plan.evidence()).containsExactly(
                new AggregationBindingCorrectionService.CorrectionEvidence(rawId, "EXTERNAL_ID_CONFLICT_TMDB"));
        verifyNoInteractions(publisher);
        verify(repository, never()).configureAggregationTransaction();
        verify(repository, never()).lockAggregationShared();
        verify(repository, never()).lockRawVideos(List.of(rawId));
        verify(repository, never()).lockUnifiedVideos(List.of(sourceId, targetId));
        verify(repository, never()).moveCorrectionBindings(sourceId, targetId, List.of(rawId));
        verify(repository, never()).markUnifiedDirty(sourceId);
        verify(repository, never()).markUnifiedDirty(targetId);
    }

    @Test
    void applyRejectsStaleFingerprintBeforeAnyCommitSynchronizationOrMutation() {
        arrangeReadyPreview();

        assertThatThrownBy(() -> service.apply(sourceId, targetId, List.of(rawId), "stale-fingerprint", "operator-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(CONFLICT));

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        verify(repository, never()).lockRetainedCorrectionBinding(sourceId, List.of(rawId));
        verify(repository, never()).moveCorrectionBindings(sourceId, targetId, List.of(rawId));
        verify(repository, never()).markUnifiedDirty(sourceId);
        verify(repository, never()).markUnifiedDirty(targetId);
        verifyNoInteractions(publisher);
    }

    @Test
    void previewBlocksSourceWithWatchHistory() {
        arrangeCommonInputs();
        when(repository.findBindingCorrectionSnapshot(sourceId, targetId, List.of(rawId)))
                .thenReturn(snapshot(2, 1, 1, 1, "snapshot-history"));

        var plan = service.preview(sourceId, targetId, List.of(rawId));

        assertThat(plan.status()).isEqualTo("WATCH_HISTORY_REQUIRES_REVIEW");
        assertThat(plan.snapshot().histories()).isEqualTo(1);
        verifyNoInteractions(publisher);
        verify(repository, never()).moveCorrectionBindings(sourceId, targetId, List.of(rawId));
    }

    @Test
    void previewBlocksChangedOrCompetingSourceBindings() {
        arrangeCommonInputs();
        when(repository.findBindingCorrectionSnapshot(sourceId, targetId, List.of(rawId)))
                .thenReturn(snapshot(2, 1, 0, 0, "snapshot-group"));

        var plan = service.preview(sourceId, targetId, List.of(rawId));

        assertThat(plan.status()).isEqualTo("SOURCE_BINDINGS_CHANGED");
        verifyNoInteractions(publisher);
        verify(repository, never()).moveCorrectionBindings(sourceId, targetId, List.of(rawId));
    }

    @Test
    void applyMovesOnlyAfterFreshReadyPlanAndRegistersSearchWorkForCommit() {
        arrangeReadyPreview();
        when(repository.lockRetainedCorrectionBinding(sourceId, List.of(rawId))).thenReturn(true);
        when(repository.moveCorrectionBindings(sourceId, targetId, List.of(rawId))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        var plan = service.apply(sourceId, targetId, List.of(rawId), "snapshot-1", "operator-2");

        assertThat(plan.status()).isEqualTo("APPLIED_DIRTY");
        assertThat(plan.rawVideoIds()).containsExactly(rawId);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(repository).lockRetainedCorrectionBinding(sourceId, List.of(rawId));
        verify(repository).moveCorrectionBindings(sourceId, targetId, List.of(rawId));
        verify(repository).markUnifiedDirty(sourceId);
        verify(repository).markUnifiedDirty(targetId);
        verify(publisher).publishRawIndex(rawId);
        verify(publisher).publishUnifiedIndex(sourceId);
        verify(publisher).publishUnifiedIndex(targetId);
    }

    private void arrangeReadyPreview() {
        arrangeCommonInputs();
        when(repository.findBindingCorrectionSnapshot(sourceId, targetId, List.of(rawId)))
                .thenReturn(snapshot(2, 1, 0, 1, "snapshot-1"));
    }

    private void arrangeCommonInputs() {
        when(repository.findRawVideos(List.of(rawId))).thenReturn(List.of(raw));
        when(repository.findUnifiedCandidatesByIds(List.of(sourceId))).thenReturn(List.of(source));
        when(repository.findUnifiedCandidatesByIds(List.of(targetId))).thenReturn(List.of(target));
        when(matcher.bindingConflictReason(raw, source)).thenReturn("EXTERNAL_ID_CONFLICT_TMDB");
        when(matcher.findBestMatch(raw, List.of(target))).thenReturn(Optional.of(targetId));
        when(repository.findBoundMemberIdentities(List.of(targetId))).thenReturn(List.of());
        when(matcher.clusterBindingConflictReason(target, List.of(), List.of(raw))).thenReturn(null);
    }

    private static JdbcAggregationRepository.BindingCorrectionSnapshot snapshot(
            long sourceMembers, long targetMembers, long histories, int expectedBindings, String fingerprint) {
        return new JdbcAggregationRepository.BindingCorrectionSnapshot(
                sourceMembers, targetMembers, 0, histories, 0, expectedBindings, fingerprint);
    }

    private static RawVideoAggregationRecord raw(UUID id) {
        return new RawVideoAggregationRecord(id, "Correction Target Movie", null, null, "2024", null, null,
                null, null, null, null, null, "电影", "movie", null, null, "101", null,
                "READY", "SUCCESS", "BOUND");
    }

    private static UnifiedVideoAggregationCandidate candidate(UUID id, String title) {
        return new UnifiedVideoAggregationCandidate(id, title, null, null, "2024", null, null, null,
                "电影", null, null, null, null, null);
    }
}
