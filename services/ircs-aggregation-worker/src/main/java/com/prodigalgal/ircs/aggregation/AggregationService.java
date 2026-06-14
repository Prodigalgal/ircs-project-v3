package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.contracts.aggregation.AggregationMaintenanceRunResponse;
import com.prodigalgal.ircs.contracts.aggregation.AggregationResetStepResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private final JdbcAggregationRepository repository;
    private final AggregationSearchSyncPublisher publisher;
    private final AggregationStorageCommandPublisher storageCommandPublisher;
    private final RawVideoGraphClusterer rawVideoGraphClusterer;
    private final AggregationPipelineRuntime aggregationPipelineRuntime;
    private final AggregationMatchKeyLock matchKeyLock;

    @Transactional
    public int aggregateBatch(int batchSize) {
        List<UUID> rawVideoIds = repository.claimPending(Math.max(1, batchSize));
        if (rawVideoIds.isEmpty()) {
            return 0;
        }
        List<RawVideoAggregationRecord> rawVideos = rawVideoIds.stream()
                .map(repository::findRawVideo)
                .flatMap(java.util.Optional::stream)
                .toList();
        if (rawVideos.size() <= 1) {
            return aggregateIndividually(rawVideos);
        }

        int processed = 0;
        for (RawVideoAggregationRecord rawVideo : rawVideos) {
            if (!rawVideo.isAggregationEligible()) {
                repository.markPending(rawVideo.id());
            }
        }
        List<RawVideoAggregationRecord> eligibleRawVideos = rawVideos.stream()
                .filter(RawVideoAggregationRecord::isAggregationEligible)
                .toList();
        List<UnifiedVideoAggregationCandidate> contextUnifiedCandidates =
                repository.findContextUnifiedCandidates(eligibleRawVideos);
        for (RawVideoAggregationCluster cluster : rawVideoGraphClusterer.cluster(
                eligibleRawVideos,
                contextUnifiedCandidates)) {
            if (!cluster.isMultiRawCluster()) {
                AggregationResult result = bindRawVideo(cluster.leader());
                if ("BOUND".equals(result.status())) {
                    publishBoundResult(result);
                    processed++;
                } else if ("PIPELINE_FAILED".equals(result.status())) {
                    result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
                }
                continue;
            }
            BoundClusterResult result = bindRawCluster(cluster);
            if ("BOUND".equals(result.status())) {
                publishClusterResult(result);
                processed += result.rawVideoIds().size();
            } else if ("PIPELINE_FAILED".equals(result.status())) {
                result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
            }
        }
        return processed;
    }

    private int aggregateIndividually(List<RawVideoAggregationRecord> rawVideos) {
        int processed = 0;
        for (RawVideoAggregationRecord rawVideo : rawVideos) {
            AggregationResult result = bindRawVideo(rawVideo);
            if ("BOUND".equals(result.status())) {
                publishBoundResult(result);
                processed++;
            } else if ("PIPELINE_FAILED".equals(result.status())) {
                result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
            }
        }
        return processed;
    }

    @Transactional
    public AggregationResult aggregateOne(UUID rawVideoId) {
        return repository.findRawVideo(rawVideoId)
                .map(this::bindRawVideo)
                .orElseGet(() -> AggregationResult.skipped(rawVideoId, "MISSING_RAW_VIDEO"));
    }

    @Transactional
    public int recalculateUnified(UUID unifiedVideoId) {
        List<UUID> rawVideoIds = repository.findRawIdsForUnified(unifiedVideoId);
        int processed = 0;
        boolean allSucceeded = true;
        boolean dirtyAlreadyMarked = false;
        for (UUID rawVideoId : rawVideoIds) {
            AggregationResult result = aggregateOne(rawVideoId);
            if ("BOUND".equals(result.status())) {
                publisher.publishRawIndex(rawVideoId);
                result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
                processed++;
            } else {
                allSucceeded = false;
                if ("PIPELINE_FAILED".equals(result.status())) {
                    dirtyAlreadyMarked = true;
                    result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
                }
            }
        }
        if (allSucceeded) {
            repository.markUnifiedSynced(unifiedVideoId);
            publisher.publishUnifiedIndex(unifiedVideoId);
        } else if (!dirtyAlreadyMarked) {
            repository.markUnifiedDirty(unifiedVideoId);
        }
        return processed;
    }

    @Transactional
    public AggregationMaintenanceRunResponse recalculateDirtyUnified(int limit) {
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

    public int resetStuckProcessing(int timeoutMinutes, int limit) {
        return repository.resetStuckProcessing(Math.max(1, timeoutMinutes), Math.max(1, limit));
    }

    @Transactional
    public AggregationResetStepResponse prepareAggregationReset(int sampleLimit) {
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
            repository.markPending(rawVideo.id());
            return AggregationResult.skipped(rawVideo.id(), "NOT_ELIGIBLE");
        }
        Optional<AggregationMatchKeyLock.MatchLockScope> lockScope =
                matchKeyLock.tryAcquire(AggregationMatchKeys.forRawVideo(rawVideo));
        if (lockScope.isEmpty()) {
            repository.markPending(rawVideo.id());
            return AggregationResult.skipped(rawVideo.id(), "MATCH_LOCK_BUSY");
        }

        try (AggregationMatchKeyLock.MatchLockScope ignored = lockScope.orElseThrow()) {
            AggregationMatchPlan matchPlan = repository.findExistingBinding(rawVideo.id())
                    .map(AggregationMatchPlan::rootOnly)
                    .orElseGet(() -> repository.findMatchPlan(rawVideo));
            UUID unifiedVideoId = matchPlan.hasRoot() ? matchPlan.rootUnifiedVideoId() : repository.createUnifiedVideo(rawVideo);
            if (matchPlan.hasVictims()) {
                repository.mergeDuplicateUnifiedVideos(unifiedVideoId, matchPlan.victimUnifiedVideoIds());
            }

            repository.upsertUnifiedFields(unifiedVideoId, rawVideo);
            repository.bindRawToUnified(rawVideo.id(), unifiedVideoId);
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
            execution.coverR2PromoteImageIds().forEach(storageCommandPublisher::publishCoverR2Promote);
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
            return BoundClusterResult.skipped();
        }
        Optional<AggregationMatchKeyLock.MatchLockScope> lockScope =
                matchKeyLock.tryAcquire(AggregationMatchKeys.forCluster(cluster));
        if (lockScope.isEmpty()) {
            rawVideos.forEach(rawVideo -> repository.markPending(rawVideo.id()));
            return BoundClusterResult.skipped();
        }

        try (AggregationMatchKeyLock.MatchLockScope ignored = lockScope.orElseThrow()) {
            AggregationMatchPlan matchPlan = findClusterMatchPlan(cluster, rawVideos);
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
            execution.coverR2PromoteImageIds().forEach(storageCommandPublisher::publishCoverR2Promote);
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
        List<UUID> matchedUnifiedIds = new java.util.ArrayList<>();
        matchedUnifiedIds.addAll(cluster.contextUnifiedVideoIds());
        for (RawVideoAggregationRecord rawVideo : leaderFirst(cluster, rawVideos)) {
            repository.findExistingBinding(rawVideo.id()).ifPresent(matchedUnifiedIds::add);
            AggregationMatchPlan plan = repository.findMatchPlan(rawVideo);
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

    private void publishClusterResult(BoundClusterResult result) {
        publisher.publishUnifiedIndex(result.unifiedVideoId());
        result.rawVideoIds().forEach(publisher::publishRawIndex);
        result.deletedUnifiedVideoIds().forEach(publisher::publishUnifiedDelete);
    }

    private record BoundClusterResult(
            List<UUID> rawVideoIds,
            UUID unifiedVideoId,
            String status,
            List<UUID> deletedUnifiedVideoIds) {

        static BoundClusterResult bound(
                List<UUID> rawVideoIds,
                UUID unifiedVideoId,
                List<UUID> deletedUnifiedVideoIds) {
            return new BoundClusterResult(
                    List.copyOf(rawVideoIds),
                    unifiedVideoId,
                    "BOUND",
                    List.copyOf(deletedUnifiedVideoIds));
        }

        static BoundClusterResult skipped() {
            return new BoundClusterResult(List.of(), null, "SKIPPED", List.of());
        }

        static BoundClusterResult pipelineFailed(
                List<UUID> rawVideoIds,
                UUID unifiedVideoId,
                List<UUID> deletedUnifiedVideoIds) {
            return new BoundClusterResult(
                    List.copyOf(rawVideoIds),
                    unifiedVideoId,
                    "PIPELINE_FAILED",
                    List.copyOf(deletedUnifiedVideoIds));
        }
    }
}
