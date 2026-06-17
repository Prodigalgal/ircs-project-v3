package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataEnrichPublisher {

    private final PipelineValkeyQueue pipelineQueue;

    public void publish(UUID rawVideoId, String pipelineVersion) {
        publish(PipelineCommand.enrich(rawVideoId, pipelineVersion));
    }

    public void publish(UUID rawVideoId, String pipelineVersion, Duration delay) {
        pipelineQueue.submitAfterCommit(PipelineCommand.enrich(rawVideoId, pipelineVersion), delay);
    }

    public void publish(PipelineCommand command) {
        pipelineQueue.submitAfterCommit(command);
    }
}
