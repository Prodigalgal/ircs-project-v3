package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RawVideoIngestionTransactions {

    private final RawVideoIngestionRepository repository;

    @Transactional(timeout = 30)
    public PersistedVideo persist(IngestionItem item) {
        var existing = repository.findStateBySourceHash(item.video().sourceHash());
        if (existing.isPresent() && !item.forceIngest()
                && Objects.equals(existing.get().dataHash(), item.video().dataHash())) {
            RawVideoState current = existing.get();
            // A publish failure can be redelivered after the database commit.
            return new PersistedVideo(current.id(), true, "PENDING".equals(current.normalizationStatus()));
        }
        UUID id = repository.upsertRawVideo(item.video(), existing.map(RawVideoState::id).orElse(null));
        return new PersistedVideo(id, false, true);
    }

    @Transactional(timeout = 30)
    public RawVideoIngestionService.PlaylistSyncResult syncPlaylists(PlaylistSyncMessage message) {
        var currentHash = repository.findDataHashById(message.videoId());
        if (currentHash.isEmpty()) {
            return RawVideoIngestionService.PlaylistSyncResult.missingVideo(message.videoId());
        }
        if (!currentHash.get().equals(message.dataHash())) {
            return RawVideoIngestionService.PlaylistSyncResult.stale(message.videoId());
        }
        return RawVideoIngestionService.PlaylistSyncResult.synced(
                message.videoId(), repository.replacePlaylists(message));
    }

    record PersistedVideo(UUID id, boolean skipped, boolean needsDownstream) {
    }
}
