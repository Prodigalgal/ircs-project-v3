package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.pipeline.PipelineRuntimeWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NormalizeVideoPublisher {

    private final RuntimeWorkQueue workQueue;

    public void publish(UUID sourceRecordId, String pipelineVersion) {
        workQueue.submitAfterCommit(request(sourceRecordId, pipelineVersion));
    }

    public void publishNow(UUID sourceRecordId, String pipelineVersion) {
        workQueue.submit(request(sourceRecordId, pipelineVersion));
    }

    private static RuntimeWorkItemRequest request(UUID sourceRecordId, String pipelineVersion) {
        return new RuntimeWorkItemRequest(
                PipelineRuntimeWorkTypes.NORMALIZE_VIDEO,
                PipelineRuntimeWorkTypes.normalizeTaskId(sourceRecordId, pipelineVersion),
                sourceRecordId.toString(),
                PipelineRuntimeWorkTypes.normalizeVersion(pipelineVersion),
                "");
    }
}
