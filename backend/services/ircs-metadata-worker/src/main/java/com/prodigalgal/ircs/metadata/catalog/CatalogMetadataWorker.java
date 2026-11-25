package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.metadata.MetadataNameCache;
import com.prodigalgal.ircs.common.metadata.MetadataNameOwnerType;
import com.prodigalgal.ircs.common.metadata.MetadataNameRelation;
import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogMetadataWorker {
    private final CatalogMetadataRepository repository;
    private final CatalogMetadataSource source;
    private final RuntimeConfigService config;
    private final SearchSyncWorkPublisher search;
    private final MetadataNameCache nameCache;

    public void runBatch() {
        flushIndexes();
        if (!config.booleanValue("app.metadata.catalog.enabled",true)) return;
        int batch=config.boundedIntValue("app.metadata.catalog.batch-size",5,1,5);
        // Claim just before each request, so waiting work does not lose its visibility lease.
        for (int n=0; n<batch; n++) {
            List<CatalogMetadataTarget> claimed=repository.claim(1);
            if (claimed.isEmpty()) break;
            CatalogMetadataTarget target=claimed.getFirst();
            try {
                CatalogMetadataEvidence evidence=source.fetch(target);
                if (repository.renew(target)) repository.apply(target,evidence);
            } catch (CatalogMetadataUnavailableException ex) {
                repository.defer(target,true,ex.getMessage());
            } catch (RuntimeException ex) {
                repository.defer(target,false,"SOURCE_FAILED");
                log.warn("Catalog metadata deferred: id={}, errorType={}",target.id(),ex.getClass().getSimpleName());
            }
        }
        flushIndexes();
    }

    private void flushIndexes() {
        for (var pending: repository.pendingIndexes()) {
            try {
                for (MetadataNameRelation relation: MetadataNameRelation.values()) {
                    nameCache.evictNames(MetadataNameOwnerType.UNIFIED,relation,List.of(pending.id()));
                }
                search.enqueue(pending.id(),SearchEntityType.UNIFIED_VIDEO,SyncOperation.INDEX);
                repository.indexed(pending);
            } catch (RuntimeException ex) {
                log.warn("Catalog search handoff deferred: id={}, errorType={}",pending.id(),ex.getClass().getSimpleName());
                break;
            }
        }
    }
}
