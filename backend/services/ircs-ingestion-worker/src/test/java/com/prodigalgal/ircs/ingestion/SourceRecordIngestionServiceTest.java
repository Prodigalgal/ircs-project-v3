package com.prodigalgal.ircs.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.prodigalgal.ircs.contracts.ingestion.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRecordIngestionServiceTest {
    private final SourceRecordIngestionTransactions transactions = mock(SourceRecordIngestionTransactions.class);
    private final IngestionPublisher publisher = mock(IngestionPublisher.class);
    private final SourceRecordIngestionService service = new SourceRecordIngestionService(transactions, publisher);

    @Test void publishesWorkAfterSuccessfulPersistence() {
        UUID id = UUID.randomUUID(); var item = new IngestionItem(video("v1"), false);
        when(transactions.persist(item)).thenReturn(new SourceRecordIngestionTransactions.PersistedRecord(id, false, true, true));
        assertThat(service.ingest(item)).isEqualTo(new SourceRecordIngestionService.IngestResult(id, false));
        verify(publisher).publishPlaylistSync(new PlaylistSyncMessage(id, "source", "v1", List.of()));
        verify(publisher).publishNormalize(id, "v1");
    }

    @Test void unchangedVersionCanBeSkippedWhileRepairingPlaylistHandoff() {
        UUID id = UUID.randomUUID(); var item = new IngestionItem(video("v1"), false);
        when(transactions.persist(item)).thenReturn(new SourceRecordIngestionTransactions.PersistedRecord(id, true, true, false));
        assertThat(service.ingest(item).skipped()).isTrue();
        verify(publisher).publishPlaylistSync(any()); verify(publisher, never()).publishNormalize(any(), any());
    }

    @Test void stalePlaylistMessageDoesNotPublishNormalization() {
        UUID id = UUID.randomUUID(); var message = new PlaylistSyncMessage(id, "source", "old", List.of());
        when(transactions.syncPlaylists(message)).thenReturn(new SourceRecordIngestionTransactions.PlaylistApplication(
                SourceRecordIngestionService.PlaylistSyncResult.stale(id), false));
        assertThat(service.syncPlaylists(message).status()).isEqualTo("STALE");
        verify(publisher, never()).publishNormalize(any(), any());
    }

    private IngestionVideoDTO video(String hash) {
        return new IngestionVideoDTO("source-vid", "source", hash, "title", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, "{}", "PENDING", null, List.of(), 0);
    }
}
