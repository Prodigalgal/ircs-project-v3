package com.prodigalgal.ircs.metadata.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataProviderResultPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishSuccess(MetadataSearchContext context, ProviderType providerType, EnrichedMetadataDTO metadata) {
        publish(EnrichmentResultMsg.success(
                context.getVideoId(),
                context.getPipelineVersion(),
                providerType,
                metadata));
    }

    public void publishFailure(
            MetadataSearchContext context,
            ProviderType providerType,
            String reason,
            boolean retryable,
            String errorCode) {
        publish(EnrichmentResultMsg.failure(
                context.getVideoId(),
                context.getPipelineVersion(),
                providerType,
                reason,
                retryable,
                errorCode));
    }

    private void publish(EnrichmentResultMsg message) {
        QueueTopic topic = QueueTopic.ENRICHMENT_RESULT;
        try {
            rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize metadata result", ex);
        }
    }
}
