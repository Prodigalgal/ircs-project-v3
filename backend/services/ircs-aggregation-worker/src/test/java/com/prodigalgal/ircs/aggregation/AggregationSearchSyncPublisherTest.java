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
            verify(workPublisher).enqueue(adult, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
            org.mockito.Mockito.verifyNoMoreInteractions(workPublisher);
            scope.publishIndexes();
        }
        // Early flush was removed, so this remains exactly one request for the already restricted ID.
        verify(workPublisher).enqueue(adult, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
        verify(workPublisher).enqueue(pending, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
        verify(workPublisher).enqueue(adult, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX);
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
        verify(workPublisher).enqueue(id, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
        verify(workPublisher).enqueue(id, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX);
    }

    @Test
    void closingDeferredOperationDiscardsPendingIndexesAndDoesNotLeakScope() {
        UUID id = UUID.randomUUID();
        try (var scope = DeferredAdultAssessments.open()) {
            publisher.publishUnifiedIndex(id);
        }
        org.mockito.Mockito.verifyNoInteractions(workPublisher);
        publisher.publishUnifiedIndex(id);
        verify(workPublisher).enqueue(id, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
    }

    @Test
    void enqueuesRawIndexInSearchWorkQueue() {
        UUID sourceRecordId = UUID.randomUUID();

        publisher.publishRawIndex(sourceRecordId);

        verify(workPublisher).enqueue(sourceRecordId, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX);
        assertThat(sourceRecordId).isNotNull();
    }

    @Test
    void enqueuesUnifiedIndexInSearchWorkQueue() {
        UUID catalogEntryId = UUID.randomUUID();

        publisher.publishUnifiedIndex(catalogEntryId);

        verify(workPublisher).enqueue(catalogEntryId, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
        assertThat(catalogEntryId).isNotNull();
    }

    @Test
    void enqueuesUnifiedDeleteInSearchWorkQueue() {
        UUID victimId = UUID.randomUUID();

        publisher.publishUnifiedDelete(victimId);

        verify(workPublisher).enqueue(victimId, SearchEntityType.CATALOG_ENTRY, SyncOperation.DELETE);
        assertThat(victimId).isNotNull();
    }
}
