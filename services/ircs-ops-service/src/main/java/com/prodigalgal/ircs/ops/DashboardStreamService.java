package com.prodigalgal.ircs.ops;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
class DashboardStreamService {

    private final DashboardSnapshotCacheService cacheService;
    private final DashboardSnapshotRefresher snapshotRefresher;
    private final TaskExecutor taskExecutor;
    private final Duration interval;
    private final Duration timeout;

    DashboardStreamService(
            DashboardSnapshotCacheService cacheService,
            DashboardSnapshotRefresher snapshotRefresher,
            ObjectProvider<TaskExecutor> taskExecutorProvider,
            @Value("${app.ops.dashboard.stream.interval:PT3S}") Duration interval,
            @Value("${app.ops.dashboard.stream.timeout:PT1H}") Duration timeout) {
        this.cacheService = cacheService;
        this.snapshotRefresher = snapshotRefresher;
        this.taskExecutor = taskExecutor(taskExecutorProvider);
        this.interval = positiveOr(interval, Duration.ofSeconds(3));
        this.timeout = positiveOr(timeout, Duration.ofHours(1));
    }

    SseEmitter stream(int taskRuntimeLimit) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(error -> open.set(false));
        taskExecutor.execute(() -> pump(emitter, open, taskRuntimeLimit));
        return emitter;
    }

    DashboardStreamSnapshot snapshot(int taskRuntimeLimit) {
        int safeLimit = Math.max(1, taskRuntimeLimit);
        return cacheService.read(safeLimit)
                .orElseGet(() -> degradedSnapshot(safeLimit));
    }

    private void pump(SseEmitter emitter, AtomicBoolean open, int taskRuntimeLimit) {
        try {
            while (open.get()) {
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(snapshot(taskRuntimeLimit)));
                sleep(interval);
            }
        } catch (IOException ex) {
            log.debug("Dashboard stream client disconnected: {}", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Dashboard stream failed: {}", ex.getMessage());
            emitter.completeWithError(ex);
            return;
        }
        emitter.complete();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dashboard stream interrupted", ex);
        }
    }

    private static TaskExecutor taskExecutor(ObjectProvider<TaskExecutor> provider) {
        if (provider != null) {
            TaskExecutor provided = provider.getIfUnique();
            if (provided != null) {
                return provided;
            }
        }
        SimpleAsyncTaskExecutor fallback = new SimpleAsyncTaskExecutor("dashboard-stream-");
        return fallback;
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static DashboardStreamSnapshot degradedSnapshot(int taskRuntimeLimit) {
        Instant now = Instant.now();
        return new DashboardStreamSnapshot(
                now,
                now,
                0L,
                true,
                true,
                Map.of("snapshot", "CACHE_MISS"),
                degradedMetrics(),
                degradedTaskRuntime(taskRuntimeLimit),
                Map.of("available", false, "unavailableReason", "CACHE_MISS"),
                Map.of("available", false, "unavailableReason", "CACHE_MISS"));
    }

    private static SystemMetricsResponse degradedMetrics() {
        return new SystemMetricsResponse(
                RateMetricResponse.empty("collection.page.discovered", "PT 发现速率", 60, 300),
                RateMetricResponse.empty("collection.detail.completed", "DT 采集速率", 60, 300),
                RateMetricResponse.empty("runtime.aggregation.completed", "清洗速率", 60, 300),
                RateMetricResponse.empty("pipeline.metadata_provider.completed", "元数据速率", 60, 300),
                0,
                0,
                0,
                0,
                List.of(),
                0,
                new RedisMetricResponse(0, 0, 0, "unknown"),
                0,
                0,
                false,
                0,
                Map.of("dashboardSnapshot", "CACHE_MISS"));
    }

    private static TaskRuntimeOverviewResponse degradedTaskRuntime(int taskRuntimeLimit) {
        Instant now = Instant.now();
        return new TaskRuntimeOverviewResponse(
                now,
                Math.max(1, taskRuntimeLimit),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                List.of());
    }
}
