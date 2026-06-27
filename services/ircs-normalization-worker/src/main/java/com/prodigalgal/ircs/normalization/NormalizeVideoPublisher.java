package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NormalizeVideoPublisher {

    private final PipelineValkeyQueue pipelineQueue;

    public void publish(UUID rawVideoId, String pipelineVersion) {
        publish(PipelineCommand.normalize(rawVideoId, pipelineVersion));
    }

    public void publish(PipelineCommand command) {
        pipelineQueue.submitAfterCommit(command);
    }

    public void publishNow(UUID rawVideoId, String pipelineVersion) {
        pipelineQueue.submit(PipelineCommand.normalize(rawVideoId, pipelineVersion));
    }
}
