package com.prodigalgal.ircs.content.video.application;




import com.prodigalgal.ircs.content.video.messaging.ContentCommandPublisher;
import com.prodigalgal.ircs.content.video.infrastructure.JdbcVideoAdminRepository;
import com.prodigalgal.ircs.content.video.api.ContentApiException;
import com.prodigalgal.ircs.content.maintenance.application.ContentMaintenanceGate;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryCardResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryCreateRequest;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryDetailResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryUpdateRequest;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class CatalogEntryAdminService {

    private final JdbcVideoAdminRepository repository;
    private final ContentCommandPublisher publisher;
    private final ContentMaintenanceGate maintenanceGate;

    public Page<CatalogEntryCardResponse> findAll(
            Pageable pageable,
            String title,
            UUID categoryId,
            String year,
            String area,
            BigDecimal minScore,
            Boolean hasDoubanId,
            Boolean hasTmdbId,
            String contentVisibility,
            String metadataStatus,
            String genre,
            String language,
            String actor,
            String director) {
        return repository.findCatalogEntries(
                pageable,
                title,
                categoryId,
                year,
                area,
                minScore,
                hasDoubanId,
                hasTmdbId,
                contentVisibility,
                metadataStatus,
                genre,
                language,
                actor,
                director);
    }

    public Optional<CatalogEntryDetailResponse> findOne(UUID id) {
        return repository.findUnifiedDetail(id);
    }

    @Transactional
    public CatalogEntryDetailResponse create(CatalogEntryCreateRequest request) {
        maintenanceGate.assertCatalogEntryWrite(null);
        UUID id = repository.createCatalogEntry(request);
        publisher.publishUnifiedSearch(id, SyncOperation.INDEX);
        if (!CollectionUtils.isEmpty(request.bindVideoIds())) {
            request.bindVideoIds().forEach(rawId -> publisher.publishRawSearch(rawId, SyncOperation.INDEX));
        }
        return repository.findUnifiedDetail(id)
                .orElseThrow(() -> new ContentApiException(HttpStatus.NOT_FOUND, "Unified video not found: " + id));
    }

    @Transactional
    public CatalogEntryDetailResponse update(UUID id, CatalogEntryUpdateRequest request) {
        maintenanceGate.assertCatalogEntryWrite(id);
        repository.updateCatalogEntry(id, request);
        publisher.publishUnifiedSearch(id, SyncOperation.INDEX);
        if (!CollectionUtils.isEmpty(request.bindVideoIds())) {
            request.bindVideoIds().forEach(rawId -> publisher.publishRawSearch(rawId, SyncOperation.INDEX));
        }
        if (!CollectionUtils.isEmpty(request.unbindVideoIds())) {
            request.unbindVideoIds().forEach(rawId -> publisher.publishRawSearch(rawId, SyncOperation.INDEX));
        }
        return repository.findUnifiedDetail(id)
                .orElseThrow(() -> new ContentApiException(HttpStatus.NOT_FOUND, "Unified video not found: " + id));
    }

    @Transactional
    public void delete(UUID id) {
        batchDelete(List.of(id));
    }

    @Transactional
    public void batchDelete(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        ids.forEach(maintenanceGate::assertCatalogEntryWrite);
        List<UUID> rawIds = repository.findRawIdsForCatalogEntries(ids);
        repository.deleteCatalogEntries(ids);
        ids.forEach(id -> publisher.publishUnifiedSearch(id, SyncOperation.DELETE));
        rawIds.forEach(id -> publisher.publishRawSearch(id, SyncOperation.INDEX));
    }

    public void batchSyncSearch(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        ids.forEach(id -> publisher.publishUnifiedSearch(id, SyncOperation.INDEX));
    }

    @Transactional
    public CatalogEntryDetailResponse recalculateMetadata(UUID id) {
        maintenanceGate.assertCatalogEntryWrite(id);
        List<UUID> rawIds = repository.recalculateUnifiedFromSources(id);
        rawIds.forEach(rawId -> publisher.publishRawSearch(rawId, SyncOperation.INDEX));
        publisher.publishUnifiedSearch(id, SyncOperation.INDEX);
        return repository.findUnifiedDetail(id)
                .orElseThrow(() -> new ContentApiException(HttpStatus.NOT_FOUND, "Unified video not found: " + id));
    }
}
