package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DashboardSnapshotComposer {

    private final DashboardQueryService dashboardQueryService;
    private final RuntimeConfigService runtimeConfig;
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory());
    private final AtomicLong sequence = new AtomicLong(0);
    private final Duration fallbackRefreshBudget = Duration.ofSeconds(6);

    DashboardStreamSnapshot compose(int taskRuntimeLimit) {
        Instant generatedAt = Instant.now();
        long deadlineNanos = System.nanoTime() + refreshBudget().toNanos();
        Map<String, String> partialErrors = new LinkedHashMap<>();
        CompletableFuture<SystemMetricsResponse> metricsFuture =
                CompletableFuture.supplyAsync(dashboardQueryService::getMetrics, executor);
        CompletableFuture<TaskRuntimeOverviewResponse> taskRuntimeFuture =
                CompletableFuture.supplyAsync(() -> dashboardQueryService.getTaskRuntimeOverview(taskRuntimeLimit), executor);
        CompletableFuture<Map<String, Object>> searchOpsFuture =
                CompletableFuture.supplyAsync(dashboardQueryService::getSearchOpsStats, executor);
        CompletableFuture<Map<String, Object>> aggregationOpsFuture =
                CompletableFuture.supplyAsync(dashboardQueryService::getAggregationOpsStats, executor);

        waitUntilReady(deadlineNanos, metricsFuture, taskRuntimeFuture, searchOpsFuture, aggregationOpsFuture);

        SystemMetricsResponse metrics = await(
                "metrics",
                metricsFuture,
                partialErrors,
                () -> DashboardSnapshotFallbacks.metrics("SNAPSHOT_METRICS_UNAVAILABLE"));
        TaskRuntimeOverviewResponse taskRuntime = await(
                "taskRuntime",
                taskRuntimeFuture,
                partialErrors,
                () -> DashboardSnapshotFallbacks.taskRuntime(taskRuntimeLimit));
        Map<String, Object> searchOps = await(
                "searchOps",
                searchOpsFuture,
                partialErrors,
                () -> DashboardSnapshotFallbacks.unavailableOps("SNAPSHOT_SEARCH_OPS_UNAVAILABLE"));
        Map<String, Object> aggregationOps = await(
                "aggregationOps",
                aggregationOpsFuture,
                partialErrors,
                () -> DashboardSnapshotFallbacks.unavailableOps("SNAPSHOT_AGGREGATION_OPS_UNAVAILABLE"));

        return new DashboardStreamSnapshot(
                generatedAt,
                generatedAt,
                sequence.incrementAndGet(),
                false,
                !partialErrors.isEmpty(),
                Map.copyOf(partialErrors),
                metrics,
                taskRuntime,
                searchOps,
                aggregationOps);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private <T> T await(
            String name,
            CompletableFuture<T> future,
            Map<String, String> partialErrors,
            Supplier<T> fallback) {
        if (!future.isDone()) {
            future.cancel(true);
            partialErrors.put(name, "TimeoutException");
            return fallback.get();
        }
        try {
            T value = future.get();
            return value == null ? fallback.get() : value;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            partialErrors.put(name, "Interrupted");
            return fallback.get();
        } catch (ExecutionException ex) {
            partialErrors.put(name, causeName(ex));
            return fallback.get();
        } catch (CancellationException ex) {
            partialErrors.put(name, ex.getClass().getSimpleName());
            return fallback.get();
        } catch (RuntimeException ex) {
            partialErrors.put(name, ex.getClass().getSimpleName());
            return fallback.get();
        }
    }

    private void waitUntilReady(long deadlineNanos, CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(observed(futures)).get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            cancelAll(futures);
        } catch (TimeoutException ignored) {
            // Individual futures are marked with TimeoutException by await().
        } catch (ExecutionException ignored) {
            // observed() converts branch failures to normal completion; kept for get() signature completeness.
        }
    }

    private CompletableFuture<?>[] observed(CompletableFuture<?>... futures) {
        CompletableFuture<?>[] observed = new CompletableFuture<?>[futures.length];
        for (int index = 0; index < futures.length; index++) {
            observed[index] = futures[index].handle((value, error) -> null);
        }
        return observed;
    }

    private void cancelAll(CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            future.cancel(true);
        }
    }

    private static long remainingMillis(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
    }

    private static String causeName(ExecutionException ex) {
        Throwable cause = ex.getCause();
        return cause == null ? ex.getClass().getSimpleName() : cause.getClass().getSimpleName();
    }

    private Duration refreshBudget() {
        return runtimeConfig == null
                ? fallbackRefreshBudget
                : runtimeConfig.positiveDurationValue(
                        "app.ops.dashboard.snapshot.refresh-budget",
                        fallbackRefreshBudget);
    }

    private static ThreadFactory threadFactory() {
        return Thread.ofVirtual().name("dashboard-snapshot-compose-", 0).factory();
    }
}
