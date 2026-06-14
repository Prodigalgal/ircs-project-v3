package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AggregationSearchSyncPublisher {

    private final SearchSyncOutboxWriter searchSyncOutboxWriter;

    public void publishRawIndex(UUID rawVideoId) {
        searchSyncOutboxWriter.enqueue(rawVideoId, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);
    }

    public void publishUnifiedIndex(UUID unifiedVideoId) {
        searchSyncOutboxWriter.enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
    }

    public void publishUnifiedDelete(UUID unifiedVideoId) {
        searchSyncOutboxWriter.enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.DELETE);
    }
}
