package com.prodigalgal.ircs.search.sync;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import com.prodigalgal.ircs.search.index.SearchIndexService;
import com.prodigalgal.ircs.search.repository.SearchDocumentJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class JdbcSearchSyncProcessor implements SearchSyncProcessor {

    private final SearchDocumentJdbcRepository documentRepository;
    private final SearchIndexService searchIndexService;

    @Override
    public void process(SearchSyncMessage message) {
        if (message.getEntityType() == SearchEntityType.CATALOG_ENTRY) {
            // A queued catalog event can be older than the entry's current publication state.
            documentRepository.findCatalogEntry(message.getEntityId())
                    .ifPresentOrElse(
                            searchIndexService::saveUnified,
                            () -> searchIndexService.delete(message.getEntityId(), SearchEntityType.CATALOG_ENTRY));
            return;
        }

        if (message.getOperation() == SyncOperation.DELETE) {
            searchIndexService.delete(message.getEntityId(), message.getEntityType());
            log.debug("Deleted search document: [{}][{}]", message.getEntityType(), message.getEntityId());
            return;
        }

        if (message.getEntityType() == SearchEntityType.SOURCE_RECORD) {
            documentRepository.findSourceRecord(message.getEntityId())
                    .ifPresentOrElse(
                            searchIndexService::saveRaw,
                            () -> searchIndexService.delete(message.getEntityId(), SearchEntityType.SOURCE_RECORD));
            return;
        }

    }
}
