package com.prodigalgal.ircs.content.video;

import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentCommandPublisher {

    private final SearchSyncWorkPublisher searchSyncWorkPublisher;
    private final PipelineValkeyQueue pipelineQueue;

    public void publishRawSearch(UUID rawVideoId, SyncOperation operation) {
        searchSyncWorkPublisher.enqueue(rawVideoId, SearchEntityType.RAW_VIDEO, operation);
    }

    public void publishUnifiedSearch(UUID unifiedVideoId, SyncOperation operation) {
        searchSyncWorkPublisher.enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, operation);
    }

    public void publishNormalize(UUID rawVideoId, String pipelineVersion) {
        pipelineQueue.submitAfterCommit(PipelineCommand.normalize(rawVideoId, pipelineVersion));
    }

    public void publishEnrich(UUID rawVideoId, String pipelineVersion) {
        pipelineQueue.submitAfterCommit(PipelineCommand.enrich(rawVideoId, pipelineVersion));
    }
}
