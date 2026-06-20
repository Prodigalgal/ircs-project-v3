package com.prodigalgal.ircs.ops;

import java.time.Instant;
import java.util.Map;

public record DashboardStreamSnapshot(
        Instant refreshedAt,
        SystemMetricsResponse metrics,
        TaskRuntimeOverviewResponse taskRuntime,
        Map<String, Object> searchOps) {
}
