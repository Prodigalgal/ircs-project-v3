package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
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
    private final RuntimeConfigService runtimeConfig;
    private final Duration fallbackInterval;
    private final Duration fallbackTimeout;
    private final ConcurrentMap<Integer, DashboardStreamSnapshot> localSnapshots = new ConcurrentHashMap<>();

    DashboardStreamService(
            DashboardSnapshotCacheService cacheService,
            DashboardSnapshotRefresher snapshotRefresher,
            RuntimeConfigService runtimeConfig) {
        this.cacheService = cacheService;
        this.snapshotRefresher = snapshotRefresher;
        this.taskExecutor = taskExecutor();
        this.runtimeConfig = runtimeConfig;
        this.fallbackInterval = Duration.ofSeconds(3);
        this.fallbackTimeout = Duration.ofHours(1);
    }

    SseEmitter stream(int taskRuntimeLimit) {
        SseEmitter emitter = new SseEmitter(timeout().toMillis());
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
                .map(snapshot -> remember(safeLimit, snapshot))
                .orElseGet(() -> snapshotFromLocalFallback(safeLimit));
    }

    private void pump(SseEmitter emitter, AtomicBoolean open, int taskRuntimeLimit) {
        try {
            while (open.get()) {
                sendSnapshot(emitter, taskRuntimeLimit);
                sleep(interval());
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

    private void sendSnapshot(SseEmitter emitter, int taskRuntimeLimit) throws IOException {
        emitter.send(SseEmitter.event()
                .name("snapshot")
                .data(snapshot(taskRuntimeLimit)));
    }

    private DashboardStreamSnapshot snapshotFromLocalFallback(int safeLimit) {
        snapshotRefresher.refreshAsync(safeLimit);
        DashboardStreamSnapshot localSnapshot = localSnapshots.get(safeLimit);
        if (localSnapshot != null) {
            return localSnapshot.withStale(true);
        }
        return DashboardSnapshotFallbacks.snapshot(safeLimit, "CACHE_MISS");
    }

    private DashboardStreamSnapshot remember(int safeLimit, DashboardStreamSnapshot snapshot) {
        if (snapshot != null) {
            localSnapshots.put(safeLimit, snapshot);
        }
        return snapshot;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dashboard stream interrupted", ex);
        }
    }

    private static TaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor("dashboard-stream-");
    }

    private Duration interval() {
        return runtimeConfig == null
                ? fallbackInterval
                : runtimeConfig.positiveDurationValue("app.ops.dashboard.stream.interval", fallbackInterval);
    }

    private Duration timeout() {
        return runtimeConfig == null
                ? fallbackTimeout
                : runtimeConfig.positiveDurationValue("app.ops.dashboard.stream.timeout", fallbackTimeout);
    }
}
