package com.prodigalgal.ircs.ops;

import java.time.Instant;
import java.util.Map;

public record DashboardStreamSnapshot(
        Instant refreshedAt,
        Instant generatedAt,
        long sequence,
        boolean stale,
        boolean partial,
        Map<String, String> partialErrors,
        SystemMetricsResponse metrics,
        TaskRuntimeOverviewResponse taskRuntime,
        Map<String, Object> searchOps,
        Map<String, Object> aggregationOps) {

    public DashboardStreamSnapshot withStale(boolean stale) {
        return new DashboardStreamSnapshot(
                refreshedAt,
                generatedAt,
                sequence,
                stale,
                partial,
                partialErrors == null ? Map.of() : partialErrors,
                metrics,
                taskRuntime,
                searchOps == null ? Map.of() : searchOps,
                aggregationOps == null ? Map.of() : aggregationOps);
    }
}
