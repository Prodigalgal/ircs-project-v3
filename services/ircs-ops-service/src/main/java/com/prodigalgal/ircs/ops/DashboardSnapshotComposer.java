package com.prodigalgal.ircs.ops;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class DashboardSnapshotComposer {

    private final DashboardQueryService dashboardQueryService;
    private final AtomicLong sequence = new AtomicLong(0);

    DashboardSnapshotComposer(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    DashboardStreamSnapshot compose(int taskRuntimeLimit) {
        Instant generatedAt = Instant.now();
        Map<String, String> partialErrors = new LinkedHashMap<>();
        SystemMetricsResponse metrics = safe("metrics", dashboardQueryService::getMetrics, partialErrors);
        TaskRuntimeOverviewResponse taskRuntime = safe(
                "taskRuntime",
                () -> dashboardQueryService.getTaskRuntimeOverview(taskRuntimeLimit),
                partialErrors);
        Map<String, Object> searchOps = safeMap(
                "searchOps",
                dashboardQueryService::getSearchOpsStats,
                partialErrors);
        Map<String, Object> aggregationOps = safeMap(
                "aggregationOps",
                dashboardQueryService::getAggregationOpsStats,
                partialErrors);

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

    private <T> T safe(String name, Supplier<T> supplier, Map<String, String> partialErrors) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            partialErrors.put(name, ex.getClass().getSimpleName());
            return null;
        }
    }

    private Map<String, Object> safeMap(
            String name,
            Supplier<Map<String, Object>> supplier,
            Map<String, String> partialErrors) {
        Map<String, Object> value = safe(name, supplier, partialErrors);
        return value == null ? Map.of() : value;
    }
}
