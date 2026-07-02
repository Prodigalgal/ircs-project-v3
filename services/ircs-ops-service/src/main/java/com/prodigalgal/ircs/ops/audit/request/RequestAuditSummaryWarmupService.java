package com.prodigalgal.ircs.ops.audit.request;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class RequestAuditSummaryWarmupService {

    private static final String ENABLED_KEY = "app.ops.request-audit.summary-snapshot.enabled";
    private static final String REFRESH_INTERVAL_KEY = "app.ops.request-audit.summary-snapshot.refresh-interval";
    private static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(30);

    private final RequestAuditQueryService queryService;
    private final RuntimeConfigService runtimeConfig;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Instant nextWarmupAt = Instant.EPOCH;

    @Scheduled(
            initialDelayString = "${app.ops.request-audit.summary-snapshot.initial-delay-ms:7000}",
            fixedDelayString = "${app.ops.request-audit.summary-snapshot.tick-delay-ms:5000}")
    public void warmupOnSchedule() {
        if (!enabled() || Instant.now().isBefore(nextWarmupAt)) {
            return;
        }
        warmupNow();
    }

    void warmupNow() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Instant startedAt = Instant.now();
        try {
            queryService.refreshSummarySnapshot();
        } catch (RuntimeException ex) {
            log.debug("Request audit summary warmup failed: {}", ex.getMessage());
        } finally {
            running.set(false);
            nextWarmupAt = startedAt.plus(refreshInterval());
        }
    }

    private boolean enabled() {
        return runtimeConfig == null || runtimeConfig.booleanValue(ENABLED_KEY, true);
    }

    private Duration refreshInterval() {
        return runtimeConfig == null
                ? DEFAULT_REFRESH_INTERVAL
                : runtimeConfig.positiveDurationValue(REFRESH_INTERVAL_KEY, DEFAULT_REFRESH_INTERVAL);
    }
}
