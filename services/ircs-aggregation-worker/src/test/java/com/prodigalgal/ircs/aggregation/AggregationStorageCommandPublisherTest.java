package com.prodigalgal.ircs.aggregation;

import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AggregationStorageCommandPublisherTest {

    private final RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    private final AggregationStorageCommandPublisher publisher = new AggregationStorageCommandPublisher(rabbitTemplate);

    @Test
    void publishesCoverR2PromoteCommandToStorageExchange() {
        UUID coverImageId = UUID.randomUUID();

        publisher.publishCoverR2Promote(coverImageId);

        verify(rabbitTemplate).convertAndSend(
                QueueTopic.Names.STORAGE_X,
                "image.cover.r2_promote",
                coverImageId.toString());
    }
}
