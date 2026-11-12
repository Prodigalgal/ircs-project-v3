package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RawVideoIngestionService {

    private final RawVideoIngestionTransactions transactions;
    private final IngestionPublisher publisher;

    public IngestResult ingest(IngestionItem item) {
        var persisted = transactions.persist(item);
        if (persisted.needsDownstream()) {
            // The transaction proxy has returned and released the JDBC connection.
            publisher.publishPlaylistSync(new PlaylistSyncMessage(
                    persisted.id(), item.video().sourceHash(), item.video().dataHash(), item.video().playlists()));
            publisher.publishNormalize(persisted.id(), item.video().dataHash());
        }
        return new IngestResult(persisted.id(), persisted.skipped());
    }

    public PlaylistSyncResult syncPlaylists(PlaylistSyncMessage message) {
        return transactions.syncPlaylists(message);
    }

    public record IngestResult(UUID rawVideoId, boolean skipped) {
        public static IngestResult persisted(UUID rawVideoId) {
            return new IngestResult(rawVideoId, false);
        }

        public static IngestResult skipped(UUID rawVideoId) {
            return new IngestResult(rawVideoId, true);
        }
    }

    public record PlaylistSyncResult(UUID rawVideoId, String status, int playlistCount) {
        public static PlaylistSyncResult synced(UUID rawVideoId, int playlistCount) {
            return new PlaylistSyncResult(rawVideoId, "SYNCED", playlistCount);
        }

        public static PlaylistSyncResult stale(UUID rawVideoId) {
            return new PlaylistSyncResult(rawVideoId, "STALE", 0);
        }

        public static PlaylistSyncResult missingVideo(UUID rawVideoId) {
            return new PlaylistSyncResult(rawVideoId, "MISSING_VIDEO", 0);
        }
    }
}
