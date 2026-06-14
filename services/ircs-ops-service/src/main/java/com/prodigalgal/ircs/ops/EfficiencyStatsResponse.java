package com.prodigalgal.ircs.ops;

public record EfficiencyStatsResponse(
        String name,
        long total,
        long successCount,
        double successRate
) {
}
