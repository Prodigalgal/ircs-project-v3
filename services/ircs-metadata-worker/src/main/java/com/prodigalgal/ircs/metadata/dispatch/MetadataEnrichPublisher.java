package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataEnrichPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PipelineCommandMessageCodec codec;

    public void publish(UUID rawVideoId, String pipelineVersion) {
        publish(PipelineCommand.enrich(rawVideoId, pipelineVersion));
    }

    public void publish(PipelineCommand command) {
        QueueTopic topic = QueueTopic.ENRICH_METADATA;
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), codec.encode(command));
    }
}
