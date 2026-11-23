package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AggregationSearchSyncPublisher {

    private final SearchSyncWorkPublisher searchSyncWorkPublisher;

    public void publishRawIndex(UUID sourceRecordId) {
        if (DeferredAdultAssessments.deferIndex("raw:" + sourceRecordId, () -> publishRawIndex(sourceRecordId))) { return; }
        searchSyncWorkPublisher.enqueue(sourceRecordId, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX);
    }

    public void publishUnifiedIndex(UUID catalogEntryId) {
        if (DeferredAdultAssessments.deferIndex("unified:" + catalogEntryId, () -> publishUnifiedIndex(catalogEntryId))) { return; }
        searchSyncWorkPublisher.enqueue(catalogEntryId, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
    }

    public void publishUnifiedDelete(UUID catalogEntryId) {
        searchSyncWorkPublisher.enqueue(catalogEntryId, SearchEntityType.CATALOG_ENTRY, SyncOperation.DELETE);
    }
}
