package com.prodigalgal.ircs.ops.dashboard.application;

import com.prodigalgal.ircs.ops.dashboard.dto.DashboardAnalysisResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.DashboardStatsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.SourceQualityResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.SystemMetricsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.TaskRuntimeOverviewResponse;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.AggregationOpsStatsClient;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.JdbcDashboardRepository;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.SearchOpsStatsClient;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final JdbcDashboardRepository dashboardRepository;
    private final SystemMetricsService systemMetricsService;
    private final TaskRuntimeOverviewService taskRuntimeOverviewService;
    private final SearchOpsStatsClient searchOpsStatsClient;
    private final AggregationOpsStatsClient aggregationOpsStatsClient;

    public DashboardStatsResponse getStatistics() {
        return dashboardRepository.loadStats();
    }

    public DashboardAnalysisResponse getAnalysis(int days) {
        int boundedDays = Math.max(1, Math.min(days, 90));
        return new DashboardAnalysisResponse(
                dashboardRepository.loadStats(),
                safeList(() -> dashboardRepository.weeklyTrend(boundedDays)),
                safeList(dashboardRepository::categoryDistribution),
                safeList(dashboardRepository::sourceDistribution),
                safeList(dashboardRepository::enrichmentStatusDistribution),
                safeMap(dashboardRepository::idCoverage),
                safeList(dashboardRepository::sourceEfficiency),
                safeList(dashboardRepository::categoryEfficiency));
    }

    public SystemMetricsResponse getMetrics() {
        return systemMetricsService.currentMetrics();
    }

    public TaskRuntimeOverviewResponse getTaskRuntimeOverview(int limit) {
        return taskRuntimeOverviewService.currentOverview(limit);
    }

    public Map<String, Object> getSearchOpsStats() {
        return safeOpsMap(searchOpsStatsClient::currentStats, "SEARCH_OPS_UNAVAILABLE");
    }

    public Map<String, Object> getAggregationOpsStats() {
        return safeOpsMap(aggregationOpsStatsClient::currentStats, "AGGREGATION_OPS_UNAVAILABLE");
    }

    public List<SourceQualityResponse> getSourceQuality() {
        return safeList(dashboardRepository::sourceQuality);
    }

    public void refresh(int days) {
        systemMetricsService.refreshRateMetrics();
    }

    private <T> List<T> safeList(Supplier<List<T>> supplier) {
        try {
            List<T> values = supplier.get();
            return values == null ? List.of() : values;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private <T> Map<String, T> safeMap(Supplier<Map<String, T>> supplier) {
        try {
            Map<String, T> values = supplier.get();
            return values == null ? Map.of() : values;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> safeOpsMap(Supplier<Map<String, Object>> supplier, String unavailableReason) {
        try {
            Map<String, Object> values = supplier.get();
            return values == null ? DashboardFallbacks.unavailableOps(unavailableReason) : values;
        } catch (RuntimeException ignored) {
            return DashboardFallbacks.unavailableOps(unavailableReason);
        }
    }
}
