package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the "multi-candidate victim" hazard: a raw that is similar to two different unified rows is
 * evidence that the raw is ambiguous, NOT that the two unified rows are the same work. Treating the
 * extra candidates as merge victims turns a weak match into a destructive merge of unrelated works
 * (favourites, watch history and field values are migrated, then the victim row is deleted).
 */
class AggregationVictimMergeHazardTest {

    private final AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();

    @Test
    void rawBridgingTwoDifferentWorksMustNotMergeThem() {
        // Root carries a usable ID; decoy has NO ID at all, so it never conflicts logically and the
        // hard-identity check waves it through. Its title is merely close, not identical.
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID decoyId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate root = candidate(rootId, "Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate decoy = candidate(decoyId, "Codex Signet", "2026", null, null);

        AggregationMatchPlan plan = strategy.findMatchPlan(raw, List.of(root, decoy));

        assertThat(plan.rootUnifiedVideoId()).isEqualTo(rootId);
        // The decoy is a different work that merely resembles the raw; it must not be merged away.
        assertThat(plan.victimUnifiedVideoIds()).isEmpty();
    }

    @Test
    void sharedUsableIdAndMatchingTitlesStillPermitDuplicateMerge() {
        // Both stored rows carry the same usable ID and corroborating title.
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID duplicateId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate root = candidate(rootId, "Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate duplicate = candidate(duplicateId, "Codex Signal", "2026", "9001", null);

        AggregationMatchPlan plan = strategy.findMatchPlan(raw, List.of(root, duplicate));

        assertThat(plan.rootUnifiedVideoId()).isEqualTo(rootId);
        assertThat(plan.victimUnifiedVideoIds()).containsExactly(duplicateId);
    }

    @Test
    void rawCarryingDifferentProviderIdsDoesNotProveStoredWorksAreDuplicates() {
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", "9001", "70001");
        UnifiedVideoAggregationCandidate root = candidate(rootId, "Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate other = candidate(otherId, "Codex Signal", "2026", null, "70001");

        AggregationMatchPlan plan = strategy.findMatchPlan(raw, List.of(root, other));

        assertThat(plan.hasRoot()).isFalse();
        assertThat(plan.victimUnifiedVideoIds()).isEmpty();
    }

    @Test
    void compliantDuplicateWithNoIdsIsStillLeftAloneWhenOnlyTitleLinksIt() {
        // Both decoys lack ids; only an approximate title ties them together. Absence of conflict is
        // not evidence of identity, so neither may be merged on the raw's say-so.
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID decoyId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", null, null);
        UnifiedVideoAggregationCandidate root = candidate(rootId, "Codex Signal", "2026", null, null);
        UnifiedVideoAggregationCandidate decoy = candidate(decoyId, "Codex Signet", "2026", null, null);

        AggregationMatchPlan plan = strategy.findMatchPlan(raw, List.of(root, decoy));

        assertThat(plan.victimUnifiedVideoIds()).isEmpty();
    }

    @Test
    void completeMergePlanRejectsVictimToVictimConflictThroughSparseRoot() {
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", null, "70001");
        UnifiedVideoAggregationCandidate root = candidate(rootId, "Codex Signal", "2026", null, "70001");
        UnifiedVideoAggregationCandidate first = candidate(UUID.randomUUID(), "Codex Signal", "2026", "9001", "70001");
        UnifiedVideoAggregationCandidate second = candidate(UUID.randomUUID(), "Codex Signal", "2026", "9002", "70001");
        List<UnifiedVideoAggregationCandidate> candidates = List.of(root, first, second);

        assertThat(strategy.isSafeUnifiedMerge(candidates, Set.of(root.id(), first.id(), second.id()))).isFalse();
        assertThat(strategy.findMatchPlan(raw, candidates).victimUnifiedVideoIds()).isEmpty();
    }

    @Test
    void completeMergePlanRejectsMissingUnexpectedAndDuplicateCandidates() {
        UnifiedVideoAggregationCandidate first = candidate(UUID.randomUUID(), "Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate second = candidate(UUID.randomUUID(), "Codex Signal", "2026", "9001", null);
        Set<UUID> expected = Set.of(first.id(), second.id());

        assertThat(strategy.isSafeUnifiedMerge(List.of(first), expected)).isFalse();
        assertThat(strategy.isSafeUnifiedMerge(List.of(first, first), expected)).isFalse();
        assertThat(strategy.isSafeUnifiedMerge(List.of(first, second), Set.of(first.id()))).isFalse();
        assertThat(strategy.isSafeUnifiedMerge(List.of(first, second), expected)).isTrue();
    }

    @Test
    void completeMergePlanRequiresPositiveEvidenceRatherThanOnlyNoConflicts() {
        UnifiedVideoAggregationCandidate first = candidate(UUID.randomUUID(), "Codex Signal", "2026", null, null);
        UnifiedVideoAggregationCandidate second = candidate(UUID.randomUUID(), "Codex Signal", "2026", null, null);

        assertThat(strategy.isSafeUnifiedMerge(List.of(first, second), Set.of(first.id(), second.id()))).isFalse();
    }

    @Test
    void approximateTitleAndSingleUnverifiedIdAreInsufficientForDestructiveMerge() {
        UnifiedVideoAggregationCandidate first = candidate(UUID.randomUUID(), "Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate second = candidate(UUID.randomUUID(), "Codex Signal Extended Cut", "2026", "9001", null);

        assertThat(strategy.isSafeUnifiedMerge(List.of(first, second), Set.of(first.id(), second.id()))).isFalse();
    }

    @Test
    void unifiedMergeUsesReleaseToleranceButUnknownYearCannotHideKnownConflict() {
        UnifiedVideoAggregationCandidate first = candidate(UUID.randomUUID(), "Codex Signal", "2025", "9001", null);
        UnifiedVideoAggregationCandidate next = candidate(UUID.randomUUID(), "Codex Signal", "2026", "9001", null);
        UnifiedVideoAggregationCandidate distant = candidate(UUID.randomUUID(), "Codex Signal", "2027", "9001", null);
        UnifiedVideoAggregationCandidate unknown = candidate(UUID.randomUUID(), "Codex Signal", null, "9001", null);

        assertThat(strategy.isSafeUnifiedMerge(List.of(first, next), Set.of(first.id(), next.id()))).isTrue();
        assertThat(strategy.isSafeUnifiedMerge(List.of(first, unknown, distant),
                Set.of(first.id(), unknown.id(), distant.id()))).isFalse();
    }

    private RawVideoAggregationRecord rawVideo(String title, String year, String doubanId, String tmdbId) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
                title,
                null,
                "Aggregation description",
                year,
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                null,
                null,
                null,
                "电影",
                "movie",
                doubanId,
                tmdbId,
                null,
                null,
                "READY",
                "SUCCESS",
                "PENDING");
    }

    private UnifiedVideoAggregationCandidate candidate(
            UUID id, String title, String year, String doubanId, String tmdbId) {
        return new UnifiedVideoAggregationCandidate(
                id,
                title,
                null,
                null,
                year,
                "12",
                "45m",
                null,
                "电影",
                doubanId,
                tmdbId,
                null,
                null,
                null);
    }
}
