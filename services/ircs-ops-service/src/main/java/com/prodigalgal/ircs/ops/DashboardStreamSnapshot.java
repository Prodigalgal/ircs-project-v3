package com.prodigalgal.ircs.ops;

import java.time.Instant;

public record DashboardStreamSnapshot(
        Instant refreshedAt,
        SystemMetricsResponse metrics,
        TaskRuntimeOverviewResponse taskRuntime) {
}
