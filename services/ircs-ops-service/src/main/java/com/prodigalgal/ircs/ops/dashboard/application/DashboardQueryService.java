package com.prodigalgal.ircs.ops.dashboard.application;

import com.prodigalgal.ircs.ops.dashboard.dto.DashboardAnalysisResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.DashboardStatsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.SourceQualityResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.SystemMetricsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.TaskRuntimeOverviewResponse;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.AggregationOpsStatsClient;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.DashboardSearchIndexStatsClient;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.JdbcDashboardRepository;
import com.prodigalgal.ircs.ops.dashboard.infrastructure.SearchOpsStatsClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private static final Duration STATS_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration ANALYSIS_CACHE_TTL = Duration.ofSeconds(45);
    private static final Duration SOURCE_QUALITY_CACHE_TTL = Duration.ofSeconds(60);

    private final JdbcDashboardRepository dashboardRepository;
    private final SystemMetricsService systemMetricsService;
    private final TaskRuntimeOverviewService taskRuntimeOverviewService;
    private final SearchOpsStatsClient searchOpsStatsClient;
    private final AggregationOpsStatsClient aggregationOpsStatsClient;
    private final DashboardSearchIndexStatsClient searchIndexStatsClient;
    private final DashboardReadCache<DashboardStatsResponse> statsCache = new DashboardReadCache<>(STATS_CACHE_TTL);
    private final DashboardKeyedReadCache<Integer, DashboardAnalysisResponse> analysisCache =
            new DashboardKeyedReadCache<>(ANALYSIS_CACHE_TTL);
    private final DashboardReadCache<List<SourceQualityResponse>> sourceQualityCache =
            new DashboardReadCache<>(SOURCE_QUALITY_CACHE_TTL);

    public DashboardStatsResponse getStatistics() {
        return statsCache.get(this::loadStatistics);
    }

    private DashboardStatsResponse loadStatistics() {
        DashboardStatsResponse dbStats = dashboardRepository.loadStats();
        try {
            DashboardSearchIndexStatsClient.SearchIndexCounts indexCounts = searchIndexStatsClient.currentCounts();
            return dbStats.withSearchCounts(
                    indexCounts.rawCount().orElse(dbStats.rawCountDb()),
                    indexCounts.unifiedCount().orElse(dbStats.unifiedCountDb()));
        } catch (RuntimeException ignored) {
            return dbStats.withSearchCounts(dbStats.rawCountDb(), dbStats.unifiedCountDb());
        }
    }

    public DashboardAnalysisResponse getAnalysis(int days) {
        int boundedDays = Math.max(1, Math.min(days, 90));
        return analysisCache.get(boundedDays, () -> loadAnalysis(boundedDays));
    }

    private DashboardAnalysisResponse loadAnalysis(int boundedDays) {
        return new DashboardAnalysisResponse(
                getStatistics(),
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
        return sourceQualityCache.get(() -> safeList(dashboardRepository::sourceQuality));
    }

    public void refresh(int days) {
        statsCache.clear();
        analysisCache.clear();
        sourceQualityCache.clear();
        taskRuntimeOverviewService.clearCache();
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

    private static final class DashboardReadCache<T> {

        private final Duration ttl;
        private volatile CachedValue<T> value;

        private DashboardReadCache(Duration ttl) {
            this.ttl = ttl;
        }

        T get(Supplier<T> loader) {
            Instant now = Instant.now();
            CachedValue<T> current = value;
            if (current != null && current.isFresh(now)) {
                return current.value();
            }
            synchronized (this) {
                now = Instant.now();
                current = value;
                if (current != null && current.isFresh(now)) {
                    return current.value();
                }
                T loaded = loader.get();
                value = new CachedValue<>(loaded, now.plus(ttl));
                return loaded;
            }
        }

        void clear() {
            value = null;
        }
    }

    private static final class DashboardKeyedReadCache<K, T> {

        private final Duration ttl;
        private final Map<K, CachedValue<T>> values = new ConcurrentHashMap<>();

        private DashboardKeyedReadCache(Duration ttl) {
            this.ttl = ttl;
        }

        T get(K key, Supplier<T> loader) {
            Instant now = Instant.now();
            CachedValue<T> current = values.get(key);
            if (current != null && current.isFresh(now)) {
                return current.value();
            }
            synchronized (this) {
                now = Instant.now();
                current = values.get(key);
                if (current != null && current.isFresh(now)) {
                    return current.value();
                }
                T loaded = loader.get();
                values.put(key, new CachedValue<>(loaded, now.plus(ttl)));
                return loaded;
            }
        }

        void clear() {
            values.clear();
        }
    }

    private record CachedValue<T>(T value, Instant expiresAt) {

        boolean isFresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
