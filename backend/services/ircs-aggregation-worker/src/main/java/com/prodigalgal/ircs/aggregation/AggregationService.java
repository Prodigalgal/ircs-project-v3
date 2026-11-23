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
import org.springframework.beans.factory.annotation.Value;
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
    private final SourceRecordGraphClusterer sourceRecordGraphClusterer;
    private final AggregationPipelineRuntime aggregationPipelineRuntime;
    private final AggregationMatchKeyLock matchKeyLock;
    private final AggregationWorkPublisher workPublisher;
    private final PlatformTransactionManager transactionManager;
    private final AggregationMatchingStrategy matchingStrategy;
    private final AggregationSafetyPolicy safetyPolicy;
    private static final AuthorityCatalogBindingPolicy AUTHORITY_CATALOG_BINDING_POLICY =
            new AuthorityCatalogBindingPolicy();

    @Value("${app.catalog.authority-first.enabled:false}")
    private boolean authorityFirstEnabled;

    public AggregationResult aggregateOne(UUID sourceRecordId) {
        return withDeferredAdultAssessments(() -> {
            return inTransaction(() -> {
                repository.lockAggregationShared();
                repository.lockSourceRecords(List.of(sourceRecordId));
                return aggregateOneInternal(sourceRecordId);
            });
        }, false);
    }

    public AggregationResult aggregateRuntimeWork(UUID sourceRecordId) {
        return aggregateRuntimeWorkBatch(List.of(sourceRecordId)).stream()
                .filter(result -> sourceRecordId.equals(result.sourceRecordId()))
                .findFirst()
                .orElseGet(() -> AggregationResult.skipped(sourceRecordId, "MISSING_SOURCE_RECORD"));
    }

    public List<AggregationResult> aggregateRuntimeWorkBatch(List<UUID> sourceRecordIds) {
        assertOutsideTransaction();
        List<UUID> orderedIds = sourceRecordIds == null ? List.of() : sourceRecordIds.stream()
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
        List<SourceRecordAggregationRecord> sourceRecords = loadSourceRecords(orderedIds, results);
        List<SourceRecordAggregationRecord> eligible = sourceRecords.stream()
                .filter(SourceRecordAggregationRecord::isAggregationEligible).toList();
        List<SourceRecordAggregationCluster> clusters = new ArrayList<>();
        List<SourceRecordAggregationRecord> sourceFirstEligible = eligible;
        if (authorityFirstEnabled) {
            List<SourceRecordAggregationRecord> authorityEligible = eligible.stream()
                    .filter(this::requiresAuthorityProjection)
                    .toList();
            authorityEligible.forEach(raw -> clusters.add(new SourceRecordAggregationCluster(raw, List.of(raw))));
            sourceFirstEligible = eligible.stream().filter(raw -> !requiresAuthorityProjection(raw)).toList();
        }
        if (sourceFirstEligible.size() > 1) {
            List<CatalogEntryAggregationCandidate> context = repository.findContextUnifiedCandidates(sourceFirstEligible);
            clusters.addAll(sourceRecordGraphClusterer.cluster(sourceFirstEligible, context));
        } else {
            sourceFirstEligible.forEach(raw -> clusters.add(new SourceRecordAggregationCluster(raw, List.of(raw))));
        }
        for (SourceRecordAggregationRecord raw : sourceRecords) {
            if (!raw.isAggregationEligible()) {
                if ("PENDING".equals(raw.aggregationStatus())) {
                    // A read-only wait needs neither row locks nor another PENDING write.
                    // A dependency becoming ready concurrently is handled by its normal handoff or durable defer.
                    results.put(raw.id(), AggregationResult.skipped(raw.id(), "WAITING_DEPENDENCIES"));
                } else {
                    clusters.add(new SourceRecordAggregationCluster(raw, List.of(raw)));
                }
            }
        }
        return withDeferredAdultAssessments(() -> {
            for (SourceRecordAggregationCluster cluster : clusters) {
                if (!cluster.isMultiRawCluster()) {
                    AggregationResult result = inTransaction(() -> aggregatePreparedRaw(cluster.leader()));
                    DeferredAdultAssessments.publishCommittedRestrictions();
                    results.put(result.sourceRecordId(), result);
                    continue;
                }
                BoundClusterResult result = inTransaction(() -> aggregatePreparedCluster(cluster));
                DeferredAdultAssessments.publishCommittedRestrictions();
                if ("SPLIT_CLUSTER".equals(result.status())) {
                    // Similar raw rows do not prove that their existing works can be destroyed.
                    // Keep each existing binding and let a new raw resolve independently instead.
                    for (SourceRecordAggregationRecord raw : cluster.members()) {
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

    private AggregationResult aggregatePreparedRaw(SourceRecordAggregationRecord expected) {
        repository.lockAggregationShared();
        repository.lockSourceRecords(List.of(expected.id()));
        Optional<SourceRecordAggregationRecord> current = repository.findSourceRecord(expected.id());
        if (current.isEmpty()) { return AggregationResult.skipped(expected.id(), "MISSING_SOURCE_RECORD"); }
        if (!expected.equals(current.orElseThrow())) {
            return AggregationResult.skipped(expected.id(), "SOURCE_CHANGED");
        }
        return publishAggregationResult(bindSourceRecord(current.orElseThrow()));
    }

    private BoundClusterResult aggregatePreparedCluster(SourceRecordAggregationCluster expected) {
        repository.lockAggregationShared();
        repository.lockSourceRecords(expected.sourceRecordIds());
        Map<UUID, AggregationResult> missing = new LinkedHashMap<>();
        List<SourceRecordAggregationRecord> current = loadSourceRecords(expected.sourceRecordIds(), missing);
        Map<UUID, SourceRecordAggregationRecord> currentById = new LinkedHashMap<>();
        current.forEach(raw -> currentById.put(raw.id(), raw));
        if (!missing.isEmpty() || expected.members().stream()
                .anyMatch(raw -> !raw.equals(currentById.get(raw.id())))) {
            return BoundClusterResult.skipped(expected.sourceRecordIds(), "SOURCE_CHANGED");
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

    private List<SourceRecordAggregationRecord> loadSourceRecords(
            List<UUID> orderedIds,
            Map<UUID, AggregationResult> results) {
        if (orderedIds.size() <= 1) {
            return loadSourceRecordsOneByOne(orderedIds, results);
        }
        List<SourceRecordAggregationRecord> loaded = repository.findSourceRecords(orderedIds);
        if (loaded == null) {
            return loadSourceRecordsOneByOne(orderedIds, results);
        }
        Map<UUID, SourceRecordAggregationRecord> rawById = new LinkedHashMap<>();
        for (SourceRecordAggregationRecord sourceRecord : loaded) {
            rawById.putIfAbsent(sourceRecord.id(), sourceRecord);
        }
        List<SourceRecordAggregationRecord> sourceRecords = new ArrayList<>(orderedIds.size());
        for (UUID sourceRecordId : orderedIds) {
            SourceRecordAggregationRecord sourceRecord = rawById.get(sourceRecordId);
            if (sourceRecord == null) {
                results.put(sourceRecordId, AggregationResult.skipped(sourceRecordId, "MISSING_SOURCE_RECORD"));
            } else {
                sourceRecords.add(sourceRecord);
            }
        }
        return sourceRecords;
    }

    private List<SourceRecordAggregationRecord> loadSourceRecordsOneByOne(
            List<UUID> orderedIds,
            Map<UUID, AggregationResult> results) {
        List<SourceRecordAggregationRecord> sourceRecords = new ArrayList<>(orderedIds.size());
        for (UUID sourceRecordId : orderedIds) {
            Optional<SourceRecordAggregationRecord> sourceRecord = repository.findSourceRecord(sourceRecordId);
            if (sourceRecord.isPresent()) {
                sourceRecords.add(sourceRecord.orElseThrow());
            } else {
                results.put(sourceRecordId, AggregationResult.skipped(sourceRecordId, "MISSING_SOURCE_RECORD"));
            }
        }
        return sourceRecords;
    }

    private AggregationResult aggregateOneInternal(UUID sourceRecordId) {
        return repository.findSourceRecord(sourceRecordId)
                .map(this::bindSourceRecord)
                .orElseGet(() -> AggregationResult.skipped(sourceRecordId, "MISSING_SOURCE_RECORD"));
    }

    public int recalculateUnified(UUID catalogEntryId) {
        // Refresh derives fields from the existing membership; it must neither demote BOUND
        // members nor rerun the complete pipeline once for every source of the same work.
        return withDeferredAdultAssessments(() -> {
            repository.lockAggregationShared();
            repository.lockCatalogEntries(List.of(catalogEntryId));
            List<UUID> sourceRecordIds = repository.findRawIdsForUnified(catalogEntryId);
            if (sourceRecordIds.isEmpty()) return 0;
            AggregationPipelineExecution execution = aggregationPipelineRuntime.run(catalogEntryId);
            if (!execution.successful()) {
                repository.markUnifiedDirty(catalogEntryId);
                return 0;
            }
            repository.markUnifiedSynced(catalogEntryId);
            publisher.publishUnifiedIndex(catalogEntryId);
            sourceRecordIds.forEach(publisher::publishRawIndex);
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            return sourceRecordIds.size();
        }, false);
    }

    public AggregationMaintenanceRunResponse recalculateDirtyUnified(int limit) {
        assertOutsideTransaction();
        List<UUID> catalogEntryIds = repository.findDirtyUnifiedIds(Math.max(1, limit));
        int processed = 0;
        for (UUID catalogEntryId : catalogEntryIds) {
            processed += recalculateUnified(catalogEntryId);
        }
        return new AggregationMaintenanceRunResponse(
                "unified-recalculate",
                catalogEntryIds.size(),
                processed,
                catalogEntryIds);
    }

    @Transactional
    public AggregationMaintenanceRunResponse enqueuePendingRawWork(int limit) {
        List<UUID> sourceRecordIds = repository.findEligiblePendingRawIds(Math.max(1, limit));
        for (UUID sourceRecordId : sourceRecordIds) {
            workPublisher.enqueue(sourceRecordId, null, "aggregation-pending-backfill");
        }
        return new AggregationMaintenanceRunResponse(
                "aggregation-pending-backfill",
                sourceRecordIds.size(),
                sourceRecordIds.size(),
                sourceRecordIds);
    }

    @Transactional
    public AggregationMaintenanceRunResponse backfillUnifiedCovers(int limit) {
        repository.lockAggregationShared();
        List<UnifiedCoverBackfillResult> backfilled = repository.backfillUnifiedCoverImagesFromRaw(Math.max(1, limit));
        for (UnifiedCoverBackfillResult result : backfilled) {
            if (result.shouldPromoteToR2()) {
                storageCommandPublisher.enqueueCoverR2Sync(result.coverImageId());
            }
            publisher.publishUnifiedIndex(result.catalogEntryId());
        }
        return new AggregationMaintenanceRunResponse(
                "aggregation-cover-backfill",
                backfilled.size(),
                backfilled.size(),
                backfilled.stream()
                        .map(UnifiedCoverBackfillResult::catalogEntryId)
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
        List<UUID> catalogEntryIds = repository.findAdultAssessmentBackfillIds(Math.max(1, limit));
        List<UUID> processedIds = repository.rebuildUnifiedAdultAssessments(catalogEntryIds);
        if (publishSearch) {
            processedIds.forEach(publisher::publishUnifiedIndex);
        }
        return new AggregationMaintenanceRunResponse(
                "aggregation-adult-assessment-backfill",
                catalogEntryIds.size(),
                processedIds.size(),
                processedIds);
    }

    public ManualUnifiedMergeResponse mergeCatalogEntries(List<UUID> catalogEntryIds) {
        return withDeferredAdultAssessments(() -> mergeCatalogEntriesInTransaction(catalogEntryIds), false);
    }

    private ManualUnifiedMergeResponse mergeCatalogEntriesInTransaction(List<UUID> catalogEntryIds) {
        repository.lockAggregationShared();
        List<UUID> orderedIds = normalizeManualMergeIds(catalogEntryIds);
        UUID rootCatalogEntryId = orderedIds.getFirst();
        List<UUID> victimCatalogEntryIds = orderedIds.subList(1, orderedIds.size());

        repository.mergeDuplicateCatalogEntries(rootCatalogEntryId, victimCatalogEntryIds);
        AggregationPipelineExecution execution = aggregationPipelineRuntime.run(rootCatalogEntryId);
        List<UUID> sourceRecordIds = repository.findRawIdsForUnified(rootCatalogEntryId);
        if (execution.successful()) {
            repository.markUnifiedSynced(rootCatalogEntryId);
            publisher.publishUnifiedIndex(rootCatalogEntryId);
            sourceRecordIds.forEach(publisher::publishRawIndex);
            victimCatalogEntryIds.forEach(publisher::publishUnifiedDelete);
            ClusterLeaseGuard.checkCurrentOwnership();
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            return new ManualUnifiedMergeResponse(
                    rootCatalogEntryId,
                    List.copyOf(victimCatalogEntryIds),
                    List.copyOf(sourceRecordIds),
                    "MERGED",
                    null);
        }

        repository.markUnifiedDirty(rootCatalogEntryId);
        victimCatalogEntryIds.forEach(publisher::publishUnifiedDelete);
        return new ManualUnifiedMergeResponse(
                rootCatalogEntryId,
                List.copyOf(victimCatalogEntryIds),
                List.copyOf(sourceRecordIds),
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
        List<UUID> sampleSourceRecordIds = repository.sampleSourceRecordIds(safeLimit);
        long sourceRecordCount = repository.countSourceRecords();
        long catalogEntryCount = repository.countCatalogEntries();
        long bindingCount = repository.countRawUnifiedBindings();
        long changedRows = repository.deleteAllAggregationResetOwnedRows();
        return new AggregationResetStepResponse(
                "aggregation-reset",
                "prepare",
                sourceRecordCount,
                catalogEntryCount,
                bindingCount,
                changedRows,
                sampleSourceRecordIds);
    }

    @Transactional
    public AggregationResetStepResponse markAllRawAggregationPending(int sampleLimit) {
        repository.lockAggregationMaintenance();
        int safeLimit = Math.max(1, sampleLimit);
        List<UUID> sampleSourceRecordIds = repository.sampleSourceRecordIds(safeLimit);
        long sourceRecordCount = repository.countSourceRecords();
        int changedRows = repository.markAllRawAggregationPending();
        return new AggregationResetStepResponse(
                "aggregation-reset",
                "mark-raw-pending",
                sourceRecordCount,
                0,
                0,
                changedRows,
                sampleSourceRecordIds);
    }

    private AggregationResult bindSourceRecord(SourceRecordAggregationRecord sourceRecord) {
        if (!sourceRecord.isAggregationEligible()) {
            if ("BOUND".equals(sourceRecord.aggregationStatus())) {
                Optional<UUID> existing = repository.findExistingBinding(sourceRecord.id());
                if (existing.isEmpty()) { return AggregationResult.skipped(sourceRecord.id(), "IDENTITY_CONFLICT"); }
                UUID unifiedId = existing.orElseThrow();
                if (repository.isAdultAssessmentPending(unifiedId)) {
                    DeferredAdultAssessments.register(unifiedId);
                    return AggregationResult.bound(sourceRecord.id(), unifiedId, List.of());
                }
                return AggregationResult.skipped(sourceRecord.id(), "ALREADY_BOUND");
            }
            repository.markPending(sourceRecord.id());
            return AggregationResult.skipped(sourceRecord.id(), "WAITING_DEPENDENCIES");
        }
        Optional<AggregationMatchKeyLock.MatchLockScope> lockScope =
                matchKeyLock.tryAcquire(AggregationMatchKeys.forSourceRecord(sourceRecord, safetyPolicy));
        if (lockScope.isEmpty()) {
            repository.markPending(sourceRecord.id());
            return AggregationResult.skipped(sourceRecord.id(), "MATCH_LOCK_BUSY");
        }

        try (AggregationMatchKeyLock.MatchLockScope ignored = lockScope.orElseThrow()) {
            ClusterLeaseGuard.checkCurrentOwnership();
            Optional<UUID> existingBinding = repository.findExistingBinding(sourceRecord.id());
            if (authorityFirstEnabled && requiresAuthorityProjection(sourceRecord)) {
                return bindToAuthorityProjection(sourceRecord, existingBinding);
            }
            AggregationMatchPlan matchPlan = existingBinding
                    .map(AggregationMatchPlan::rootOnly)
                    .orElseGet(() -> repository.findMatchPlan(sourceRecord));
            if (matchPlan.isAmbiguous()) {
                return AggregationResult.skipped(sourceRecord.id(), "MERGE_AMBIGUOUS");
            }
            if (matchPlan.hasRoot()) {
                Set<UUID> expectedIds = plannedUnifiedIds(matchPlan);
                repository.lockCatalogEntries(expectedIds);
                List<CatalogEntryAggregationCandidate> candidates = repository.findUnifiedCandidatesByIds(expectedIds);
                CatalogEntryAggregationCandidate root = candidates.stream()
                        .filter(candidate -> matchPlan.rootCatalogEntryId().equals(candidate.id())).findFirst().orElse(null);
                if (root == null) { return AggregationResult.skipped(sourceRecord.id(), "SOURCE_CHANGED"); }
                if (!matchingStrategy.isCompatibleBinding(sourceRecord, root)) {
                    return AggregationResult.skipped(sourceRecord.id(), "IDENTITY_CONFLICT");
                }
                if (existingBinding.isEmpty() && matchingStrategy.findBestMatch(sourceRecord, List.of(root)).isEmpty()) {
                    return AggregationResult.skipped(sourceRecord.id(), "SOURCE_CHANGED");
                }
                if (matchPlan.hasVictims() && (!safetyPolicy.automaticUnifiedMergeEnabled()
                        || !matchingStrategy.isSafeUnifiedMerge(candidates, expectedIds))) {
                    return AggregationResult.skipped(sourceRecord.id(), "MERGE_AMBIGUOUS");
                }
                String clusterConflict = clusterBindingConflict(root, expectedIds, List.of(sourceRecord));
                if (clusterConflict != null) {
                    String status = AggregationMemberIdentity.REVIEW_LIMIT_REASON.equals(clusterConflict)
                            ? "MERGE_AMBIGUOUS" : "IDENTITY_CONFLICT";
                    return AggregationResult.skipped(sourceRecord.id(), status);
                }
            }
            UUID catalogEntryId = matchPlan.hasRoot() ? matchPlan.rootCatalogEntryId() : repository.createCatalogEntry(sourceRecord);
            if (matchPlan.hasVictims()) {
                repository.mergeDuplicateCatalogEntries(catalogEntryId, matchPlan.victimCatalogEntryIds());
            }

            repository.upsertUnifiedFields(catalogEntryId, sourceRecord);
            repository.bindRawToUnified(sourceRecord.id(), catalogEntryId);
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationPipelineExecution execution = aggregationPipelineRuntime.run(catalogEntryId);
            if (!execution.successful()) {
                repository.markUnifiedDirty(catalogEntryId);
                repository.markPending(sourceRecord.id());
                log.warn(
                        "Raw video aggregation pipeline failed; sourceRecordId={}, catalogEntryId={}, failures={}",
                        sourceRecord.id(),
                        catalogEntryId,
                        execution.failures());
                return AggregationResult.pipelineFailed(sourceRecord.id(), catalogEntryId, matchPlan.victimCatalogEntryIds());
            }
            ClusterLeaseGuard.checkCurrentOwnership();
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            repository.markBound(sourceRecord.id());

            log.info(
                    "Raw video aggregated: sourceRecordId={}, catalogEntryId={}, deletedCatalogEntryIds={}",
                    sourceRecord.id(),
                    catalogEntryId,
                    matchPlan.victimCatalogEntryIds());
            return AggregationResult.bound(sourceRecord.id(), catalogEntryId, matchPlan.victimCatalogEntryIds());
        }
    }

    private AggregationResult bindToAuthorityProjection(
            SourceRecordAggregationRecord sourceRecord, Optional<UUID> existingBinding) {
        AggregationCategoryProfile profile = AggregationCategoryProfile.from(
                AggregationCategoryProfile.category(sourceRecord));
        List<String> mediaTypes = authorityMediaTypes(profile, sourceRecord);
        if (mediaTypes.isEmpty()) {
            repository.markPending(sourceRecord.id());
            return AggregationResult.skipped(sourceRecord.id(), "WAITING_AUTHORITY");
        }
        AuthorityCatalogBindingDecision decision = authorityDecision(sourceRecord, mediaTypes);
        if (!"BOUND".equals(decision.status())) {
            if (existingBinding.isPresent()) {
                return AggregationResult.skipped(sourceRecord.id(), "IDENTITY_CONFLICT");
            }
            if ("WAITING_AUTHORITY".equals(decision.status())) {
                if (mediaTypes.size() == 1) {
                    enqueueAuthorityImportWhenEligible(sourceRecord, mediaTypes.getFirst());
                }
                repository.markPending(sourceRecord.id());
            }
            return AggregationResult.skipped(sourceRecord.id(), decision.status());
        }
        UUID catalogEntryId = decision.catalogEntryId();
        if (existingBinding.isPresent() && !existingBinding.orElseThrow().equals(catalogEntryId)) {
            return AggregationResult.skipped(sourceRecord.id(), "IDENTITY_CONFLICT");
        }
        // Serialize writes for the selected video and recheck under that lock.
        // Read-only candidate recall must not take shared locks that later need upgrading.
        repository.lockCatalogEntries(List.of(catalogEntryId));
        if (!decision.equals(authorityDecision(sourceRecord, mediaTypes))) {
            return AggregationResult.skipped(sourceRecord.id(), "SOURCE_CHANGED");
        }
        if (existingBinding.isEmpty() && repository.bindRawToUnified(sourceRecord.id(), catalogEntryId) == 0) {
            return AggregationResult.skipped(sourceRecord.id(), "IDENTITY_CONFLICT");
        }
        if (repository.recordAuthorityBinding(sourceRecord.id(), catalogEntryId, decision.evidenceKind()) == 0) {
            throw new IllegalStateException("The verified source could not be associated with the selected catalog entry");
        }
        ClusterLeaseGuard.checkCurrentOwnership();
        AggregationPipelineExecution execution = aggregationPipelineRuntime.run(catalogEntryId);
        if (!execution.successful()) {
            repository.markUnifiedDirty(catalogEntryId);
            repository.markPending(sourceRecord.id());
            log.warn("Authority projection aggregation pipeline failed; sourceRecordId={}, catalogEntryId={}, failures={}",
                    sourceRecord.id(), catalogEntryId, execution.failures());
            return AggregationResult.pipelineFailed(sourceRecord.id(), catalogEntryId, List.of());
        }
        ClusterLeaseGuard.checkCurrentOwnership();
        execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
        repository.markBound(sourceRecord.id());
        return AggregationResult.bound(sourceRecord.id(), catalogEntryId);
    }

    private AuthorityCatalogBindingDecision authorityDecision(SourceRecordAggregationRecord sourceRecord, List<String> mediaTypes) {
        Long tmdbId = AuthorityCatalogBindingPolicy.normalizeTmdbId(sourceRecord.tmdbId());
        String imdbId = AuthorityCatalogBindingPolicy.normalizeImdbId(sourceRecord.imdbId());
        Set<String> titleKeys = AuthorityCatalogBindingPolicy.rawTitleKeys(sourceRecord);
        List<AuthorityCatalogProjection> projections = mediaTypes.stream()
                .flatMap(mediaType -> repository.findAuthorityCatalogProjections(mediaType, titleKeys, tmdbId, imdbId).stream())
                .toList();
        return AUTHORITY_CATALOG_BINDING_POLICY.decide(sourceRecord, Set.copyOf(mediaTypes), projections);
    }

    private void enqueueAuthorityImportWhenEligible(SourceRecordAggregationRecord sourceRecord, String mediaType) {
        if (!AuthorityCatalogBindingPolicy.hasImportRequestEvidence(sourceRecord)) {
            return;
        }
        java.util.OptionalLong tmdbId = AuthorityCatalogBindingPolicy.importTmdbId(sourceRecord);
        if (tmdbId.isEmpty()) {
            return;
        }
        int seasonNumber = "movie".equals(mediaType)
                ? -1
                : sourceRecord.season() == null ? -1 : sourceRecord.season();
        repository.enqueueAuthorityCatalogImport(mediaType, tmdbId.getAsLong(), seasonNumber);
    }

    private boolean requiresAuthorityProjection(SourceRecordAggregationRecord sourceRecord) {
        AggregationCategoryProfile profile = AggregationCategoryProfile.from(
                AggregationCategoryProfile.category(sourceRecord));
        return profile != AggregationCategoryProfile.SHORT_DRAMA
                && profile != AggregationCategoryProfile.SPORTS
                && profile != AggregationCategoryProfile.NEWS
                && profile != AggregationCategoryProfile.EDUCATION
                && profile != AggregationCategoryProfile.MUSIC;
    }

    private List<String> authorityMediaTypes(
            AggregationCategoryProfile profile, SourceRecordAggregationRecord sourceRecord) {
        return switch (profile) {
            case MOVIE -> List.of("movie");
            case SERIES, PROGRAMME -> List.of("tv");
            case ANIMATION, DOCUMENTARY -> sourceRecord.season() == null
                    ? List.of("movie", "tv") : List.of("tv");
            default -> List.of();
        };
    }

    private BoundClusterResult bindRawCluster(SourceRecordAggregationCluster cluster) {
        List<SourceRecordAggregationRecord> sourceRecords = cluster.members().stream()
                .filter(SourceRecordAggregationRecord::isAggregationEligible)
                .toList();
        if (sourceRecords.isEmpty()) {
            return BoundClusterResult.skipped(cluster.sourceRecordIds(), "NOT_ELIGIBLE");
        }
        Optional<AggregationMatchKeyLock.MatchLockScope> lockScope =
                matchKeyLock.tryAcquire(AggregationMatchKeys.forCluster(cluster, safetyPolicy));
        if (lockScope.isEmpty()) {
            sourceRecords.forEach(sourceRecord -> repository.markPending(sourceRecord.id()));
            return BoundClusterResult.skipped(cluster.sourceRecordIds(), "MATCH_LOCK_BUSY");
        }

        try (AggregationMatchKeyLock.MatchLockScope ignored = lockScope.orElseThrow()) {
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationMatchPlan matchPlan = findClusterMatchPlan(cluster, sourceRecords);
            if (matchPlan.isAmbiguous()) {
                return BoundClusterResult.skipped(cluster.sourceRecordIds(), "SPLIT_CLUSTER");
            }
            if (matchPlan.hasRoot()) {
                Set<UUID> expectedIds = plannedUnifiedIds(matchPlan);
                repository.lockCatalogEntries(expectedIds);
                List<CatalogEntryAggregationCandidate> candidates = repository.findUnifiedCandidatesByIds(expectedIds);
                CatalogEntryAggregationCandidate root = candidates.stream()
                        .filter(candidate -> matchPlan.rootCatalogEntryId().equals(candidate.id())).findFirst().orElse(null);
                if (root == null || sourceRecords.stream().anyMatch(raw -> !matchingStrategy.isCompatibleBinding(raw, root))) {
                    return BoundClusterResult.skipped(cluster.sourceRecordIds(), "SPLIT_CLUSTER");
                }
                if (sourceRecords.stream().anyMatch(raw -> repository.findExistingBinding(raw.id()).isEmpty()
                        && matchingStrategy.findBestMatch(raw, List.of(root)).isEmpty())) {
                    return BoundClusterResult.skipped(cluster.sourceRecordIds(), "SPLIT_CLUSTER");
                }
                if (matchPlan.hasVictims() && (!safetyPolicy.automaticUnifiedMergeEnabled()
                        || !matchingStrategy.isSafeUnifiedMerge(candidates, expectedIds))) {
                    return BoundClusterResult.skipped(cluster.sourceRecordIds(), "SPLIT_CLUSTER");
                }
                if (clusterBindingConflict(root, expectedIds, sourceRecords) != null) {
                    return BoundClusterResult.skipped(cluster.sourceRecordIds(), "SPLIT_CLUSTER");
                }
            }
            UUID catalogEntryId = matchPlan.hasRoot()
                    ? matchPlan.rootCatalogEntryId()
                    : repository.createCatalogEntry(cluster.leader());
            if (matchPlan.hasVictims()) {
                repository.mergeDuplicateCatalogEntries(catalogEntryId, matchPlan.victimCatalogEntryIds());
            }

            for (SourceRecordAggregationRecord sourceRecord : sourceRecords) {
                repository.upsertUnifiedFields(catalogEntryId, sourceRecord);
                repository.bindRawToUnified(sourceRecord.id(), catalogEntryId);
            }
            ClusterLeaseGuard.checkCurrentOwnership();
            AggregationPipelineExecution execution = aggregationPipelineRuntime.run(catalogEntryId);
            if (!execution.successful()) {
                repository.markUnifiedDirty(catalogEntryId);
                sourceRecords.forEach(sourceRecord -> repository.markPending(sourceRecord.id()));
                log.warn(
                        "Raw video cluster aggregation pipeline failed; sourceRecordIds={}, catalogEntryId={}, failures={}",
                        cluster.sourceRecordIds(),
                        catalogEntryId,
                        execution.failures());
                return BoundClusterResult.pipelineFailed(
                        cluster.sourceRecordIds(),
                        catalogEntryId,
                        matchPlan.victimCatalogEntryIds());
            }
            ClusterLeaseGuard.checkCurrentOwnership();
            execution.coverR2SyncImageIds().forEach(storageCommandPublisher::enqueueCoverR2Sync);
            for (SourceRecordAggregationRecord sourceRecord : sourceRecords) {
                repository.markBound(sourceRecord.id());
            }

            log.info(
                    "Raw video cluster aggregated: sourceRecordIds={}, catalogEntryId={}, deletedCatalogEntryIds={}",
                    cluster.sourceRecordIds(),
                    catalogEntryId,
                    matchPlan.victimCatalogEntryIds());
            return BoundClusterResult.bound(cluster.sourceRecordIds(), catalogEntryId, matchPlan.victimCatalogEntryIds());
        }
    }

    private AggregationMatchPlan findClusterMatchPlan(
            SourceRecordAggregationCluster cluster,
            List<SourceRecordAggregationRecord> sourceRecords) {
        // A raw cluster is recall evidence, not authorization to merge its existing works.
        // The complete proposed set is locked and checked again before any destructive write.
        List<UUID> matchedUnifiedIds = new java.util.ArrayList<>();
        for (SourceRecordAggregationRecord sourceRecord : leaderFirst(cluster, sourceRecords)) {
            repository.findExistingBinding(sourceRecord.id()).ifPresent(matchedUnifiedIds::add);
            AggregationMatchPlan plan = repository.findMatchPlan(sourceRecord);
            if (plan.isAmbiguous()) { return AggregationMatchPlan.ambiguous(); }
            if (plan.hasRoot()) {
                matchedUnifiedIds.add(plan.rootCatalogEntryId());
            }
            matchedUnifiedIds.addAll(plan.victimCatalogEntryIds());
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
        Set<UUID> ids = new LinkedHashSet<>(plan.victimCatalogEntryIds());
        ids.add(plan.rootCatalogEntryId());
        return ids;
    }

    private String clusterBindingConflict(CatalogEntryAggregationCandidate root, Set<UUID> expectedIds,
            List<SourceRecordAggregationRecord> incoming) {
        // Root and victim row locks serialize membership writes. Witness rows are read
        // without acquiring a second set of raw locks in the reverse order.
        String conflict = matchingStrategy.clusterBindingConflictReason(root,
                repository.findBoundMemberIdentities(expectedIds), incoming);
        if (conflict != null) {
            log.warn("Aggregation cluster requires review: catalogEntryId={}, sourceRecordIds={}, reason={}",
                    root.id(), incoming.stream().map(SourceRecordAggregationRecord::id).toList(), conflict);
        }
        return conflict;
    }

    private List<SourceRecordAggregationRecord> leaderFirst(
            SourceRecordAggregationCluster cluster,
            List<SourceRecordAggregationRecord> sourceRecords) {
        java.util.LinkedHashSet<SourceRecordAggregationRecord> ordered = new java.util.LinkedHashSet<>();
        ordered.add(cluster.leader());
        ordered.addAll(sourceRecords);
        return List.copyOf(ordered);
    }

    private void publishBoundResult(AggregationResult result) {
        publisher.publishUnifiedIndex(result.catalogEntryId());
        publisher.publishRawIndex(result.sourceRecordId());
        result.deletedCatalogEntryIds().forEach(publisher::publishUnifiedDelete);
    }

    private AggregationResult publishAggregationResult(AggregationResult result) {
        if ("BOUND".equals(result.status())) {
            publishBoundResult(result);
        } else if ("PIPELINE_FAILED".equals(result.status())) {
            result.deletedCatalogEntryIds().forEach(publisher::publishUnifiedDelete);
        }
        return result;
    }

    private void publishClusterResult(BoundClusterResult result) {
        publisher.publishUnifiedIndex(result.catalogEntryId());
        result.sourceRecordIds().forEach(publisher::publishRawIndex);
        result.deletedCatalogEntryIds().forEach(publisher::publishUnifiedDelete);
    }

    private void recordClusterResults(Map<UUID, AggregationResult> results, BoundClusterResult result) {
        if ("BOUND".equals(result.status())) {
            result.sourceRecordIds().forEach(sourceRecordId -> results.put(
                    sourceRecordId,
                    AggregationResult.bound(sourceRecordId, result.catalogEntryId(), result.deletedCatalogEntryIds())));
            return;
        }
        if ("PIPELINE_FAILED".equals(result.status())) {
            result.deletedCatalogEntryIds().forEach(publisher::publishUnifiedDelete);
            result.sourceRecordIds().forEach(sourceRecordId -> results.put(
                    sourceRecordId,
                    AggregationResult.pipelineFailed(sourceRecordId, result.catalogEntryId(), result.deletedCatalogEntryIds())));
            return;
        }
        result.sourceRecordIds().forEach(sourceRecordId -> results.put(
                sourceRecordId,
                AggregationResult.skipped(sourceRecordId, result.reason())));
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

    private List<UUID> normalizeManualMergeIds(List<UUID> catalogEntryIds) {
        if (catalogEntryIds == null) {
            throw new IllegalArgumentException("catalogEntryIds must contain at least two IDs");
        }
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(catalogEntryIds.stream()
                .filter(Objects::nonNull)
                .toList()));
        if (ids.size() < 2) {
            throw new IllegalArgumentException("catalogEntryIds must contain at least two distinct IDs");
        }
        return ids;
    }

    private record BoundClusterResult(
            List<UUID> sourceRecordIds,
            UUID catalogEntryId,
            String status,
            List<UUID> deletedCatalogEntryIds,
            String reason) {

        static BoundClusterResult bound(
                List<UUID> sourceRecordIds,
                UUID catalogEntryId,
                List<UUID> deletedCatalogEntryIds) {
            return new BoundClusterResult(
                    List.copyOf(sourceRecordIds),
                    catalogEntryId,
                    "BOUND",
                    List.copyOf(deletedCatalogEntryIds),
                    null);
        }

        static BoundClusterResult skipped(List<UUID> sourceRecordIds, String reason) {
            return new BoundClusterResult(List.copyOf(sourceRecordIds), null, reason, List.of(), reason);
        }

        static BoundClusterResult pipelineFailed(
                List<UUID> sourceRecordIds,
                UUID catalogEntryId,
                List<UUID> deletedCatalogEntryIds) {
            return new BoundClusterResult(
                    List.copyOf(sourceRecordIds),
                    catalogEntryId,
                    "PIPELINE_FAILED",
                    List.copyOf(deletedCatalogEntryIds),
                    null);
        }
    }
}
