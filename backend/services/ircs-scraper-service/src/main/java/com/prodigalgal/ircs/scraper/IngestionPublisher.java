package com.prodigalgal.ircs.scraper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import lombok.RequiredArgsConstructor;
import com.prodigalgal.ircs.messaging.RabbitConfirmedPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class IngestionPublisher {

    private final RabbitConfirmedPublisher rabbitTemplate;
    private final ObjectMapper objectMapper;

    void publish(IngestionItem item) {
        QueueTopic topic = QueueTopic.INGEST_VIDEO;
        try {
            rabbitTemplate.publish(topic.exchange(), topic.routingKey(), objectMapper.writeValueAsString(item));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize ingestion item", e);
        }
    }
}
