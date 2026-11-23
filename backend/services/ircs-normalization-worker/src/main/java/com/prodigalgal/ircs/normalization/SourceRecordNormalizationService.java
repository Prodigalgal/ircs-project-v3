package com.prodigalgal.ircs.normalization;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.contracts.normalization.NormalizationMaintenanceRunResponse;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class SourceRecordNormalizationService {

    private final SourceRecordNormalizationRepository repository;
    private final SourceRecordTextNormalizer textNormalizer;
    private final RawSearchSyncPublisher searchSyncPublisher;
    private final MetadataEnrichPublisher metadataEnrichPublisher;
    private final AggregationWorkPublisher aggregationWorkPublisher;
    private final NormalizeVideoPublisher normalizeVideoPublisher;
    private final ObjectMapper objectMapper;
    private final NormalizationConfigValues configValues;

    public List<NormalizationPreviewResult> preview(List<UUID> sourceRecordIds) {
        if (sourceRecordIds == null || sourceRecordIds.isEmpty() || sourceRecordIds.size() > 50
                || sourceRecordIds.stream().anyMatch(java.util.Objects::isNull)
                || sourceRecordIds.stream().distinct().count() != sourceRecordIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1..50 distinct raw video IDs");
        }
        List<NormalizationPreviewResult> results = new ArrayList<>();
        for (UUID id : sourceRecordIds) {
            Optional<SourceRecordRecord> found = repository.findById(id);
            if (found.isEmpty()) {
                results.add(new NormalizationPreviewResult(id, null, null, "MISSING", null));
                continue;
            }
            SourceRecordRecord record = found.orElseThrow();
            String raw = record.rawMetadata();
            String fingerprint = raw == null ? null : metadataDigest(raw);
            if (!StringUtils.hasText(raw)) {
                results.add(new NormalizationPreviewResult(id, record.dataHash(), fingerprint, "INVALID_RAW_METADATA", null));
                continue;
            }
            JsonNode metadata;
            try {
                metadata = objectMapper.readTree(raw);
            } catch (tools.jackson.core.JacksonException error) {
                results.add(new NormalizationPreviewResult(id, record.dataHash(), fingerprint, "INVALID_RAW_METADATA", null));
                continue;
            }
            if (metadata == null || !metadata.isObject() || metadata.isEmpty()) {
                results.add(new NormalizationPreviewResult(id, record.dataHash(), fingerprint, "INVALID_RAW_METADATA", null));
                continue;
            }
            results.add(new NormalizationPreviewResult(id, record.dataHash(), fingerprint, "PREVIEW_ONLY",
                    textNormalizer.normalize(record, metadata)));
        }
        return List.copyOf(results);
    }

    private String metadataDigest(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", error);
        }
    }

    @Transactional
    public NormalizationResult normalize(UUID sourceRecordId, String pipelineVersion) {
        Optional<SourceRecordRecord> maybeRecord = repository.findById(sourceRecordId);
        if (maybeRecord.isEmpty()) {
            return NormalizationResult.missing(sourceRecordId);
        }

        SourceRecordRecord record = maybeRecord.get();
        if (isStale(pipelineVersion, record.dataHash())) {
            return NormalizationResult.stale(sourceRecordId);
        }

        if ("READY".equals(record.normalizationStatus())) {
            metadataEnrichPublisher.publish(sourceRecordId, record.dataHash());
            enqueueAggregation(sourceRecordId, record.dataHash(), "normalization-already-ready");
            return NormalizationResult.skipped(sourceRecordId);
        }

        if (!StringUtils.hasText(record.rawMetadata())) {
            return markFailure(record, "EMPTY_RAW_METADATA");
        }

        SourceRecordPatch patch;
        try {
            JsonNode metadata = objectMapper.readTree(record.rawMetadata());
            if (!metadata.isObject() || metadata.isEmpty()) {
                return markFailure(record, "EMPTY_RAW_METADATA");
            }
            patch = textNormalizer.normalize(record, metadata);
        } catch (Exception ex) {
            log.warn("Unable to normalize raw video {}: reason={}, errorClass={}",
                    sourceRecordId, "INVALID_RAW_METADATA", ex.getClass().getSimpleName());
            return markFailure(record, "INVALID_RAW_METADATA");
        }

        repository.markReady(sourceRecordId, patch);
        searchSyncPublisher.publishIndex(sourceRecordId);
        metadataEnrichPublisher.publish(sourceRecordId, record.dataHash());
        enqueueAggregation(sourceRecordId, record.dataHash(), "normalization-ready");
        return NormalizationResult.ready(sourceRecordId);
    }

    private void enqueueAggregation(UUID sourceRecordId, String pipelineVersion, String reason) {
        aggregationWorkPublisher.enqueue(sourceRecordId, pipelineVersion, reason);
    }

    private boolean isStale(String pipelineVersion, String currentDataHash) {
        if (StringUtils.hasText(currentDataHash)) {
            return !StringUtils.hasText(pipelineVersion) || !pipelineVersion.trim().equals(currentDataHash);
        }
        return StringUtils.hasText(pipelineVersion);
    }

    private NormalizationResult markFailure(SourceRecordRecord record, String reason) {
        int currentRetry = record.normalizationRetryCount() == null ? 0 : record.normalizationRetryCount();
        int nextRetry = currentRetry + 1;
        int maxRetries = configValues.maxRetries();
        boolean permanent = nextRetry >= Math.max(1, maxRetries);
        Instant nextRetryTime = permanent ? null : Instant.now().plusSeconds(delaySeconds(nextRetry));
        String status = permanent ? "PERMANENT_FAILURE" : "FAILED";
        repository.markFailure(record.id(), status, nextRetry, nextRetryTime);
        return new NormalizationResult(record.id(), status, reason);
    }

    private long delaySeconds(int retryCount) {
        long multiplier = 1L << Math.min(10, Math.max(0, retryCount - 1));
        return Math.max(1, configValues.backoffBaseSeconds()) * multiplier;
    }

    public NormalizationMaintenanceRunResponse resetAllNormalizationPending(int sampleLimit) {
        return resetAllNormalizationPending(sampleLimit, false, 500);
    }

    public NormalizationMaintenanceRunResponse resetAllNormalizationPending(int sampleLimit, boolean enqueue, int batchSize) {
        int safeLimit = Math.max(1, sampleLimit);
        List<UUID> sampleSourceRecordIds = repository.sampleSourceRecordIds(safeLimit);
        long sourceRecordCount = repository.countSourceRecords();
        int changedRows = repository.resetAllNormalizationPending();
        long enqueuedRows = enqueue ? enqueueAllNormalization(batchSize) : 0L;
        return new NormalizationMaintenanceRunResponse(
                "sanitize",
                sourceRecordCount,
                changedRows,
                enqueuedRows,
                sampleSourceRecordIds);
    }

    private long enqueueAllNormalization(int batchSize) {
        int safeBatchSize = Math.max(1, batchSize);
        long enqueuedRows = 0L;
        UUID cursor = null;
        while (true) {
            List<SourceRecordNormalizationRepository.SourceRecordQueueItem> items =
                    repository.findNormalizationQueueItems(cursor, safeBatchSize);
            if (items.isEmpty()) {
                return enqueuedRows;
            }
            for (SourceRecordNormalizationRepository.SourceRecordQueueItem item : items) {
                normalizeVideoPublisher.publishNow(item.id(), item.dataHash());
            }
            enqueuedRows += items.size();
            cursor = items.getLast().id();
            if (items.size() < safeBatchSize) {
                return enqueuedRows;
            }
        }
    }

    public record NormalizationResult(UUID sourceRecordId, String status, String reason) {
        public static NormalizationResult ready(UUID sourceRecordId) {
            return new NormalizationResult(sourceRecordId, "READY", "NORMALIZED");
        }

        public static NormalizationResult skipped(UUID sourceRecordId) {
            return new NormalizationResult(sourceRecordId, "SKIPPED", "ALREADY_READY");
        }

        public static NormalizationResult missing(UUID sourceRecordId) {
            return new NormalizationResult(sourceRecordId, "MISSING", "SOURCE_RECORD_NOT_FOUND");
        }

        public static NormalizationResult stale(UUID sourceRecordId) {
            return new NormalizationResult(sourceRecordId, "STALE", "PIPELINE_VERSION_STALE");
        }
    }
}
