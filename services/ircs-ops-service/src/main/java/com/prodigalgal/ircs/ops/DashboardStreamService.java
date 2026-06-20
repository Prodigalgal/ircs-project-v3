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
                .orElseGet(() -> snapshotRefresher.refresh(safeLimit));
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
}
