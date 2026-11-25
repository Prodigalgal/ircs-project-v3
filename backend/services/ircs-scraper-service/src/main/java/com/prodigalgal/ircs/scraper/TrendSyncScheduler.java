package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.lock.DistributedLockService;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class TrendSyncScheduler {
    private final RuntimeConfigService config;
    private final DistributedLockService locks;
    private final JdbcTemplate jdbc;
    private final TrendSyncService service;
    private final String owner = UUID.randomUUID().toString();
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("trend-sync-trigger-vt-");
    private Instant retryAfter = Instant.EPOCH;

    @Scheduled(initialDelayString = "${app.scraper.trend-sync.initial-delay-ms:30000}",
            fixedDelayString = "${app.scraper.trend-sync.poll-delay-ms:60000}")
    void scheduled() {
        ScheduledTriggers.submit(executor, this::tick, log, "scraper.trend-sync");
    }

    void tick() {
        if (!config.booleanValue("app.scraper.trend-sync.enabled", true) || !running.compareAndSet(false, true)) return;
        try {
            if (Instant.now().isBefore(retryAfter)) return;
            locks.runWithLock("lock:scraper:trend-sync", owner, Duration.ofMinutes(10), () -> {
                Instant now = Instant.now();
                Instant last = jdbc.queryForObject("select max(last_attempt_at) from external_trend_boards",
                        (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant());
                if (!due(last, now, config.value("app.scraper.trend-sync.cron").orElse("0 */30 * * * *"),
                        config.value("app.scraper.trend-sync.timezone").orElse("Asia/Shanghai"))) return;
                var result = service.syncTrends("trend-" + now.toEpochMilli());
                log.info("Trend sync completed: providers={}, items={}, created={}, sourceErrors={}",
                        result.providerCount(), result.fetchedItems(), result.applyResult().createdGhosts(), result.providerErrors());
            });
        } catch (RuntimeException ex) {
            retryAfter = Instant.now().plus(Duration.ofMinutes(5));
            log.warn("Trend sync deferred: errorType={}", ex.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    static boolean due(Instant last, Instant now, String cron, String zone) {
        CronExpression expression = CronExpression.parse(cron);
        ZoneId timezone = ZoneId.of(zone);
        if (last == null) return true;
        var next = expression.next(last.atZone(timezone));
        return next != null && !next.toInstant().isAfter(now);
    }

    @PreDestroy
    void close() { executor.shutdownNow(); }
}
