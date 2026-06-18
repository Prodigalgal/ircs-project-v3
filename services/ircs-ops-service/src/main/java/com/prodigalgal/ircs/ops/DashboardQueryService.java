package com.prodigalgal.ircs.ops;

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

    private Map<String, Long> safeMap(Supplier<Map<String, Long>> supplier) {
        try {
            Map<String, Long> values = supplier.get();
            return values == null ? Map.of() : values;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}
