package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SourceRecordIngestionService {

    private final SourceRecordIngestionTransactions transactions;
    private final IngestionPublisher publisher;

    public IngestResult ingest(IngestionItem item) {
        var persisted = transactions.persist(item);
        if (persisted.syncPlaylists()) {
            // The transaction proxy has returned and released the JDBC connection.
            publisher.publishPlaylistSync(new PlaylistSyncMessage(
                    persisted.id(), item.video().sourceHash(), item.video().dataHash(), List.of()));
        }
        if (persisted.normalize()) publisher.publishNormalize(persisted.id(), item.video().dataHash());
        return new IngestResult(persisted.id(), persisted.skipped());
    }

    public PlaylistSyncResult syncPlaylists(PlaylistSyncMessage message) {
        var applied = transactions.syncPlaylists(message);
        if (applied.normalize()) {
            publisher.publishNormalize(message.videoId(), message.dataHash());
        }
        return applied.result();
    }

    public record IngestResult(UUID sourceRecordId, boolean skipped) {
        public static IngestResult persisted(UUID sourceRecordId) {
            return new IngestResult(sourceRecordId, false);
        }

        public static IngestResult skipped(UUID sourceRecordId) {
            return new IngestResult(sourceRecordId, true);
        }
    }

    public record PlaylistSyncResult(UUID sourceRecordId, String status, int playlistCount) {
        public static PlaylistSyncResult synced(UUID sourceRecordId, int playlistCount) {
            return new PlaylistSyncResult(sourceRecordId, "SYNCED", playlistCount);
        }

        public static PlaylistSyncResult stale(UUID sourceRecordId) {
            return new PlaylistSyncResult(sourceRecordId, "STALE", 0);
        }

        public static PlaylistSyncResult missingVideo(UUID sourceRecordId) {
            return new PlaylistSyncResult(sourceRecordId, "MISSING_VIDEO", 0);
        }
    }
}
