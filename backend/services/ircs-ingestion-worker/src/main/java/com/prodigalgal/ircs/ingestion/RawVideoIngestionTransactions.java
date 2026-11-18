package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.IngestionPlaylistDTO;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
class RawVideoIngestionTransactions {

    private final RawVideoIngestionRepository repository;

    @Transactional(timeout = 30)
    public PersistedVideo persist(IngestionItem item) {
        var existing = repository.lockStateBySourceHash(item.video().sourceHash());
        if (existing.isPresent() && !item.forceIngest()
                && Objects.equals(existing.get().dataHash(), item.video().dataHash())) {
            RawVideoState current = existing.get();
            // A publish failure can be redelivered after the database commit.
            boolean normalize = "PENDING".equals(current.normalizationStatus());
            boolean syncPlaylists = normalize || (hasIncomingEpisodes(item) && !repository.hasStoredEpisodes(current.id()));
            return new PersistedVideo(current.id(), true, syncPlaylists, normalize);
        }
        UUID id = repository.upsertRawVideo(item.video(), existing.map(RawVideoState::id).orElse(null));
        return new PersistedVideo(id, false, true, true);
    }

    private boolean hasIncomingEpisodes(IngestionItem item) {
        var playlists = item.video().playlists();
        return playlists != null && playlists.stream().filter(Objects::nonNull)
                .map(IngestionPlaylistDTO::episodes).filter(Objects::nonNull)
                .flatMap(java.util.Collection::stream).filter(Objects::nonNull)
                .anyMatch(episode -> StringUtils.hasText(episode.url()));
    }

    @Transactional(timeout = 30)
    public RawVideoIngestionService.PlaylistSyncResult syncPlaylists(PlaylistSyncMessage message) {
        var currentHash = repository.lockDataHashById(message.videoId());
        if (currentHash.isEmpty()) {
            return RawVideoIngestionService.PlaylistSyncResult.missingVideo(message.videoId());
        }
        if (!currentHash.get().equals(message.dataHash())) {
            return RawVideoIngestionService.PlaylistSyncResult.stale(message.videoId());
        }
        return RawVideoIngestionService.PlaylistSyncResult.synced(
                message.videoId(), repository.replacePlaylists(message));
    }

    record PersistedVideo(UUID id, boolean skipped, boolean syncPlaylists, boolean normalize) {
    }
}
