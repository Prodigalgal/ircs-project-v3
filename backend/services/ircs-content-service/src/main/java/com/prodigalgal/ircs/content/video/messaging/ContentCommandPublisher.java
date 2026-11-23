package com.prodigalgal.ircs.content.video.messaging;

import com.prodigalgal.ircs.common.pipeline.PipelineRuntimeWorkTypes;
import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentCommandPublisher {

    private final SearchSyncWorkPublisher searchSyncWorkPublisher;
    private final RuntimeWorkQueue workQueue;

    public void publishRawSearch(UUID sourceRecordId, SyncOperation operation) {
        searchSyncWorkPublisher.enqueue(sourceRecordId, SearchEntityType.SOURCE_RECORD, operation);
    }

    public void publishUnifiedSearch(UUID catalogEntryId, SyncOperation operation) {
        searchSyncWorkPublisher.enqueue(catalogEntryId, SearchEntityType.CATALOG_ENTRY, operation);
    }

    public void publishNormalize(UUID sourceRecordId, String pipelineVersion) {
        workQueue.submitAfterCommit(request(
                PipelineRuntimeWorkTypes.NORMALIZE_VIDEO,
                PipelineRuntimeWorkTypes.normalizeTaskId(sourceRecordId, pipelineVersion),
                sourceRecordId,
                pipelineVersion));
    }

    public void publishEnrich(UUID sourceRecordId, String pipelineVersion) {
        workQueue.submitAfterCommit(request(
                PipelineRuntimeWorkTypes.ENRICH_METADATA,
                PipelineRuntimeWorkTypes.enrichTaskId(sourceRecordId, pipelineVersion),
                sourceRecordId,
                pipelineVersion));
    }

    private static RuntimeWorkItemRequest request(
            String taskType,
            String taskId,
            UUID sourceRecordId,
            String pipelineVersion) {
        return new RuntimeWorkItemRequest(
                taskType,
                taskId,
                sourceRecordId.toString(),
                PipelineRuntimeWorkTypes.normalizeVersion(pipelineVersion),
                "");
    }
}
