package com.prodigalgal.ircs.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestionPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PipelineCommandMessageCodec pipelineCodec;

    public void publishPlaylistSync(PlaylistSyncMessage message) {
        publishJson(QueueTopic.PLAYLIST_SYNC, message);
    }

    public void publishNormalize(UUID rawVideoId, String pipelineVersion) {
        QueueTopic topic = QueueTopic.NORMALIZE_VIDEO;
        rabbitTemplate.convertAndSend(
                topic.exchange(),
                topic.routingKey(),
                pipelineCodec.encode(PipelineCommand.normalize(rawVideoId, pipelineVersion)));
    }

    private void publishJson(QueueTopic topic, Object payload) {
        try {
            rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize ingestion payload", ex);
        }
    }
}
