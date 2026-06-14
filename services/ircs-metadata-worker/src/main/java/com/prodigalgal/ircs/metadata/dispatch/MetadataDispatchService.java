package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.result.RawSearchSyncPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataDispatchService {

    private final RawVideoEnrichmentRepository repository;
    private final MetadataConfigValues configValues;
    private final MetadataPendingRegistry pendingRegistry;
    private final MetadataProviderTaskPublisher providerTaskPublisher;
    private final RawSearchSyncPublisher searchSyncPublisher;

    @Transactional
    public MetadataDispatchResult dispatch(UUID videoId) {
        Optional<RawVideoEnrichmentRecord> recordOpt = repository.findById(videoId);
        if (recordOpt.isEmpty()) {
            pendingRegistry.clear(videoId);
            log.warn("Metadata dispatch references missing raw video: {}", videoId);
            return new MetadataDispatchResult(videoId, false, null, List.of());
        }

        RawVideoEnrichmentRecord record = recordOpt.get();
        if ("SUCCESS".equals(record.getEnrichmentStatus())
                || "PERMANENT_FAILURE".equals(record.getEnrichmentStatus())) {
            return new MetadataDispatchResult(videoId, true, record.getEnrichmentStatus(), List.of());
        }

        if (!configValues.metadataEnabled()) {
            String status = finalizeImmediate(record);
            searchSyncPublisher.publishIndex(videoId);
            return new MetadataDispatchResult(videoId, true, status, List.of());
        }

        List<ProviderType> providers = providersFor(record);
        if (providers.isEmpty()) {
            String status = finalizeImmediate(record);
            searchSyncPublisher.publishIndex(videoId);
            return new MetadataDispatchResult(videoId, true, status, List.of());
        }

        MetadataSearchContext context = toContext(record);
        pendingRegistry.register(videoId, providers, configValues.pendingTtl());
        providers.forEach(provider -> providerTaskPublisher.publish(provider, context));
        log.info("Dispatched metadata fetch tasks: videoId={}, providers={}", videoId, providers);
        return new MetadataDispatchResult(videoId, true, "DISPATCHED", providers);
    }

    private String finalizeImmediate(RawVideoEnrichmentRecord record) {
        if (record.hasAuthoritativeId()) {
            repository.updateStatus(record.getId(), "SUCCESS", 0);
            return "SUCCESS";
        }

        int nextRetry = (record.getEnrichmentRetryCount() == null ? 0 : record.getEnrichmentRetryCount()) + 1;
        if (nextRetry >= RawVideoEnrichmentRepository.MAX_ENRICHMENT_RETRIES) {
            repository.updateStatus(record.getId(), "PERMANENT_FAILURE", record.getEnrichmentRetryCount());
            return "PERMANENT_FAILURE";
        }

        repository.updateStatus(record.getId(), "FAILED", nextRetry);
        return "FAILED";
    }

    private List<ProviderType> providersFor(RawVideoEnrichmentRecord record) {
        List<ProviderType> providers = new ArrayList<>();
        if (configValues.doubanEnabled() && !RawVideoEnrichmentRecord.hasText(record.getDoubanId())) {
            providers.add(ProviderType.DOUBAN);
        }
        if (configValues.tmdbEnabled() && !RawVideoEnrichmentRecord.hasText(record.getTmdbId())) {
            providers.add(ProviderType.TMDB);
        }
        if (configValues.rottenTomatoesEnabled() && !RawVideoEnrichmentRecord.hasText(record.getRottenTomatoesId())) {
            providers.add(ProviderType.ROTTEN_TOMATOES);
        }
        return providers;
    }

    private MetadataSearchContext toContext(RawVideoEnrichmentRecord record) {
        return MetadataSearchContext.builder()
                .videoId(record.getId())
                .title(record.getTitle())
                .aliasTitle(record.getAliasTitle())
                .subtitle(record.getSubtitle())
                .season(record.getSeason())
                .year(record.getYear())
                .categorySlug(record.getCategorySlug())
                .doubanId(record.getDoubanId())
                .tmdbId(record.getTmdbId())
                .imdbId(record.getImdbId())
                .rottenTomatoesId(record.getRottenTomatoesId())
                .build();
    }
}
