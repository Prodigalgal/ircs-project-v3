package com.prodigalgal.ircs.aggregation;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Repairs a bounded set of historical bindings while retaining both existing works. */
@Service
@Slf4j
@RequiredArgsConstructor
class AggregationBindingCorrectionService {
    static final int MAX_RAW_VIDEOS = 20;
    private static final String READY = "READY";

    private final JdbcAggregationRepository repository;
    private final AggregationMatchingStrategy matcher;
    private final AggregationSearchSyncPublisher publisher;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public CorrectionPlan preview(UUID source, UUID target, List<UUID> requestedIds) {
        return inspect(source, target, validateIds(source, target, requestedIds));
    }

    @Transactional(timeout = 15)
    public CorrectionPlan apply(UUID source, UUID target, List<UUID> requestedIds, String fingerprint, String operator) {
        if (target == null || fingerprint == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A prepared target and fingerprint are required");
        }
        List<UUID> rawIds = validateIds(source, target, requestedIds);
        repository.configureAggregationTransaction();
        repository.lockAggregationShared();
        repository.lockRawVideos(rawIds);
        repository.lockUnifiedVideos(List.of(source, target));
        repository.lockSelectedCorrectionBindings(rawIds);
        CorrectionPlan plan = inspect(source, target, rawIds);
        if (!READY.equals(plan.status()) || !fingerprint.equals(plan.snapshot().fingerprint())) {
            throw new ResponseStatusException(CONFLICT, "Correction preview is stale or blocked: " + plan.status());
        }
        if (!repository.lockRetainedCorrectionBinding(source, rawIds)) {
            throw new ResponseStatusException(CONFLICT, "Correction would empty the source work");
        }
        if (repository.moveCorrectionBindings(source, target, rawIds) != rawIds.size()) {
            throw new ResponseStatusException(CONFLICT, "Bindings changed during correction");
        }

        // Preserve the works and their attached user/catalog/magnet records. The dirty maintenance
        // runner rebuilds derived fields; search work is published through the existing commit hook.
        repository.markUnifiedDirty(source);
        repository.markUnifiedDirty(target);
        rawIds.forEach(publisher::publishRawIndex);
        publisher.publishUnifiedIndex(source);
        publisher.publishUnifiedIndex(target);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Aggregation correction committed: operator={}, source={}, target={}, rawIds={}, fingerprint={}",
                        operator, source, target, rawIds, fingerprint);
            }
        });
        return new CorrectionPlan("APPLIED_DIRTY", source, target, rawIds, plan.evidence(), plan.snapshot());
    }

    private CorrectionPlan inspect(UUID source, UUID target, List<UUID> rawIds) {
        List<RawVideoAggregationRecord> rows = repository.findRawVideos(rawIds);
        if (rows.size() != rawIds.size() || rows.stream().anyMatch(raw -> !isReadyForCorrection(raw))) {
            return new CorrectionPlan("SOURCE_INPUT_NOT_READY", source, target, rawIds, List.of(), null);
        }
        var sourceWork = repository.findUnifiedCandidatesByIds(List.of(source)).stream().findFirst();
        if (sourceWork.isEmpty()) {
            return new CorrectionPlan("SOURCE_MISSING", source, target, rawIds, List.of(), null);
        }
        List<CorrectionEvidence> evidence = rows.stream().map(raw -> new CorrectionEvidence(raw.id(),
                matcher.bindingConflictReason(raw, sourceWork.orElseThrow()))).toList();
        if (evidence.stream().anyMatch(item -> item.sourceConflict() == null)) {
            return new CorrectionPlan("SOURCE_IDENTITY_NOT_EXCLUDED", source, target, rawIds, evidence, null);
        }
        UUID destination = target == null ? discoverDestination(source, rows) : target;
        if (destination == null) {
            return new CorrectionPlan("TARGET_REQUIRES_REVIEW", source, null, rawIds, evidence, null);
        }
        var targetWork = repository.findUnifiedCandidatesByIds(List.of(destination)).stream().findFirst();
        if (targetWork.isEmpty() || rows.stream().anyMatch(raw -> matcher.findBestMatch(raw, List.of(targetWork.orElseThrow())).isEmpty())) {
            return new CorrectionPlan("TARGET_IDENTITY_UNSUPPORTED", source, destination, rawIds, evidence, null);
        }

        var snapshot = repository.findBindingCorrectionSnapshot(source, destination, rawIds);
        String status = reviewStatus(rows, targetWork.orElseThrow(), snapshot);
        return new CorrectionPlan(status, source, destination, rawIds, evidence, snapshot);
    }

    private boolean isReadyForCorrection(RawVideoAggregationRecord raw) {
        // Older refreshes may have parked an existing association in PENDING. Its exact
        // membership and version are still checked by the same preview and commit guards.
        return "BOUND".equals(raw.aggregationStatus()) && "READY".equals(raw.normalizationStatus())
                && !"PENDING".equals(raw.enrichmentStatus())
                || "PENDING".equals(raw.aggregationStatus()) && raw.isAggregationEligible();
    }

    private String reviewStatus(List<RawVideoAggregationRecord> rows, UnifiedVideoAggregationCandidate target,
            JdbcAggregationRepository.BindingCorrectionSnapshot snapshot) {
        if (snapshot.expectedBindings() != rows.size()) { return "SOURCE_BINDINGS_CHANGED"; }
        if (snapshot.sourceMembers() <= rows.size()) { return "SOURCE_WOULD_BE_EMPTY"; }
        if (snapshot.histories() > 0) { return "WATCH_HISTORY_REQUIRES_REVIEW"; }
        if (snapshot.targetMembers() + rows.size() > AggregationMemberIdentity.REVIEW_MEMBER_LIMIT) {
            return AggregationMemberIdentity.REVIEW_LIMIT_REASON;
        }
        String conflict = matcher.clusterBindingConflictReason(target,
                repository.findBoundMemberIdentities(List.of(target.id())), rows);
        return conflict == null ? READY : "TARGET_CLUSTER:" + conflict;
    }

    private UUID discoverDestination(UUID source, List<RawVideoAggregationRecord> rows) {
        List<AggregationMatchPlan> matches = rows.stream().map(raw -> repository.findCorrectionMatch(raw, source)).toList();
        if (matches.stream().anyMatch(plan -> !plan.hasRoot() || plan.hasVictims())) {
            return null;
        }
        List<UUID> targets = matches.stream().map(AggregationMatchPlan::rootUnifiedVideoId).distinct().toList();
        return targets.size() == 1 ? targets.getFirst() : null;
    }

    private List<UUID> validateIds(UUID source, UUID target, List<UUID> ids) {
        if (source == null || source.equals(target) || ids == null || ids.isEmpty()
                || ids.size() > MAX_RAW_VIDEOS || ids.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(BAD_REQUEST, "One source, a distinct target, and 1 to 20 raw IDs are required");
        }
        List<UUID> ordered = ids.stream().distinct().sorted().toList();
        if (ordered.size() != ids.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Raw IDs must be unique");
        }
        return ordered;
    }

    record CorrectionEvidence(UUID rawVideoId, String sourceConflict) { }

    record CorrectionPlan(String status, UUID sourceUnifiedVideoId, UUID targetUnifiedVideoId,
            List<UUID> rawVideoIds, List<CorrectionEvidence> evidence,
            JdbcAggregationRepository.BindingCorrectionSnapshot snapshot) { }
}
