package com.prodigalgal.ircs.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.normalization.NormalizationMaintenanceRunResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class RawVideoNormalizationService {

    private final RawVideoNormalizationRepository repository;
    private final RawVideoTextNormalizer textNormalizer;
    private final RawSearchSyncPublisher searchSyncPublisher;
    private final ObjectMapper objectMapper;
    private final NormalizationConfigValues configValues;

    @Transactional
    public NormalizationResult normalize(UUID rawVideoId) {
        Optional<RawVideoRecord> maybeRecord = repository.findById(rawVideoId);
        if (maybeRecord.isEmpty()) {
            return NormalizationResult.missing(rawVideoId);
        }

        RawVideoRecord record = maybeRecord.get();
        if ("READY".equals(record.normalizationStatus())) {
            return NormalizationResult.skipped(rawVideoId);
        }

        if (!StringUtils.hasText(record.rawMetadata())) {
            return markFailure(record, "EMPTY_RAW_METADATA");
        }

        try {
            JsonNode metadata = objectMapper.readTree(record.rawMetadata());
            if (!metadata.isObject() || metadata.isEmpty()) {
                return markFailure(record, "EMPTY_RAW_METADATA");
            }
            RawVideoPatch patch = textNormalizer.normalize(record, metadata);
            repository.markReady(rawVideoId, patch);
            searchSyncPublisher.publishIndex(rawVideoId);
            return NormalizationResult.ready(rawVideoId);
        } catch (Exception ex) {
            log.warn("Unable to normalize raw video {}: reason={}, errorClass={}",
                    rawVideoId, "INVALID_RAW_METADATA", ex.getClass().getSimpleName());
            return markFailure(record, "INVALID_RAW_METADATA");
        }
    }

    private NormalizationResult markFailure(RawVideoRecord record, String reason) {
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

    @Transactional
    public NormalizationMaintenanceRunResponse resetAllNormalizationPending(int sampleLimit) {
        int safeLimit = Math.max(1, sampleLimit);
        List<UUID> sampleRawVideoIds = repository.sampleRawVideoIds(safeLimit);
        long rawVideoCount = repository.countRawVideos();
        int changedRows = repository.resetAllNormalizationPending();
        return new NormalizationMaintenanceRunResponse(
                "sanitize",
                rawVideoCount,
                changedRows,
                sampleRawVideoIds);
    }

    public record NormalizationResult(UUID rawVideoId, String status, String reason) {
        public static NormalizationResult ready(UUID rawVideoId) {
            return new NormalizationResult(rawVideoId, "READY", "NORMALIZED");
        }

        public static NormalizationResult skipped(UUID rawVideoId) {
            return new NormalizationResult(rawVideoId, "SKIPPED", "ALREADY_READY");
        }

        public static NormalizationResult missing(UUID rawVideoId) {
            return new NormalizationResult(rawVideoId, "MISSING", "RAW_VIDEO_NOT_FOUND");
        }
    }
}
