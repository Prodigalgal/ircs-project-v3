package com.prodigalgal.ircs.aggregation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only single-raw shadow decisions. Each database/search read may observe a newer snapshot. */
@Service
@RequiredArgsConstructor
class AggregationEvaluationService {
    private final JdbcAggregationRepository repository;
    private final AggregationMatchingStrategy matchingStrategy;
    private final AggregationSafetyPolicy safetyPolicy;

    @Transactional(propagation = Propagation.NEVER)
    public EvaluationResponse evaluate(List<UUID> rawVideoIds) {
        if (rawVideoIds == null || rawVideoIds.isEmpty() || rawVideoIds.size() > 100
                || rawVideoIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("rawVideoIds must contain 1 to 100 non-null UUIDs");
        }
        List<UUID> orderedIds = rawVideoIds.stream().distinct().toList();
        Map<UUID, RawVideoAggregationRecord> rawById = loadRawVideos(orderedIds);
        List<EvaluationDecision> decisions = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            RawVideoAggregationRecord raw = rawById.get(id);
            decisions.add(raw == null
                    ? new EvaluationDecision(id, null, null, List.of(), "MISSING", "RAW_VIDEO_NOT_FOUND")
                    : evaluateRaw(raw));
        }
        return new EvaluationResponse("SNAPSHOT_ONLY", Instant.now(), safetyPolicy.automaticUnifiedMergeEnabled(),
                "Single-raw observation only; no locks, writes, batch graph, content-safety evaluation or commit authorization.",
                List.copyOf(decisions));
    }

    private Map<UUID, RawVideoAggregationRecord> loadRawVideos(List<UUID> ids) {
        Map<UUID, RawVideoAggregationRecord> loaded = new LinkedHashMap<>();
        if (ids.size() > 1) {
            List<RawVideoAggregationRecord> batch = repository.findRawVideos(ids);
            if (batch != null) {
                batch.forEach(raw -> loaded.putIfAbsent(raw.id(), raw));
                return loaded;
            }
        }
        for (UUID id : ids) { repository.findRawVideo(id).ifPresent(raw -> loaded.put(id, raw)); }
        return loaded;
    }

    private EvaluationDecision evaluateRaw(RawVideoAggregationRecord raw) {
        Optional<UUID> existing = repository.findExistingBinding(raw.id());
        UUID original = existing.orElse(null);
        boolean alreadyBound = "BOUND".equals(raw.aggregationStatus());
        if (!raw.isAggregationEligible()) {
            if (alreadyBound && existing.isEmpty()) {
                return decision(raw, null, AggregationMatchPlan.none(), "IDENTITY_CONFLICT", "BOUND_WITHOUT_BINDING");
            }
            if (!alreadyBound) {
                return decision(raw, original, AggregationMatchPlan.none(), "WAITING_DEPENDENCIES",
                        "INPUT_NOT_ELIGIBLE:normalization=" + raw.normalizationStatus()
                                + ",enrichment=" + raw.enrichmentStatus() + ",aggregation=" + raw.aggregationStatus());
            }
        }

        // Production refreshes an existing binding first; it does not search for a replacement work.
        AggregationMatchPlan plan = existing.map(AggregationMatchPlan::rootOnly)
                .orElseGet(() -> repository.findMatchPlan(raw));
        if (plan.isAmbiguous()) {
            return decision(raw, original, plan, "AMBIGUOUS", plan.resolutionReason());
        }
        if (!plan.hasRoot()) {
            return decision(raw, original, plan, "CREATE_NEW", "NO_SUFFICIENT_IDENTITY_MATCH");
        }
        Set<UUID> expected = new LinkedHashSet<>();
        expected.add(plan.rootUnifiedVideoId());
        expected.addAll(plan.victimUnifiedVideoIds());
        List<UnifiedVideoAggregationCandidate> candidates = repository.findUnifiedCandidatesByIds(expected);
        Set<UUID> actual = new LinkedHashSet<>();
        candidates.forEach(candidate -> actual.add(candidate.id()));
        if (candidates.size() != expected.size() || !actual.equals(expected)) {
            return decision(raw, original, plan, "IDENTITY_CONFLICT", "CANDIDATE_SET_CHANGED_OR_INCOMPLETE");
        }
        UnifiedVideoAggregationCandidate root = candidates.stream()
                .filter(candidate -> plan.rootUnifiedVideoId().equals(candidate.id())).findFirst().orElseThrow();
        if (!matchingStrategy.isCompatibleBinding(raw, root)) {
            return decision(raw, original, plan, "IDENTITY_CONFLICT", "RAW_AND_TARGET_IDENTITY_CONFLICT");
        }
        if (existing.isEmpty() && matchingStrategy.findBestMatch(raw, List.of(root)).isEmpty()) {
            return decision(raw, original, plan, "SOURCE_CHANGED", "FRESH_TARGET_NO_LONGER_MEETS_MATCH_RULES");
        }
        if (plan.hasVictims() && !safetyPolicy.automaticUnifiedMergeEnabled()) {
            return decision(raw, original, plan, "AMBIGUOUS", "AUTOMATIC_UNIFIED_MERGE_DISABLED");
        }
        if (plan.hasVictims() && !matchingStrategy.isSafeUnifiedMerge(candidates, expected)) {
            return decision(raw, original, plan, "AMBIGUOUS", "FINAL_UNIFIED_SET_LACKS_MERGE_EVIDENCE");
        }
        return decision(raw, original, plan, alreadyBound ? "ALREADY_BOUND"
                        : existing.isPresent() ? "EXISTING_BINDING_COMPATIBLE" : "MATCH",
                alreadyBound ? "BOUND_IDENTITY_COMPATIBLE_CONTENT_SAFETY_NOT_EVALUATED"
                        : existing.isPresent() ? "EXISTING_BINDING_PASSES_SHARED_COMPATIBILITY_RULES"
                        : "MATCH_PLAN_PASSES_SHARED_COMPATIBILITY_AND_MERGE_RULES");
    }

    private static EvaluationDecision decision(RawVideoAggregationRecord raw, UUID original,
            AggregationMatchPlan plan, String decision, String reason) {
        return new EvaluationDecision(raw.id(), original, plan.rootUnifiedVideoId(),
                plan.victimUnifiedVideoIds(), decision, reason);
    }

    record EvaluationResponse(String scope, Instant evaluatedAt, boolean autoUnifiedMergeEnabled,
            String limitations, List<EvaluationDecision> decisions) { }

    record EvaluationDecision(UUID rawVideoId, UUID originalUnifiedVideoId, UUID proposedRootUnifiedVideoId,
            List<UUID> proposedVictimUnifiedVideoIds, String decision, String reason) { }
}
