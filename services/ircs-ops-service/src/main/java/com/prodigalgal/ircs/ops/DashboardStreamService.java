package com.prodigalgal.ircs.ops;

import java.io.IOException;
import java.time.Duration;
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
    private final TaskExecutor taskExecutor;
    private final Duration interval;
    private final Duration timeout;

    DashboardStreamService(
            DashboardSnapshotCacheService cacheService,
            ObjectProvider<TaskExecutor> taskExecutorProvider,
            @Value("${app.ops.dashboard.stream.interval:PT3S}") Duration interval,
            @Value("${app.ops.dashboard.stream.timeout:PT1H}") Duration timeout) {
        this.cacheService = cacheService;
        this.taskExecutor = taskExecutor();
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
                .orElseGet(() -> DashboardSnapshotFallbacks.snapshot(safeLimit, "CACHE_MISS"));
    }

    private void pump(SseEmitter emitter, AtomicBoolean open, int taskRuntimeLimit) {
        try {
            while (open.get()) {
                sendSnapshot(emitter, taskRuntimeLimit);
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

    private void sendSnapshot(SseEmitter emitter, int taskRuntimeLimit) throws IOException {
        emitter.send(SseEmitter.event()
                .name("snapshot")
                .data(snapshot(taskRuntimeLimit)));
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

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
