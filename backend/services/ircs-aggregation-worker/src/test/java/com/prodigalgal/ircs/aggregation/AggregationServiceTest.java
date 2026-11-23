package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock
    private JdbcAggregationRepository repository;

    @Mock
    private AggregationSearchSyncPublisher publisher;

    @Mock
    private AggregationStorageCommandPublisher storageCommandPublisher;

    @Mock
    private SourceRecordGraphClusterer sourceRecordGraphClusterer;

    @Mock
    private AggregationPipelineRuntime aggregationPipelineRuntime;

    @Mock
    private AggregationMatchKeyLock matchKeyLock;

    @Mock
    private AggregationWorkPublisher workPublisher;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private AggregationService service;
    private final AggregationMatchingStrategy matchingStrategy = AggregationTestFixtures.matchingStrategy();
    private final AggregationSafetyPolicy safetyPolicy = AggregationTestFixtures.safetyPolicy();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any()))
                .thenAnswer(ignored -> new org.springframework.transaction.support.SimpleTransactionStatus());
        lenient().when(matchKeyLock.tryAcquire(any()))
                .thenReturn(Optional.of(AggregationMatchKeyLock.MatchLockScope.noop()));
        lenient().when(repository.findUnifiedCandidatesByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().map(id -> unifiedCandidate(id, "Codex Aggregation", "2026")).toList();
        });
        service = new AggregationService(repository, publisher, storageCommandPublisher, sourceRecordGraphClusterer,
                aggregationPipelineRuntime, matchKeyLock, workPublisher, transactionManager, matchingStrategy, safetyPolicy);
    }

    @Test
    void runtimePendingEscapesAfterCommittedAggregationForOriginalClaimRetry() {
        UUID raw = UUID.randomUUID();
        UUID unified = UUID.randomUUID();
        when(repository.findSourceRecord(raw)).thenReturn(Optional.of(readySourceRecord(raw)));
        when(repository.findExistingBinding(raw)).thenReturn(Optional.of(unified));
        when(aggregationPipelineRuntime.run(unified)).thenAnswer(ignored -> {
            DeferredAdultAssessments.register(unified);
            return pipelineExecution(unified, List.of());
        });
        var snapshot = new AdultAssessmentSnapshot(unified, "v1", "sources1", null);
        when(repository.captureAdultAssessment(unified)).thenReturn(snapshot);
        var deferred = new AdultAssessmentDeferredException("GOOGLE_NSFW_PENDING", 86400);
        when(repository.evaluateAdultAssessments(List.of(snapshot))).thenThrow(deferred);

        org.junit.jupiter.api.Assertions.assertSame(deferred,
                org.junit.jupiter.api.Assertions.assertThrows(AdultAssessmentDeferredException.class,
                        () -> service.aggregateRuntimeWork(raw)));
        InOrder order = inOrder(transactionManager, repository);
        order.verify(transactionManager).commit(any());
        order.verify(repository).captureAdultAssessment(unified);
        order.verify(transactionManager).commit(any());
        order.verify(repository).evaluateAdultAssessments(List.of(snapshot));
        verifyNoInteractions(workPublisher);
        verify(repository, never()).applyAdultAssessment(any(), any());
    }

    @Test
    void authorityFirstSinglePathBindsAnExactProjectionWithoutCreatingOrUpdatingAWork() {
        ReflectionTestUtils.setField(service, "authorityFirstEnabled", true);
        UUID rawId = UUID.randomUUID();
        UUID projectionId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = authorityRaw(rawId, "Authority Film", "2020", "movie", null);
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(rawId)).thenReturn(Optional.empty());
        when(repository.findAuthorityCatalogProjections(eq("movie"), anySet(), any(), any())).thenReturn(List.of(
                authorityProjection(projectionId, "movie", "Authority Film", 2020, 1, -1)));
        when(repository.recordAuthorityBinding(rawId, projectionId, "TITLE_YEAR_SEASON")).thenReturn(1);
        when(repository.bindRawToUnified(rawId, projectionId)).thenReturn(1);
        when(aggregationPipelineRuntime.run(projectionId)).thenReturn(pipelineExecution(projectionId, List.of()));

        AggregationResult result = service.aggregateOne(rawId);

        assertEquals("BOUND", result.status());
        assertEquals(projectionId, result.catalogEntryId());
        verify(repository).bindRawToUnified(rawId, projectionId);
        verify(repository, never()).createCatalogEntry(any());
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(repository, never()).mergeDuplicateCatalogEntries(any(), any());
    }

    @Test
    void authorityFirstBatchWaitsWithoutCreatingWorksOrGraphClusteringWhenEvidenceIsMissing() {
        ReflectionTestUtils.setField(service, "authorityFirstEnabled", true);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SourceRecordAggregationRecord first = authorityRaw(firstId, "Authority Film", "2020", "movie", null);
        SourceRecordAggregationRecord second = authorityRaw(secondId, "Authority Film", "2020", "movie", null);
        when(repository.findSourceRecords(List.of(firstId, secondId))).thenReturn(List.of(first, second));
        when(repository.findSourceRecord(firstId)).thenReturn(Optional.of(first));
        when(repository.findSourceRecord(secondId)).thenReturn(Optional.of(second));
        when(repository.findExistingBinding(firstId)).thenReturn(Optional.empty());
        when(repository.findExistingBinding(secondId)).thenReturn(Optional.empty());
        when(repository.findAuthorityCatalogProjections(eq("movie"), anySet(), any(), any())).thenReturn(List.of());

        List<AggregationResult> results = service.aggregateRuntimeWorkBatch(List.of(firstId, secondId));

        assertEquals(List.of("WAITING_AUTHORITY", "WAITING_AUTHORITY"),
                results.stream().map(AggregationResult::status).toList());
        verify(repository, times(2)).markPending(any());
        verify(repository, never()).createCatalogEntry(any());
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(sourceRecordGraphClusterer, never()).cluster(any(), any());
    }

    @Test
    void unknownCategoryWaitsForAuthorityClassificationInsteadOfFallingBackToSourceFirst() {
        ReflectionTestUtils.setField(service, "authorityFirstEnabled", true);
        UUID rawId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = authorityRaw(rawId, "Unknown Title", "2020", "unknown", null);
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));

        AggregationResult result = service.aggregateOne(rawId);

        assertEquals("WAITING_AUTHORITY", result.status());
        verify(repository).markPending(rawId);
        verify(repository, never()).findAuthorityCatalogProjections(any(), anySet(), any(), any());
        verify(repository, never()).createCatalogEntry(any());
    }

    @Test
    void unmatchedRawWithCleanTitleYearAndTmdbIdEnqueuesImportWithoutBinding() {
        ReflectionTestUtils.setField(service, "authorityFirstEnabled", true);
        UUID rawId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = new SourceRecordAggregationRecord(rawId, "Authority Film", null, null,
                "2020", null, null, null, null, null, null, null, "movie", "movie",
                null, "123", null, null, "READY", "SUCCESS", "PROCESSING");
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(rawId)).thenReturn(Optional.empty());
        when(repository.findAuthorityCatalogProjections(eq("movie"), anySet(), any(), any())).thenReturn(List.of());

        AggregationResult result = service.aggregateOne(rawId);

        assertEquals("WAITING_AUTHORITY", result.status());
        verify(repository).enqueueAuthorityCatalogImport("movie", 123L, -1);
        verify(repository, never()).recordAuthorityBinding(any(), any(), any());
        verify(repository, never()).bindRawToUnified(any(), any());
        verify(repository, never()).createCatalogEntry(any());
    }

    private SourceRecordAggregationRecord authorityRaw(
            UUID id, String title, String year, String category, Integer season) {
        return new SourceRecordAggregationRecord(id, title, null, null, year, null,
                null, null, null, null, null, season, category, category,
                null, null, null, null, "READY", "SUCCESS", "PROCESSING");
    }

    private AuthorityCatalogProjection authorityProjection(
            UUID catalogEntryId, String mediaType, String title, Integer year, Integer seasons, int season) {
        return new AuthorityCatalogProjection(UUID.randomUUID(), mediaType, null, null,
                AuthorityCatalogBindingPolicy.normalizeAuthorityTitleKey(title), null,
                year, null, seasons, season, null, catalogEntryId);
    }

    @Test
    void runtimeSnapshotChangeRetainsOriginalClaimForRetry() {
        UUID raw = UUID.randomUUID();
        UUID unified = UUID.randomUUID();
        when(repository.findSourceRecord(raw)).thenReturn(Optional.of(readySourceRecord(raw)));
        when(repository.findExistingBinding(raw)).thenReturn(Optional.of(unified));
        when(aggregationPipelineRuntime.run(unified)).thenAnswer(ignored -> {
            DeferredAdultAssessments.register(unified);
            return pipelineExecution(unified, List.of());
        });
        var snapshot = new AdultAssessmentSnapshot(unified, "v1", "sources1", null);
        var assessment = com.prodigalgal.ircs.common.adult.AdultAssessment.safe("test");
        when(repository.captureAdultAssessment(unified)).thenReturn(snapshot);
        when(repository.evaluateAdultAssessments(List.of(snapshot))).thenReturn(java.util.Map.of(unified, assessment));
        when(repository.applyAdultAssessment(snapshot, assessment)).thenReturn(false);

        var exception = org.junit.jupiter.api.Assertions.assertThrows(AdultAssessmentDeferredException.class,
                () -> service.aggregateRuntimeWork(raw));
        assertEquals("CONTENT_SAFETY_SNAPSHOT_CHANGED", exception.getMessage());
        verify(transactionManager).rollback(any());
        verifyNoInteractions(workPublisher);
    }

    @Test
    void createsCatalogEntryBindsSourceRecordAndPublishesSearchSync() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = readySourceRecord(sourceRecordId);

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));
        when(repository.findExistingBinding(sourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(sourceRecord)).thenReturn(AggregationMatchPlan.none());
        when(repository.createCatalogEntry(sourceRecord)).thenReturn(catalogEntryId);
        UUID coverImageId = UUID.randomUUID();
        when(aggregationPipelineRuntime.run(catalogEntryId))
                .thenReturn(pipelineExecution(catalogEntryId, List.of(coverImageId)));

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("BOUND", result.status());
        verify(repository).upsertUnifiedFields(catalogEntryId, sourceRecord);
        verify(repository).bindRawToUnified(sourceRecordId, catalogEntryId);
        verify(aggregationPipelineRuntime).run(catalogEntryId);
        verify(storageCommandPublisher).enqueueCoverR2Sync(coverImageId);
        verify(repository).markBound(sourceRecordId);
        verify(publisher).publishUnifiedIndex(catalogEntryId);
        verify(publisher).publishRawIndex(sourceRecordId);
    }

    @Test
    void reusesExistingCatalogEntryWhenExternalIdMatches() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = readySourceRecord(sourceRecordId);

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));
        when(repository.findExistingBinding(sourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(sourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(catalogEntryId));
        when(repository.findBoundMemberIdentities(java.util.Set.of(catalogEntryId))).thenReturn(
                java.util.stream.IntStream.range(0, 25)
                        .mapToObj(index -> memberWithTmdb("210001")).toList());
        when(aggregationPipelineRuntime.run(catalogEntryId))
                .thenReturn(pipelineExecution(catalogEntryId, List.of()));

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("BOUND", result.status());
        verify(repository, never()).createCatalogEntry(sourceRecord);
        verify(repository).upsertUnifiedFields(catalogEntryId, sourceRecord);
        verify(repository).bindRawToUnified(sourceRecordId, catalogEntryId);
        verify(aggregationPipelineRuntime).run(catalogEntryId);
        verify(repository).markBound(sourceRecordId);
        verify(publisher).publishUnifiedIndex(catalogEntryId);
        verify(publisher).publishRawIndex(sourceRecordId);
    }

    @Test
    void checksConflictsBetweenPreviouslyBoundMembersBeforeAppendingToSparseRoot() {
        UUID rawId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = readySourceRecord(rawId);
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.rootOnly(rootId));
        when(repository.findBoundMemberIdentities(java.util.Set.of(rootId)))
                .thenReturn(List.of(memberWithTmdb("210001"), memberWithTmdb("210002")));

        AggregationResult result = service.aggregateRuntimeWork(rawId);

        assertEquals("IDENTITY_CONFLICT", result.status());
        InOrder order = inOrder(repository);
        order.verify(repository).lockCatalogEntries(java.util.Set.of(rootId));
        order.verify(repository).findBoundMemberIdentities(java.util.Set.of(rootId));
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(repository, never()).bindRawToUnified(any(), any());
        verifyNoInteractions(aggregationPipelineRuntime, publisher);
    }

    @Test
    void unverifiedLargeClusterGoesToReviewWithoutChangingItsExistingBinding() {
        UUID rawId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = readySourceRecord(rawId);
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(rawId)).thenReturn(Optional.of(rootId));
        when(repository.findBoundMemberIdentities(java.util.Set.of(rootId))).thenReturn(
                java.util.stream.IntStream.rangeClosed(0, AggregationMemberIdentity.REVIEW_MEMBER_LIMIT)
                        .mapToObj(index -> memberWithTmdb("210001")).toList());

        AggregationResult result = service.aggregateRuntimeWork(rawId);

        assertEquals("MERGE_AMBIGUOUS", result.status());
        verify(repository, never()).mergeDuplicateCatalogEntries(any(), any());
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(repository, never()).bindRawToUnified(any(), any());
        verifyNoInteractions(aggregationPipelineRuntime, publisher);
    }

    private AggregationMemberIdentity memberWithTmdb(String tmdbId) {
        return new AggregationMemberIdentity(UUID.randomUUID(), "Codex Aggregation", null, null, null,
                "2026", null, "movie", "1234567", tmdbId, null, null);
    }

    @Test
    void clustersMultipleSourceRecordsIntoOneUnifiedAndPublishesPerRawSearchSync() {
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        UUID coverImageId = UUID.randomUUID();
        SourceRecordAggregationRecord firstSourceRecord = readySourceRecord(firstSourceRecordId, null);
        SourceRecordAggregationRecord secondSourceRecord = readySourceRecord(
                secondSourceRecordId,
                "Codex Aggregation 1080p",
                "Codex Alias",
                null);
        SourceRecordAggregationCluster cluster =
                new SourceRecordAggregationCluster(firstSourceRecord, List.of(firstSourceRecord, secondSourceRecord));

        when(repository.findSourceRecords(List.of(firstSourceRecordId, secondSourceRecordId)))
                .thenReturn(List.of(firstSourceRecord, secondSourceRecord));
        when(repository.findContextUnifiedCandidates(List.of(firstSourceRecord, secondSourceRecord))).thenReturn(List.of());
        when(sourceRecordGraphClusterer.cluster(List.of(firstSourceRecord, secondSourceRecord), List.of())).thenReturn(List.of(cluster));
        when(repository.findExistingBinding(firstSourceRecordId)).thenReturn(Optional.empty());
        when(repository.findExistingBinding(secondSourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(firstSourceRecord)).thenReturn(AggregationMatchPlan.none());
        when(repository.findMatchPlan(secondSourceRecord)).thenReturn(AggregationMatchPlan.none());
        when(repository.createCatalogEntry(firstSourceRecord)).thenReturn(catalogEntryId);
        when(aggregationPipelineRuntime.run(catalogEntryId))
                .thenReturn(pipelineExecution(catalogEntryId, List.of(coverImageId)));

        List<AggregationResult> results =
                service.aggregateRuntimeWorkBatch(List.of(firstSourceRecordId, secondSourceRecordId));

        assertEquals(List.of("BOUND", "BOUND"), results.stream().map(AggregationResult::status).toList());
        verify(repository).createCatalogEntry(firstSourceRecord);
        verify(repository).upsertUnifiedFields(catalogEntryId, firstSourceRecord);
        verify(repository).upsertUnifiedFields(catalogEntryId, secondSourceRecord);
        verify(repository).bindRawToUnified(firstSourceRecordId, catalogEntryId);
        verify(repository).bindRawToUnified(secondSourceRecordId, catalogEntryId);
        verify(aggregationPipelineRuntime).run(catalogEntryId);
        verify(storageCommandPublisher).enqueueCoverR2Sync(coverImageId);
        verify(repository).markBound(firstSourceRecordId);
        verify(repository).markBound(secondSourceRecordId);
        verify(publisher).publishUnifiedIndex(catalogEntryId);
        verify(publisher).publishRawIndex(firstSourceRecordId);
        verify(publisher).publishRawIndex(secondSourceRecordId);
    }

    @Test
    void clusteredSourceRecordsMergeExistingUnifiedVictimsThroughExistingPath() {
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        UUID rootCatalogEntryId = UUID.randomUUID();
        UUID victimCatalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord firstSourceRecord = readySourceRecord(firstSourceRecordId, null);
        SourceRecordAggregationRecord secondSourceRecord = readySourceRecord(
                secondSourceRecordId,
                "Codex Aggregation 1080p",
                "Codex Alias",
                null);
        SourceRecordAggregationCluster cluster =
                new SourceRecordAggregationCluster(firstSourceRecord, List.of(firstSourceRecord, secondSourceRecord));

        when(repository.findSourceRecords(List.of(firstSourceRecordId, secondSourceRecordId)))
                .thenReturn(List.of(firstSourceRecord, secondSourceRecord));
        when(repository.findContextUnifiedCandidates(List.of(firstSourceRecord, secondSourceRecord))).thenReturn(List.of());
        when(sourceRecordGraphClusterer.cluster(List.of(firstSourceRecord, secondSourceRecord), List.of())).thenReturn(List.of(cluster));
        when(repository.findExistingBinding(firstSourceRecordId)).thenReturn(Optional.of(rootCatalogEntryId));
        when(repository.findExistingBinding(secondSourceRecordId)).thenReturn(Optional.of(victimCatalogEntryId));
        when(repository.findMatchPlan(firstSourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(rootCatalogEntryId));
        when(repository.findMatchPlan(secondSourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(victimCatalogEntryId));
        when(aggregationPipelineRuntime.run(rootCatalogEntryId))
                .thenReturn(pipelineExecution(rootCatalogEntryId, List.of()));

        List<AggregationResult> results =
                service.aggregateRuntimeWorkBatch(List.of(firstSourceRecordId, secondSourceRecordId));

        assertEquals(List.of("BOUND", "BOUND"), results.stream().map(AggregationResult::status).toList());
        verify(repository).mergeDuplicateCatalogEntries(rootCatalogEntryId, List.of(victimCatalogEntryId));
        verify(repository, never()).createCatalogEntry(firstSourceRecord);
        verify(repository).bindRawToUnified(firstSourceRecordId, rootCatalogEntryId);
        verify(repository).bindRawToUnified(secondSourceRecordId, rootCatalogEntryId);
        verify(publisher).publishUnifiedIndex(rootCatalogEntryId);
        verify(publisher).publishRawIndex(firstSourceRecordId);
        verify(publisher).publishRawIndex(secondSourceRecordId);
        verify(publisher).publishUnifiedDelete(victimCatalogEntryId);
    }

    @Test
    void mixedGraphClusterReusesContextUnifiedWithoutCreatingNewUnified() {
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        UUID contextCatalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord firstSourceRecord = readySourceRecord(firstSourceRecordId, null);
        SourceRecordAggregationRecord secondSourceRecord = readySourceRecord(
                secondSourceRecordId,
                "Codex Aggregation 1080p",
                "Codex Alias",
                null);
        CatalogEntryAggregationCandidate context = unifiedCandidate(contextCatalogEntryId, "Codex Aggregation", "2026");
        SourceRecordAggregationCluster cluster = new SourceRecordAggregationCluster(
                firstSourceRecord,
                List.of(firstSourceRecord, secondSourceRecord),
                List.of(contextCatalogEntryId));

        when(repository.findSourceRecords(List.of(firstSourceRecordId, secondSourceRecordId)))
                .thenReturn(List.of(firstSourceRecord, secondSourceRecord));
        when(repository.findContextUnifiedCandidates(List.of(firstSourceRecord, secondSourceRecord))).thenReturn(List.of(context));
        when(sourceRecordGraphClusterer.cluster(List.of(firstSourceRecord, secondSourceRecord), List.of(context))).thenReturn(List.of(cluster));
        when(repository.findExistingBinding(firstSourceRecordId)).thenReturn(Optional.empty());
        when(repository.findExistingBinding(secondSourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(firstSourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(contextCatalogEntryId));
        when(repository.findMatchPlan(secondSourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(contextCatalogEntryId));
        when(aggregationPipelineRuntime.run(contextCatalogEntryId))
                .thenReturn(pipelineExecution(contextCatalogEntryId, List.of()));

        List<AggregationResult> results =
                service.aggregateRuntimeWorkBatch(List.of(firstSourceRecordId, secondSourceRecordId));

        assertEquals(List.of("BOUND", "BOUND"), results.stream().map(AggregationResult::status).toList());
        verify(repository, never()).createCatalogEntry(firstSourceRecord);
        verify(repository).bindRawToUnified(firstSourceRecordId, contextCatalogEntryId);
        verify(repository).bindRawToUnified(secondSourceRecordId, contextCatalogEntryId);
        verify(aggregationPipelineRuntime).run(contextCatalogEntryId);
        verify(publisher).publishUnifiedIndex(contextCatalogEntryId);
        verify(publisher).publishRawIndex(firstSourceRecordId);
        verify(publisher).publishRawIndex(secondSourceRecordId);
    }

    @Test
    void mixedGraphClusterMergesMultipleContextUnifiedVictims() {
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        UUID rootCatalogEntryId = UUID.randomUUID();
        UUID victimCatalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord firstSourceRecord = readySourceRecord(firstSourceRecordId, null);
        SourceRecordAggregationRecord secondSourceRecord = readySourceRecord(
                secondSourceRecordId,
                "Codex Aggregation 1080p",
                "Codex Alias",
                null);
        CatalogEntryAggregationCandidate root = unifiedCandidate(rootCatalogEntryId, "Codex Aggregation", "2026");
        CatalogEntryAggregationCandidate victim = unifiedCandidate(victimCatalogEntryId, "Codex Aggregation 1080p", "2026");
        SourceRecordAggregationCluster cluster = new SourceRecordAggregationCluster(
                firstSourceRecord,
                List.of(firstSourceRecord, secondSourceRecord),
                List.of(rootCatalogEntryId, victimCatalogEntryId));

        when(repository.findSourceRecords(List.of(firstSourceRecordId, secondSourceRecordId)))
                .thenReturn(List.of(firstSourceRecord, secondSourceRecord));
        when(repository.findContextUnifiedCandidates(List.of(firstSourceRecord, secondSourceRecord))).thenReturn(List.of(root, victim));
        when(sourceRecordGraphClusterer.cluster(List.of(firstSourceRecord, secondSourceRecord), List.of(root, victim))).thenReturn(List.of(cluster));
        when(repository.findExistingBinding(firstSourceRecordId)).thenReturn(Optional.empty());
        when(repository.findExistingBinding(secondSourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(firstSourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(rootCatalogEntryId));
        when(repository.findMatchPlan(secondSourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(victimCatalogEntryId));
        when(aggregationPipelineRuntime.run(rootCatalogEntryId))
                .thenReturn(pipelineExecution(rootCatalogEntryId, List.of()));

        List<AggregationResult> results =
                service.aggregateRuntimeWorkBatch(List.of(firstSourceRecordId, secondSourceRecordId));

        assertEquals(List.of("BOUND", "BOUND"), results.stream().map(AggregationResult::status).toList());
        verify(repository).mergeDuplicateCatalogEntries(rootCatalogEntryId, List.of(victimCatalogEntryId));
        verify(repository, never()).createCatalogEntry(firstSourceRecord);
        verify(repository).bindRawToUnified(firstSourceRecordId, rootCatalogEntryId);
        verify(repository).bindRawToUnified(secondSourceRecordId, rootCatalogEntryId);
        verify(publisher).publishUnifiedIndex(rootCatalogEntryId);
        verify(publisher).publishRawIndex(firstSourceRecordId);
        verify(publisher).publishRawIndex(secondSourceRecordId);
        verify(publisher).publishUnifiedDelete(victimCatalogEntryId);
    }

    @Test
    void mergesDuplicateUnifiedVictimsBeforeBindingAndPublishesVictimDelete() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID rootCatalogEntryId = UUID.randomUUID();
        UUID victimCatalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = readySourceRecord(sourceRecordId);

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));
        when(repository.findExistingBinding(sourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(sourceRecord)).thenReturn(AggregationMatchPlan.rootWithVictims(
                rootCatalogEntryId,
                List.of(victimCatalogEntryId)));
        UUID coverImageId = UUID.randomUUID();
        when(aggregationPipelineRuntime.run(rootCatalogEntryId))
                .thenReturn(pipelineExecution(rootCatalogEntryId, List.of(coverImageId)));

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("BOUND", result.status());
        InOrder ordered = inOrder(repository, aggregationPipelineRuntime, storageCommandPublisher, publisher);
        ordered.verify(repository).mergeDuplicateCatalogEntries(rootCatalogEntryId, List.of(victimCatalogEntryId));
        ordered.verify(repository).upsertUnifiedFields(rootCatalogEntryId, sourceRecord);
        ordered.verify(repository).bindRawToUnified(sourceRecordId, rootCatalogEntryId);
        ordered.verify(aggregationPipelineRuntime).run(rootCatalogEntryId);
        ordered.verify(storageCommandPublisher).enqueueCoverR2Sync(coverImageId);
        ordered.verify(repository).markBound(sourceRecordId);
        ordered.verify(publisher).publishUnifiedIndex(rootCatalogEntryId);
        ordered.verify(publisher).publishRawIndex(sourceRecordId);
        ordered.verify(publisher).publishUnifiedDelete(victimCatalogEntryId);
        verify(repository, never()).createCatalogEntry(sourceRecord);
    }

    @Test
    void skipsIneligibleSourceRecordAndDoesNotPublishSearchSync() {
        UUID sourceRecordId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = new SourceRecordAggregationRecord(
                sourceRecordId,
                "Codex Aggregation",
                null,
                null,
                "2026",
                null,
                null,
                null,
                "电影",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "PENDING",
                "SUCCESS",
                "PROCESSING");

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("WAITING_DEPENDENCIES", result.status());
        verify(repository).markPending(sourceRecordId);
        verify(publisher, never()).publishUnifiedIndex(org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publishRawIndex(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pendingDependencyWaitDoesNotStartWriteTransactionsOrRetouchTheRawRow() {
        UUID id = UUID.randomUUID();
        SourceRecordAggregationRecord raw = new SourceRecordAggregationRecord(id, "Pending fixture", null, null, "2026",
                null, null, null, "电影", null, null, null, null, null, null, null, null,
                "PENDING", "PENDING", "PENDING");
        when(repository.findSourceRecord(id)).thenReturn(Optional.of(raw));

        AggregationResult result = service.aggregateRuntimeWork(id);

        assertEquals("WAITING_DEPENDENCIES", result.status());
        verifyNoInteractions(transactionManager, publisher, storageCommandPublisher, aggregationPipelineRuntime,
                matchKeyLock, sourceRecordGraphClusterer);
        verify(repository, never()).configureAggregationTransaction();
        verify(repository, never()).lockAggregationShared();
        verify(repository, never()).lockSourceRecords(anyCollection());
        verify(repository, never()).markPending(any());
    }

    @Test
    void pipelineFailureKeepsRawPendingDirtyUnifiedAndSuppressesSideEffects() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = readySourceRecord(sourceRecordId);

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));
        when(repository.findExistingBinding(sourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(sourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(catalogEntryId));
        when(aggregationPipelineRuntime.run(catalogEntryId)).thenReturn(pipelineFailure(catalogEntryId));

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("PIPELINE_FAILED", result.status());
        verify(repository).upsertUnifiedFields(catalogEntryId, sourceRecord);
        verify(repository).bindRawToUnified(sourceRecordId, catalogEntryId);
        verify(repository).markUnifiedDirty(catalogEntryId);
        verify(repository).markPending(sourceRecordId);
        verify(repository, never()).markBound(sourceRecordId);
        verifyNoInteractions(storageCommandPublisher);
        verify(publisher, never()).publishUnifiedIndex(org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publishRawIndex(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void matchLockBusyReturnsRawToPendingWithoutCreatingCatalogEntry() {
        UUID sourceRecordId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = readySourceRecord(sourceRecordId);

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));
        when(matchKeyLock.tryAcquire(any())).thenReturn(Optional.empty());

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("MATCH_LOCK_BUSY", result.status());
        verify(repository).markPending(sourceRecordId);
        verify(repository, never()).findMatchPlan(sourceRecord);
        verify(repository, never()).createCatalogEntry(sourceRecord);
        verifyNoInteractions(storageCommandPublisher);
        verify(publisher, never()).publishUnifiedIndex(org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publishRawIndex(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void clusterMatchLockBusyReturnsAllClusterMembersToPending() {
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        SourceRecordAggregationRecord firstSourceRecord = readySourceRecord(firstSourceRecordId, null);
        SourceRecordAggregationRecord secondSourceRecord = readySourceRecord(
                secondSourceRecordId,
                "Codex Aggregation 1080p",
                "Codex Alias",
                null);
        SourceRecordAggregationCluster cluster =
                new SourceRecordAggregationCluster(firstSourceRecord, List.of(firstSourceRecord, secondSourceRecord));

        when(repository.findSourceRecords(List.of(firstSourceRecordId, secondSourceRecordId)))
                .thenReturn(List.of(firstSourceRecord, secondSourceRecord));
        when(repository.findContextUnifiedCandidates(List.of(firstSourceRecord, secondSourceRecord))).thenReturn(List.of());
        when(sourceRecordGraphClusterer.cluster(List.of(firstSourceRecord, secondSourceRecord), List.of())).thenReturn(List.of(cluster));
        when(matchKeyLock.tryAcquire(any())).thenReturn(Optional.empty());

        List<AggregationResult> results =
                service.aggregateRuntimeWorkBatch(List.of(firstSourceRecordId, secondSourceRecordId));

        assertEquals(
                List.of("MATCH_LOCK_BUSY", "MATCH_LOCK_BUSY"),
                results.stream().map(AggregationResult::status).toList());
        verify(repository).markPending(firstSourceRecordId);
        verify(repository).markPending(secondSourceRecordId);
        verify(repository, never()).createCatalogEntry(firstSourceRecord);
        verifyNoInteractions(storageCommandPublisher);
        verify(publisher, never()).publishUnifiedIndex(org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publishRawIndex(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recalculateKeepsUnifiedDirtyWhenTheSingleUnifiedPipelineFails() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.findRawIdsForUnified(catalogEntryId)).thenReturn(List.of(sourceRecordId));
        when(aggregationPipelineRuntime.run(catalogEntryId)).thenReturn(pipelineFailure(catalogEntryId));

        int processed = service.recalculateUnified(catalogEntryId);

        assertEquals(0, processed);
        verify(repository, never()).markPending(sourceRecordId);
        verify(aggregationPipelineRuntime).run(catalogEntryId);
        verify(repository).markUnifiedDirty(catalogEntryId);
        verify(repository, never()).markUnifiedSynced(catalogEntryId);
        verify(publisher, never()).publishUnifiedIndex(catalogEntryId);
        verify(publisher, never()).publishRawIndex(sourceRecordId);
    }

    @Test
    void recalculationRunsPipelineOnceThenPublishesAllMembersAfterCommit() {
        UUID unifiedId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findRawIdsForUnified(unifiedId)).thenReturn(List.of(first, second));
        when(aggregationPipelineRuntime.run(unifiedId)).thenReturn(pipelineExecution(unifiedId, List.of()));

        var search = org.mockito.Mockito.mock(com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher.class);
        var actual = new AggregationService(repository, new AggregationSearchSyncPublisher(search),
                storageCommandPublisher, sourceRecordGraphClusterer, aggregationPipelineRuntime,
                matchKeyLock, workPublisher, transactionManager, matchingStrategy, safetyPolicy);
        assertEquals(2, actual.recalculateUnified(unifiedId));

        InOrder order = inOrder(transactionManager, aggregationPipelineRuntime, search);
        order.verify(transactionManager).getTransaction(any());
        order.verify(aggregationPipelineRuntime).run(unifiedId);
        order.verify(transactionManager).commit(any());
        order.verify(search).enqueue(first, com.prodigalgal.ircs.contracts.search.SearchEntityType.SOURCE_RECORD,
                com.prodigalgal.ircs.contracts.search.SyncOperation.INDEX);
        order.verify(search).enqueue(second, com.prodigalgal.ircs.contracts.search.SearchEntityType.SOURCE_RECORD,
                com.prodigalgal.ircs.contracts.search.SyncOperation.INDEX);
        verify(repository, never()).findSourceRecord(any());
        verify(repository, never()).markPending(any());
    }

    @Test
    void recalculateDirtyUnifiedUsesCappedDirtySelection() {
        UUID firstUnifiedId = UUID.randomUUID();
        UUID secondUnifiedId = UUID.randomUUID();

        when(repository.findDirtyUnifiedIds(2)).thenReturn(List.of(firstUnifiedId, secondUnifiedId));
        when(repository.findRawIdsForUnified(firstUnifiedId)).thenReturn(List.of());
        when(repository.findRawIdsForUnified(secondUnifiedId)).thenReturn(List.of());

        var result = service.recalculateDirtyUnified(2);

        assertEquals("unified-recalculate", result.taskName());
        assertEquals(2, result.candidates());
        assertEquals(0, result.processed());
        assertEquals(List.of(firstUnifiedId, secondUnifiedId), result.entityIds());
        verify(repository).findDirtyUnifiedIds(2);
        verify(repository, never()).markUnifiedSynced(firstUnifiedId);
        verify(repository, never()).markUnifiedSynced(secondUnifiedId);
        verify(publisher, never()).publishUnifiedIndex(firstUnifiedId);
        verify(publisher, never()).publishUnifiedIndex(secondUnifiedId);
        verify(aggregationPipelineRuntime, never()).run(any());
    }

    @Test
    void prepareAggregationResetDeletesOwnedRowsAndReturnsCounts() {
        UUID sourceRecordId = UUID.randomUUID();
        when(repository.sampleSourceRecordIds(3)).thenReturn(List.of(sourceRecordId));
        when(repository.countSourceRecords()).thenReturn(11L);
        when(repository.countCatalogEntries()).thenReturn(4L);
        when(repository.countRawUnifiedBindings()).thenReturn(7L);
        when(repository.deleteAllAggregationResetOwnedRows()).thenReturn(19);

        var result = service.prepareAggregationReset(3);

        assertEquals("aggregation-reset", result.taskName());
        assertEquals("prepare", result.stepName());
        assertEquals(11L, result.sourceRecordCount());
        assertEquals(4L, result.catalogEntryCount());
        assertEquals(7L, result.bindingCount());
        assertEquals(19L, result.changedRows());
        assertEquals(List.of(sourceRecordId), result.sampleSourceRecordIds());
        InOrder resetOrder = inOrder(repository);
        resetOrder.verify(repository).lockAggregationMaintenance();
        resetOrder.verify(repository).deleteAllAggregationResetOwnedRows();
    }

    @Test
    void markAllRawAggregationPendingReturnsRawCountAndChangedRows() {
        UUID sourceRecordId = UUID.randomUUID();
        when(repository.sampleSourceRecordIds(2)).thenReturn(List.of(sourceRecordId));
        when(repository.countSourceRecords()).thenReturn(8L);
        when(repository.markAllRawAggregationPending()).thenReturn(8);

        var result = service.markAllRawAggregationPending(2);

        assertEquals("aggregation-reset", result.taskName());
        assertEquals("mark-raw-pending", result.stepName());
        assertEquals(8L, result.sourceRecordCount());
        assertEquals(0L, result.catalogEntryCount());
        assertEquals(0L, result.bindingCount());
        assertEquals(8L, result.changedRows());
        assertEquals(List.of(sourceRecordId), result.sampleSourceRecordIds());
        InOrder resetOrder = inOrder(repository);
        resetOrder.verify(repository).lockAggregationMaintenance();
        resetOrder.verify(repository).markAllRawAggregationPending();
    }

    @Test
    void enqueuePendingRawWorkPublishesRuntimeWorkForPendingSourceRecords() {
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        when(repository.findEligiblePendingRawIds(50))
                .thenReturn(List.of(firstSourceRecordId, secondSourceRecordId));

        var result = service.enqueuePendingRawWork(50);

        assertEquals("aggregation-pending-backfill", result.taskName());
        assertEquals(2, result.candidates());
        assertEquals(2, result.processed());
        assertEquals(List.of(firstSourceRecordId, secondSourceRecordId), result.entityIds());
        verify(workPublisher).enqueue(firstSourceRecordId, null, "aggregation-pending-backfill");
        verify(workPublisher).enqueue(secondSourceRecordId, null, "aggregation-pending-backfill");
    }

    @Test
    void backfillUnifiedCoversUpdatesCoverAndPublishesFollowUpSyncs() {
        UUID catalogEntryId = UUID.randomUUID();
        UUID coverImageId = UUID.randomUUID();
        when(repository.backfillUnifiedCoverImagesFromRaw(20))
                .thenReturn(List.of(new UnifiedCoverBackfillResult(catalogEntryId, coverImageId, "LOCAL", "LOCAL_STORED")));

        var result = service.backfillUnifiedCovers(20);

        assertEquals("aggregation-cover-backfill", result.taskName());
        assertEquals(1, result.candidates());
        assertEquals(1, result.processed());
        assertEquals(List.of(catalogEntryId), result.entityIds());
        verify(storageCommandPublisher).enqueueCoverR2Sync(coverImageId);
        verify(publisher).publishUnifiedIndex(catalogEntryId);
    }

    @Test
    void backfillUnifiedCoversSkipsR2SyncForExternalCover() {
        UUID catalogEntryId = UUID.randomUUID();
        UUID coverImageId = UUID.randomUUID();
        when(repository.backfillUnifiedCoverImagesFromRaw(20))
                .thenReturn(List.of(new UnifiedCoverBackfillResult(catalogEntryId, coverImageId, "EXTERNAL", "UNPROCESSED")));

        var result = service.backfillUnifiedCovers(20);

        assertEquals(1, result.processed());
        verify(storageCommandPublisher, never()).enqueueCoverR2Sync(coverImageId);
        verify(publisher).publishUnifiedIndex(catalogEntryId);
    }

    @Test
    void backfillUnifiedAdultAssessmentsRebuildsAssessmentAndPublishesSearchSync() {
        UUID firstCatalogEntryId = UUID.randomUUID();
        UUID secondCatalogEntryId = UUID.randomUUID();
        when(repository.findAdultAssessmentBackfillIds(20))
                .thenReturn(List.of(firstCatalogEntryId, secondCatalogEntryId));
        when(repository.rebuildUnifiedAdultAssessments(List.of(firstCatalogEntryId, secondCatalogEntryId)))
                .thenReturn(List.of(firstCatalogEntryId, secondCatalogEntryId));

        var result = service.backfillUnifiedAdultAssessments(20);

        assertEquals("aggregation-adult-assessment-backfill", result.taskName());
        assertEquals(2, result.candidates());
        assertEquals(2, result.processed());
        assertEquals(List.of(firstCatalogEntryId, secondCatalogEntryId), result.entityIds());
        verify(repository).rebuildUnifiedAdultAssessments(List.of(firstCatalogEntryId, secondCatalogEntryId));
        verify(publisher).publishUnifiedIndex(firstCatalogEntryId);
        verify(publisher).publishUnifiedIndex(secondCatalogEntryId);
    }

    @Test
    void backfillUnifiedAdultAssessmentsCanSkipSearchSyncWhenFullReindexWillFollow() {
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.findAdultAssessmentBackfillIds(20)).thenReturn(List.of(catalogEntryId));
        when(repository.rebuildUnifiedAdultAssessments(List.of(catalogEntryId))).thenReturn(List.of(catalogEntryId));

        var result = service.backfillUnifiedAdultAssessments(20, false);

        assertEquals("aggregation-adult-assessment-backfill", result.taskName());
        assertEquals(1, result.candidates());
        assertEquals(1, result.processed());
        assertEquals(List.of(catalogEntryId), result.entityIds());
        verify(repository).rebuildUnifiedAdultAssessments(List.of(catalogEntryId));
        verify(publisher, never()).publishUnifiedIndex(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void manuallyMergesCatalogEntriesAndPublishesSearchSync() {
        UUID rootCatalogEntryId = UUID.randomUUID();
        UUID victimCatalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID coverImageId = UUID.randomUUID();
        when(aggregationPipelineRuntime.run(rootCatalogEntryId))
                .thenReturn(pipelineExecution(rootCatalogEntryId, List.of(coverImageId)));
        when(repository.findRawIdsForUnified(rootCatalogEntryId)).thenReturn(List.of(sourceRecordId));

        ManualUnifiedMergeResponse result =
                service.mergeCatalogEntries(List.of(rootCatalogEntryId, victimCatalogEntryId));

        assertEquals("MERGED", result.status());
        assertEquals(rootCatalogEntryId, result.rootCatalogEntryId());
        assertEquals(List.of(victimCatalogEntryId), result.victimCatalogEntryIds());
        assertEquals(List.of(sourceRecordId), result.sourceRecordIds());
        InOrder ordered = inOrder(repository, aggregationPipelineRuntime, publisher, storageCommandPublisher);
        ordered.verify(repository).mergeDuplicateCatalogEntries(rootCatalogEntryId, List.of(victimCatalogEntryId));
        ordered.verify(aggregationPipelineRuntime).run(rootCatalogEntryId);
        ordered.verify(repository).findRawIdsForUnified(rootCatalogEntryId);
        ordered.verify(repository).markUnifiedSynced(rootCatalogEntryId);
        ordered.verify(publisher).publishUnifiedIndex(rootCatalogEntryId);
        ordered.verify(publisher).publishRawIndex(sourceRecordId);
        ordered.verify(publisher).publishUnifiedDelete(victimCatalogEntryId);
        ordered.verify(storageCommandPublisher).enqueueCoverR2Sync(coverImageId);
    }

    @Test
    void manualMergeMarksRootDirtyWhenPipelineFails() {
        UUID rootCatalogEntryId = UUID.randomUUID();
        UUID victimCatalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        when(aggregationPipelineRuntime.run(rootCatalogEntryId)).thenReturn(pipelineFailure(rootCatalogEntryId));
        when(repository.findRawIdsForUnified(rootCatalogEntryId)).thenReturn(List.of(sourceRecordId));

        ManualUnifiedMergeResponse result =
                service.mergeCatalogEntries(List.of(rootCatalogEntryId, victimCatalogEntryId));

        assertEquals("PIPELINE_FAILED", result.status());
        verify(repository).markUnifiedDirty(rootCatalogEntryId);
        verify(repository, never()).markUnifiedSynced(rootCatalogEntryId);
        verify(publisher).publishUnifiedDelete(victimCatalogEntryId);
        verify(publisher, never()).publishUnifiedIndex(rootCatalogEntryId);
        verify(publisher, never()).publishRawIndex(sourceRecordId);
    }

    @Test
    void reusesExistingCatalogEntryWhenSimilarTitleMatchesWithoutExternalIds() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = readySourceRecord(sourceRecordId, null);

        when(repository.findSourceRecord(sourceRecordId)).thenReturn(Optional.of(sourceRecord));
        when(repository.findExistingBinding(sourceRecordId)).thenReturn(Optional.empty());
        when(repository.findMatchPlan(sourceRecord)).thenReturn(AggregationMatchPlan.rootOnly(catalogEntryId));
        when(aggregationPipelineRuntime.run(catalogEntryId))
                .thenReturn(pipelineExecution(catalogEntryId, List.of()));

        AggregationResult result = service.aggregateRuntimeWork(sourceRecordId);

        assertEquals("BOUND", result.status());
        verify(repository, never()).createCatalogEntry(sourceRecord);
        verify(repository).upsertUnifiedFields(catalogEntryId, sourceRecord);
        verify(repository).bindRawToUnified(sourceRecordId, catalogEntryId);
        verify(aggregationPipelineRuntime).run(catalogEntryId);
        verify(repository).markBound(sourceRecordId);
        verify(publisher).publishUnifiedIndex(catalogEntryId);
        verify(publisher).publishRawIndex(sourceRecordId);
    }

    @Test
    void automaticMergeSwitchPreventsAllDestructiveWrites() {
        UUID rawId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = readySourceRecord(rawId);
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.rootWithVictims(rootId, List.of(victimId)));
        var disabledPolicy = new AggregationSafetyPolicy(false, "douban:123");
        var disabledService = new AggregationService(repository, publisher, storageCommandPublisher, sourceRecordGraphClusterer,
                aggregationPipelineRuntime, matchKeyLock, workPublisher, transactionManager, matchingStrategy, disabledPolicy);

        AggregationResult result = disabledService.aggregateRuntimeWork(rawId);

        assertEquals("MERGE_AMBIGUOUS", result.status());
        verify(repository, never()).mergeDuplicateCatalogEntries(any(), any());
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(repository, never()).bindRawToUnified(any(), any());
        verifyNoInteractions(aggregationPipelineRuntime);
    }

    @Test
    void conflictingFinalUnifiedSetSplitsClusterAndPreservesExistingBindings() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID firstUnified = UUID.randomUUID();
        UUID secondUnified = UUID.randomUUID();
        SourceRecordAggregationRecord first = readySourceRecord(firstId, null);
        SourceRecordAggregationRecord second = readySourceRecord(secondId, null);
        List<SourceRecordAggregationRecord> raws = List.of(first, second);
        var cluster = new SourceRecordAggregationCluster(first, raws);
        List<CatalogEntryAggregationCandidate> candidates = List.of(
                unifiedCandidateWithId(firstUnified, "9001"), unifiedCandidateWithId(secondUnified, "9002"));
        when(repository.findSourceRecords(List.of(firstId, secondId))).thenReturn(raws);
        when(repository.findSourceRecord(firstId)).thenReturn(Optional.of(first));
        when(repository.findSourceRecord(secondId)).thenReturn(Optional.of(second));
        when(repository.findContextUnifiedCandidates(raws)).thenReturn(List.of());
        when(sourceRecordGraphClusterer.cluster(raws, List.of())).thenReturn(List.of(cluster));
        when(repository.findExistingBinding(firstId)).thenReturn(Optional.of(firstUnified));
        when(repository.findExistingBinding(secondId)).thenReturn(Optional.of(secondUnified));
        when(repository.findMatchPlan(first)).thenReturn(AggregationMatchPlan.rootOnly(firstUnified));
        when(repository.findMatchPlan(second)).thenReturn(AggregationMatchPlan.rootOnly(secondUnified));
        when(repository.findUnifiedCandidatesByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return candidates.stream().filter(candidate -> ids.contains(candidate.id())).toList();
        });
        when(aggregationPipelineRuntime.run(firstUnified)).thenReturn(pipelineExecution(firstUnified, List.of()));
        when(aggregationPipelineRuntime.run(secondUnified)).thenReturn(pipelineExecution(secondUnified, List.of()));

        List<AggregationResult> results = service.aggregateRuntimeWorkBatch(List.of(firstId, secondId));

        assertEquals(List.of("BOUND", "BOUND"), results.stream().map(AggregationResult::status).toList());
        verify(repository, never()).mergeDuplicateCatalogEntries(any(), any());
        verify(repository).bindRawToUnified(firstId, firstUnified);
        verify(repository).bindRawToUnified(secondId, secondUnified);
        verify(repository, times(2)).findSourceRecords(List.of(firstId, secondId));
    }

    @Test
    void existingBindingWithChangedIdentityIsNotRefreshed() {
        UUID rawId = UUID.randomUUID();
        UUID unifiedId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = readySourceRecord(rawId, "9002");
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(rawId)).thenReturn(Optional.of(unifiedId));
        when(repository.findUnifiedCandidatesByIds(anyCollection()))
                .thenReturn(List.of(unifiedCandidateWithId(unifiedId, "9001")));

        AggregationResult result = service.aggregateRuntimeWork(rawId);

        assertEquals("IDENTITY_CONFLICT", result.status());
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(repository, never()).bindRawToUnified(any(), any());
        verifyNoInteractions(aggregationPipelineRuntime);
    }

    @Test
    void changedRawSnapshotIsDeferredBeforeAnyBusinessWrite() {
        UUID rawId = UUID.randomUUID();
        SourceRecordAggregationRecord original = readySourceRecord(rawId);
        SourceRecordAggregationRecord changed = readySourceRecord(rawId, "A corrected title", "Codex Alias", "1234567");
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(original), Optional.of(changed));

        AggregationResult result = service.aggregateRuntimeWork(rawId);

        assertEquals("SOURCE_CHANGED", result.status());
        verify(repository, times(2)).findSourceRecord(rawId);
        verify(repository, never()).upsertUnifiedFields(any(), any());
        verify(repository, never()).bindRawToUnified(any(), any());
        verify(repository, never()).markBound(any());
        verifyNoInteractions(aggregationPipelineRuntime);
    }

    @Test
    void alreadyBoundDuplicateCompletesWithoutPipelineOrStatusDemotion() {
        UUID rawId = UUID.randomUUID();
        UUID unifiedId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = withAggregationStatus(readySourceRecord(rawId), "BOUND");
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(rawId)).thenReturn(Optional.of(unifiedId));

        AggregationResult result = service.aggregateRuntimeWork(rawId);

        assertEquals("ALREADY_BOUND", result.status());
        verify(repository, never()).markPending(any());
        verifyNoInteractions(aggregationPipelineRuntime);
    }

    @Test
    void boundRawWithPendingAdultAssessmentStillResumesDeferredEvaluation() {
        UUID rawId = UUID.randomUUID();
        UUID unifiedId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = withAggregationStatus(readySourceRecord(rawId), "BOUND");
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findExistingBinding(rawId)).thenReturn(Optional.of(unifiedId));
        when(repository.isAdultAssessmentPending(unifiedId)).thenReturn(true);
        var snapshot = new AdultAssessmentSnapshot(unifiedId, "v1", "sources1", null);
        var assessment = com.prodigalgal.ircs.common.adult.AdultAssessment.safe("test");
        when(repository.captureAdultAssessment(unifiedId)).thenReturn(snapshot);
        when(repository.evaluateAdultAssessments(List.of(snapshot))).thenReturn(java.util.Map.of(unifiedId, assessment));
        when(repository.applyAdultAssessment(snapshot, assessment)).thenReturn(true);

        AggregationResult result = service.aggregateRuntimeWork(rawId);

        assertEquals("BOUND", result.status());
        verify(repository).evaluateAdultAssessments(List.of(snapshot));
        verify(repository).applyAdultAssessment(snapshot, assessment);
        verify(repository, never()).markPending(any());
        verifyNoInteractions(aggregationPipelineRuntime);
    }

    @Test
    void ambiguousCandidatePlanDoesNotCreateAnotherUnified() {
        UUID rawId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = readySourceRecord(rawId);
        when(repository.findSourceRecord(rawId)).thenReturn(Optional.of(raw));
        when(repository.findMatchPlan(raw)).thenReturn(AggregationMatchPlan.ambiguous());

        assertEquals("MERGE_AMBIGUOUS", service.aggregateRuntimeWork(rawId).status());
        verify(repository, never()).createCatalogEntry(any());
        verifyNoInteractions(aggregationPipelineRuntime);
    }

    @Test
    void contextRecallAloneDoesNotAuthorizeBindingOrMergingExistingWorks() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();
        UUID createdId = UUID.randomUUID();
        SourceRecordAggregationRecord first = readySourceRecord(firstId, null);
        SourceRecordAggregationRecord second = readySourceRecord(secondId, null);
        List<SourceRecordAggregationRecord> raws = List.of(first, second);
        CatalogEntryAggregationCandidate context = unifiedCandidate(contextId, "Codex Aggregation", "2026");
        var cluster = new SourceRecordAggregationCluster(first, raws, List.of(contextId));
        when(repository.findSourceRecords(List.of(firstId, secondId))).thenReturn(raws);
        when(repository.findContextUnifiedCandidates(raws)).thenReturn(List.of(context));
        when(sourceRecordGraphClusterer.cluster(raws, List.of(context))).thenReturn(List.of(cluster));
        when(repository.findMatchPlan(first)).thenReturn(AggregationMatchPlan.none());
        when(repository.findMatchPlan(second)).thenReturn(AggregationMatchPlan.none());
        when(repository.createCatalogEntry(first)).thenReturn(createdId);
        when(aggregationPipelineRuntime.run(createdId)).thenReturn(pipelineExecution(createdId, List.of()));

        List<AggregationResult> results = service.aggregateRuntimeWorkBatch(List.of(firstId, secondId));

        assertEquals(List.of("BOUND", "BOUND"), results.stream().map(AggregationResult::status).toList());
        verify(repository).bindRawToUnified(firstId, createdId);
        verify(repository).bindRawToUnified(secondId, createdId);
        verify(repository, never()).mergeDuplicateCatalogEntries(any(), any());
        InOrder order = inOrder(repository, sourceRecordGraphClusterer, transactionManager);
        order.verify(repository).findContextUnifiedCandidates(raws);
        order.verify(sourceRecordGraphClusterer).cluster(raws, List.of(context));
        order.verify(transactionManager).getTransaction(any());
        order.verify(repository).lockSourceRecords(List.of(firstId, secondId));
    }

    private SourceRecordAggregationRecord withAggregationStatus(SourceRecordAggregationRecord raw, String status) {
        return new SourceRecordAggregationRecord(raw.id(), raw.title(), raw.aliasTitle(), raw.description(), raw.year(),
                raw.score(), raw.publishedAt(), raw.totalEpisodes(), raw.duration(), raw.remarks(), raw.subtitle(),
                raw.season(), raw.categoryName(), raw.categoryCode(), raw.actorNames(), raw.directorNames(), raw.areaNames(),
                raw.doubanId(), raw.tmdbId(), raw.imdbId(), raw.rottenTomatoesId(), raw.normalizationStatus(),
                raw.enrichmentStatus(), status);
    }

    private CatalogEntryAggregationCandidate unifiedCandidateWithId(UUID id, String doubanId) {
        return new CatalogEntryAggregationCandidate(id, "Codex Aggregation", null, null, "2026", "12", "45m",
                null, "电影", doubanId, null, null, null, null);
    }

    @Test
    void committedAdultRestrictionIsPublishedEvenWhenALaterClusterRollsBack() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID firstUnified = UUID.randomUUID();
        UUID secondUnified = UUID.randomUUID();
        SourceRecordAggregationRecord first = readySourceRecord(firstId);
        SourceRecordAggregationRecord second = readySourceRecord(secondId);
        when(repository.findSourceRecords(List.of(firstId, secondId))).thenReturn(List.of(first, second));
        when(repository.findContextUnifiedCandidates(List.of(first, second))).thenReturn(List.of());
        when(sourceRecordGraphClusterer.cluster(List.of(first, second), List.of())).thenReturn(List.of(
                new SourceRecordAggregationCluster(first, List.of(first)),
                new SourceRecordAggregationCluster(second, List.of(second))));
        when(repository.findSourceRecord(firstId)).thenReturn(Optional.of(first));
        when(repository.findSourceRecord(secondId)).thenReturn(Optional.of(second));
        when(repository.findExistingBinding(firstId)).thenReturn(Optional.of(firstUnified));
        when(repository.findExistingBinding(secondId)).thenReturn(Optional.of(secondUnified));
        when(aggregationPipelineRuntime.run(firstUnified)).thenAnswer(call -> {
            DeferredAdultAssessments.register(firstUnified);
            DeferredAdultAssessments.registerRestricted(firstUnified);
            return pipelineExecution(firstUnified, List.of());
        });
        when(aggregationPipelineRuntime.run(secondUnified)).thenThrow(new IllegalStateException("later cluster failed"));
        var search = org.mockito.Mockito.mock(com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher.class);
        var actual = new AggregationService(repository, new AggregationSearchSyncPublisher(search), storageCommandPublisher,
                sourceRecordGraphClusterer, aggregationPipelineRuntime, matchKeyLock, workPublisher, transactionManager,
                matchingStrategy, safetyPolicy);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> actual.aggregateRuntimeWorkBatch(List.of(firstId, secondId)));
        verify(search).enqueue(firstUnified, com.prodigalgal.ircs.contracts.search.SearchEntityType.CATALOG_ENTRY,
                com.prodigalgal.ircs.contracts.search.SyncOperation.INDEX);
        verify(repository, never()).evaluateAdultAssessments(any());
    }

    @Test
    void rejectsEmptyTransactionSynchronizationBeforePreparingAWorkBatch() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> service.aggregateRuntimeWorkBatch(List.of(UUID.randomUUID())));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        verifyNoInteractions(repository);
    }

    private SourceRecordAggregationRecord readySourceRecord(UUID sourceRecordId) {
        return readySourceRecord(sourceRecordId, "1234567");
    }

    private AggregationPipelineExecution pipelineExecution(UUID catalogEntryId, List<UUID> coverR2SyncImageIds) {
        return new AggregationPipelineExecution(catalogEntryId, coverR2SyncImageIds, List.of());
    }

    private AggregationPipelineExecution pipelineFailure(UUID catalogEntryId) {
        return new AggregationPipelineExecution(
                catalogEntryId,
                List.of(UUID.randomUUID()),
                List.of(new AggregationPipelineStageFailure(
                        AggregationPipelineStage.METADATA,
                        "MetadataHandler",
                        30,
                        "metadata failed")));
    }

    private SourceRecordAggregationRecord readySourceRecord(UUID sourceRecordId, String doubanId) {
        return readySourceRecord(sourceRecordId, "Codex Aggregation", "Codex Alias", doubanId);
    }

    private SourceRecordAggregationRecord readySourceRecord(
            UUID sourceRecordId,
            String title,
            String aliasTitle,
            String doubanId) {
        return new SourceRecordAggregationRecord(
                sourceRecordId,
                title,
                aliasTitle,
                "Aggregation description",
                "2026",
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                "Ready",
                null,
                null,
                "电影",
                doubanId,
                null,
                null,
                null,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private CatalogEntryAggregationCandidate unifiedCandidate(UUID id, String title, String year) {
        return new CatalogEntryAggregationCandidate(
                id,
                title,
                null,
                null,
                year,
                "12",
                "45m",
                null,
                "电影",
                "1234567",
                null,
                null,
                null,
                null);
    }
}
