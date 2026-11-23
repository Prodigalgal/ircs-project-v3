package com.prodigalgal.ircs.content.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.content.video.messaging.ContentCommandPublisher;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class CoverImageUnlinkServiceTest {

    @Mock
    private CoverImageReferenceRepository referenceRepository;

    @Mock
    private CoverImageUnlinkedPublisher unlinkedPublisher;

    @Mock
    private ContentCommandPublisher searchPublisher;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void unlinksRawAndUnifiedReferencesThenPublishesSearchSyncAndEvent() {
        UUID id = UUID.randomUUID();
        UUID rawId = UUID.randomUUID();
        UUID unifiedId = UUID.randomUUID();
        when(referenceRepository.findSourceRecordIds(id)).thenReturn(List.of(rawId));
        when(referenceRepository.findCatalogEntryIds(id)).thenReturn(List.of(unifiedId));
        when(referenceRepository.unlinkSourceRecords(id)).thenReturn(2);
        when(referenceRepository.unlinkCatalogEntries(id)).thenReturn(1);

        CoverImageUnlinkResult result =
                new CoverImageUnlinkService(referenceRepository, unlinkedPublisher, searchPublisher).unlink(id);

        assertEquals(id, result.imageId());
        assertEquals(2, result.sourceRecordCount());
        assertEquals(1, result.catalogEntryCount());
        verify(referenceRepository).findSourceRecordIds(id);
        verify(referenceRepository).findCatalogEntryIds(id);
        verify(referenceRepository).unlinkSourceRecords(id);
        verify(referenceRepository).unlinkCatalogEntries(id);
        verify(searchPublisher).publishRawSearch(rawId, SyncOperation.DELETE);
        verify(searchPublisher).publishRawSearch(rawId, SyncOperation.INDEX);
        verify(searchPublisher).publishUnifiedSearch(unifiedId, SyncOperation.DELETE);
        verify(searchPublisher).publishUnifiedSearch(unifiedId, SyncOperation.INDEX);
        verify(unlinkedPublisher).publish(id);
    }

    @Test
    void publishesSearchSyncAndEventAfterTransactionCommit() {
        UUID id = UUID.randomUUID();
        UUID rawId = UUID.randomUUID();
        UUID unifiedId = UUID.randomUUID();
        when(referenceRepository.findSourceRecordIds(id)).thenReturn(List.of(rawId));
        when(referenceRepository.findCatalogEntryIds(id)).thenReturn(List.of(unifiedId));
        when(referenceRepository.unlinkSourceRecords(id)).thenReturn(1);
        when(referenceRepository.unlinkCatalogEntries(id)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        new CoverImageUnlinkService(referenceRepository, unlinkedPublisher, searchPublisher).unlink(id);

        verifyNoInteractions(searchPublisher);
        verifyNoInteractions(unlinkedPublisher);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        verify(searchPublisher).publishRawSearch(rawId, SyncOperation.DELETE);
        verify(searchPublisher).publishRawSearch(rawId, SyncOperation.INDEX);
        verify(searchPublisher).publishUnifiedSearch(unifiedId, SyncOperation.DELETE);
        verify(searchPublisher).publishUnifiedSearch(unifiedId, SyncOperation.INDEX);
        verify(unlinkedPublisher).publish(id);
    }
}
