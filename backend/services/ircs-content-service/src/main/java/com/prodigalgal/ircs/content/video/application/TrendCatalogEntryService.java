package com.prodigalgal.ircs.content.video.application;

import com.prodigalgal.ircs.content.maintenance.application.ContentMaintenanceGate;
import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.content.video.infrastructure.TrendCatalogRepository;
import com.prodigalgal.ircs.content.video.messaging.ContentCommandPublisher;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrendCatalogEntryService {
    private final TrendCatalogRepository repository;
    private final ContentCommandPublisher publisher;
    private final ContentMaintenanceGate maintenanceGate;

    @Transactional(timeout = 15)
    public Resolution resolve(TrendCatalogCandidate candidate) {
        maintenanceGate.assertUnifiedVideoWrite(null);
        repository.lockIdentity(candidate);
        var mapped = repository.findIdentity(candidate);
        if (mapped.isPresent()) {
            repository.requestMissingResources(candidate, mapped.get());
            repository.requestMissingMetadata(candidate, mapped.get());
            return new Resolution(mapped.get(), false);
        }
        var existing = repository.findExisting(candidate);
        boolean created = existing.isEmpty();
        UUID id = existing.orElseGet(() -> repository.create(candidate));
        repository.bind(candidate, id);
        repository.requestMissingResources(candidate, id);
        repository.requestMissingMetadata(candidate, id);
        if (created) publisher.publishUnifiedSearch(id, SyncOperation.INDEX);
        return new Resolution(id, created);
    }

    public record Resolution(UUID id, boolean created) { }
}
