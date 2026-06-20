package com.prodigalgal.ircs.ops;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DashboardSnapshotComposer {

    private final DashboardQueryService dashboardQueryService;
    private final ExecutorService executor;
    private final AtomicLong sequence = new AtomicLong(0);
    private final Duration refreshBudget;

    DashboardSnapshotComposer(
            DashboardQueryService dashboardQueryService,
            @Value("${app.ops.dashboard.snapshot.refresh-budget:PT3S}") Duration refreshBudget) {
        this.dashboardQueryService = dashboardQueryService;
        this.refreshBudget = positiveOr(refreshBudget, Duration.ofSeconds(3));
        this.executor = Executors.newFixedThreadPool(4, threadFactory());
    }

    DashboardStreamSnapshot compose(int taskRuntimeLimit) {
        Instant generatedAt = Instant.now();
        long deadlineNanos = System.nanoTime() + refreshBudget.toNanos();
        Map<String, String> partialErrors = new LinkedHashMap<>();
        CompletableFuture<SystemMetricsResponse> metricsFuture =
                CompletableFuture.supplyAsync(dashboardQueryService::getMetrics, executor);
        CompletableFuture<TaskRuntimeOverviewResponse> taskRuntimeFuture =
                CompletableFuture.supplyAsync(() -> dashboardQueryService.getTaskRuntimeOverview(taskRuntimeLimit), executor);
        CompletableFuture<Map<String, Object>> searchOpsFuture =
                CompletableFuture.supplyAsync(dashboardQueryService::getSearchOpsStats, executor);
        CompletableFuture<Map<String, Object>> aggregationOpsFuture =
                CompletableFuture.supplyAsync(dashboardQueryService::getAggregationOpsStats, executor);

        SystemMetricsResponse metrics = await(
                "metrics",
                metricsFuture,
                deadlineNanos,
                partialErrors,
                () -> DashboardSnapshotFallbacks.metrics("SNAPSHOT_METRICS_UNAVAILABLE"));
        TaskRuntimeOverviewResponse taskRuntime = await(
                "taskRuntime",
                taskRuntimeFuture,
                deadlineNanos,
                partialErrors,
                () -> DashboardSnapshotFallbacks.taskRuntime(taskRuntimeLimit));
        Map<String, Object> searchOps = await(
                "searchOps",
                searchOpsFuture,
                deadlineNanos,
                partialErrors,
                () -> DashboardSnapshotFallbacks.unavailableOps("SNAPSHOT_SEARCH_OPS_UNAVAILABLE"));
        Map<String, Object> aggregationOps = await(
                "aggregationOps",
                aggregationOpsFuture,
                deadlineNanos,
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
            long deadlineNanos,
            Map<String, String> partialErrors,
            Supplier<T> fallback) {
        try {
            T value = future.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
            return value == null ? fallback.get() : value;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            partialErrors.put(name, "Interrupted");
            return fallback.get();
        } catch (TimeoutException ex) {
            future.cancel(true);
            partialErrors.put(name, "TimeoutException");
            return fallback.get();
        } catch (ExecutionException ex) {
            partialErrors.put(name, causeName(ex));
            return fallback.get();
        } catch (RuntimeException ex) {
            partialErrors.put(name, ex.getClass().getSimpleName());
            return fallback.get();
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

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "dashboard-snapshot-compose-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
