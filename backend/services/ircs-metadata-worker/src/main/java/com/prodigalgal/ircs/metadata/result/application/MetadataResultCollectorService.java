package com.prodigalgal.ircs.metadata.result.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.metadata.config.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.dispatch.messaging.MetadataEnrichPublisher;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository;
import com.prodigalgal.ircs.metadata.pipeline.MetadataPipelineRunRepository.ProviderCompletion;
import com.prodigalgal.ircs.metadata.result.domain.RawVideoMetadataRecord;
import com.prodigalgal.ircs.metadata.result.dto.MetadataResultProcessingResult;
import com.prodigalgal.ircs.metadata.result.infrastructure.RawVideoMetadataRepository;
import com.prodigalgal.ircs.metadata.result.messaging.RawSearchSyncPublisher;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataResultCollectorService {

    private static final int MAX_ENRICHMENT_RETRIES = 5;

    private final RawVideoMetadataRepository repository;
    private final RawSearchSyncPublisher searchSyncPublisher;
    private final MetadataPipelineRunRepository pipelineRunRepository;
    private final MetadataEnrichPublisher metadataEnrichPublisher;
    private final AggregationWorkPublisher aggregationWorkPublisher;
    private final MetadataConfigValues configValues;
    private final ObjectMapper objectMapper;

    @Transactional(timeout = 30)
    public MetadataResultProcessingResult reconcileCompletedRun(UUID videoId, String pipelineVersion) {
        // Match the normal result writer's pipeline -> raw lock order. This path has no provider HTTP calls.
        ProviderCompletion completion = pipelineRunRepository.findCompletionForUpdate(videoId, pipelineVersion)
                .orElse(ProviderCompletion.untracked());
        if (!completion.allCompleted()) {
            return new MetadataResultProcessingResult(videoId, true, "PENDING");
        }
        Optional<RawVideoMetadataRecord> current = repository.findByIdForUpdate(videoId);
        if (current.isEmpty()) {
            return new MetadataResultProcessingResult(videoId, false, null);
        }
        RawVideoMetadataRecord record = current.orElseThrow();
        if (isStale(pipelineVersion, record.getDataHash())) {
            return new MetadataResultProcessingResult(videoId, true, "STALE");
        }
        if (!"PENDING".equals(record.getEnrichmentStatus())) {
            return new MetadataResultProcessingResult(videoId, true, record.getEnrichmentStatus());
        }
        finalizeVideoStatus(record, completion, false);
        if (repository.updateEnrichmentStatusIfPending(videoId, record.getDataHash(), record.getEnrichmentStatus(),
                record.getEnrichmentRetryCount()) != 1) {
            return new MetadataResultProcessingResult(videoId, true, "STALE");
        }
        pipelineRunRepository.markRunFinished(videoId, record.getDataHash(), record.getEnrichmentStatus());
        searchSyncPublisher.publishIndex(videoId);
        aggregationWorkPublisher.enqueue(videoId, record.getDataHash(), "metadata-completed-run-reconciled");
        return new MetadataResultProcessingResult(videoId, true, record.getEnrichmentStatus());
    }

    @Transactional
    public MetadataResultProcessingResult process(EnrichmentResultMsg message, int expectedAttempt) {
        if (expectedAttempt < 0) {
            throw new IllegalArgumentException("provider attempt is required");
        }
        Optional<RawVideoMetadataRecord> recordOpt = repository.findById(message.getVideoId());
        if (recordOpt.isEmpty()) {
            log.warn("Metadata result references missing raw video: {}", message.getVideoId());
            return new MetadataResultProcessingResult(message.getVideoId(), false, null);
        }

        RawVideoMetadataRecord record = recordOpt.get();
        if (isStale(message.getPipelineVersion(), record.getDataHash())) {
            log.info(
                    "Ignoring stale metadata result: videoId={}, provider={}, commandVersion={}, currentVersion={}",
                    message.getVideoId(),
                    message.getProviderType(),
                    message.getPipelineVersion(),
                    record.getDataHash());
            return new MetadataResultProcessingResult(record.getId(), true, "STALE");
        }

        boolean validResult = message.isSuccess() && validProviderIdentity(message.getProviderType(), message.getMetadata());
        boolean invalidSuccess = message.isSuccess() && !validResult;
        ProviderCompletion completion = pipelineRunRepository.completeProvider(
                record.getId(),
                record.getDataHash(),
                message.getProviderType(),
                validResult,
                !invalidSuccess && message.isRetryable(),
                invalidSuccess ? "INVALID_PROVIDER_ID" : message.getErrorCode(),
                invalidSuccess ? "Provider result did not contain a usable provider identifier" : message.getErrorMessage(),
                expectedAttempt);
        if (!completion.tracked()) {
            log.warn(
                    "Metadata result has no durable provider run: videoId={}, provider={}, pipelineVersion={}",
                    record.getId(),
                    message.getProviderType(),
                    message.getPipelineVersion());
            return new MetadataResultProcessingResult(record.getId(), true, record.getEnrichmentStatus());
        }
        if (!completion.newlyCompleted()) {
            return new MetadataResultProcessingResult(record.getId(), true, record.getEnrichmentStatus());
        }
        // completeProvider holds the pipeline lock. Match reconciliation's pipeline -> raw lock order,
        // then re-read so another provider result or a new scrape cannot be overwritten by our snapshot.
        Optional<RawVideoMetadataRecord> locked = repository.findByIdForUpdate(record.getId());
        if (locked.isEmpty()) {
            return new MetadataResultProcessingResult(record.getId(), false, null);
        }
        record = locked.orElseThrow();
        if (isStale(message.getPipelineVersion(), record.getDataHash())) {
            return new MetadataResultProcessingResult(record.getId(), true, "STALE");
        }
        normalizeIdentifiers(record);
        boolean metadataUpdated = validResult && applyMetadata(record, message.getMetadata());
        if (!completion.allCompleted()) {
            saveCurrent(record);
            return new MetadataResultProcessingResult(record.getId(), true, record.getEnrichmentStatus());
        }

        finalizeVideoStatus(record, completion, metadataUpdated);
        saveCurrent(record);
        pipelineRunRepository.markRunFinished(record.getId(), record.getDataHash(), record.getEnrichmentStatus());
        searchSyncPublisher.publishIndex(record.getId());
        aggregationWorkPublisher.enqueue(record.getId(), record.getDataHash(), "metadata-finalized");
        scheduleRetryIfNeeded(record);

        return new MetadataResultProcessingResult(record.getId(), true, record.getEnrichmentStatus());
    }

    private void saveCurrent(RawVideoMetadataRecord record) {
        if (repository.save(record) != 1) {
            throw new IllegalStateException("Metadata input changed before projection was saved: " + record.getId());
        }
    }

    private boolean validProviderIdentity(ProviderType provider, EnrichedMetadataDTO metadata) {
        if (provider == null || metadata == null) {
            return false;
        }
        return switch (provider) {
            case DOUBAN -> AuthoritativeIds.normalizeDouban(metadata.getDoubanId()) != null;
            case TMDB -> AuthoritativeIds.normalizeTmdb(metadata.getTmdbId()) != null;
            case ROTTEN_TOMATOES -> AuthoritativeIds.normalizeRottenTomatoes(metadata.getRottenTomatoesId()) != null;
            default -> false;
        };
    }

    private void normalizeIdentifiers(RawVideoMetadataRecord record) {
        if (!record.isFieldLocked("doubanId")) record.setDoubanId(AuthoritativeIds.normalizeDouban(record.getDoubanId()));
        if (!record.isFieldLocked("tmdbId")) record.setTmdbId(AuthoritativeIds.normalizeTmdb(record.getTmdbId()));
        if (!record.isFieldLocked("imdbId")) record.setImdbId(AuthoritativeIds.normalizeImdb(record.getImdbId()));
        if (!record.isFieldLocked("rottenTomatoesId")) record.setRottenTomatoesId(
                AuthoritativeIds.normalizeRottenTomatoes(record.getRottenTomatoesId()));
    }

    private void scheduleRetryIfNeeded(RawVideoMetadataRecord record) {
        if (!"FAILED".equals(record.getEnrichmentStatus())) {
            return;
        }
        try {
            metadataEnrichPublisher.publish(record.getId(), record.getDataHash(), configValues.retryDelay());
        } catch (RuntimeException ex) {
            log.warn(
                    "Metadata retry Valkey submit failed: videoId={}, pipelineVersion={}, error={}",
                    record.getId(),
                    record.getDataHash(),
                    ex.getMessage());
            throw ex;
        }
    }

    private boolean isStale(String pipelineVersion, String currentDataHash) {
        if (StringUtils.hasText(currentDataHash)) {
            return !StringUtils.hasText(pipelineVersion) || !pipelineVersion.trim().equals(currentDataHash);
        }
        return StringUtils.hasText(pipelineVersion);
    }

    private boolean applyMetadata(RawVideoMetadataRecord record, EnrichedMetadataDTO metadata) {
        boolean updated = false;
        updated |= !record.isFieldLocked("doubanId") && fillAuthoritativeId(record::getDoubanId, record::setDoubanId,
                AuthoritativeIds.normalizeDouban(metadata.getDoubanId()));
        updated |= !record.isFieldLocked("tmdbId") && fillAuthoritativeId(record::getTmdbId, record::setTmdbId,
                AuthoritativeIds.normalizeTmdb(metadata.getTmdbId()));
        updated |= !record.isFieldLocked("imdbId") && fillAuthoritativeId(record::getImdbId, record::setImdbId,
                AuthoritativeIds.normalizeImdb(metadata.getImdbId()));
        updated |= !record.isFieldLocked("rottenTomatoesId") && fillAuthoritativeId(
                record::getRottenTomatoesId,
                record::setRottenTomatoesId,
                AuthoritativeIds.normalizeRottenTomatoes(metadata.getRottenTomatoesId()));

        if (!record.isFieldLocked("description") && RawVideoMetadataRecord.hasText(metadata.getDescription())) {
            record.setDescription(metadata.getDescription());
            updated = true;
        }
        if (!record.isFieldLocked("score") && metadata.getScore() != null) {
            record.setScore(metadata.getScore());
            updated = true;
        }
        if (!record.isFieldLocked("year") && RawVideoMetadataRecord.hasText(metadata.getYear())) {
            record.setYear(metadata.getYear());
            updated = true;
        }
        if (!record.isFieldLocked("aliasTitle") && RawVideoMetadataRecord.hasText(metadata.getOriginalTitle())) {
            String originalTitle = metadata.getOriginalTitle();
            if (!RawVideoMetadataRecord.hasText(record.getAliasTitle())) {
                record.setAliasTitle(originalTitle);
                updated = true;
            } else if (!record.getAliasTitle().contains(originalTitle)) {
                record.setAliasTitle(record.getAliasTitle() + "/" + originalTitle);
                updated = true;
            }
        }
        updated |= mergeRawMetadata(record, metadata);
        return updated;
    }

    private boolean mergeRawMetadata(RawVideoMetadataRecord record, EnrichedMetadataDTO metadata) {
        ObjectNode merged = readRawMetadata(record.getRawMetadata());
        String before = merged.toString();

        if (!record.isFieldLocked("area")) mergeDelimitedText(merged, "area", metadata.getArea(), ", ");
        if (!record.isFieldLocked("language")) mergeDelimitedText(merged, "language", metadata.getLanguage(), "/");
        if (!record.isFieldLocked("genres")) mergeArrayValues(merged, "genreNames", metadata.getGenreNames());
        if (!record.isFieldLocked("actors")) mergeArrayValues(merged, "actorNames", metadata.getActorNames());
        if (!record.isFieldLocked("directors")) mergeArrayValues(merged, "directorNames", metadata.getDirectorNames());
        putIfAbsent(merged, "posterUrl", metadata.getPosterUrl());
        putIfAbsent(merged, "coverImageUrl", metadata.getPosterUrl());
        putIfAbsent(merged, "backdropUrl", metadata.getBackdropUrl());
        if (metadata.getImageSource() != null && isMissing(merged.get("imageSource"))) {
            merged.put("imageSource", metadata.getImageSource().name());
        }

        String after = merged.toString();
        if (!after.equals(before)) {
            record.setRawMetadata(after);
            return true;
        }
        return false;
    }

    private ObjectNode readRawMetadata(String rawMetadata) {
        if (!StringUtils.hasText(rawMetadata)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(rawMetadata);
            if (root != null && root.isObject()) {
                return (ObjectNode) root;
            }
            ObjectNode wrapper = objectMapper.createObjectNode();
            if (root != null && !root.isNull()) {
                wrapper.set("previousRawMetadata", root);
            }
            return wrapper;
        } catch (Exception ex) {
            log.warn("Unable to parse raw_metadata for enrichment merge; replacing with provider fields", ex);
            return objectMapper.createObjectNode();
        }
    }

    private void mergeDelimitedText(ObjectNode target, String fieldName, String incoming, String delimiter) {
        if (!StringUtils.hasText(incoming)) {
            return;
        }
        JsonNode current = target.get(fieldName);
        if (isMissing(current)) {
            target.put(fieldName, incoming.trim());
            return;
        }

        Set<String> values = relationValues(current);
        boolean changed = values.add(incoming.trim());
        if (changed) {
            target.put(fieldName, String.join(delimiter, values));
        }
    }

    private void mergeArrayValues(ObjectNode target, String fieldName, Set<String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        Set<String> values = relationValues(target.get(fieldName));
        boolean changed = false;
        for (String value : incoming) {
            if (StringUtils.hasText(value)) {
                changed |= values.add(value.trim());
            }
        }
        if (!changed && !values.isEmpty() && target.get(fieldName) != null && target.get(fieldName).isArray()) {
            return;
        }
        if (!values.isEmpty() && (changed || target.get(fieldName) == null || !target.get(fieldName).isArray())) {
            ArrayNode array = objectMapper.createArrayNode();
            values.forEach(array::add);
            target.set(fieldName, array);
        }
    }

    private Set<String> relationValues(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        collectRelationValues(node, values);
        return values;
    }

    private void collectRelationValues(JsonNode node, Set<String> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectRelationValues(item, values));
            return;
        }
        String text = node.isTextual() ? node.asText() : node.asText(node.toString());
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String part : text.split("[,，、/|;；]+")) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim());
            }
        }
    }

    private void putIfAbsent(ObjectNode target, String fieldName, String value) {
        if (StringUtils.hasText(value) && isMissing(target.get(fieldName))) {
            target.put(fieldName, value.trim());
        }
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isNull() || (node.isTextual() && !StringUtils.hasText(node.asText()));
    }

    private boolean fillAuthoritativeId(
            java.util.function.Supplier<String> current,
            java.util.function.Consumer<String> setter,
            String incoming) {
        if (!RawVideoMetadataRecord.hasText(current.get()) && RawVideoMetadataRecord.hasText(incoming)) {
            setter.accept(incoming);
            return true;
        }
        return false;
    }

    private void finalizeVideoStatus(
            RawVideoMetadataRecord record,
            ProviderCompletion completion,
            boolean metadataUpdated) {
        if (record.hasAuthoritativeId()) {
            record.setEnrichmentStatus("SUCCESS");
            record.setEnrichmentRetryCount(0);
            if (metadataUpdated) {
                record.setAggregationStatus("PENDING");
            }
            return;
        }

        if (completion.retryableFailureCount() <= 0) {
            record.setEnrichmentStatus("PERMANENT_FAILURE");
            return;
        }

        int nextRetry = (record.getEnrichmentRetryCount() == null ? 0 : record.getEnrichmentRetryCount()) + 1;
        if (nextRetry >= MAX_ENRICHMENT_RETRIES) {
            record.setEnrichmentStatus("PERMANENT_FAILURE");
            return;
        }

        record.setEnrichmentStatus("FAILED");
        record.setEnrichmentRetryCount(nextRetry);
    }
}
