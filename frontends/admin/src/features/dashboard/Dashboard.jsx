// File: ircs-frontend/src/features/dashboard/Dashboard.jsx
import React, { Suspense, lazy, useState, useCallback, useEffect, useMemo } from 'react';
import { Alert, Box, Button, CircularProgress, Container, LinearProgress, Typography, MenuItem, Select, FormControl, InputLabel, Tabs, Tab } from '@mui/material';
import Grid from '@mui/material/Grid';
import {
    DataObject, Movie, Pending, Refresh as RefreshIcon, Storage,
    MonitorHeart, Analytics, Dashboard as DashboardIcon, ViewModule
} from '@mui/icons-material';
import {
    useGetDashboardStatsQuery,
    useGetDashboardTrendQuery,
    useGetDashboardDistributionsQuery,
    useGetDashboardCoverageQuery,
    useGetDashboardEfficiencyQuery,
    useRefreshDashboardMutation,
    useGetRealTimeMetricsQuery,
    useGetTaskRuntimeOverviewQuery,
    useGetSearchOpsStatsQuery,
    useGetAggregationOpsStatsQuery,
    useGetSourceQualityReportQuery
} from './dashboardApi';
import StatCard from './components/StatCard';
import { useDashboardTopicStreams } from './useDashboardTopicStreams';

const DashboardAnalysisPanel = lazy(() => import('./components/DashboardAnalysisPanel'));
const SystemMonitor = lazy(() => import('./components/SystemMonitor'));
const SourceQualityTable = lazy(() => import('./components/SourceQualityTable'));

const TIME_RANGES = [
    { value: 7, label: '近 7 天' },
    { value: 14, label: '近 14 天' },
    { value: 30, label: '近 30 天' },
    { value: 90, label: '近 90 天' },
    { value: 365, label: '近 1 年' }
];

function TabPanel(props) {
    const { children, value, index, ...other } = props;
    return (
        <div role="tabpanel" hidden={value !== index} {...other}>
            {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
        </div>
    );
}

function DeferredPanelFallback({ label = '正在加载面板...' }) {
    return (
        <Box sx={{ py: 4 }}>
            <LinearProgress />
            <Typography
                variant="body2"
                sx={{
                    color: "text.secondary",
                    mt: 1
                }}>
                {label}
            </Typography>
        </Box>
    );
}

function Dashboard() {
    const [days, setDays] = useState(14);
    const [tabIndex, setTabIndex] = useState(0);
    const [monitorStreamsEnabled, setMonitorStreamsEnabled] = useState(false);

    const {
        data: dashboardStats,
        isLoading: isStatsLoading,
        isFetching: isStatsFetching,
        error: statsError,
        refetch: refetchStats
    } = useGetDashboardStatsQuery(undefined, {
        skip: tabIndex !== 0
    });

    const {
        data: weeklyTrend = [],
        isLoading: isTrendLoading,
        isFetching: isTrendFetching,
        error: trendError
    } = useGetDashboardTrendQuery(days, {
        skip: tabIndex !== 0
    });

    const {
        data: distributions,
        isLoading: isDistributionsLoading,
        isFetching: isDistributionsFetching,
        error: distributionsError
    } = useGetDashboardDistributionsQuery(undefined, {
        skip: tabIndex !== 0
    });

    const {
        data: idCoverage = {},
        isLoading: isCoverageLoading,
        isFetching: isCoverageFetching,
        error: coverageError
    } = useGetDashboardCoverageQuery(undefined, {
        skip: tabIndex !== 0
    });

    const {
        data: efficiency,
        isLoading: isEfficiencyLoading,
        isFetching: isEfficiencyFetching,
        error: efficiencyError
    } = useGetDashboardEfficiencyQuery(undefined, {
        skip: tabIndex !== 0
    });

    const [refreshAnalysis, { isLoading: isRefreshingAnalysis }] = useRefreshDashboardMutation();
    const dashboardStreams = useDashboardTopicStreams(monitorStreamsEnabled);

    useEffect(() => {
        if (tabIndex !== 1) {
            setMonitorStreamsEnabled(false);
            return undefined;
        }
        const timer = setTimeout(() => setMonitorStreamsEnabled(true), 1200);
        return () => clearTimeout(timer);
    }, [tabIndex]);

    const {
        data: polledMetrics,
        isLoading: isMetricsLoading,
        isFetching: isMetricsFetching,
        error: metricsError,
        refetch: refetchMetrics
    } = useGetRealTimeMetricsQuery(undefined, {
        skip: tabIndex !== 1
    });

    const {
        data: polledTaskRuntime,
        isLoading: isTaskRuntimeLoading,
        isFetching: isTaskRuntimeFetching,
        error: taskRuntimeError,
        refetch: refetchTaskRuntime
    } = useGetTaskRuntimeOverviewQuery(50, {
        skip: tabIndex !== 1
    });

    const {
        data: polledSearchOps,
        isLoading: isSearchOpsLoading,
        isFetching: isSearchOpsFetching,
        error: searchOpsError,
        refetch: refetchSearchOps
    } = useGetSearchOpsStatsQuery(undefined, {
        skip: tabIndex !== 1
    });

    const {
        data: polledAggregationOps,
        isLoading: isAggregationOpsLoading,
        isFetching: isAggregationOpsFetching,
        error: aggregationOpsError,
        refetch: refetchAggregationOps
    } = useGetAggregationOpsStatsQuery(undefined, {
        skip: tabIndex !== 1
    });

    const {
        data: qualityReport,
        isLoading: isQualityLoading,
        isFetching: isQualityFetching,
        refetch: refetchQuality
    } = useGetSourceQualityReportQuery(undefined, {
        pollingInterval: 300000,
        skip: tabIndex !== 2
    });

    const handleGlobalRefresh = useCallback(() => {
        if (tabIndex === 0) {
            refetchStats();
            refreshAnalysis(days);
        } else if (tabIndex === 1) {
            dashboardStreams.reconnect();
            refetchMetrics();
            refetchTaskRuntime();
            refetchSearchOps();
            refetchAggregationOps();
        } else if (tabIndex === 2) {
            refetchQuality();
        }
    }, [
        tabIndex,
        days,
        refetchStats,
        refreshAnalysis,
        dashboardStreams,
        refetchMetrics,
        refetchTaskRuntime,
        refetchSearchOps,
        refetchAggregationOps,
        refetchQuality
    ]);

    const isCurrentTabLoading =
        (tabIndex === 0 && (
            isStatsFetching ||
            isTrendFetching ||
            isDistributionsFetching ||
            isCoverageFetching ||
            isEfficiencyFetching ||
            isRefreshingAnalysis
        )) ||
        (tabIndex === 1 && (
            isMetricsFetching ||
            isTaskRuntimeFetching ||
            isSearchOpsFetching ||
            isAggregationOpsFetching ||
            dashboardStreams.streamState.connecting
        )) ||
        (tabIndex === 2 && isQualityFetching);

    // --- 数据转换逻辑 ---
    const idCoverageData = useMemo(() => {
        if (!idCoverage) return [];
        const rawMap = idCoverage;
        // Map backend keys to frontend labels
        const mapping = {
            'Both': '双ID齐全',
            'DoubanOnly': '仅豆瓣',
            'TmdbOnly': '仅TMDB',
            'None': '缺失ID'
        };
        return Object.entries(rawMap).map(([k, v]) => ({
            label: mapping[k] || k,
            value: v
        }));
    }, [idCoverage]);

    const sourceEfficiencyData = useMemo(() => {
        if (!efficiency?.sourceEfficiency) return [];
        return efficiency.sourceEfficiency.map(item => ({
            name: item.name,
            total: item.total,
            successRate: (item.successRate * 100).toFixed(1) // Convert to percentage
        }));
    }, [efficiency]);

    const categoryEfficiencyData = useMemo(() => {
        if (!efficiency?.categoryEfficiency) return [];
        return efficiency.categoryEfficiency.map(item => ({
            name: item.name,
            total: item.total,
            successRate: (item.successRate * 100).toFixed(1)
        }));
    }, [efficiency]);


    if (tabIndex === 0 && isStatsLoading && !dashboardStats) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
                <CircularProgress />
            </Box>
        );
    }

    if (tabIndex === 0 && statsError && !dashboardStats) {
        return (
            <Box sx={{ p: 3 }}>
                <Typography color="error">加载仪表盘数据失败，请检查网络或后端服务。</Typography>
                <Button variant="outlined" startIcon={<RefreshIcon />} onClick={handleGlobalRefresh} sx={{ mt: 2 }}>重试</Button>
            </Box>
        );
    }

    const {
        categoryDistribution = [],
        enrichmentStatusDistribution = []
    } = distributions || {};
    const stats = dashboardStats;
    const analysisLoading = (
        isTrendFetching ||
        isTrendLoading ||
        isDistributionsFetching ||
        isDistributionsLoading ||
        isCoverageFetching ||
        isCoverageLoading ||
        isEfficiencyFetching ||
        isEfficiencyLoading
    ) && (
        !weeklyTrend.length ||
        !distributions ||
        !idCoverage ||
        !efficiency
    );
    const analysisUnavailable = Boolean(
        trendError ||
        distributionsError ||
        coverageError ||
        efficiencyError
    );
    const metrics = dashboardStreams.metrics || polledMetrics;
    const taskRuntime = dashboardStreams.taskRuntime || polledTaskRuntime;
    const searchOps = dashboardStreams.searchOps || polledSearchOps;
    const aggregationOps = dashboardStreams.aggregationOps || polledAggregationOps;
    const monitorLoading = (isMetricsFetching || isMetricsLoading) && !metrics;
    const taskRuntimeLoading = (isTaskRuntimeFetching || isTaskRuntimeLoading) && !taskRuntime;

    return (
        <Box sx={{ height: '100%', overflow: 'auto' }}>
            <Container maxWidth="xl" sx={{ pt: 3, pb: 4 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <Typography variant="h4" sx={{ fontWeight: 'bold' }}>仪表盘</Typography>

                    <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                        {tabIndex === 0 && (
                            <FormControl size="small" variant="outlined" sx={{ minWidth: 120 }}>
                                <InputLabel>时间范围</InputLabel>
                                <Select value={days} label="时间范围" onChange={(e) => setDays(e.target.value)}>
                                    {TIME_RANGES.map(opt => <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>)}
                                </Select>
                            </FormControl>
                        )}
                        <Button
                            variant="contained"
                            startIcon={isCurrentTabLoading ? <CircularProgress size={20} color="inherit" /> : <RefreshIcon />}
                            onClick={handleGlobalRefresh}
                            disabled={isCurrentTabLoading}
                        >
                            {isCurrentTabLoading ? '刷新中...' : '刷新数据'}
                        </Button>
                    </Box>
                </Box>

                <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)} sx={{ borderBottom: 1, borderColor: 'divider' }}>
                    <Tab icon={<DashboardIcon />} label="总览" iconPosition="start" />
                    <Tab icon={<MonitorHeart />} label="系统监控" iconPosition="start" />
                    <Tab icon={<Analytics />} label="源质量分析" iconPosition="start" />
                </Tabs>

                <TabPanel value={tabIndex} index={0}>
                    {/* Row 1: Stat Cards */}
                    <Grid container spacing={3} sx={{ mb: 4 }}>
                        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
                            <StatCard
                                title="源视频总数 (Raw)"
                                value={stats?.rawCountDb?.toLocaleString() ?? '0'}
                                subtitle={`ES 索引: ${stats?.rawCountEs?.toLocaleString() ?? '0'}`}
                                icon={<Movie fontSize="large" />}
                                color="#2196f3"
                            />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
                            <StatCard
                                title="聚合视频总数 (Unified)"
                                value={stats?.unifiedCountDb?.toLocaleString() ?? '0'}
                                subtitle={`ES 索引: ${stats?.unifiedCountEs?.toLocaleString() ?? '0'}`}
                                icon={<ViewModule fontSize="large" />}
                                color="#9c27b0"
                            />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
                            <StatCard
                                title="待归一化 (Pending)"
                                value={stats?.pendingNormalization?.toLocaleString() ?? '0'}
                                icon={<DataObject fontSize="large" />}
                                color="#ff9800"
                                subtitle="等待字段映射清洗"
                            />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
                            <StatCard
                                title="待搜刮元数据"
                                value={stats?.pendingEnrichment?.toLocaleString() ?? '0'}
                                icon={<Pending fontSize="large" />}
                                color="#00bcd4"
                                subtitle="等待爬虫抓取"
                            />
                        </Grid>
                    </Grid>

                    {analysisLoading && (
                        <Box sx={{ mb: 3 }}>
                            <LinearProgress />
                            <Typography
                                variant="body2"
                                sx={{
                                    color: "text.secondary",
                                    mt: 1
                                }}>
                                正在加载趋势与分布图表，统计卡片已可先查看。
                            </Typography>
                        </Box>
                    )}

                    {analysisUnavailable && (
                        <Alert severity="warning" sx={{ mb: 3 }}>
                            趋势与分布图表暂时不可用，统计卡片不受影响。请稍后刷新重试。
                        </Alert>
                    )}

                    <Suspense fallback={<DeferredPanelFallback label="正在加载趋势与分布图表..." />}>
                        <DashboardAnalysisPanel
                            weeklyTrend={weeklyTrend}
                            days={days}
                            idCoverageData={idCoverageData}
                            enrichmentStatusDistribution={enrichmentStatusDistribution}
                            sourceEfficiencyData={sourceEfficiencyData}
                            categoryEfficiencyData={categoryEfficiencyData}
                            categoryDistribution={categoryDistribution}
                        />
                    </Suspense>
                </TabPanel>

                <TabPanel value={tabIndex} index={1}>
                    <Suspense fallback={<DeferredPanelFallback label="正在加载系统监控面板..." />}>
                        <SystemMonitor
                            metrics={metrics}
                            loading={monitorLoading}
                            taskRuntime={taskRuntime}
                            taskRuntimeLoading={taskRuntimeLoading}
                            searchOps={searchOps}
                            searchOpsLoading={(isSearchOpsFetching || isSearchOpsLoading) && !searchOps}
                            searchOpsError={searchOpsError}
                            aggregationOps={aggregationOps}
                            aggregationOpsLoading={(isAggregationOpsFetching || isAggregationOpsLoading) && !aggregationOps}
                            aggregationOpsError={aggregationOpsError}
                            metricsError={metricsError}
                            taskRuntimeError={taskRuntimeError}
                            streamState={dashboardStreams.streamState}
                        />
                    </Suspense>
                </TabPanel>

                <TabPanel value={tabIndex} index={2}>
                    <Suspense fallback={<DeferredPanelFallback label="正在加载源质量分析..." />}>
                        <SourceQualityTable
                            data={qualityReport}
                            loading={isQualityFetching || isQualityLoading}
                        />
                    </Suspense>
                </TabPanel>
            </Container>
        </Box>
    );
}

export default Dashboard;
