package com.prodigalgal.ircs.metadata.dispatch.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.common.pipeline.PipelineRuntimeWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.config.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.dispatch.domain.RawVideoEnrichmentRecord;
import com.prodigalgal.ircs.metadata.dispatch.dto.MetadataDispatchResult;
import com.prodigalgal.ircs.metadata.dispatch.infrastructure.RawVideoEnrichmentRepository;
import com.prodigalgal.ircs.metadata.dispatch.messaging.MetadataProviderTaskPublisher;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository;
import com.prodigalgal.ircs.metadata.result.messaging.RawSearchSyncPublisher;
import com.prodigalgal.ircs.metadata.result.application.MetadataResultCollectorService;
import com.prodigalgal.ircs.metadata.result.dto.MetadataResultProcessingResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetadataDispatchServiceTest {

    @Mock
    private RawVideoEnrichmentRepository repository;

    @Mock
    private MetadataPipelineRunRepository pipelineRunRepository;

    @Mock
    private MetadataProviderTaskPublisher providerTaskPublisher;

    @Mock
    private RawSearchSyncPublisher searchSyncPublisher;

    @Mock
    private AggregationWorkPublisher aggregationWorkPublisher;

    @Mock
    private MetadataConfigValues configValues;

    @Mock
    private MetadataResultCollectorService resultCollector;

    @Test
    void reconcilesCompletedProviderRunsWithoutRequestingTheProviderAgain() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));
        when(pipelineRunRepository.prepareDispatch(id, "hash-v1", List.of(ProviderType.TMDB))).thenReturn(List.of());
        when(resultCollector.reconcileCompletedRun(id, "hash-v1"))
                .thenReturn(new MetadataResultProcessingResult(id, true, "PERMANENT_FAILURE"));

        MetadataDispatchResult result = newService(true).dispatch(id, "hash-v1");

        assertEquals("PERMANENT_FAILURE", result.status());
        verify(resultCollector).reconcileCompletedRun(id, "hash-v1");
        org.mockito.Mockito.verifyNoInteractions(providerTaskPublisher);
    }

    @Test
    void incompleteDurableRunDoesNotPretendEnrichmentHasFinished() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));
        when(pipelineRunRepository.prepareDispatch(id, "hash-v1", List.of(ProviderType.TMDB))).thenReturn(List.of());
        when(resultCollector.reconcileCompletedRun(id, "hash-v1"))
                .thenReturn(new MetadataResultProcessingResult(id, true, "PENDING"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> newService(true).dispatch(id, "hash-v1"));
        org.mockito.Mockito.verifyNoInteractions(providerTaskPublisher);
    }

    @Test
    void terminalDispatchRetryRepublishesIdempotentDownstreamHandoffs() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord raw = record(id);
        raw.setEnrichmentStatus("SUCCESS");
        raw.setTmdbId("101");
        when(repository.findById(id)).thenReturn(Optional.of(raw));

        assertEquals("SUCCESS", newService(false).dispatch(id, "hash-v1").status());

        verify(searchSyncPublisher).publishIndex(id);
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "metadata-already-finalized");
        org.mockito.Mockito.verifyNoInteractions(providerTaskPublisher, pipelineRunRepository, resultCollector);
    }

    @AfterEach
    void tearDown() {
        org.mockito.Mockito.validateMockitoUsage();
    }

    @Test
    void finalizesImmediatelyWhenAuthoritativeIdExists() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("101");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.updateStatus(id, "hash-v1", "SUCCESS", 0)).thenReturn(1);

        MetadataDispatchResult result = newService(false).dispatch(id, "hash-v1");

        assertEquals("SUCCESS", result.status());
        verify(repository).updateStatus(id, "hash-v1", "SUCCESS", 0);
        verify(searchSyncPublisher).publishIndex(id);
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "metadata-immediate-finalized");
        verify(providerTaskPublisher, never()).publish(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void dispatchesEnabledMissingProvidersToValkeyTasks() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(pipelineRunRepository.prepareDispatch(id, "hash-v1", List.of(ProviderType.TMDB)))
                .thenReturn(List.of(ProviderType.TMDB));

        MetadataDispatchResult result = newService(true).dispatch(id, "hash-v1");

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.TMDB), result.providers());
        verify(providerTaskPublisher).publish(org.mockito.Mockito.eq(ProviderType.TMDB), org.mockito.Mockito.any());
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void stalePipelineVersionDoesNotFanOutProviderTasks() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(record(id)));

        MetadataDispatchResult result = newService(false).dispatch(id, "old-hash");

        assertEquals("STALE", result.status());
        verify(providerTaskPublisher, never()).publish(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void fanOutUsesProviderConfigAndPublishesProviderTasksThroughValkey() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(record));
        RuntimeWorkQueue workQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(workQueue, new ObjectMapper());
        when(configValues.doubanEnabled()).thenReturn(true);
        when(configValues.tmdbEnabled()).thenReturn(true);
        when(configValues.rottenTomatoesEnabled()).thenReturn(true);
        when(pipelineRunRepository.prepareDispatch(
                        id,
                        "hash-v1",
                        List.of(ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES)))
                .thenReturn(List.of(ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES));
        MetadataDispatchService service = new MetadataDispatchService(
                repository,
                configValues,
                pipelineRunRepository,
                publisher,
                searchSyncPublisher,
                aggregationWorkPublisher,
                resultCollector);

        MetadataDispatchResult result = service.dispatch(id, "hash-v1");

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES), result.providers());
        ArgumentCaptor<RuntimeWorkItemRequest> requestCaptor = ArgumentCaptor.forClass(RuntimeWorkItemRequest.class);
        verify(workQueue, org.mockito.Mockito.times(3)).submitAfterCommit(requestCaptor.capture());
        assertEquals(List.of(
                PipelineRuntimeWorkTypes.providerTaskId("DOUBAN", id, "hash-v1"),
                PipelineRuntimeWorkTypes.providerTaskId("TMDB", id, "hash-v1"),
                PipelineRuntimeWorkTypes.providerTaskId("ROTTEN_TOMATOES", id, "hash-v1")),
                requestCaptor.getAllValues().stream().map(RuntimeWorkItemRequest::taskId).toList());
        assertEquals(List.of(
                PipelineRuntimeWorkTypes.METADATA_PROVIDER,
                PipelineRuntimeWorkTypes.METADATA_PROVIDER,
                PipelineRuntimeWorkTypes.METADATA_PROVIDER),
                requestCaptor.getAllValues().stream().map(RuntimeWorkItemRequest::taskType).toList());
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void skipsProviderWhenAuthoritativeProviderIdAlreadyExists() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("101");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        RuntimeWorkQueue workQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(workQueue, new ObjectMapper());
        when(configValues.doubanEnabled()).thenReturn(true);
        when(configValues.tmdbEnabled()).thenReturn(true);
        when(configValues.rottenTomatoesEnabled()).thenReturn(true);
        when(pipelineRunRepository.prepareDispatch(
                        id,
                        "hash-v1",
                        List.of(ProviderType.DOUBAN, ProviderType.ROTTEN_TOMATOES)))
                .thenReturn(List.of(ProviderType.DOUBAN, ProviderType.ROTTEN_TOMATOES));
        MetadataDispatchService service = new MetadataDispatchService(
                repository,
                configValues,
                pipelineRunRepository,
                publisher,
                searchSyncPublisher,
                aggregationWorkPublisher,
                resultCollector);

        MetadataDispatchResult result = service.dispatch(id, "hash-v1");

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.ROTTEN_TOMATOES), result.providers());
        ArgumentCaptor<RuntimeWorkItemRequest> requestCaptor = ArgumentCaptor.forClass(RuntimeWorkItemRequest.class);
        verify(workQueue, org.mockito.Mockito.times(2)).submitAfterCommit(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getAllValues())
                .extracting(RuntimeWorkItemRequest::taskId)
                .noneMatch(taskId -> taskId.toString().contains("TMDB"));
    }

    @Test
    void dispatchesKnownTmdbIdToCompleteMissingCoreMetadata() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord raw = record(id);
        raw.setTmdbId("101");
        raw.setCoreMetadataIncomplete(true);
        when(repository.findById(id)).thenReturn(Optional.of(raw));
        when(pipelineRunRepository.prepareDispatch(id, "hash-v1", List.of(ProviderType.TMDB)))
                .thenReturn(List.of(ProviderType.TMDB));

        MetadataDispatchResult result = newService(true).dispatch(id, "hash-v1");

        assertEquals(List.of(ProviderType.TMDB), result.providers());
        ArgumentCaptor<MetadataSearchContext> context = ArgumentCaptor.forClass(MetadataSearchContext.class);
        verify(providerTaskPublisher).publish(org.mockito.Mockito.eq(ProviderType.TMDB), context.capture());
        assertEquals("101", context.getValue().getTmdbId());
    }

    @Test
    void noAvailableProviderEndsWithoutMeaninglessRetry() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setEnrichmentRetryCount(2);
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.updateStatus(id, "hash-v1", "PERMANENT_FAILURE", 0)).thenReturn(1);

        MetadataDispatchResult result = newService(false).dispatch(id, "hash-v1");

        assertEquals("PERMANENT_FAILURE", result.status());
        verify(repository).updateStatus(id, "hash-v1", "PERMANENT_FAILURE", 0);
        verify(searchSyncPublisher).publishIndex(id);
    }

    @Test
    void missingVideoReturnsNotFoundWithoutTouchingSearchSync() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        MetadataDispatchResult result = newService(false).dispatch(id, "hash-v1");

        assertEquals(false, result.videoFound());
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sports", "adult", "other"})
    void sourceOnlyCategoriesDoNotFanOutEvenWhenAllFilmProvidersAreEnabled(String category) {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setCategorySlug(category);
        record.setTmdbId("0");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.updateStatus(id, "hash-v1", "PERMANENT_FAILURE", 0)).thenReturn(1);
        when(configValues.doubanEnabled()).thenReturn(true);
        when(configValues.tmdbEnabled()).thenReturn(true);
        when(configValues.rottenTomatoesEnabled()).thenReturn(true);

        MetadataDispatchResult result = newService(false).dispatch(id, "hash-v1");

        assertEquals("PERMANENT_FAILURE", result.status());
        verify(repository).updateStatus(id, "hash-v1", "PERMANENT_FAILURE", 0);
        org.mockito.Mockito.verifyNoInteractions(providerTaskPublisher, pipelineRunRepository, resultCollector);
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "metadata-immediate-finalized");
    }

    @Test
    void shortDramaOnlyDispatchesDoubanAndTmdbAndIncludesCompleteTitleInContext() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setCategorySlug("short-drama");
        record.setTitle("被家族抛弃");
        record.setSubtitle("我觉醒九亿属性点");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(configValues.doubanEnabled()).thenReturn(true);
        when(configValues.tmdbEnabled()).thenReturn(true);
        when(configValues.rottenTomatoesEnabled()).thenReturn(true);
        when(pipelineRunRepository.prepareDispatch(id, "hash-v1", List.of(ProviderType.DOUBAN, ProviderType.TMDB)))
                .thenReturn(List.of(ProviderType.DOUBAN, ProviderType.TMDB));

        var result = newService(false).dispatch(id, "hash-v1");

        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.TMDB), result.providers());
        ArgumentCaptor<MetadataSearchContext> context = ArgumentCaptor.forClass(MetadataSearchContext.class);
        verify(providerTaskPublisher).publish(org.mockito.ArgumentMatchers.eq(ProviderType.TMDB), context.capture());
        assertEquals("被家族抛弃 我觉醒九亿属性点", context.getValue().getFullTitle());
        verify(providerTaskPublisher, never()).publish(org.mockito.ArgumentMatchers.eq(ProviderType.ROTTEN_TOMATOES), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void placeholderIdDoesNotSkipProviderAndContextContainsCanonicalIdsOnly() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("0");
        record.setDoubanId("00123");
        record.setImdbId(" TT1234567 ");
        record.setRottenTomatoesId(" N/A ");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(pipelineRunRepository.prepareDispatch(id, "hash-v1", List.of(ProviderType.TMDB)))
                .thenReturn(List.of(ProviderType.TMDB));

        assertEquals("DISPATCHED", newService(true).dispatch(id, "hash-v1").status());

        ArgumentCaptor<MetadataSearchContext> context = ArgumentCaptor.forClass(MetadataSearchContext.class);
        verify(providerTaskPublisher).publish(org.mockito.ArgumentMatchers.eq(ProviderType.TMDB), context.capture());
        assertEquals("123", context.getValue().getDoubanId());
        assertEquals("tt1234567", context.getValue().getImdbId());
        org.junit.jupiter.api.Assertions.assertNull(context.getValue().getTmdbId());
        org.junit.jupiter.api.Assertions.assertNull(context.getValue().getRottenTomatoesId());
    }

    @Test
    void immediateStatusCasFailureDoesNotPublishAnyDownstreamWork() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("101");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.updateStatus(id, "hash-v1", "SUCCESS", 0)).thenReturn(0);

        assertEquals("STALE", newService(false).dispatch(id, "hash-v1").status());

        org.mockito.Mockito.verifyNoInteractions(searchSyncPublisher, aggregationWorkPublisher,
                providerTaskPublisher, pipelineRunRepository, resultCollector);
    }

    @Test
    void historicalSuccessWithOnlyZeroIdIsReclassifiedInsteadOfRepublishingFalseSuccess() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setEnrichmentStatus("SUCCESS");
        record.setTmdbId("0");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        when(repository.updateStatus(id, "hash-v1", "PERMANENT_FAILURE", 0)).thenReturn(1);

        assertEquals("PERMANENT_FAILURE", newService(false).dispatch(id, "hash-v1").status());

        verify(repository).updateStatus(id, "hash-v1", "PERMANENT_FAILURE", 0);
        org.mockito.Mockito.verifyNoInteractions(providerTaskPublisher, pipelineRunRepository, resultCollector);
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "metadata-immediate-finalized");
    }

    private MetadataDispatchService newService(boolean tmdbEnabled) {
        if (tmdbEnabled) {
            when(configValues.tmdbEnabled()).thenReturn(true);
        }
        return new MetadataDispatchService(
                repository,
                configValues,
                pipelineRunRepository,
                providerTaskPublisher,
                searchSyncPublisher,
                aggregationWorkPublisher,
                resultCollector);
    }

    private RawVideoEnrichmentRecord record(UUID id) {
        return new RawVideoEnrichmentRecord(
                id,
                "title",
                null,
                null,
                null,
                null,
                "movie",
                null,
                null,
                null,
                null,
                "PENDING",
                0,
                "hash-v1",
                false);
    }
}
