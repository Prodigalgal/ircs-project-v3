package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RawSearchSyncPublisher {

    private final SearchSyncWorkPublisher searchSyncWorkPublisher;

    public void publishIndex(UUID sourceRecordId) {
        searchSyncWorkPublisher.enqueue(sourceRecordId, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX);
    }
}
