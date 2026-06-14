package com.prodigalgal.ircs.metadata.dispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataProviderTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ProviderType provider, MetadataSearchContext context) {
        QueueTopic topic = topicFor(provider);
        try {
            rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), objectMapper.writeValueAsString(context));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize metadata search context", ex);
        }
    }

    private QueueTopic topicFor(ProviderType provider) {
        return switch (provider) {
            case DOUBAN -> QueueTopic.FETCH_METADATA_DOUBAN;
            case TMDB -> QueueTopic.FETCH_METADATA_TMDB;
            case ROTTEN_TOMATOES -> QueueTopic.FETCH_METADATA_RT;
            case IMDB -> throw new IllegalArgumentException("IMDB provider has no fetch queue");
        };
    }
}
