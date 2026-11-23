package com.prodigalgal.ircs.ingestion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.IngestionEpisodeDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionPlaylistDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionVideoDTO;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRecordIngestionServiceTest {

    private final SourceRecordIngestionRepository repository = org.mockito.Mockito.mock(SourceRecordIngestionRepository.class);
    private final IngestionPublisher publisher = org.mockito.Mockito.mock(IngestionPublisher.class);
    private final SourceRecordIngestionService service = new SourceRecordIngestionService(
            new SourceRecordIngestionTransactions(repository), publisher);

    @Test
    void skipsUnchangedPayloadWhenForceIngestIsFalse() {
        UUID sourceRecordId = UUID.randomUUID();
        IngestionItem item = new IngestionItem(video("source-hash", "data-hash"), false);
        when(repository.lockStateBySourceHash("source-hash"))
                .thenReturn(Optional.of(new SourceRecordState(sourceRecordId, "data-hash", "READY")));

        SourceRecordIngestionService.IngestResult result = service.ingest(item);

        assertTrue(result.skipped());
        verify(repository, never()).upsertSourceRecord(item.video(), sourceRecordId);
        verify(publisher, never()).publishNormalize(sourceRecordId, "data-hash");
    }

    @Test
    void repairsDownstreamHandoffWhenRedeliveredAfterDatabaseCommit() {
        UUID id = UUID.randomUUID();
        IngestionItem item = new IngestionItem(video("source-hash", "data-hash"), false);
        when(repository.lockStateBySourceHash("source-hash"))
                .thenReturn(Optional.of(new SourceRecordState(id, "data-hash", "PENDING")));

        assertTrue(service.ingest(item).skipped());

        verify(repository, never()).upsertSourceRecord(item.video(), id);
        verify(publisher).publishNormalize(id, "data-hash");
        verify(publisher).publishPlaylistSync(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unchangedRefetchRepairsMissingPlaylistsWithoutRepeatingNormalization() {
        UUID id = UUID.randomUUID();
        var playlists = List.of(new IngestionPlaylistDTO("Main", List.of(
                new IngestionEpisodeDTO("EP01", "https://media.example/1.m3u8", null))));
        var item = new IngestionItem(video("source-hash", "data-hash", playlists), false);
        when(repository.lockStateBySourceHash("source-hash"))
                .thenReturn(Optional.of(new SourceRecordState(id, "data-hash", "READY")));
        when(repository.hasStoredEpisodes(id)).thenReturn(false, true);

        assertTrue(service.ingest(item).skipped());
        assertTrue(service.ingest(item).skipped());

        verify(publisher, times(1)).publishPlaylistSync(new PlaylistSyncMessage(id, "source-hash", "data-hash", playlists));
        verify(publisher, never()).publishNormalize(id, "data-hash");
        verify(repository, never()).upsertSourceRecord(item.video(), id);
    }

    @Test
    void upsertsChangedPayloadAndPublishesDownstreamMessages() {
        UUID sourceRecordId = UUID.randomUUID();
        IngestionItem item = new IngestionItem(video("source-hash", "new-data-hash"), false);
        when(repository.lockStateBySourceHash("source-hash"))
                .thenReturn(Optional.of(new SourceRecordState(sourceRecordId, "old-data-hash", "READY")));
        when(repository.upsertSourceRecord(item.video(), sourceRecordId)).thenReturn(sourceRecordId);

        SourceRecordIngestionService.IngestResult result = service.ingest(item);

        assertFalse(result.skipped());
        verify(repository).upsertSourceRecord(item.video(), sourceRecordId);
        verify(publisher).publishNormalize(sourceRecordId, "new-data-hash");
    }

    private IngestionVideoDTO video(String sourceHash, String dataHash) {
        return video(sourceHash, dataHash, List.of());
    }

    private IngestionVideoDTO video(String sourceHash, String dataHash, List<IngestionPlaylistDTO> playlists) {
        return new IngestionVideoDTO(
                "source-vid",
                sourceHash,
                dataHash,
                "Codex Smoke",
                null,
                null,
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{}",
                "PENDING",
                null,
                playlists,
                0);
    }
}
