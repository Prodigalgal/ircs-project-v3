package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorageCommandPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishImageUnlink(UUID imageId) {
        publish(QueueTopic.CMD_IMAGE_UNLINK, imageId);
    }

    public void publishImageDownload(UUID imageId) {
        publish(QueueTopic.DOWNLOAD_IMAGE, imageId);
    }

    private void publish(QueueTopic topic, UUID imageId) {
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), imageId.toString());
    }
}
