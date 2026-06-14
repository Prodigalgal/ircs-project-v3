package com.prodigalgal.ircs.metadata.dispatch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class MetadataProviderTaskPublisherTest {

    @Test
    void mapsProvidersToDedicatedFetchTopics() {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(rabbitTemplate, new ObjectMapper());
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("test")
                .build();

        publisher.publish(ProviderType.DOUBAN, context);
        publisher.publish(ProviderType.TMDB, context);
        publisher.publish(ProviderType.ROTTEN_TOMATOES, context);

        verify(rabbitTemplate).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.douban"),
                org.mockito.Mockito.contains(context.getVideoId().toString()));
        verify(rabbitTemplate).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.tmdb"),
                org.mockito.Mockito.contains(context.getVideoId().toString()));
        verify(rabbitTemplate).convertAndSend(
                eq(QueueTopic.Names.PROCESS_X),
                eq("metadata.fetch.rt"),
                org.mockito.Mockito.contains(context.getVideoId().toString()));
    }

    @Test
    void rejectsImdbBecauseNoFetchQueueExists() {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(rabbitTemplate, new ObjectMapper());
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("test")
                .build();

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(ProviderType.IMDB, context));
    }
}
