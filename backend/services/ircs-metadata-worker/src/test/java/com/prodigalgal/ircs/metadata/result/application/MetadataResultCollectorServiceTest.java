package com.prodigalgal.ircs.metadata.result.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.config.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.dispatch.messaging.MetadataEnrichPublisher;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository.ProviderCompletion;
import com.prodigalgal.ircs.metadata.result.domain.RawVideoMetadataRecord;
import com.prodigalgal.ircs.metadata.result.dto.MetadataResultProcessingResult;
import com.prodigalgal.ircs.metadata.result.infrastructure.RawVideoMetadataRepository;
import com.prodigalgal.ircs.metadata.result.messaging.RawSearchSyncPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetadataResultCollectorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RawVideoMetadataRepository repository;

    @Mock
    private RawSearchSyncPublisher searchSyncPublisher;

    @Mock
    private MetadataPipelineRunRepository pipelineRunRepository;

    @Mock
    private MetadataEnrichPublisher metadataEnrichPublisher;

    @Mock
    private AggregationWorkPublisher aggregationWorkPublisher;

    @Mock
    private MetadataConfigValues configValues;

    @Test
    void appliesSuccessfulMetadataAndPublishesSearchSync() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("101");
        metadata.setImdbId("tt0000001");
        metadata.setDescription("description");
        metadata.setYear("2026");
        metadata.setScore(new BigDecimal("8.5"));
        metadata.setOriginalTitle("Original Title");

        MetadataResultProcessingResult result = newService(completedSuccess()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB)
                .success(true)
                .metadata(metadata)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("SUCCESS", result.enrichmentStatus());
        assertEquals("101", record.getValue().getTmdbId());
        assertEquals("tt0000001", record.getValue().getImdbId());
        assertEquals("description", record.getValue().getDescription());
        assertEquals("2026", record.getValue().getYear());
        assertEquals(new BigDecimal("8.5"), record.getValue().getScore());
        assertEquals("Original Title", record.getValue().getAliasTitle());
        assertEquals("PENDING", record.getValue().getAggregationStatus());
        verify(searchSyncPublisher).publishIndex(id);
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "metadata-finalized");
    }

    @Test
    void treatsRottenTomatoesIdAsAuthoritativeSuccess() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setRottenTomatoesId("matrix");

        MetadataResultProcessingResult result = newService(completedSuccess()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.ROTTEN_TOMATOES)
                .success(true)
                .metadata(metadata)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("SUCCESS", result.enrichmentStatus());
        assertEquals("matrix", record.getValue().getRottenTomatoesId());
        assertEquals("PENDING", record.getValue().getAggregationStatus());
        verify(searchSyncPublisher).publishIndex(id);
    }

    @Test
    void mergesEnrichmentRichFieldsIntoRawMetadataForNormalization() throws Exception {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setRawMetadata("""
                {
                  "genreNames": ["动作"],
                  "language": "国语",
                  "area": "中国大陆",
                  "actorNames": ["演员甲"],
                  "posterUrl": "https://img.example.invalid/existing.jpg",
                  "sourceNote": "keep"
                }
                """);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);

        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("102");
        metadata.setArea("美国");
        metadata.setLanguage("英语");
        metadata.addGenre("科幻");
        metadata.addActor("演员乙");
        metadata.addDirector("导演甲");
        metadata.setPosterUrl("https://img.example.invalid/provider.jpg");
        metadata.setBackdropUrl("https://img.example.invalid/backdrop.jpg");
        metadata.setImageSource(ProviderType.TMDB);

        MetadataResultProcessingResult result = newService(completedSuccess()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB)
                .success(true)
                .metadata(metadata)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        JsonNode rawMetadata = objectMapper.readTree(record.getValue().getRawMetadata());
        assertEquals("SUCCESS", result.enrichmentStatus());
        assertEquals("中国大陆, 美国", rawMetadata.path("area").asText());
        assertEquals("国语/英语", rawMetadata.path("language").asText());
        assertArrayContains(rawMetadata.path("genreNames"), "动作", "科幻");
        assertArrayContains(rawMetadata.path("actorNames"), "演员甲", "演员乙");
        assertArrayContains(rawMetadata.path("directorNames"), "导演甲");
        assertEquals("https://img.example.invalid/existing.jpg", rawMetadata.path("posterUrl").asText());
        assertEquals("https://img.example.invalid/provider.jpg", rawMetadata.path("coverImageUrl").asText());
        assertEquals("https://img.example.invalid/backdrop.jpg", rawMetadata.path("backdropUrl").asText());
        assertEquals("TMDB", rawMetadata.path("imageSource").asText());
        assertEquals("keep", rawMetadata.path("sourceNote").asText());
        assertEquals("PENDING", record.getValue().getAggregationStatus());
        verify(searchSyncPublisher).publishIndex(id);
    }

    @Test
    void honorsLockedFields() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setDescription("locked description");
        existing.setYear("2025");
        existing.setLockedFields(Set.of("description", "year"));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("103");
        metadata.setDescription("new description");
        metadata.setYear("2026");

        newService(completedSuccess()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB)
                .success(true)
                .metadata(metadata)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("locked description", record.getValue().getDescription());
        assertEquals("2025", record.getValue().getYear());
    }

    @Test
    void retryableFailureMovesToFailedAndIncrementsRetryCount() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setEnrichmentRetryCount(1);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        when(configValues.retryDelay()).thenReturn(Duration.ofMinutes(5));

        MetadataResultProcessingResult result = newService(completedRetryableFailure()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.DOUBAN)
                .success(false)
                .retryable(true)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("FAILED", result.enrichmentStatus());
        assertEquals(2, record.getValue().getEnrichmentRetryCount());
        verify(metadataEnrichPublisher).publish(id, "hash-v1", Duration.ofMinutes(5));
        org.mockito.Mockito.verifyNoInteractions(aggregationWorkPublisher);
    }

    @Test
    void aKnownIdStillRetriesWhenDetailFetchHasNoSuccessfulProvider() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setTmdbId("106");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        when(configValues.retryDelay()).thenReturn(Duration.ofMinutes(5));
        var failure = EnrichmentResultMsg.builder().videoId(id).pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB).success(false).retryable(true).build();

        var result = newService(completedRetryableFailure()).process(failure, 0);

        assertEquals("FAILED", result.enrichmentStatus());
        assertEquals("106", existing.getTmdbId());
        assertEquals(1, existing.getEnrichmentRetryCount());
        verify(metadataEnrichPublisher).publish(id, "hash-v1", Duration.ofMinutes(5));
    }

    @Test
    void successfulTmdbDoesNotReleaseAggregationWhileOtherAuthorityRetries() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setTmdbId("106");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        when(configValues.retryDelay()).thenReturn(Duration.ofMinutes(5));
        var completion = ProviderCompletion.tracked(true, 3, 3, 1, 2, 1, 1);

        var result = newService(completion).process(EnrichmentResultMsg.builder()
                .videoId(id).pipelineVersion("hash-v1").providerType(ProviderType.IMDB)
                .success(false).retryable(true).build(), 0);

        assertEquals("FAILED", result.enrichmentStatus());
        verify(metadataEnrichPublisher).publish(id, "hash-v1", Duration.ofMinutes(5));
        org.mockito.Mockito.verifyNoInteractions(aggregationWorkPublisher);
    }

    @Test
    void terminalFailureMovesToPermanentFailureAndPublishesSearchSync() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);

        MetadataResultProcessingResult result = newService(completedPermanentFailure()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.DOUBAN)
                .success(false)
                .retryable(false)
                .errorCode("PROVIDER_NOT_FOUND")
                .errorMessage("Provider implementation not found")
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("PERMANENT_FAILURE", result.enrichmentStatus());
        assertEquals("PERMANENT_FAILURE", record.getValue().getEnrichmentStatus());
        verify(searchSyncPublisher).publishIndex(id);
    }

    @Test
    void stalePipelineVersionDoesNotSaveMetadata() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));

        MetadataResultProcessingResult result = newService().process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("old-hash")
                .providerType(ProviderType.TMDB)
                .success(true)
                .metadata(new EnrichedMetadataDTO())
                .build(), 0);

        assertEquals("STALE", result.enrichmentStatus());
        verify(repository, never()).save(any());
        verify(repository, never()).findByIdForUpdate(id);
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void missingVideoDoesNotPublishSearchSync() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        MetadataResultProcessingResult result = newService().process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .success(true)
                .build(), 0);

        assertEquals(false, result.videoFound());
        verify(repository, never()).save(any());
        verify(repository, never()).findByIdForUpdate(id);
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void firstProviderResultWithRemainingPendingOnlySavesMetadata() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("104");
        metadata.addGenre("动作");

        MetadataResultProcessingResult result = newService(pendingCompletion()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB)
                .success(true)
                .metadata(metadata)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("PENDING", result.enrichmentStatus());
        assertEquals("104", record.getValue().getTmdbId());
        assertEquals("PENDING", record.getValue().getEnrichmentStatus());
        assertTrue(record.getValue().getRawMetadata().contains("动作"));
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void untrackedProviderResultDoesNotFinalizeOrSaveMetadata() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("105");

        MetadataResultProcessingResult result = newService(ProviderCompletion.untracked()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB)
                .success(true)
                .metadata(metadata)
                .build(), 0);

        assertEquals("PENDING", result.enrichmentStatus());
        verify(repository, never()).save(any());
        verify(repository, never()).findByIdForUpdate(id);
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void finalProviderResultSettlesStatusClearsPendingAndPublishesSearchSync() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setTmdbId("106");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);

        MetadataResultProcessingResult result = newService(completedPermanentFailure()).process(EnrichmentResultMsg.builder()
                .videoId(id)
                .pipelineVersion("hash-v1")
                .providerType(ProviderType.DOUBAN)
                .success(false)
                .retryable(false)
                .build(), 0);

        ArgumentCaptor<RawVideoMetadataRecord> record = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository).save(record.capture());
        assertEquals("SUCCESS", result.enrichmentStatus());
        assertEquals("SUCCESS", record.getValue().getEnrichmentStatus());
        verify(pipelineRunRepository).markRunFinished(id, "hash-v1", "SUCCESS");
        verify(searchSyncPublisher).publishIndex(id);
    }

    @Test
    void lastProviderFailureStillRefreshesBindingWhenEarlierProviderSavedFacts() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setTmdbId("106");
        existing.setDescription("此前核实的 TMDB 简介");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);

        var completedWithOneSuccess = ProviderCompletion.tracked(true, 2, 2, 1, 1, 0, 1);
        var result = newService(completedWithOneSuccess).process(EnrichmentResultMsg.builder()
                .videoId(id).pipelineVersion("hash-v1").providerType(ProviderType.DOUBAN)
                .success(false).retryable(false).build(), 0);

        assertEquals("SUCCESS", result.enrichmentStatus());
        assertEquals("PENDING", existing.getAggregationStatus());
        assertEquals("此前核实的 TMDB 简介", existing.getDescription());
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "metadata-finalized");
    }

    @Test
    void lateOldAttemptDoesNotSaveItsMetadataIntoANewPendingRetry() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("107");
        MetadataResultCollectorService service = newService(ProviderCompletion.tracked(false, 1, 0, 0, 0, 0, 0));

        service.process(EnrichmentResultMsg.builder().videoId(id).pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB).success(true).metadata(metadata).build(), 0);

        verify(repository, never()).save(any());
        verify(repository, never()).findByIdForUpdate(id);
        verify(searchSyncPublisher, never()).publishIndex(id);
        verify(pipelineRunRepository, never()).markRunFinished(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "null", "tmdb-not-an-id"})
    void rejectsSuccessWithInvalidOwnProviderIdWithoutApplyingRichFieldsOrRetrying(String providerId) throws Exception {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setTmdbId("0");
        existing.setRawMetadata("{\"doubanId\":0,\"sourcePayload\":{\"vod_douban_id\":0},\"sourceNote\":\"keep\"}");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId(providerId);
        metadata.setDoubanId("123");
        metadata.setDescription("must not be applied");
        metadata.addActor("must not be applied");
        metadata.setArea("美国");

        var result = newService(completedPermanentFailure()).process(message(id, ProviderType.TMDB, metadata), 0);

        assertEquals("PERMANENT_FAILURE", result.enrichmentStatus());
        assertNull(existing.getTmdbId());
        assertNull(existing.getDoubanId());
        assertNull(existing.getDescription());
        JsonNode raw = objectMapper.readTree(existing.getRawMetadata());
        assertEquals(0, raw.path("doubanId").asInt());
        assertEquals(0, raw.path("sourcePayload").path("vod_douban_id").asInt());
        assertTrue(raw.path("actorNames").isMissingNode());
        verify(pipelineRunRepository).completeProvider(eq(id), eq("hash-v1"), eq(ProviderType.TMDB),
                eq(false), eq(false), eq("INVALID_PROVIDER_ID"), any(), eq(0));
        verify(metadataEnrichPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void placeholderZeroCannotFinalizeAsSuccessButANewValidProviderCanReplaceIt() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setTmdbId("0");
        assertEquals(false, existing.hasAuthoritativeId());
        assertEquals(false, existing.hasProviderId(ProviderType.TMDB));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("00101");

        var result = newService(completedSuccess()).process(message(id, ProviderType.TMDB, metadata), 0);

        assertEquals("SUCCESS", result.enrichmentStatus());
        assertEquals("101", existing.getTmdbId());
    }

    @Test
    void twoProvidersMergeIntoFreshLockedRowInsteadOfOverwritingEachOthersResults() throws Exception {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord snapshot = record(id);
        RawVideoMetadataRecord firstCurrent = record(id);
        RawVideoMetadataRecord secondCurrent = record(id);
        secondCurrent.setTmdbId("101");
        secondCurrent.setRawMetadata("{\"actorNames\":[\"罗予彤\"],\"sourcePayload\":{\"doubanId\":0}}");
        when(repository.findById(id)).thenReturn(Optional.of(snapshot));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(firstCurrent), Optional.of(secondCurrent));
        when(repository.save(any())).thenReturn(1);
        when(pipelineRunRepository.completeProvider(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(pendingCompletion(),
                        ProviderCompletion.tracked(true, 2, 2, 2, 0, 0, 0));
        EnrichedMetadataDTO first = new EnrichedMetadataDTO();
        first.setTmdbId("101");
        first.addActor("罗予彤");
        EnrichedMetadataDTO second = new EnrichedMetadataDTO();
        second.setDoubanId("202");
        second.addActor("孙乾钊");
        MetadataResultCollectorService service = newService();

        assertEquals("PENDING", service.process(message(id, ProviderType.TMDB, first), 0).enrichmentStatus());
        assertEquals("SUCCESS", service.process(message(id, ProviderType.DOUBAN, second), 0).enrichmentStatus());

        ArgumentCaptor<RawVideoMetadataRecord> saved = ArgumentCaptor.forClass(RawVideoMetadataRecord.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        RawVideoMetadataRecord merged = saved.getAllValues().get(1);
        assertEquals("101", merged.getTmdbId());
        assertEquals("202", merged.getDoubanId());
        assertArrayContains(objectMapper.readTree(merged.getRawMetadata()).path("actorNames"), "罗予彤", "孙乾钊");
        assertEquals(0, objectMapper.readTree(merged.getRawMetadata()).path("sourcePayload").path("doubanId").asInt());
        var order = org.mockito.Mockito.inOrder(repository, pipelineRunRepository);
        order.verify(repository).findById(id);
        order.verify(pipelineRunRepository).completeProvider(eq(id), eq("hash-v1"), eq(ProviderType.TMDB),
                eq(true), eq(false), any(), any(), eq(0));
        order.verify(repository).findByIdForUpdate(id);
        order.verify(repository).save(firstCurrent);
    }

    @Test
    void changedHashDiscoveredAfterPipelineLockDoesNotWriteOrPublish() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord newer = record(id);
        newer.setDataHash("hash-v2");
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(newer));
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("101");

        var result = newService(completedSuccess()).process(message(id, ProviderType.TMDB, metadata), 0);

        assertEquals("STALE", result.enrichmentStatus());
        verify(repository, never()).save(any());
        verify(pipelineRunRepository, never()).markRunFinished(any(), any(), any());
        verify(searchSyncPublisher, never()).publishIndex(any());
        verify(aggregationWorkPublisher, never()).enqueue(any(), any(), any());
    }

    @Test
    void failedProjectionCasAbortsWithoutPublishingDownstreamSuccess() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(0);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("101");
        MetadataResultCollectorService service = newService(completedSuccess());

        assertThrows(IllegalStateException.class, () -> service.process(message(id, ProviderType.TMDB, metadata), 0));

        verify(pipelineRunRepository, never()).markRunFinished(any(), any(), any());
        verify(searchSyncPublisher, never()).publishIndex(any());
        verify(aggregationWorkPublisher, never()).enqueue(any(), any(), any());
    }

    @Test
    void protectsAuthorityAliasPeopleAndClassificationLocksAcrossProviderMerge() throws Exception {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord existing = record(id);
        existing.setDoubanId("123");
        existing.setTmdbId("456");
        existing.setImdbId("tt1234567");
        existing.setRottenTomatoesId("manual_slug");
        existing.setAliasTitle("人工别名");
        existing.setLockedFields(Set.of("doubanId", "tmdbId", "imdbId", "rottenTomatoesId", "aliasTitle",
                "actors", "directors", "area", "language", "genres"));
        existing.setRawMetadata("{\"actorNames\":[\"人工演员\"],\"directorNames\":[\"人工导演\"],\"area\":\"中国大陆\",\"language\":\"国语\",\"genreNames\":[\"动作\"]}");
        JsonNode before = objectMapper.readTree(existing.getRawMetadata());
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(1);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setDoubanId("999");
        metadata.setTmdbId("999");
        metadata.setImdbId("tt9999999");
        metadata.setRottenTomatoesId("new_slug");
        metadata.setOriginalTitle("Provider Alias");
        metadata.addActor("新演员");
        metadata.addDirector("新导演");
        metadata.addGenre("科幻");
        metadata.setArea("美国");
        metadata.setLanguage("英语");

        newService(completedSuccess()).process(message(id, ProviderType.TMDB, metadata), 0);

        assertEquals("123", existing.getDoubanId());
        assertEquals("456", existing.getTmdbId());
        assertEquals("tt1234567", existing.getImdbId());
        assertEquals("manual_slug", existing.getRottenTomatoesId());
        assertEquals("人工别名", existing.getAliasTitle());
        assertEquals(before, objectMapper.readTree(existing.getRawMetadata()));
    }

    @Test
    void completedRunWithOnlyPlaceholderIdFinalizesFailureUsingPipelineThenRawLockOrder() {
        UUID id = UUID.randomUUID();
        RawVideoMetadataRecord record = record(id);
        record.setTmdbId("0");
        when(pipelineRunRepository.findCompletionForUpdate(id, "hash-v1")).thenReturn(Optional.of(completedSuccess()));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(record));
        when(repository.updateEnrichmentStatusIfPending(id, "hash-v1", "PERMANENT_FAILURE", 0, false)).thenReturn(1);

        assertEquals("PERMANENT_FAILURE", newService().reconcileCompletedRun(id, "hash-v1").enrichmentStatus());

        var order = org.mockito.Mockito.inOrder(pipelineRunRepository, repository);
        order.verify(pipelineRunRepository).findCompletionForUpdate(id, "hash-v1");
        order.verify(repository).findByIdForUpdate(id);
        order.verify(repository).updateEnrichmentStatusIfPending(id, "hash-v1", "PERMANENT_FAILURE", 0, false);
        verify(repository, never()).findById(id);
        verify(repository, never()).save(any());
    }

    private EnrichmentResultMsg message(UUID id, ProviderType provider, EnrichedMetadataDTO metadata) {
        return EnrichmentResultMsg.builder().videoId(id).pipelineVersion("hash-v1").providerType(provider)
                .success(true).retryable(false).metadata(metadata).build();
    }

    private MetadataResultCollectorService newService(ProviderCompletion completion) {
        when(pipelineRunRepository.completeProvider(
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        any(),
                        any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(completion);
        return newService();
    }

    private MetadataResultCollectorService newService() {
        return new MetadataResultCollectorService(
                repository,
                searchSyncPublisher,
                pipelineRunRepository,
                metadataEnrichPublisher,
                aggregationWorkPublisher,
                configValues,
                objectMapper);
    }

    private ProviderCompletion completedSuccess() {
        return ProviderCompletion.tracked(true, 1, 1, 1, 0, 0, 0);
    }

    private ProviderCompletion completedRetryableFailure() {
        return ProviderCompletion.tracked(true, 1, 1, 0, 1, 1, 0);
    }

    private ProviderCompletion completedPermanentFailure() {
        return ProviderCompletion.tracked(true, 1, 1, 0, 1, 0, 1);
    }

    private ProviderCompletion pendingCompletion() {
        return ProviderCompletion.tracked(true, 2, 1, 1, 0, 0, 0);
    }

    private RawVideoMetadataRecord record(UUID id) {
        return new RawVideoMetadataRecord(
                id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{}",
                Set.of(),
                "PENDING",
                0,
                "BOUND",
                "hash-v1");
    }

    private void assertArrayContains(JsonNode node, String... values) {
        for (String value : values) {
            boolean found = false;
            for (JsonNode item : node) {
                found |= value.equals(item.asText());
            }
            assertTrue(found, "expected array to contain " + value + ": " + node);
        }
    }
}
