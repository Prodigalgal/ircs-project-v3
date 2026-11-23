package com.prodigalgal.ircs.metadata.dispatch.application;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.config.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.dispatch.domain.SourceRecordEnrichmentRecord;
import com.prodigalgal.ircs.metadata.dispatch.dto.MetadataDispatchResult;
import com.prodigalgal.ircs.metadata.dispatch.infrastructure.SourceRecordEnrichmentRepository;
import com.prodigalgal.ircs.metadata.dispatch.messaging.MetadataProviderTaskPublisher;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository;
import com.prodigalgal.ircs.metadata.result.messaging.RawSearchSyncPublisher;
import com.prodigalgal.ircs.metadata.result.application.MetadataResultCollectorService;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataEnrichmentPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataDispatchService {

    private final SourceRecordEnrichmentRepository repository;
    private final MetadataConfigValues configValues;
    private final MetadataPipelineRunRepository pipelineRunRepository;
    private final MetadataProviderTaskPublisher providerTaskPublisher;
    private final RawSearchSyncPublisher searchSyncPublisher;
    private final AggregationWorkPublisher aggregationWorkPublisher;
    private final MetadataResultCollectorService resultCollector;

    public MetadataDispatchResult dispatch(UUID videoId, String pipelineVersion) {
        Optional<SourceRecordEnrichmentRecord> recordOpt = repository.findById(videoId);
        if (recordOpt.isEmpty()) {
            log.warn("Metadata dispatch references missing raw video: {}", videoId);
            return new MetadataDispatchResult(videoId, false, null, List.of());
        }

        SourceRecordEnrichmentRecord record = recordOpt.get();
        if (isStale(pipelineVersion, record.getDataHash())) {
            return new MetadataDispatchResult(videoId, true, "STALE", List.of());
        }

        if (("SUCCESS".equals(record.getEnrichmentStatus()) && record.hasAuthoritativeId())
                || "PERMANENT_FAILURE".equals(record.getEnrichmentStatus())) {
            searchSyncPublisher.publishIndex(videoId);
            aggregationWorkPublisher.enqueue(videoId, record.getDataHash(), "metadata-already-finalized");
            return new MetadataDispatchResult(videoId, true, record.getEnrichmentStatus(), List.of());
        }

        List<ProviderType> providers = providersFor(record);
        if (providers.isEmpty()) {
            String status = finalizeImmediate(record);
            if ("STALE".equals(status)) {
                return new MetadataDispatchResult(videoId, true, status, List.of());
            }
            searchSyncPublisher.publishIndex(videoId);
            aggregationWorkPublisher.enqueue(videoId, record.getDataHash(), "metadata-immediate-finalized");
            return new MetadataDispatchResult(videoId, true, status, List.of());
        }

        MetadataSearchContext context = toContext(record);
        List<ProviderType> pendingProviders =
                pipelineRunRepository.prepareDispatch(videoId, record.getDataHash(), providers);
        if (pendingProviders.isEmpty()) {
            var reconciled = resultCollector.reconcileCompletedRun(videoId, record.getDataHash());
            if (reconciled != null && (!reconciled.videoFound() || !"PENDING".equals(reconciled.enrichmentStatus()))) {
                return new MetadataDispatchResult(videoId, reconciled.videoFound(), reconciled.enrichmentStatus(), List.of());
            }
            throw new IllegalStateException("Metadata providers require enrichment but no provider run is dispatchable: " + videoId);
        }
        pendingProviders.forEach(provider -> providerTaskPublisher.publish(provider, context));
        log.info("Dispatched metadata provider Valkey tasks: videoId={}, providers={}", videoId, pendingProviders);
        return new MetadataDispatchResult(videoId, true, "DISPATCHED", pendingProviders);
    }

    private String finalizeImmediate(SourceRecordEnrichmentRecord record) {
        if (record.hasAuthoritativeId()) {
            return repository.updateStatus(record.getId(), record.getDataHash(), "SUCCESS", 0) == 1 ? "SUCCESS" : "STALE";
        }

        // No applicable/configured provider is a terminal source-only outcome, not a transient
        // external failure. Aggregation is still allowed to use the reliable source evidence.
        return repository.updateStatus(record.getId(), record.getDataHash(), "PERMANENT_FAILURE", 0) == 1
                ? "PERMANENT_FAILURE" : "STALE";
    }

    private List<ProviderType> providersFor(SourceRecordEnrichmentRecord record) {
        List<ProviderType> providers = new ArrayList<>();
        MetadataSearchContext context = toContext(record);
        if (configValues.doubanEnabled() && AuthoritativeIds.normalizeDouban(record.getDoubanId()) == null
                && MetadataEnrichmentPolicy.supports(context, ProviderType.DOUBAN)) {
            providers.add(ProviderType.DOUBAN);
        }
        if (configValues.tmdbEnabled() && AuthoritativeIds.normalizeTmdb(record.getTmdbId()) == null
                && MetadataEnrichmentPolicy.supports(context, ProviderType.TMDB)) {
            providers.add(ProviderType.TMDB);
        }
        if (configValues.rottenTomatoesEnabled() && AuthoritativeIds.normalizeRottenTomatoes(record.getRottenTomatoesId()) == null
                && MetadataEnrichmentPolicy.supports(context, ProviderType.ROTTEN_TOMATOES)) {
            providers.add(ProviderType.ROTTEN_TOMATOES);
        }
        return providers;
    }

    private MetadataSearchContext toContext(SourceRecordEnrichmentRecord record) {
        return MetadataSearchContext.builder()
                .videoId(record.getId())
                .pipelineVersion(record.getDataHash())
                .title(record.getTitle())
                .aliasTitle(record.getAliasTitle())
                .subtitle(record.getSubtitle())
                .season(record.getSeason())
                .year(record.getYear())
                .categorySlug(record.getCategorySlug())
                .doubanId(AuthoritativeIds.normalizeDouban(record.getDoubanId()))
                .tmdbId(AuthoritativeIds.normalizeTmdb(record.getTmdbId()))
                .imdbId(AuthoritativeIds.normalizeImdb(record.getImdbId()))
                .rottenTomatoesId(AuthoritativeIds.normalizeRottenTomatoes(record.getRottenTomatoesId()))
                .build();
    }

    private boolean isStale(String pipelineVersion, String currentDataHash) {
        if (StringUtils.hasText(currentDataHash)) {
            return !StringUtils.hasText(pipelineVersion) || !pipelineVersion.trim().equals(currentDataHash);
        }
        return StringUtils.hasText(pipelineVersion);
    }
}
