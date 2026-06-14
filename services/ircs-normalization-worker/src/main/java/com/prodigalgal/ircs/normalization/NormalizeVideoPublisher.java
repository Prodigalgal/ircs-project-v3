package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NormalizeVideoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID rawVideoId) {
        QueueTopic topic = QueueTopic.NORMALIZE_VIDEO;
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), rawVideoId.toString());
    }
}
