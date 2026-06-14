package com.prodigalgal.ircs.identity;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvatarSyncPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID memberId) {
        QueueTopic topic = QueueTopic.SYNC_AVATAR;
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), memberId.toString());
    }
}
