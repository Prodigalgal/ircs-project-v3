package com.prodigalgal.ircs.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.pipeline.PipelineRuntimeWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import com.prodigalgal.ircs.messaging.MessagePublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestionPublisher {

    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    private final RuntimeWorkQueue workQueue;

    public void publishPlaylistSync(PlaylistSyncMessage message) {
        publishJson(QueueTopic.PLAYLIST_SYNC, message);
    }

    public void publishNormalize(UUID rawVideoId, String pipelineVersion) {
        String taskId = PipelineRuntimeWorkTypes.normalizeTaskId(rawVideoId, pipelineVersion);
        if (workQueue.hasOpenTask(PipelineRuntimeWorkTypes.NORMALIZE_VIDEO, taskId)) {
            return;
        }
        workQueue.submitAfterCommit(new RuntimeWorkItemRequest(
                PipelineRuntimeWorkTypes.NORMALIZE_VIDEO,
                taskId,
                rawVideoId.toString(),
                PipelineRuntimeWorkTypes.normalizeVersion(pipelineVersion),
                ""));
    }

    private void publishJson(QueueTopic topic, Object payload) {
        try {
            messagePublisher.publish(topic.exchange(), topic.routingKey(), objectMapper.writeValueAsString(payload));
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to serialize ingestion payload", ex);
        }
    }
}
