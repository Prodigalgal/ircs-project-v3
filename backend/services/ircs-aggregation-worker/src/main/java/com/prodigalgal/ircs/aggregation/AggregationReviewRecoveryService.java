package com.prodigalgal.ircs.aggregation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AggregationReviewRecoveryService {
    static final int MAX_SOURCE_RECORDS = 50;
    private final AggregationEvaluationService evaluation;
    private final AggregationWorkDeferrer queue;

    RecoveryResponse recover(List<UUID> rawIds, boolean execute) {
        if (rawIds == null || rawIds.isEmpty() || rawIds.size() > MAX_SOURCE_RECORDS
                || rawIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sourceRecordIds must contain 1 to 50 non-null UUIDs");
        }
        Map<UUID, AggregationWorkDeferrer.ReviewedWork> reviewed = new LinkedHashMap<>();
        List<UUID> unique = rawIds.stream().distinct().toList();
        unique.forEach(id -> queue.findReviewedWork(id).ifPresent(work -> reviewed.put(id, work)));
        Map<UUID, AggregationEvaluationService.EvaluationDecision> decisions = new LinkedHashMap<>();
        if (!reviewed.isEmpty()) {
            evaluation.evaluate(List.copyOf(reviewed.keySet())).decisions()
                    .forEach(decision -> decisions.put(decision.sourceRecordId(), decision));
        }
        List<RecoveryDecision> result = unique.stream()
                .map(id -> recoverOne(id, reviewed.get(id), decisions.get(id), execute)).toList();
        return new RecoveryResponse(execute, result);
    }

    private RecoveryDecision recoverOne(UUID id, AggregationWorkDeferrer.ReviewedWork work,
            AggregationEvaluationService.EvaluationDecision decision, boolean execute) {
        if (work == null || decision == null) {
            return new RecoveryDecision(id, null, "NOT_PENDING_IDENTITY_REVIEW", null);
        }
        UUID target = decision.proposedRootCatalogEntryId();
        boolean newBinding = "MATCH".equals(decision.decision()) && decision.originalCatalogEntryId() == null;
        boolean retainedBinding = "EXISTING_BINDING_COMPATIBLE".equals(decision.decision())
                && Objects.equals(decision.originalCatalogEntryId(), target);
        if ((!newBinding && !retainedBinding) || target == null || !decision.proposedVictimCatalogEntryIds().isEmpty()) {
            return new RecoveryDecision(id, target, "STILL_REQUIRES_REVIEW", decision.reason());
        }
        String result = execute
                ? (queue.requeueReviewedWork(work) ? "REQUEUED" : "QUEUE_CHANGED")
                : "READY";
        return new RecoveryDecision(id, target, result, work.reason());
    }

    record RecoveryDecision(UUID sourceRecordId, UUID targetCatalogEntryId, String status, String reason) { }
    record RecoveryResponse(boolean execute, List<RecoveryDecision> decisions) { }
}
