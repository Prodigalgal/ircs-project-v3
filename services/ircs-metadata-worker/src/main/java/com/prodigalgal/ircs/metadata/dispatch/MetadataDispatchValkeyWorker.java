package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTask;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.metadata.SystemConfigRepository;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MetadataDispatchValkeyWorker {

    private static final String ENABLED_KEY = "app.metadata.valkey-dispatcher.enabled";
    private static final long ENABLED_CACHE_TTL_NANOS = Duration.ofSeconds(30).toNanos();
    private final PipelineValkeyQueue pipelineQueue;
    private final MetadataDispatchService dispatchService;
    private final String workerId;
    private final int batchSize;
    private final Duration visibilityTimeout;
    private final Duration retryDelay;
    private volatile Boolean cachedEnabled;
    private volatile long cachedEnabledExpiresAtNanos;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    @Value("${app.metadata.valkey-dispatcher.enabled:false}")
    private boolean enabledByDeployment = true;

    MetadataDispatchValkeyWorker(
            PipelineValkeyQueue pipelineQueue,
            MetadataDispatchService dispatchService,
            @Value("${spring.application.name:ircs-metadata-worker}") String applicationName,
            @Value("${app.metadata.pipeline.worker-id:${APP_METADATA_PIPELINE_WORKER_ID:}}")
                    String configuredWorkerId,
            @Value("${app.metadata.valkey-dispatcher.batch-size:${APP_METADATA_VALKEY_DISPATCHER_BATCH_SIZE:10}}")
                    int batchSize,
            @Value("${app.metadata.valkey-dispatcher.visibility-timeout:PT10M}") Duration visibilityTimeout,
            @Value("${app.metadata.valkey-dispatcher.retry-delay:PT5M}") Duration retryDelay) {
        this.pipelineQueue = pipelineQueue;
        this.dispatchService = dispatchService;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
        this.batchSize = Math.max(1, batchSize);
        this.visibilityTimeout = positive(visibilityTimeout, Duration.ofMinutes(10));
        this.retryDelay = positive(retryDelay, Duration.ofMinutes(5));
    }

    @Scheduled(
            initialDelayString = "${app.metadata.valkey-dispatcher.initial-delay-ms:10000}",
            fixedDelayString = "${app.metadata.valkey-dispatcher.fixed-delay-ms:1000}")
    public void runScheduled() {
        runOnce();
    }

    int runOnce() {
        if (!workerEnabled()) {
            return 0;
        }
        pipelineQueue.heartbeatConsumer(PipelineTaskTypes.ENRICH_METADATA, workerId, visibilityTimeout.plusMinutes(1));
        int reclaimed = pipelineQueue.requeueExpired(PipelineTaskTypes.ENRICH_METADATA, batchSize);
        if (reclaimed > 0) {
            log.info("Requeued expired metadata dispatch tasks from Valkey: count={}", reclaimed);
        }
        List<PipelineValkeyTask> tasks = pipelineQueue.claim(
                PipelineTaskTypes.ENRICH_METADATA,
                workerId,
                batchSize,
                visibilityTimeout);
        tasks.forEach(this::process);
        return tasks.size();
    }

    private void process(PipelineValkeyTask task) {
        try {
            MetadataDispatchResult result = dispatchService.dispatch(task.rawVideoId(), task.pipelineVersion());
            if (!result.videoFound()) {
                pipelineQueue.fail(task, false, Duration.ZERO, "RAW_VIDEO_NOT_FOUND");
                return;
            }
            if ("FAILED".equals(result.status())) {
                pipelineQueue.fail(task, true, retryDelay, "ENRICHMENT_RETRYABLE_FAILURE");
                return;
            }
            pipelineQueue.complete(task);
        } catch (RuntimeException ex) {
            pipelineQueue.fail(task, true, retryDelay, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        }
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private boolean workerEnabled() {
        SystemConfigRepository repository = systemConfigRepository;
        if (repository == null) {
            return enabledByDeployment;
        }
        long now = System.nanoTime();
        Boolean value = cachedEnabled;
        if (value != null && now < cachedEnabledExpiresAtNanos) {
            return value;
        }
        synchronized (this) {
            value = cachedEnabled;
            if (value != null && now < cachedEnabledExpiresAtNanos) {
                return value;
            }
            boolean refreshed = enabledByDeployment;
            try {
                repository.evict(ENABLED_KEY);
                refreshed = repository.findValue(ENABLED_KEY)
                        .map(raw -> parseBoolean(raw, enabledByDeployment))
                        .orElse(enabledByDeployment);
            } catch (RuntimeException ex) {
                log.warn("Unable to read metadata dispatcher enabled config: {}", ex.getMessage());
            }
            cachedEnabled = refreshed;
            cachedEnabledExpiresAtNanos = now + ENABLED_CACHE_TTL_NANOS;
            return refreshed;
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "y", "on", "enabled" -> true;
            case "false", "0", "no", "n", "off", "disabled" -> false;
            default -> defaultValue;
        };
    }
}
