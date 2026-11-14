package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregationEvaluationServiceTest {
    private final JdbcAggregationRepository repository = mock(JdbcAggregationRepository.class);
    private final AggregationSafetyPolicy policy = new AggregationSafetyPolicy(false, "douban:123");
    private final AggregationMatchingStrategy matcher = new AggregationMatchingStrategy(policy);
    private final AggregationEvaluationService service = new AggregationEvaluationService(repository, matcher, policy);

    @Test
    void deduplicatesPreservingOrderAndReportsMissingAndNewWithoutAnyWrite() {
        UUID id = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "PENDING", "101");
        when(repository.findRawVideos(List.of(id, missing))).thenReturn(List.of(raw));
        when(repository.findExistingBinding(id)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.none());

        var result = service.evaluate(List.of(id, missing, id));

        assertThat(result.scope()).isEqualTo("SNAPSHOT_ONLY");
        assertThat(result.autoUnifiedMergeEnabled()).isFalse();
        assertThat(result.limitations()).contains("no locks, writes", "commit authorization");
        assertThat(result.decisions()).extracting(AggregationEvaluationService.EvaluationDecision::rawVideoId)
                .containsExactly(id, missing);
        assertThat(result.decisions()).extracting(AggregationEvaluationService.EvaluationDecision::decision)
                .containsExactly("CREATE_NEW", "MISSING");
        verify(repository).findRawVideos(List.of(id, missing));
        verify(repository).findExistingBinding(id);
        verify(repository).findMatchPlan(raw);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void fallsBackToSingleReadsWhenBatchLoaderIsUnavailable() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findRawVideos(List.of(first, second))).thenReturn(null);
        when(repository.findRawVideo(first)).thenReturn(Optional.empty());
        when(repository.findRawVideo(second)).thenReturn(Optional.empty());

        assertThat(service.evaluate(List.of(first, second)).decisions())
                .extracting(AggregationEvaluationService.EvaluationDecision::decision)
                .containsExactly("MISSING", "MISSING");
        verify(repository).findRawVideos(List.of(first, second));
        verify(repository).findRawVideo(first);
        verify(repository).findRawVideo(second);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void waitingInputPreservesBindingAndDoesNotRecallCandidates() {
        UUID id = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "PENDING", "PENDING", "PENDING", "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(id)).thenReturn(Optional.of(existing));

        var decision = service.evaluate(List.of(id)).decisions().getFirst();

        assertThat(decision.originalUnifiedVideoId()).isEqualTo(existing);
        assertThat(decision.decision()).isEqualTo("WAITING_DEPENDENCIES");
        assertThat(decision.reason()).contains("normalization=PENDING", "enrichment=PENDING");
        verify(repository).findRawVideo(id);
        verify(repository).findExistingBinding(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void refreshUsesExistingBindingAndSharedCompatibilityRulesInsteadOfReplacingIt() {
        UUID id = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "PENDING", "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(id)).thenReturn(Optional.of(existing));
        when(repository.findUnifiedCandidatesByIds(Set.of(existing))).thenReturn(List.of(candidate(existing, "101")));

        var decision = service.evaluate(List.of(id)).decisions().getFirst();

        assertThat(decision.decision()).isEqualTo("EXISTING_BINDING_COMPATIBLE");
        assertThat(decision.proposedRootUnifiedVideoId()).isEqualTo(existing);
        verify(repository).findRawVideo(id);
        verify(repository).findExistingBinding(id);
        verify(repository).findUnifiedCandidatesByIds(Set.of(existing));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void boundHistoricalConflictIsVisibleEvenThoughProductionWouldSkipDuplicate() {
        UUID id = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "BOUND", "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(id)).thenReturn(Optional.of(existing));
        when(repository.findUnifiedCandidatesByIds(Set.of(existing))).thenReturn(List.of(candidate(existing, "202")));

        var decision = service.evaluate(List.of(id)).decisions().getFirst();

        assertThat(decision.decision()).isEqualTo("IDENTITY_CONFLICT");
        assertThat(decision.reason()).isEqualTo("RAW_AND_TARGET_IDENTITY_CONFLICT:EXTERNAL_ID_CONFLICT_TMDB");
        verify(repository, never()).findMatchPlan(any());
    }

    @Test
    void compatibleBoundInputIsAlreadyBoundAndBrokenBoundInputIsConflict() {
        UUID id = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "BOUND", "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(id)).thenReturn(Optional.of(existing));
        when(repository.findUnifiedCandidatesByIds(Set.of(existing))).thenReturn(List.of(candidate(existing, "101")));
        assertThat(service.evaluate(List.of(id)).decisions().getFirst().decision()).isEqualTo("ALREADY_BOUND");

        when(repository.findExistingBinding(id)).thenReturn(Optional.empty());
        assertThat(service.evaluate(List.of(id)).decisions().getFirst().reason()).isEqualTo("BOUND_WITHOUT_BINDING");
    }

    @Test
    void recallsRealMatchPlanAndRechecksItsTargetUsingSharedMatcher() {
        UUID id = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "PENDING", "101");
        UnifiedVideoAggregationCandidate candidate = candidate(target, "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(matcher.findMatchPlan(raw, List.of(candidate)));
        when(repository.findUnifiedCandidatesByIds(Set.of(target))).thenReturn(List.of(candidate));

        var decision = service.evaluate(List.of(id)).decisions().getFirst();

        assertThat(decision.decision()).isEqualTo("MATCH");
        assertThat(decision.originalUnifiedVideoId()).isNull();
        assertThat(decision.proposedRootUnifiedVideoId()).isEqualTo(target);
        assertThat(decision.proposedVictimUnifiedVideoIds()).isEmpty();
    }

    @Test
    void newBindingMustStillMeetMatchThresholdAfterReadingFreshTarget() {
        UUID id = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "PENDING", "101");
        UnifiedVideoAggregationCandidate fresh = new UnifiedVideoAggregationCandidate(target,
                "完全不同的无关作品", null, null, "2026", "1", "90m", null, "电影",
                null, null, null, null, null);
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.rootOnly(target));
        when(repository.findUnifiedCandidatesByIds(Set.of(target))).thenReturn(List.of(fresh));
        assertThat(matcher.isCompatibleBinding(raw, fresh)).isTrue();
        assertThat(matcher.findBestMatch(raw, List.of(fresh))).isEmpty();

        var decision = service.evaluate(List.of(id)).decisions().getFirst();

        assertThat(decision.decision()).isEqualTo("SOURCE_CHANGED");
        assertThat(decision.reason()).isEqualTo("FRESH_TARGET_NO_LONGER_MEETS_MATCH_RULES");
        assertThat(decision.proposedRootUnifiedVideoId()).isEqualTo(target);

        // A legitimate existing binding refresh only needs hard-identity compatibility.
        when(repository.findExistingBinding(id)).thenReturn(Optional.of(target));
        assertThat(service.evaluate(List.of(id)).decisions().getFirst().decision())
                .isEqualTo("EXISTING_BINDING_COMPATIBLE");
    }

    @Test
    void ambiguousRecallAndMergeDisabledAreExplainedWithoutChangingProposedVictims() {
        UUID id = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "PENDING", "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.ambiguous());
        assertThat(service.evaluate(List.of(id)).decisions().getFirst().reason())
                .isEqualTo("MULTIPLE_IDENTITY_CANDIDATES");

        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.rootWithVictims(root, List.of(victim)));
        when(repository.findUnifiedCandidatesByIds(Set.of(root, victim)))
                .thenReturn(List.of(candidate(root, "101"), candidate(victim, "101")));
        var disabled = service.evaluate(List.of(id)).decisions().getFirst();
        assertThat(disabled.decision()).isEqualTo("AMBIGUOUS");
        assertThat(disabled.reason()).isEqualTo("AUTOMATIC_UNIFIED_MERGE_DISABLED");
        assertThat(disabled.proposedVictimUnifiedVideoIds()).containsExactly(victim);
    }

    @Test
    void mergeEnabledStillRechecksFullUnifiedSetAndRejectsMissingTarget() {
        var enabledPolicy = new AggregationSafetyPolicy(true, "douban:123");
        var enabled = new AggregationEvaluationService(repository,
                new AggregationMatchingStrategy(enabledPolicy), enabledPolicy);
        UUID id = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        RawVideoAggregationRecord raw = raw(id, "READY", "SUCCESS", "PENDING", "101");
        when(repository.findRawVideo(id)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.rootWithVictims(root, List.of(victim)));
        when(repository.findUnifiedCandidatesByIds(Set.of(root, victim)))
                .thenReturn(List.of(candidate(root, "101"), candidate(victim, "202")));
        assertThat(enabled.evaluate(List.of(id)).decisions().getFirst().reason())
                .isEqualTo("FINAL_UNIFIED_SET_LACKS_MERGE_EVIDENCE");

        when(repository.findUnifiedCandidatesByIds(Set.of(root, victim))).thenReturn(List.of(
                candidate(root, "101").withMetadata(Set.of("Shared Actor"), Set.of(), Set.of()),
                candidate(victim, "101").withMetadata(Set.of("Shared Actor"), Set.of(), Set.of())));
        var acceptedSnapshot = enabled.evaluate(List.of(id)).decisions().getFirst();
        assertThat(acceptedSnapshot.decision()).isEqualTo("MATCH");
        assertThat(acceptedSnapshot.proposedVictimUnifiedVideoIds()).containsExactly(victim);

        when(repository.findUnifiedCandidatesByIds(Set.of(root, victim))).thenReturn(List.of(candidate(root, "101")));
        assertThat(enabled.evaluate(List.of(id)).decisions().getFirst().reason())
                .isEqualTo("CANDIDATE_SET_CHANGED_OR_INCOMPLETE");
    }

    @Test
    void rejectsInvalidOrUnboundedInputBeforeAnyRepositoryCall() {
        assertThatThrownBy(() -> service.evaluate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(List.of())).isInstanceOf(IllegalArgumentException.class);
        List<UUID> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> service.evaluate(withNull)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(java.util.Collections.nCopies(101, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsEmptyTransactionSynchronizationBeforeItCanRetainAConnection() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.evaluate(List.of(UUID.randomUUID())))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        verifyNoInteractions(repository);
    }

    private RawVideoAggregationRecord raw(UUID id, String normalization, String enrichment, String aggregation, String tmdb) {
        return new RawVideoAggregationRecord(id, "Shadow Evaluation Movie", null, null, "2026", null, null,
                "1", "90m", null, null, null, "电影", "movie", null, tmdb, null, null,
                normalization, enrichment, aggregation);
    }

    private UnifiedVideoAggregationCandidate candidate(UUID id, String tmdb) {
        return new UnifiedVideoAggregationCandidate(id, "Shadow Evaluation Movie", null, null, "2026",
                "1", "90m", null, "电影", null, tmdb, null, null, null);
    }
}
