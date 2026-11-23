package com.prodigalgal.ircs.search.index;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.search.portal.cache.SearchPortalReadModelCache;
import com.prodigalgal.ircs.search.document.AuditEventSearchDocument;
import com.prodigalgal.ircs.search.document.SourceRecordSearchDocument;
import com.prodigalgal.ircs.search.document.CatalogEntrySearchDocument;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SearchIndexService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchPortalReadModelCache readModelCache;
    private final Set<Class<?>> ensuredIndexes = ConcurrentHashMap.newKeySet();

    public SearchIndexService(
            ElasticsearchOperations elasticsearchOperations,
            SearchPortalReadModelCache readModelCache) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.readModelCache = readModelCache;
    }

    @PostConstruct
    void ensureIndices() {
        ensureIndex(SourceRecordSearchDocument.class);
        ensureIndex(CatalogEntrySearchDocument.class);
        ensureIndex(AuditEventSearchDocument.class);
    }

    public void saveRaw(SourceRecordSearchDocument document) {
        ensureIndex(SourceRecordSearchDocument.class);
        elasticsearchOperations.save(document);
    }

    public void saveUnified(CatalogEntrySearchDocument document) {
        ensureIndex(CatalogEntrySearchDocument.class);
        elasticsearchOperations.save(document);
        readModelCache.evictPortalPublicReadModel();
    }

    public void saveAudit(AuditEventSearchDocument document) {
        ensureIndex(AuditEventSearchDocument.class);
        elasticsearchOperations.save(document);
    }

    public long deleteAuditOlderThan(Instant cutoff) {
        if (cutoff == null) {
            return 0L;
        }
        ensureIndex(AuditEventSearchDocument.class);
        CriteriaQuery query = new CriteriaQuery(Criteria.where("createdAt").lessThan(cutoff));
        DeleteQuery deleteQuery = DeleteQuery.builder(query)
                .withRefresh(Boolean.TRUE)
                .build();
        ByQueryResponse response = elasticsearchOperations.delete(deleteQuery, AuditEventSearchDocument.class);
        return response == null ? 0L : response.getDeleted();
    }

    public void delete(UUID id, SearchEntityType type) {
        if (type == SearchEntityType.SOURCE_RECORD) {
            ensureIndex(SourceRecordSearchDocument.class);
            elasticsearchOperations.delete(id.toString(), SourceRecordSearchDocument.class);
            return;
        }
        if (type == SearchEntityType.CATALOG_ENTRY) {
            ensureIndex(CatalogEntrySearchDocument.class);
            elasticsearchOperations.delete(id.toString(), CatalogEntrySearchDocument.class);
            readModelCache.evictPortalPublicReadModel();
        }
    }

    public boolean hardReset(SearchEntityType type) {
        Class<?> documentType = documentType(type);
        IndexOperations indexOperations = elasticsearchOperations.indexOps(documentType);
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
        boolean recreated = indexOperations.createWithMapping();
        ensuredIndexes.add(documentType);
        if (type == SearchEntityType.CATALOG_ENTRY) {
            readModelCache.evictPortalPublicReadModel();
        }
        return recreated;
    }

    public Set<UUID> existingRawIds(Collection<UUID> ids) {
        return existingIds(ids, SourceRecordSearchDocument.class);
    }

    public Set<UUID> existingUnifiedIds(Collection<UUID> ids) {
        return existingIds(ids, CatalogEntrySearchDocument.class);
    }

    private void ensureIndex(Class<?> documentType) {
        if (ensuredIndexes.contains(documentType)) {
            return;
        }
        IndexOperations indexOperations = elasticsearchOperations.indexOps(documentType);
        if (!indexOperations.exists()) {
            indexOperations.createWithMapping();
            log.info("Created Elasticsearch index for {}", documentType.getSimpleName());
        } else {
            indexOperations.putMapping();
        }
        ensuredIndexes.add(documentType);
    }

    private Class<?> documentType(SearchEntityType type) {
        if (type == SearchEntityType.SOURCE_RECORD) {
            return SourceRecordSearchDocument.class;
        }
        return CatalogEntrySearchDocument.class;
    }

    private <T> Set<UUID> existingIds(Collection<UUID> ids, Class<T> documentType) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        ensureIndex(documentType);
        List<String> values = ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.ids(i -> i.values(values)))
                .withMaxResults(values.size())
                .build();
        return elasticsearchOperations.search(query, documentType)
                .stream()
                .map(SearchHit::getId)
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }
}
