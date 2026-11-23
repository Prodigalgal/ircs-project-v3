package com.prodigalgal.ircs.search.sync;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import com.prodigalgal.ircs.search.document.SourceRecordSearchDocument;
import com.prodigalgal.ircs.search.document.CatalogEntrySearchDocument;
import com.prodigalgal.ircs.search.index.SearchIndexService;
import com.prodigalgal.ircs.search.repository.SearchDocumentJdbcRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcSearchSyncProcessorTest {

    @Mock
    private SearchDocumentJdbcRepository documentRepository;

    @Mock
    private SearchIndexService searchIndexService;

    @Test
    void indexesSourceRecordWhenDocumentExists() {
        UUID id = UUID.randomUUID();
        SourceRecordSearchDocument document = new SourceRecordSearchDocument();
        document.setId(id);
        when(documentRepository.findSourceRecord(id)).thenReturn(Optional.of(document));

        JdbcSearchSyncProcessor processor = new JdbcSearchSyncProcessor(documentRepository, searchIndexService);
        processor.process(new SearchSyncMessage(id, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX));

        verify(searchIndexService).saveRaw(document);
        verify(searchIndexService, never()).delete(id, SearchEntityType.SOURCE_RECORD);
    }

    @Test
    void deletesSourceRecordWhenIndexMessageReferencesMissingEntity() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findSourceRecord(id)).thenReturn(Optional.empty());

        JdbcSearchSyncProcessor processor = new JdbcSearchSyncProcessor(documentRepository, searchIndexService);
        processor.process(new SearchSyncMessage(id, SearchEntityType.SOURCE_RECORD, SyncOperation.INDEX));

        verify(searchIndexService).delete(id, SearchEntityType.SOURCE_RECORD);
    }

    @Test
    void aStaleCatalogDeleteReconcilesTheCurrentPublication() {
        UUID id = UUID.randomUUID();
        CatalogEntrySearchDocument current = new CatalogEntrySearchDocument();
        current.setId(id);
        when(documentRepository.findCatalogEntry(id)).thenReturn(Optional.of(current));
        JdbcSearchSyncProcessor processor = new JdbcSearchSyncProcessor(documentRepository, searchIndexService);
        processor.process(new SearchSyncMessage(id, SearchEntityType.CATALOG_ENTRY, SyncOperation.DELETE));

        verify(searchIndexService).saveUnified(current);
        verify(searchIndexService, never()).delete(id, SearchEntityType.CATALOG_ENTRY);
    }
}
