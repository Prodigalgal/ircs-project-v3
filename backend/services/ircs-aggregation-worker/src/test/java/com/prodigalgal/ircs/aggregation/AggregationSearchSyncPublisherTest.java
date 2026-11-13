package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregationSearchSyncPublisherTest {

    private final SearchSyncWorkPublisher workPublisher = org.mockito.Mockito.mock(SearchSyncWorkPublisher.class);
    private final AggregationSearchSyncPublisher publisher =
            new AggregationSearchSyncPublisher(workPublisher);

    @Test
    void committedAdultIndexBypassesOtherPendingIndexesAndIsRemovedFromBuffer() {
        UUID adult = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        try (var scope = DeferredAdultAssessments.open()) {
            DeferredAdultAssessments.registerRestricted(adult);
            publisher.publishUnifiedIndex(adult);
            publisher.publishUnifiedIndex(pending);
            publisher.publishRawIndex(adult);
            scope.publishRestrictedIndexes();
            verify(workPublisher).enqueue(adult, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
            org.mockito.Mockito.verifyNoMoreInteractions(workPublisher);
            scope.publishIndexes();
        }
        // Early flush was removed, so this remains exactly one request for the already restricted ID.
        verify(workPublisher).enqueue(adult, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        verify(workPublisher).enqueue(pending, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        verify(workPublisher).enqueue(adult, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);
    }

    @Test
    void buffersAndDeduplicatesIndexesUntilFinalAssessmentTransaction() {
        UUID id = UUID.randomUUID();
        try (var scope = DeferredAdultAssessments.open()) {
            publisher.publishUnifiedIndex(id);
            publisher.publishUnifiedIndex(id);
            publisher.publishRawIndex(id);
            org.mockito.Mockito.verifyNoInteractions(workPublisher);
            scope.publishIndexes();
        }
        verify(workPublisher).enqueue(id, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        verify(workPublisher).enqueue(id, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);
    }

    @Test
    void closingDeferredOperationDiscardsPendingIndexesAndDoesNotLeakScope() {
        UUID id = UUID.randomUUID();
        try (var scope = DeferredAdultAssessments.open()) {
            publisher.publishUnifiedIndex(id);
        }
        org.mockito.Mockito.verifyNoInteractions(workPublisher);
        publisher.publishUnifiedIndex(id);
        verify(workPublisher).enqueue(id, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
    }

    @Test
    void enqueuesRawIndexInSearchWorkQueue() {
        UUID rawVideoId = UUID.randomUUID();

        publisher.publishRawIndex(rawVideoId);

        verify(workPublisher).enqueue(rawVideoId, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);
        assertThat(rawVideoId).isNotNull();
    }

    @Test
    void enqueuesUnifiedIndexInSearchWorkQueue() {
        UUID unifiedVideoId = UUID.randomUUID();

        publisher.publishUnifiedIndex(unifiedVideoId);

        verify(workPublisher).enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        assertThat(unifiedVideoId).isNotNull();
    }

    @Test
    void enqueuesUnifiedDeleteInSearchWorkQueue() {
        UUID victimId = UUID.randomUUID();

        publisher.publishUnifiedDelete(victimId);

        verify(workPublisher).enqueue(victimId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.DELETE);
        assertThat(victimId).isNotNull();
    }
}
