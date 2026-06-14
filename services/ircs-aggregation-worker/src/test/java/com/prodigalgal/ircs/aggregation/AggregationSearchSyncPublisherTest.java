package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregationSearchSyncPublisherTest {

    private final SearchSyncOutboxWriter outboxWriter = org.mockito.Mockito.mock(SearchSyncOutboxWriter.class);
    private final AggregationSearchSyncPublisher publisher =
            new AggregationSearchSyncPublisher(outboxWriter);

    @Test
    void enqueuesRawIndexInSearchOutbox() {
        UUID rawVideoId = UUID.randomUUID();

        publisher.publishRawIndex(rawVideoId);

        verify(outboxWriter).enqueue(rawVideoId, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);
        assertThat(rawVideoId).isNotNull();
    }

    @Test
    void enqueuesUnifiedIndexInSearchOutbox() {
        UUID unifiedVideoId = UUID.randomUUID();

        publisher.publishUnifiedIndex(unifiedVideoId);

        verify(outboxWriter).enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        assertThat(unifiedVideoId).isNotNull();
    }

    @Test
    void enqueuesUnifiedDeleteInSearchOutbox() {
        UUID victimId = UUID.randomUUID();

        publisher.publishUnifiedDelete(victimId);

        verify(outboxWriter).enqueue(victimId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.DELETE);
        assertThat(victimId).isNotNull();
    }
}
