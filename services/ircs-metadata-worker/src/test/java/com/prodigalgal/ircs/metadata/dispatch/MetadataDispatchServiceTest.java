package com.prodigalgal.ircs.metadata.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository;
import com.prodigalgal.ircs.metadata.result.RawSearchSyncPublisher;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @AfterEach
    void tearDown() {
        org.mockito.Mockito.validateMockitoUsage();
    }

    @Test
    void finalizesImmediatelyWhenAuthoritativeIdExists() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("tmdb-1");
        when(repository.findById(id)).thenReturn(Optional.of(record));

        MetadataDispatchResult result = newService(false).dispatch(id, "hash-v1");

        assertEquals("SUCCESS", result.status());
        verify(repository).updateStatus(id, "SUCCESS", 0);
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
        PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(pipelineQueue, new ObjectMapper());
        when(configValues.metadataEnabled()).thenReturn(true);
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
                aggregationWorkPublisher);

        MetadataDispatchResult result = service.dispatch(id, "hash-v1");

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES), result.providers());
        verify(pipelineQueue).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("DOUBAN"),
                eq(id),
                eq("hash-v1"),
                contains("\"provider\":\"DOUBAN\""),
                eq(Duration.ZERO));
        verify(pipelineQueue).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("TMDB"),
                eq(id),
                eq("hash-v1"),
                contains("\"provider\":\"TMDB\""),
                eq(Duration.ZERO));
        verify(pipelineQueue).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("ROTTEN_TOMATOES"),
                eq(id),
                eq("hash-v1"),
                contains("\"provider\":\"ROTTEN_TOMATOES\""),
                eq(Duration.ZERO));
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void skipsProviderWhenAuthoritativeProviderIdAlreadyExists() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("tmdb-1");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(pipelineQueue, new ObjectMapper());
        when(configValues.metadataEnabled()).thenReturn(true);
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
                aggregationWorkPublisher);

        MetadataDispatchResult result = service.dispatch(id, "hash-v1");

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.ROTTEN_TOMATOES), result.providers());
        verify(pipelineQueue, never()).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("TMDB"),
                eq(id),
                eq("hash-v1"),
                org.mockito.Mockito.anyString(),
                eq(Duration.ZERO));
    }

    @Test
    void retryableImmediateFailureIncrementsRetryCount() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setEnrichmentRetryCount(2);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        MetadataDispatchResult result = newService(false).dispatch(id, "hash-v1");

        assertEquals("FAILED", result.status());
        verify(repository).updateStatus(id, "FAILED", 3);
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

    private MetadataDispatchService newService(boolean tmdbEnabled) {
        org.mockito.Mockito.lenient().when(configValues.metadataEnabled()).thenReturn(true);
        org.mockito.Mockito.lenient().when(configValues.tmdbEnabled()).thenReturn(tmdbEnabled);
        return new MetadataDispatchService(
                repository,
                configValues,
                pipelineRunRepository,
                providerTaskPublisher,
                searchSyncPublisher,
                aggregationWorkPublisher);
    }

    private RawVideoEnrichmentRecord record(UUID id) {
        return new RawVideoEnrichmentRecord(
                id,
                "title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "PENDING",
                0,
                "hash-v1");
    }
}
