package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.pipeline.PipelineRuntimeWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataEnrichPublisher {

    private final RuntimeWorkQueue workQueue;

    public void publish(UUID sourceRecordId, String pipelineVersion) {
        publish(sourceRecordId, pipelineVersion, Duration.ZERO);
    }

    public void publish(UUID sourceRecordId, String pipelineVersion, Duration delay) {
        workQueue.submitAfterCommit(request(sourceRecordId, pipelineVersion), delay);
    }

    private static RuntimeWorkItemRequest request(UUID sourceRecordId, String pipelineVersion) {
        return new RuntimeWorkItemRequest(
                PipelineRuntimeWorkTypes.ENRICH_METADATA,
                PipelineRuntimeWorkTypes.enrichTaskId(sourceRecordId, pipelineVersion),
                sourceRecordId.toString(),
                PipelineRuntimeWorkTypes.normalizeVersion(pipelineVersion),
                "");
    }
}
