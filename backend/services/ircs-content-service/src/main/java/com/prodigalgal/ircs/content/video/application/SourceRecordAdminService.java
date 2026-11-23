package com.prodigalgal.ircs.content.video.application;




import com.prodigalgal.ircs.content.video.infrastructure.InternalContentClients;
import com.prodigalgal.ircs.content.video.messaging.ContentCommandPublisher;
import com.prodigalgal.ircs.content.video.infrastructure.JdbcVideoAdminRepository;
import com.prodigalgal.ircs.content.video.infrastructure.JdbcVideoAdminRepository.SourceRecordSnapshot;
import com.prodigalgal.ircs.content.maintenance.application.ContentMaintenanceGate;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordCardResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordCreateRequest;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordDetailResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordUpdateRequest;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class SourceRecordAdminService {

    private final JdbcVideoAdminRepository repository;
    private final ContentCommandPublisher publisher;
    private final InternalContentClients internalClients;
    private final ContentMaintenanceGate maintenanceGate;

    public Page<SourceRecordCardResponse> findAll(
            Pageable pageable,
            String title,
            UUID categoryId,
            String enrichmentStatus,
            String normalizationStatus,
            String aggregationStatus,
            String year,
            String area,
            BigDecimal minScore,
            Boolean isMissingSlug,
            UUID dataSourceId,
            String sourceCategoryName,
            String genre,
            String language) {
        return repository.findSourceRecords(pageable, title, categoryId, enrichmentStatus, normalizationStatus,
                aggregationStatus, year, area, minScore, isMissingSlug, dataSourceId, sourceCategoryName, genre, language);
    }

    public Optional<SourceRecordDetailResponse> findOne(UUID id) {
        return repository.findRawDetail(id);
    }

    @Transactional
    public UUID create(SourceRecordCreateRequest request) {
        maintenanceGate.assertSourceRecordWrite(null);
        UUID id = repository.createSourceRecord(request);
        publisher.publishRawSearch(id, SyncOperation.INDEX);
        return id;
    }

    @Transactional
    public void update(UUID id, SourceRecordUpdateRequest request) {
        maintenanceGate.assertSourceRecordWrite(id);
        repository.lockSourceRecord(id);
        SourceRecordSnapshot before = repository.rawSnapshot(id);
        repository.updateSourceRecord(id, request);

        boolean criticalChanged = changed(request.title(), before.title())
                || changed(request.aliasTitle(), before.aliasTitle())
                || changed(request.year(), before.year())
                || changed(request.season(), before.season());

        if (criticalChanged) {
            if (before.catalogEntryId() != null) {
                repository.markUnifiedDirty(before.catalogEntryId());
            }
            repository.unbindRaw(id);
        } else if (before.catalogEntryId() != null) {
            repository.markUnifiedDirty(before.catalogEntryId());
        }

        if (request.catalogEntryId() != null) {
            repository.bindRawToUnified(id, request.catalogEntryId());
            if (before.catalogEntryId() != null && !before.catalogEntryId().equals(request.catalogEntryId())) {
                publisher.publishUnifiedSearch(before.catalogEntryId(), SyncOperation.INDEX);
            }
            publisher.publishUnifiedSearch(request.catalogEntryId(), SyncOperation.INDEX);
        }
        publisher.publishRawSearch(id, SyncOperation.INDEX);
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
        ids.forEach(maintenanceGate::assertSourceRecordWrite);
        List<UUID> unifiedIds = repository.findRawUnifiedBindings(ids);
        repository.deleteSourceRecords(ids);
        ids.forEach(id -> publisher.publishRawSearch(id, SyncOperation.DELETE));
        unifiedIds.forEach(id -> {
            repository.markUnifiedDirty(id);
            publisher.publishUnifiedSearch(id, SyncOperation.INDEX);
        });
    }

    public void batchSyncSearch(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        ids.forEach(id -> publisher.publishRawSearch(id, SyncOperation.INDEX));
    }

    @Transactional
    public void reNormalize(UUID id) {
        maintenanceGate.assertSourceRecordWrite(id);
        SourceRecordSnapshot snapshot = repository.rawSnapshot(id);
        repository.setRawNormalizationPending(id);
        publisher.publishNormalize(id, snapshot.dataHash());
    }

    public void reEnrich(UUID id) {
        maintenanceGate.assertSourceRecordWrite(id);
        SourceRecordSnapshot snapshot = repository.rawSnapshot(id);
        publisher.publishEnrich(id, snapshot.dataHash());
    }

    public void reFetchFromSource(UUID id) {
        maintenanceGate.assertSourceRecordWrite(id);
        repository.rawSnapshot(id);
        internalClients.refetchSourceRecord(id);
    }

    @Transactional
    public void batchReNormalize(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        ids.forEach(this::reNormalize);
    }

    public void batchReEnrich(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        ids.forEach(this::reEnrich);
    }

    public void batchReFetch(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        ids.forEach(this::reFetchFromSource);
    }

    private boolean changed(Object incoming, Object current) {
        return incoming != null && !Objects.equals(incoming, current);
    }
}
