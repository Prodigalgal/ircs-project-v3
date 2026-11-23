package com.prodigalgal.ircs.content.image;

import com.prodigalgal.ircs.content.video.messaging.ContentCommandPublisher;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoverImageUnlinkService {

    private final CoverImageReferenceRepository referenceRepository;
    private final CoverImageUnlinkedPublisher unlinkedPublisher;
    private final ContentCommandPublisher searchPublisher;

    @Transactional
    public CoverImageUnlinkResult unlink(UUID imageId) {
        List<UUID> sourceRecordIds = referenceRepository.findSourceRecordIds(imageId);
        List<UUID> catalogEntryIds = referenceRepository.findCatalogEntryIds(imageId);
        int sourceRecordCount = referenceRepository.unlinkSourceRecords(imageId);
        int catalogEntryCount = referenceRepository.unlinkCatalogEntries(imageId);

        log.info(
                "Unlinked cover image {} from {} raw videos and {} unified videos",
                imageId,
                sourceRecordCount,
                catalogEntryCount);
        publishAfterCommit(() -> {
            log.info(
                    "Publishing search refresh after cover image unlink: imageId={}, rawIds={}, unifiedIds={}",
                    imageId,
                    sourceRecordIds,
                    catalogEntryIds);
            sourceRecordIds.forEach(id -> {
                searchPublisher.publishRawSearch(id, SyncOperation.DELETE);
                searchPublisher.publishRawSearch(id, SyncOperation.INDEX);
            });
            catalogEntryIds.forEach(id -> {
                searchPublisher.publishUnifiedSearch(id, SyncOperation.DELETE);
                searchPublisher.publishUnifiedSearch(id, SyncOperation.INDEX);
            });
            unlinkedPublisher.publish(imageId);
        });
        return new CoverImageUnlinkResult(imageId, sourceRecordCount, catalogEntryCount);
    }

    private void publishAfterCommit(Runnable publisher) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.run();
            }
        });
    }
}
