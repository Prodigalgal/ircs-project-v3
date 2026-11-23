package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SourceRecordIngestionTransactions {

    private final SourceRecordIngestionRepository repository;

    @Transactional(timeout = 30)
    public PersistedRecord persist(IngestionItem item) {
        var video = item.video();
        Instant observedAt = item.observedAt() == null ? Instant.EPOCH : item.observedAt().truncatedTo(ChronoUnit.MICROS);
        var existing = repository.lockState(video);
        if (existing.isPresent() && existing.get().currentVersionId() != null) {
            SourceRecordState current = existing.get();
            boolean sameContent = Objects.equals(current.dataHash(), video.dataHash());
            boolean isNewer = observedAt.isAfter(current.observedAt());
            if (!sameContent && !isNewer && current.observedAt().isAfter(Instant.EPOCH)) {
                repository.recordVersion(current.id(), video, observedAt, false);
                return new PersistedRecord(current.id(), true, false, false);
            }
            if (sameContent && (!item.forceIngest() || !isNewer)) {
                if (isNewer) {
                    repository.advanceObservationTime(current.id(), observedAt);
                }
                boolean playbackCurrent = current.playbackCurrent();
                return new PersistedRecord(current.id(), true, !playbackCurrent,
                        playbackCurrent && "PENDING".equals(current.normalizationStatus()));
            }
        }
        UUID id = repository.upsertSourceRecord(video, existing.map(SourceRecordState::id).orElse(null));
        repository.recordVersion(id, video, observedAt, true);
        return new PersistedRecord(id, false, true, false);
    }

    @Transactional(timeout = 30)
    public PlaylistApplication syncPlaylists(PlaylistSyncMessage message) {
        var current = repository.lockPlaybackSyncState(message.videoId());
        if (current.isEmpty()) {
            return new PlaylistApplication(SourceRecordIngestionService.PlaylistSyncResult.missingVideo(message.videoId()), false);
        }
        var version = current.get();
        if (!Objects.equals(version.dataHash(), message.dataHash())) {
            return new PlaylistApplication(SourceRecordIngestionService.PlaylistSyncResult.stale(message.videoId()), false);
        }
        int playlistCount = version.playbackCurrent() ? 0 : repository.replacePlaylists(message.videoId(), version);
        return new PlaylistApplication(SourceRecordIngestionService.PlaylistSyncResult.synced(message.videoId(), playlistCount),
                "PENDING".equals(version.normalizationStatus()));
    }

    record PersistedRecord(UUID id, boolean skipped, boolean syncPlaylists, boolean normalize) {
    }

    record PlaylistApplication(SourceRecordIngestionService.PlaylistSyncResult result, boolean normalize) {
    }
}
