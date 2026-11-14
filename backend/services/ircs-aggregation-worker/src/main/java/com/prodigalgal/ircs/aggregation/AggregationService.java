package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.contracts.aggregation.AggregationMaintenanceRunResponse;
import com.prodigalgal.ircs.contracts.aggregation.AggregationResetStepResponse;
import java.util.ArrayList;
import com.prodigalgal.ircs.common.lease.ClusterLeaseGuard;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private static final int MAX_GRAPH_RAW_BATCH_SIZE = 5;

    private final JdbcAggregationRepository repository;
    private final AggregationSearchSyncPublisher publisher;
    private final AggregationStorageCommandPublisher storageCommandPublisher;
    private final RawVideoGraphClusterer rawVideoGraphClusterer;
    private final AggregationPipelineRuntime aggregationPipelineRuntime;
    private final AggregationMatchKeyLock matchKeyLock;
    private final AggregationWorkPublisher workPublisher;
    private final PlatformTransactionManager transactionManager;
    private final AggregationMatchingStrategy matchingStrategy;
    private final AggregationSafetyPolicy safetyPolicy;

    public AggregationResult aggregateOne(UUID rawVideoId) {
        return withDeferredAdultAssessments(() -> {
            repository.lockAggregationShared();
            repository.lockRawVideos(List.of(rawVideoId));
            return aggregateOneInternal(rawVideoId);
        }, false);
    }

    public AggregationResult aggregateRuntimeWork(UUID rawVideoId) {
        return aggregateRuntimeWorkBatch(List.of(rawVideoId)).stream()
                .filter(result -> rawVideoId.equals(result.rawVideoId()))
                .findFirst()
                .orElseGet(() -> AggregationResult.skipped(rawVideoId, "MISSING_RAW_VIDEO"));
    }

    public List<AggregationResult> aggregateRuntimeWorkBatch(List<UUID> rawVideoIds) {
        assertOutsideTransaction();
        List<UUID> orderedIds = rawVideoIds == null ? List.of() : rawVideoIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (orderedIds.size() > 100) { throw new IllegalArgumentException("Aggregation batch cannot exceed 100 raw videos"); }
        if (orderedIds.isEmpty()) { return List.of(); }
        if (orderedIds.size() > MAX_GRAPH_RAW_BATCH_SIZE) {
            List<AggregationResult> results = new ArrayList<>(orderedIds.size());
            for (int start = 0; start < orderedIds.size(); start += MAX_GRAPH_RAW_BATCH_SIZE) {
                results.addAll(aggregateRuntimeWorkBatch(orderedIds.subList(
                        start, Math.min(start + MAX_GRAPH_RAW_BATCH_SIZE, orderedIds.size()))));
            }
            return List.copyOf(results);
        }
        Map<UUID, AggregationResult> results = new LinkedHashMap<>();
        // Search recall and graph construction must not retain a JDBC connection while waiting
        // for another service. Every input is locked and re-read before applying this snapshot.
        List<RawVideoAggregationRecord> rawVideos = loadRawVideos(orderedIds, results);
        List<RawVideoAggregationRecord> eligible = rawVideos.stream()
                .filter(RawVideoAggregationRecord::isAggregationEligible).toList();
        List<RawVideoAggregationCluster> clusters = new ArrayList<>();
        if (eligible.size() > 1) {
            List<UnifiedVideoAggregationCandidate> context = repository.findContextUnifiedCandidates(eligible);
            clusters.addAll(rawVideoGraphClusterer.cluster(eligible, context));
        } else {
            eligible.forEach(raw -> clusters.add(new RawVideoAggregationCluster(raw, List.of(raw))));
        }
        rawVideos.stream().filter(raw -> !raw.isAggregationEligible())
                .forEach(raw -> clusters.add(new RawVideoAggregationCluster(raw, List.of(raw))));
        return withDeferredAdultAssessments(() -> {
            for (RawVideoAggregationCluster cluster : clusters) {
                if (!cluster.isMultiRawCluster()) {
                    AggregationResult result = inTransaction(() -> aggregatePreparedRaw(cluster.leader()));
                    DeferredAdultAssessments.publishCommittedRestrictions();
                    results.put(result.rawVideoId(), result);
                    continue;
                }
                BoundClusterResult result = inTransaction(() -> aggregatePreparedCluster(cluster));
                DeferredAdultAssessments.publishCommittedRestrictions();
                if ("SPLIT_CLUSTER".equals(result.status())) {
                    // Similar raw rows do not prove that their existing works can be destroyed.
                    // Keep each existing binding and let a new raw resolve independently instead.
                    for (RawVideoAggregationRecord raw : cluster.members()) {
                        AggregationResult single = inTransaction(() -> aggregatePreparedRaw(raw));
                        DeferredAdultAssessments.publishCommittedRestrictions();
                        results.put(raw.id(), single);
                    }
                } else {
                    recordClusterResults(results, result);
                }
            }
            return orderedResults(orderedIds, results);
        }, true, false);
    }

    private AggregationResult aggregatePreparedRaw(RawVideoAggregationRecord expected) {
        repository.lockAggregationShared();
        repository.lockRawVideos(List.of(expected.id()));
        Optional<RawVideoAggregationRecord> current = repository.findRawVideo(expected.id());
        if (current.isEmpty()) { return AggregationResult.skipped(expected.id(), "MISSING_RAW_VIDEO"); }
        if (!expected.equals(current.orElseThrow())) {
            return AggregationResult.skipped(expected.id(), "SOURCE_CHANGED");
        }
        return publishAggregationResult(bindRawVideo(current.orElseThrow()));
    }

    private BoundClusterResult aggregatePreparedCluster(RawVideoAggregationCluster expected) {
        repository.lockAggregationShared();
        repository.lockRawVideos(expected.rawVideoIds());
        Map<UUID, AggregationResult> missing = new LinkedHashMap<>();
        List<RawVideoAggregationRecord> current = loadRawVideos(expected.rawVideoIds(), missing);
        Map<UUID, RawVideoAggregationRecord> currentById = new LinkedHashMap<>();
        current.forEach(raw -> currentById.put(raw.id(), raw));
        if (!missing.isEmpty() || expected.members().stream()
                .anyMatch(raw -> !raw.equals(currentById.get(raw.id())))) {
            return BoundClusterResult.skipped(expected.rawVideoIds(), "SOURCE_CHANGED");
        }
        BoundClusterResult result = bindRawCluster(expected);
        if ("BOUND".equals(result.status())) { publishClusterResult(result); }
        return result;
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(30);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return transaction.execute(status -> {
                repository.configureAggregationTransaction();
                AggregationQueueClaimGuard.checkCurrentOwnership();
                if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void beforeCommit(boolean readOnly) {
                                    AggregationQueueClaimGuard.checkCurrentOwnership();
                                    ClusterLeaseGuard.checkCurrentOwnership();
                                }
                            });
                }
                T result = action.get();
                AggregationQueueClaimGuard.checkCurrentOwnership();
                ClusterLeaseGuard.checkCurrentOwnership();
                return result;
            });
        } catch (org.springframework.dao.DataAccessException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql
                        && ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) {
                    throw new AggregationDeferredException("MATCH_LOCK_BUSY", 2);
                }
            }
            throw ex;
        }
    }

    private <T> T withDeferredAdultAssessments(Supplier<T> action, boolean retryClaimedWork) {
        return withDeferredAdultAssessments(action, retryClaimedWork, true);
    }

    private <T> T withDeferredAdultAssessments(
            Supplier<T> action, boolean retryClaimedWork, boolean transactionalAction) {
        assertOutsideTransaction();
        try (DeferredAdultAssessments scope = DeferredAdultAssessments.open()) {
            T result = transactionalAction ? inTransaction(action) : action.get();
            // execute() has completed transaction synchronizations, released JDBC and match/advisory locks.
            // An existing SAFE search document must not outlive a committed, definite adult restriction.
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationQueueClaimGuard.checkCurrentOwnership();
            scope.publishRestrictedIndexes();
            if (scope.ids().isEmpty()) {
                scope.publishIndexes();
                return result;
            }
            try {
                List<AdultAssessmentSnapshot> snapshots = new ArrayList<>();
                for (UUID id : scope.ids()) {
                    TransactionTemplate read = new TransactionTemplate(transactionManager);
                    read.setReadOnly(true);
                    read.setTimeout(30);
                    read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                    AdultAssessmentSnapshot snapshot = read.execute(status -> repository.captureAdultAssessment(id));
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                }
                snapshots.sort(java.util.Comparator.comparing(AdultAssessmentSnapshot::id));
                ClusterLeaseGuard.checkCurrentOwnership();
                Map<UUID, com.prodigalgal.ircs.common.adult.AdultAssessment> assessments =
                        snapshots.isEmpty() ? Map.of() : repository.evaluateAdultAssessments(snapshots);
                // No network work inside this final transaction. A changed snapshot rolls back all
                // results and index requests; the original claimed item is retained for retry.
                try {
                    inTransaction(() -> {
                        ClusterLeaseGuard.checkCurrentOwnership();
                        AggregationQueueClaimGuard.checkCurrentOwnership();
                        repository.lockAggregationShared();
                        for (AdultAssessmentSnapshot snapshot : snapshots) {
                            var assessment = assessments.get(snapshot.id());
                            if (assessment == null || !repository.applyAdultAssessment(snapshot, assessment)) {
                                throw new AdultAssessmentDeferredException("CONTENT_SAFETY_SNAPSHOT_CHANGED", 5);
                            }
                            if (scope.restrictedWasPublished(snapshot.id())) {
                                publisher.publishUnifiedIndex(snapshot.id());
                            }
                        }
                        ClusterLeaseGuard.checkCurrentOwnership();
                        AggregationQueueClaimGuard.checkCurrentOwnership();
                        scope.publishIndexes();
                        return null;
                    });
                } catch (AggregationDeferredException ex) {
                    throw new AdultAssessmentDeferredException("CONTENT_SAFETY_SNAPSHOT_CHANGED", 2);
                }
            } catch (AdultAssessmentDeferredException ex) {
                if (retryClaimedWork) { throw ex; }
                // Interactive/maintenance operations have no claim to fail. Queue their committed rows
                // using the existing idempotent publisher, without changing the merge/reset result.
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    for (UUID id : scope.ids()) {
                        repository.findRawIdsForUnified(id).forEach(raw -> workPublisher.enqueue(raw, null, "adult-assessment-pending"));
                    }
                });
            }
            return result;
        }
    }

    private List<RawVideoAggregationRecord> loadRawVideos(
            List<UUID> orderedIds,
            Map<UUID, AggregationResult> results) {
        if (orderedIds.size() <= 1) {
            return loadRawVideosOneByOne(orderedIds, results);
        }
        List<RawVideoAggregationRecord> loaded = repository.findRawVideos(orderedIds);
        if (loaded == null) {
            return loadRawVideosOneByOne(orderedIds, results);
        }
        Map<UUID, RawVideoAggregationRecord> rawById = new LinkedHashMap<>();
        for (RawVideoAggregationRecord rawVideo : loaded) {
            rawById.putIfAbsent(rawVideo.id(), rawVideo);
        }
        List<RawVideoAggregationRecord> rawVideos = new ArrayList<>(orderedIds.size());
        for (UUID rawVideoId : orderedIds) {
            RawVideoAggregationRecord rawVideo = rawById.get(rawVideoId);
            if (rawVideo == null) {
                results.put(rawVideoId, AggregationResult.skipped(rawVideoId, "MISSING_RAW_VIDEO"));
            } else {
                rawVideos.add(rawVideo);
            }
        }
        return rawVideos;
    }

    private List<RawVideoAggregationRecord> loadRawVideosOneByOne(
            List<UUID> orderedIds,
            Map<UUID, AggregationResult> results) {
        List<RawVideoAggregationRecord> rawVideos = new ArrayList<>(orderedIds.size());
        for (UUID rawVideoId : orderedIds) {
            Optional<RawVideoAggregationRecord> rawVideo = repository.findRawVideo(rawVideoId);
            if (rawVideo.isPresent()) {
                rawVideos.add(rawVideo.orElseThrow());
            } else {
                results.put(rawVideoId, AggregationResult.skipped(rawVideoId, "MISSING_RAW_VIDEO"));
            }
        }
        return rawVideos;
    }

    private AggregationResult aggregateOneInternal(UUID rawVideoId) {
        return repository.findRawVideo(rawVideoId)
                .map(this::bindRawVideo)
                .orElseGet(() -> AggregationResult.skipped(rawVideoId, "MISSING_RAW_VIDEO"));
    }

    public int recalculateUnified(UUID unifiedVideoId) {
        // Refresh derives fields from the existing membership; it must neither demote BOUND
        // members nor rerun the complete pipeline once for every source of the same work.
        return withDeferredAdultAssessments(() -> {
            repository.lockAggregationShared();
            repository.lockUnifiedVideos(List.of(unifiedVideoId));
            List<UUID> rawVideoIds = repository.findRawIdsForUnified(unifiedVideoId);
            AggregationPipelineExecution execution = aggregationPipelineRuntime.run(unifiedVideoId);
            if (!execution.successful()) {
                repository.markUnifiedDirty(unifiedVideoId);
                return 0;
            }
            repository.markUnifiedSynced(unifiedVideoId);
            publisher.publishUnifiedIndex(unifiedVideoId);
            rawVideoIds.forEach(publisher::publishRawIndex);
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            return rawVideoIds.size();
        }, false);
    }

    public AggregationMaintenanceRunResponse recalculateDirtyUnified(int limit) {
        assertOutsideTransaction();
        List<UUID> unifiedVideoIds = repository.findDirtyUnifiedIds(Math.max(1, limit));
        int processed = 0;
        for (UUID unifiedVideoId : unifiedVideoIds) {
            processed += recalculateUnified(unifiedVideoId);
        }
        return new AggregationMaintenanceRunResponse(
                "unified-recalculate",
                unifiedVideoIds.size(),
                processed,
                unifiedVideoIds);
    }

    @Transactional
    public AggregationMaintenanceRunResponse enqueuePendingRawWork(int limit) {
        List<UUID> rawVideoIds = repository.findEligiblePendingRawIds(Math.max(1, limit));
        for (UUID rawVideoId : rawVideoIds) {
            workPublisher.enqueue(rawVideoId, null, "aggregation-pending-backfill");
        }
        return new AggregationMaintenanceRunResponse(
                "aggregation-pending-backfill",
                rawVideoIds.size(),
                rawVideoIds.size(),
                rawVideoIds);
    }

    @Transactional
    public AggregationMaintenanceRunResponse backfillUnifiedCovers(int limit) {
        repository.lockAggregationShared();
        List<UnifiedCoverBackfillResult> backfilled = repository.backfillUnifiedCoverImagesFromRaw(Math.max(1, limit));
        for (UnifiedCoverBackfillResult result : backfilled) {
            if (result.shouldPromoteToR2()) {
                storageCommandPublisher.enqueueCoverR2Sync(result.coverImageId());
            }
            publisher.publishUnifiedIndex(result.unifiedVideoId());
        }
        return new AggregationMaintenanceRunResponse(
                "aggregation-cover-backfill",
                backfilled.size(),
                backfilled.size(),
                backfilled.stream()
                        .map(UnifiedCoverBackfillResult::unifiedVideoId)
                        .toList());
    }

    public AggregationMaintenanceRunResponse backfillUnifiedAdultAssessments(int limit) {
        return backfillUnifiedAdultAssessments(limit, true);
    }

    public AggregationMaintenanceRunResponse backfillUnifiedAdultAssessments(int limit, boolean publishSearch) {
        return withDeferredAdultAssessments(() -> backfillUnifiedAdultAssessmentsInTransaction(limit, publishSearch), false);
    }

    private AggregationMaintenanceRunResponse backfillUnifiedAdultAssessmentsInTransaction(int limit, boolean publishSearch) {
        repository.lockAggregationShared();
        List<UUID> unifiedVideoIds = repository.findAdultAssessmentBackfillIds(Math.max(1, limit));
        List<UUID> processedIds = repository.rebuildUnifiedAdultAssessments(unifiedVideoIds);
        if (publishSearch) {
            processedIds.forEach(publisher::publishUnifiedIndex);
        }
        return new AggregationMaintenanceRunResponse(
                "aggregation-adult-assessment-backfill",
                unifiedVideoIds.size(),
                processedIds.size(),
                processedIds);
    }

    public ManualUnifiedMergeResponse mergeUnifiedVideos(List<UUID> unifiedVideoIds) {
        return withDeferredAdultAssessments(() -> mergeUnifiedVideosInTransaction(unifiedVideoIds), false);
    }

    private ManualUnifiedMergeResponse mergeUnifiedVideosInTransaction(List<UUID> unifiedVideoIds) {
        repository.lockAggregationShared();
        List<UUID> orderedIds = normalizeManualMergeIds(unifiedVideoIds);
        UUID rootUnifiedVideoId = orderedIds.getFirst();
        List<UUID> victimUnifiedVideoIds = orderedIds.subList(1, orderedIds.size());

        repository.mergeDuplicateUnifiedVideos(rootUnifiedVideoId, victimUnifiedVideoIds);
        AggregationPipelineExecution execution = aggregationPipelineRuntime.run(rootUnifiedVideoId);
        List<UUID> rawVideoIds = repository.findRawIdsForUnified(rootUnifiedVideoId);
        if (execution.successful()) {
            repository.markUnifiedSynced(rootUnifiedVideoId);
            publisher.publishUnifiedIndex(rootUnifiedVideoId);
            rawVideoIds.forEach(publisher::publishRawIndex);
            victimUnifiedVideoIds.forEach(publisher::publishUnifiedDelete);
            ClusterLeaseGuard.checkCurrentOwnership();
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            return new ManualUnifiedMergeResponse(
                    rootUnifiedVideoId,
                    List.copyOf(victimUnifiedVideoIds),
                    List.copyOf(rawVideoIds),
                    "MERGED",
                    null);
        }

        repository.markUnifiedDirty(rootUnifiedVideoId);
        victimUnifiedVideoIds.forEach(publisher::publishUnifiedDelete);
        return new ManualUnifiedMergeResponse(
                rootUnifiedVideoId,
                List.copyOf(victimUnifiedVideoIds),
                List.copyOf(rawVideoIds),
                "PIPELINE_FAILED",
                execution.failures());
    }

    public int resetStuckProcessing(int timeoutMinutes, int limit) {
        return repository.resetStuckProcessing(Math.max(1, timeoutMinutes), Math.max(1, limit));
    }

    @Transactional
    public AggregationResetStepResponse prepareAggregationReset(int sampleLimit) {
        repository.lockAggregationMaintenance();
        int safeLimit = Math.max(1, sampleLimit);
        List<UUID> sampleRawVideoIds = repository.sampleRawVideoIds(safeLimit);
        long rawVideoCount = repository.countRawVideos();
        long unifiedVideoCount = repository.countUnifiedVideos();
        long bindingCount = repository.countRawUnifiedBindings();
        long changedRows = repository.deleteAllAggregationResetOwnedRows();
        return new AggregationResetStepResponse(
                "aggregation-reset",
                "prepare",
                rawVideoCount,
                unifiedVideoCount,
                bindingCount,
                changedRows,
                sampleRawVideoIds);
    }

    @Transactional
    public AggregationResetStepResponse markAllRawAggregationPending(int sampleLimit) {
        repository.lockAggregationMaintenance();
        int safeLimit = Math.max(1, sampleLimit);
        List<UUID> sampleRawVideoIds = repository.sampleRawVideoIds(safeLimit);
        long rawVideoCount = repository.countRawVideos();
        int changedRows = repository.markAllRawAggregationPending();
        return new AggregationResetStepResponse(
                "aggregation-reset",
                "mark-raw-pending",
                rawVideoCount,
                0,
                0,
                changedRows,
                sampleRawVideoIds);
    }

    private AggregationResult bindRawVideo(RawVideoAggregationRecord rawVideo) {
        if (!rawVideo.isAggregationEligible()) {
            if ("BOUND".equals(rawVideo.aggregationStatus())) {
                Optional<UUID> existing = repository.findExistingBinding(rawVideo.id());
                if (existing.isEmpty()) { return AggregationResult.skipped(rawVideo.id(), "IDENTITY_CONFLICT"); }
                UUID unifiedId = existing.orElseThrow();
                if (repository.isAdultAssessmentPending(unifiedId)) {
                    DeferredAdultAssessments.register(unifiedId);
                    return AggregationResult.bound(rawVideo.id(), unifiedId, List.of());
                }
                return AggregationResult.skipped(rawVideo.id(), "ALREADY_BOUND");
            }
            repository.markPending(rawVideo.id());
            return AggregationResult.skipped(rawVideo.id(), "WAITING_DEPENDENCIES");
        }
        Optional<AggregationMatchKeyLock.MatchLockScope> lockScope =
                matchKeyLock.tryAcquire(AggregationMatchKeys.forRawVideo(rawVideo, safetyPolicy));
        if (lockScope.isEmpty()) {
            repository.markPending(rawVideo.id());
            return AggregationResult.skipped(rawVideo.id(), "MATCH_LOCK_BUSY");
        }

        try (AggregationMatchKeyLock.MatchLockScope ignored = lockScope.orElseThrow()) {
            ClusterLeaseGuard.checkCurrentOwnership();
            Optional<UUID> existingBinding = repository.findExistingBinding(rawVideo.id());
            AggregationMatchPlan matchPlan = existingBinding
                    .map(AggregationMatchPlan::rootOnly)
                    .orElseGet(() -> repository.findMatchPlan(rawVideo));
            if (matchPlan.isAmbiguous()) {
                return AggregationResult.skipped(rawVideo.id(), "MERGE_AMBIGUOUS");
            }
            if (matchPlan.hasRoot()) {
                Set<UUID> expectedIds = plannedUnifiedIds(matchPlan);
                repository.lockUnifiedVideos(expectedIds);
                List<UnifiedVideoAggregationCandidate> candidates = repository.findUnifiedCandidatesByIds(expectedIds);
                UnifiedVideoAggregationCandidate root = candidates.stream()
                        .filter(candidate -> matchPlan.rootUnifiedVideoId().equals(candidate.id())).findFirst().orElse(null);
                if (root == null) { return AggregationResult.skipped(rawVideo.id(), "SOURCE_CHANGED"); }
                if (!matchingStrategy.isCompatibleBinding(rawVideo, root)) {
                    return AggregationResult.skipped(rawVideo.id(), "IDENTITY_CONFLICT");
                }
                if (existingBinding.isEmpty() && matchingStrategy.findBestMatch(rawVideo, List.of(root)).isEmpty()) {
                    return AggregationResult.skipped(rawVideo.id(), "SOURCE_CHANGED");
                }
                if (matchPlan.hasVictims() && (!safetyPolicy.automaticUnifiedMergeEnabled()
                        || !matchingStrategy.isSafeUnifiedMerge(candidates, expectedIds))) {
                    return AggregationResult.skipped(rawVideo.id(), "MERGE_AMBIGUOUS");
                }
            }
            UUID unifiedVideoId = matchPlan.hasRoot() ? matchPlan.rootUnifiedVideoId() : repository.createUnifiedVideo(rawVideo);
            if (matchPlan.hasVictims()) {
                repository.mergeDuplicateUnifiedVideos(unifiedVideoId, matchPlan.victimUnifiedVideoIds());
            }

            repository.upsertUnifiedFields(unifiedVideoId, rawVideo);
            repository.bindRawToUnified(rawVideo.id(), unifiedVideoId);
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationPipelineExecution execution = aggregationPipelineRuntime.run(unifiedVideoId);
            if (!execution.successful()) {
                repository.markUnifiedDirty(unifiedVideoId);
                repository.markPending(rawVideo.id());
                log.warn(
                        "Raw video aggregation pipeline failed; rawVideoId={}, unifiedVideoId={}, failures={}",
                        rawVideo.id(),
                        unifiedVideoId,
                        execution.failures());
                return AggregationResult.pipelineFailed(rawVideo.id(), unifiedVideoId, matchPlan.victimUnifiedVideoIds());
            }
            ClusterLeaseGuard.checkCurrentOwnership();
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            repository.markBound(rawVideo.id());

            log.info(
                    "Raw video aggregated: rawVideoId={}, unifiedVideoId={}, deletedUnifiedVideoIds={}",
                    rawVideo.id(),
                    unifiedVideoId,
                    matchPlan.victimUnifiedVideoIds());
            return AggregationResult.bound(rawVideo.id(), unifiedVideoId, matchPlan.victimUnifiedVideoIds());
        }
    }

    private BoundClusterResult bindRawCluster(RawVideoAggregationCluster cluster) {
        List<RawVideoAggregationRecord> rawVideos = cluster.members().stream()
                .filter(RawVideoAggregationRecord::isAggregationEligible)
                .toList();
        if (rawVideos.isEmpty()) {
            return BoundClusterResult.skipped(cluster.rawVideoIds(), "NOT_ELIGIBLE");
        }
        Optional<AggregationMatchKeyLock.MatchLockScope> lockScope =
                matchKeyLock.tryAcquire(AggregationMatchKeys.forCluster(cluster, safetyPolicy));
        if (lockScope.isEmpty()) {
            rawVideos.forEach(rawVideo -> repository.markPending(rawVideo.id()));
            return BoundClusterResult.skipped(cluster.rawVideoIds(), "MATCH_LOCK_BUSY");
        }

        try (AggregationMatchKeyLock.MatchLockScope ignored = lockScope.orElseThrow()) {
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationMatchPlan matchPlan = findClusterMatchPlan(cluster, rawVideos);
            if (matchPlan.isAmbiguous()) {
                return BoundClusterResult.skipped(cluster.rawVideoIds(), "SPLIT_CLUSTER");
            }
            if (matchPlan.hasRoot()) {
                Set<UUID> expectedIds = plannedUnifiedIds(matchPlan);
                repository.lockUnifiedVideos(expectedIds);
                List<UnifiedVideoAggregationCandidate> candidates = repository.findUnifiedCandidatesByIds(expectedIds);
                UnifiedVideoAggregationCandidate root = candidates.stream()
                        .filter(candidate -> matchPlan.rootUnifiedVideoId().equals(candidate.id())).findFirst().orElse(null);
                if (root == null || rawVideos.stream().anyMatch(raw -> !matchingStrategy.isCompatibleBinding(raw, root))) {
                    return BoundClusterResult.skipped(cluster.rawVideoIds(), "SPLIT_CLUSTER");
                }
                if (rawVideos.stream().anyMatch(raw -> repository.findExistingBinding(raw.id()).isEmpty()
                        && matchingStrategy.findBestMatch(raw, List.of(root)).isEmpty())) {
                    return BoundClusterResult.skipped(cluster.rawVideoIds(), "SPLIT_CLUSTER");
                }
                if (matchPlan.hasVictims() && (!safetyPolicy.automaticUnifiedMergeEnabled()
                        || !matchingStrategy.isSafeUnifiedMerge(candidates, expectedIds))) {
                    return BoundClusterResult.skipped(cluster.rawVideoIds(), "SPLIT_CLUSTER");
                }
            }
            UUID unifiedVideoId = matchPlan.hasRoot()
                    ? matchPlan.rootUnifiedVideoId()
                    : repository.createUnifiedVideo(cluster.leader());
            if (matchPlan.hasVictims()) {
                repository.mergeDuplicateUnifiedVideos(unifiedVideoId, matchPlan.victimUnifiedVideoIds());
            }

            for (RawVideoAggregationRecord rawVideo : rawVideos) {
                repository.upsertUnifiedFields(unifiedVideoId, rawVideo);
                repository.bindRawToUnified(rawVideo.id(), unifiedVideoId);
            }
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationPipelineExecution execution = aggregationPipelineRuntime.run(unifiedVideoId);
            if (!execution.successful()) {
                repository.markUnifiedDirty(unifiedVideoId);
                rawVideos.forEach(rawVideo -> repository.markPending(rawVideo.id()));
                log.warn(
                        "Raw video cluster aggregation pipeline failed; rawVideoIds={}, unifiedVideoId={}, failures={}",
                        cluster.rawVideoIds(),
                        unifiedVideoId,
                        execution.failures());
                return BoundClusterResult.pipelineFailed(
                        cluster.rawVideoIds(),
                        unifiedVideoId,
                        matchPlan.victimUnifiedVideoIds());
            }
            ClusterLeaseGuard.checkCurrentOwnership();
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            for (RawVideoAggregationRecord rawVideo : rawVideos) {
                repository.markBound(rawVideo.id());
            }

            log.info(
                    "Raw video cluster aggregated: rawVideoIds={}, unifiedVideoId={}, deletedUnifiedVideoIds={}",
                    cluster.rawVideoIds(),
                    unifiedVideoId,
                    matchPlan.victimUnifiedVideoIds());
            return BoundClusterResult.bound(cluster.rawVideoIds(), unifiedVideoId, matchPlan.victimUnifiedVideoIds());
        }
    }

    private AggregationMatchPlan findClusterMatchPlan(
            RawVideoAggregationCluster cluster,
            List<RawVideoAggregationRecord> rawVideos) {
        // A raw cluster is recall evidence, not authorization to merge its existing works.
        // The complete proposed set is locked and checked again before any destructive write.
        List<UUID> matchedUnifiedIds = new java.util.ArrayList<>();
        for (RawVideoAggregationRecord rawVideo : leaderFirst(cluster, rawVideos)) {
            repository.findExistingBinding(rawVideo.id()).ifPresent(matchedUnifiedIds::add);
            AggregationMatchPlan plan = repository.findMatchPlan(rawVideo);
            if (plan.isAmbiguous()) { return AggregationMatchPlan.ambiguous(); }
            if (plan.hasRoot()) {
                matchedUnifiedIds.add(plan.rootUnifiedVideoId());
            }
            matchedUnifiedIds.addAll(plan.victimUnifiedVideoIds());
        }
        List<UUID> distinctMatches = matchedUnifiedIds.stream()
                .distinct()
                .toList();
        if (distinctMatches.isEmpty()) {
            return AggregationMatchPlan.none();
        }
        if (distinctMatches.size() == 1) {
            return AggregationMatchPlan.rootOnly(distinctMatches.getFirst());
        }
        return AggregationMatchPlan.rootWithVictims(distinctMatches.getFirst(), distinctMatches.subList(1, distinctMatches.size()));
    }

    private Set<UUID> plannedUnifiedIds(AggregationMatchPlan plan) {
        Set<UUID> ids = new LinkedHashSet<>(plan.victimUnifiedVideoIds());
        ids.add(plan.rootUnifiedVideoId());
        return ids;
    }

    private List<RawVideoAggregationRecord> leaderFirst(
            RawVideoAggregationCluster cluster,
            List<RawVideoAggregationRecord> rawVideos) {
        java.util.LinkedHashSet<RawVideoAggregationRecord> ordered = new java.util.LinkedHashSet<>();
        ordered.add(cluster.leader());
        ordered.addAll(rawVideos);
        return List.copyOf(ordered);
    }

    private void publishBoundResult(AggregationResult result) {
        publisher.publishUnifiedIndex(result.unifiedVideoId());
        publisher.publishRawIndex(result.rawVideoId());
        result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
    }

    private AggregationResult publishAggregationResult(AggregationResult result) {
        if ("BOUND".equals(result.status())) {
            publishBoundResult(result);
        } else if ("PIPELINE_FAILED".equals(result.status())) {
            result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
        }
        return result;
    }

    private void publishClusterResult(BoundClusterResult result) {
        publisher.publishUnifiedIndex(result.unifiedVideoId());
        result.rawVideoIds().forEach(publisher::publishRawIndex);
        result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
    }

    private void recordClusterResults(Map<UUID, AggregationResult> results, BoundClusterResult result) {
        if ("BOUND".equals(result.status())) {
            result.rawVideoIds().forEach(rawVideoId -> results.put(
                    rawVideoId,
                    AggregationResult.bound(rawVideoId, result.unifiedVideoId(), result.deletedUnifiedVideoIds())));
            return;
        }
        if ("PIPELINE_FAILED".equals(result.status())) {
            result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
            result.rawVideoIds().forEach(rawVideoId -> results.put(
                    rawVideoId,
                    AggregationResult.pipelineFailed(rawVideoId, result.unifiedVideoId(), result.deletedUnifiedVideoIds())));
            return;
        }
        result.rawVideoIds().forEach(rawVideoId -> results.put(
                rawVideoId,
                AggregationResult.skipped(rawVideoId, result.reason())));
    }

    private List<AggregationResult> orderedResults(
            List<UUID> orderedIds,
            Map<UUID, AggregationResult> results) {
        return orderedIds.stream()
                .map(results::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private void assertOutsideTransaction() {
        // Even an empty NEVER transaction scope can retain a JDBC connection through Spring's
        // synchronization. Orchestration uses explicit short TransactionTemplates exclusively.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Aggregation orchestration requires an unbound database context");
        }
    }

    private List<UUID> normalizeManualMergeIds(List<UUID> unifiedVideoIds) {
        if (unifiedVideoIds == null) {
            throw new IllegalArgumentException("unifiedVideoIds must contain at least two IDs");
        }
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(unifiedVideoIds.stream()
                .filter(Objects::nonNull)
                .toList()));
        if (ids.size() < 2) {
            throw new IllegalArgumentException("unifiedVideoIds must contain at least two distinct IDs");
        }
        return ids;
    }

    private record BoundClusterResult(
            List<UUID> rawVideoIds,
            UUID unifiedVideoId,
            String status,
            List<UUID> deletedUnifiedVideoIds,
            String reason) {

        static BoundClusterResult bound(
                List<UUID> rawVideoIds,
                UUID unifiedVideoId,
                List<UUID> deletedUnifiedVideoIds) {
            return new BoundClusterResult(
                    List.copyOf(rawVideoIds),
                    unifiedVideoId,
                    "BOUND",
                    List.copyOf(deletedUnifiedVideoIds),
                    null);
        }

        static BoundClusterResult skipped(List<UUID> rawVideoIds, String reason) {
            return new BoundClusterResult(List.copyOf(rawVideoIds), null, reason, List.of(), reason);
        }

        static BoundClusterResult pipelineFailed(
                List<UUID> rawVideoIds,
                UUID unifiedVideoId,
                List<UUID> deletedUnifiedVideoIds) {
            return new BoundClusterResult(
                    List.copyOf(rawVideoIds),
                    unifiedVideoId,
                    "PIPELINE_FAILED",
                    List.copyOf(deletedUnifiedVideoIds),
                    null);
        }
    }
}
