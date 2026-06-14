package com.prodigalgal.ircs.ops;

import java.util.List;
import java.util.Map;

public record DashboardAnalysisResponse(
        DashboardStatsResponse stats,
        List<ChartDataPoint> weeklyTrend,
        List<ChartDataPoint> categoryDistribution,
        List<ChartDataPoint> sourceDistribution,
        List<ChartDataPoint> enrichmentStatusDistribution,
        Map<String, Long> idCoverage,
        List<EfficiencyStatsResponse> sourceEfficiency,
        List<EfficiencyStatsResponse> categoryEfficiency
) {
}
