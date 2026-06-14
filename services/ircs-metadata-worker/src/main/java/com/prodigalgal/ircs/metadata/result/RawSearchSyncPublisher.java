package com.prodigalgal.ircs.metadata.result;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RawSearchSyncPublisher {

    private final SearchSyncOutboxWriter searchSyncOutboxWriter;

    public void publishIndex(UUID rawVideoId) {
        searchSyncOutboxWriter.enqueue(rawVideoId, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);
    }
}
