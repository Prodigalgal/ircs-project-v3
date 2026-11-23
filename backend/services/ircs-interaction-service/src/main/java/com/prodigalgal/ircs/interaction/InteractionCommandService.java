package com.prodigalgal.ircs.interaction;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InteractionCommandService {

    private final JdbcInteractionRepository repository;

    @Transactional
    public boolean toggleFavorite(UUID memberId, UUID catalogEntryId) {
        requireCatalogEntry(catalogEntryId);
        if (repository.deleteFavorite(memberId, catalogEntryId) > 0) {
            return false;
        }
        Instant now = Instant.now();
        repository.insertFavorite(IrcsUuidGenerators.nextId(), memberId, catalogEntryId, now);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean favoriteStatus(UUID memberId, UUID catalogEntryId) {
        requireUuid(catalogEntryId, "影片标识不能为空");
        return repository.favoriteExists(memberId, catalogEntryId);
    }

    @Transactional
    public void reportProgress(UUID memberId, ProgressReportRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "观看进度不能为空");
        }
        requireCatalogEntry(request.catalogEntryId());
        if (!StringUtils.hasText(request.episodeName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "剧集名称不能为空");
        }
        Instant now = Instant.now();
        boolean saved = repository.upsertWatchHistory(
                IrcsUuidGenerators.nextId(),
                memberId,
                request.catalogEntryId(),
                request.videoId(),
                request.episodeId(),
                request.episodeName().trim(),
                Math.max(request.progress(), 0),
                Math.max(request.duration(), 0),
                now);
        if (!saved) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "播放来源或剧集不属于该作品");
        }
    }

    @Transactional
    public void clearHistory(UUID memberId) {
        repository.deleteHistory(memberId);
    }

    @Transactional
    public void deleteHistoryRecord(UUID memberId, UUID historyId) {
        requireUuid(historyId, "历史记录标识不能为空");
        repository.deleteHistoryRecord(memberId, historyId);
    }

    private void requireCatalogEntry(UUID catalogEntryId) {
        requireUuid(catalogEntryId, "影片标识不能为空");
        if (!repository.catalogEntryExists(catalogEntryId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "影片不存在");
        }
    }

    private void requireUuid(UUID value, String message) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }
}

