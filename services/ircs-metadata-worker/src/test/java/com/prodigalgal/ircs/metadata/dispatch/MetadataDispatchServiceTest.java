package com.prodigalgal.ircs.metadata.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.metadata.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.result.RawSearchSyncPublisher;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetadataDispatchServiceTest {

    @Mock
    private RawVideoEnrichmentRepository repository;

    @Mock
    private MetadataPendingRegistry pendingRegistry;

    @Mock
    private MetadataProviderTaskPublisher providerTaskPublisher;

    @Mock
    private RawSearchSyncPublisher searchSyncPublisher;

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

        MetadataDispatchResult result = newService(false).dispatch(id);

        assertEquals("SUCCESS", result.status());
        verify(repository).updateStatus(id, "SUCCESS", 0);
        verify(searchSyncPublisher).publishIndex(id);
        verify(providerTaskPublisher, never()).publish(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void dispatchesEnabledMissingProvidersAndRegistersPendingSet() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        MetadataDispatchResult result = newService(true).dispatch(id);

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.TMDB), result.providers());
        verify(pendingRegistry).register(id, List.of(ProviderType.TMDB), Duration.ofMinutes(30));
        verify(providerTaskPublisher).publish(org.mockito.Mockito.eq(ProviderType.TMDB), org.mockito.Mockito.any());
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void fanOutUsesProviderConfigAndPublishesFetchTopicsThroughPublisher() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        when(repository.findById(id)).thenReturn(Optional.of(record));
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(rabbitTemplate, new ObjectMapper());
        when(configValues.metadataEnabled()).thenReturn(true);
        when(configValues.doubanEnabled()).thenReturn(true);
        when(configValues.tmdbEnabled()).thenReturn(true);
        when(configValues.rottenTomatoesEnabled()).thenReturn(true);
        when(configValues.pendingTtl()).thenReturn(Duration.ofMinutes(15));
        MetadataDispatchService service = new MetadataDispatchService(
                repository,
                configValues,
                pendingRegistry,
                publisher,
                searchSyncPublisher);

        MetadataDispatchResult result = service.dispatch(id);

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES), result.providers());
        verify(pendingRegistry).register(
                id,
                List.of(ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES),
                Duration.ofMinutes(15));
        verify(rabbitTemplate).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.douban"),
                org.mockito.Mockito.contains(id.toString()));
        verify(rabbitTemplate).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.tmdb"),
                org.mockito.Mockito.contains(id.toString()));
        verify(rabbitTemplate).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.rt"),
                org.mockito.Mockito.contains(id.toString()));
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    @Test
    void skipsProviderWhenAuthoritativeProviderIdAlreadyExists() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setTmdbId("tmdb-1");
        when(repository.findById(id)).thenReturn(Optional.of(record));
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(rabbitTemplate, new ObjectMapper());
        when(configValues.metadataEnabled()).thenReturn(true);
        when(configValues.doubanEnabled()).thenReturn(true);
        when(configValues.tmdbEnabled()).thenReturn(true);
        when(configValues.rottenTomatoesEnabled()).thenReturn(true);
        when(configValues.pendingTtl()).thenReturn(Duration.ofMinutes(30));
        MetadataDispatchService service = new MetadataDispatchService(
                repository,
                configValues,
                pendingRegistry,
                publisher,
                searchSyncPublisher);

        MetadataDispatchResult result = service.dispatch(id);

        assertEquals("DISPATCHED", result.status());
        assertEquals(List.of(ProviderType.DOUBAN, ProviderType.ROTTEN_TOMATOES), result.providers());
        verify(rabbitTemplate, never()).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.tmdb"),
                org.mockito.Mockito.anyString());
    }

    @Test
    void retryableImmediateFailureIncrementsRetryCount() {
        UUID id = UUID.randomUUID();
        RawVideoEnrichmentRecord record = record(id);
        record.setEnrichmentRetryCount(2);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        MetadataDispatchResult result = newService(false).dispatch(id);

        assertEquals("FAILED", result.status());
        verify(repository).updateStatus(id, "FAILED", 3);
        verify(searchSyncPublisher).publishIndex(id);
    }

    @Test
    void missingVideoClearsPendingSet() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        MetadataDispatchResult result = newService(false).dispatch(id);

        assertEquals(false, result.videoFound());
        verify(pendingRegistry).clear(id);
        verify(searchSyncPublisher, never()).publishIndex(id);
    }

    private MetadataDispatchService newService(boolean tmdbEnabled) {
        org.mockito.Mockito.lenient().when(configValues.metadataEnabled()).thenReturn(true);
        org.mockito.Mockito.lenient().when(configValues.tmdbEnabled()).thenReturn(tmdbEnabled);
        if (tmdbEnabled) {
            when(configValues.pendingTtl()).thenReturn(Duration.ofMinutes(30));
        }
        return new MetadataDispatchService(
                repository,
                configValues,
                pendingRegistry,
                providerTaskPublisher,
                searchSyncPublisher);
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
                0);
    }
}
