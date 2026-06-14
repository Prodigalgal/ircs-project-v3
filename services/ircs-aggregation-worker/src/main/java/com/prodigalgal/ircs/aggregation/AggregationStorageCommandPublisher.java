package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AggregationStorageCommandPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishCoverR2Promote(UUID coverImageId) {
        QueueTopic topic = QueueTopic.PROMOTE_COVER_R2;
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), coverImageId.toString());
    }
}
