// File: ircs-frontend/src/features/dashboard/components/SystemMonitor.jsx
import React, { memo, useMemo } from 'react';
import { Alert, Box, Typography, Paper, Grid } from '@mui/material';
import TaskRuntimeOverview from './TaskRuntimeOverview';
import MetricCard from './system-monitor/MetricCard';
import PerformanceHealthChart from './system-monitor/PerformanceHealthChart';
import QueueGroup from './system-monitor/QueueGroup';
import ResourceTrendChart from './system-monitor/ResourceTrendChart';
import TopicStatusChips from './system-monitor/TopicStatusChips';
import WorkerLaneCard from './system-monitor/WorkerLaneCard';
import { createSystemMonitorViewModel } from './system-monitor/systemMonitorViewModel';
import { errorMessage, formatBytes, formatNumber, rateSubtitle, stableRate } from './system-monitor/systemMonitorFormatters';

function SystemMonitor({
    metrics,
    loading,
    metricsError,
    taskRuntime,
    taskRuntimeLoading,
    taskRuntimeError,
    searchOps,
    searchOpsLoading,
    searchOpsError,
    aggregationOps,
    aggregationOpsLoading,
    aggregationOpsError,
    streamState
}) {
    const {
        chartData,
        valkeyGroups,
        rabbitGroups,
        dlqGroups,
        valkeyBacklog,
        rabbitBacklog,
        dlqBacklog,
        searchLaneState,
        aggregationWorkerState,
        queueLaneState,
        laneSummary
    } = useMemo(
        () => createSystemMonitorViewModel({ metrics, searchOps, aggregationOps }),
        [metrics, searchOps, aggregationOps]
    );

    return (
        <Box sx={{ mt: 3 }}>
            <TopicStatusChips streamState={streamState} />
            {(metricsError || taskRuntimeError) && (
                <Alert severity="warning" variant="outlined" sx={{ mb: 2 }}>
                    {metricsError && `指标数据加载失败：${errorMessage(metricsError) || '请稍后重试'} `}
                    {taskRuntimeError && `运行任务加载失败：${errorMessage(taskRuntimeError) || '请稍后重试'}`}
                </Alert>
            )}
            <TaskRuntimeOverview overview={taskRuntime} loading={taskRuntimeLoading} />

            <Typography variant="overline" color="primary" sx={{ mb: 2, display: 'block', fontWeight: 'bold' }}>
                核心流水线速率 (RATE)
            </Typography>
            <Grid container spacing={3} sx={{ mb: 4 }}>
                <Grid size={{xs:12, sm:6, lg:2.4}}>
                    <MetricCard title="PT 发现速率" value={formatNumber(stableRate(metrics?.pageDiscoveryRate))} unit="条/分" color="#8884d8" loading={loading} subtitle={rateSubtitle(metrics?.pageDiscoveryRate)} />
                </Grid>
                <Grid size={{xs:12, sm:6, lg:2.4}}>
                    <MetricCard title="DT 采集速率" value={formatNumber(stableRate(metrics?.detailCollectionRate))} unit="条/分" color="#38bdf8" loading={loading} subtitle={rateSubtitle(metrics?.detailCollectionRate)} />
                </Grid>
                <Grid size={{xs:12, sm:6, lg:2.4}}>
                    <MetricCard title="清洗速率" value={formatNumber(stableRate(metrics?.normalizationRate))} unit="条/分" color="#82ca9d" loading={loading} subtitle={rateSubtitle(metrics?.normalizationRate)} />
                </Grid>
                <Grid size={{xs:12, sm:6, lg:2.4}}>
                    <MetricCard title="元数据速率" value={formatNumber(stableRate(metrics?.metadataRate))} unit="条/分" color="#00C49F" loading={loading} subtitle={rateSubtitle(metrics?.metadataRate)} />
                </Grid>
                <Grid size={{xs:12, sm:6, lg:2.4}}>
                    <MetricCard
                        title="Valkey 状态"
                        value={metrics?.redisMetric?.opsPerSec || 0}
                        unit="ops/s"
                        color="#ff7300"
                        loading={loading}
                        subtitle={`内存占用: ${formatBytes(metrics?.redisMetric?.usedMemoryBytes)}`}
                    />
                </Grid>
            </Grid>

            <Typography
                variant="overline"
                sx={{
                    color: "warning.main",
                    mb: 2,
                    display: 'block',
                    fontWeight: 'bold'
                }}>
                Valkey 运行态与基础设施背压
            </Typography>
            <Grid container spacing={3} sx={{ mb: 3 }}>
                <Grid size={{xs:12, sm:4}}>
                    <MetricCard title="Valkey Runtime Backlog" value={formatNumber(valkeyBacklog)} unit="" color="#2dd4bf" loading={loading} subtitle="统一 RuntimeWorkQueue" />
                </Grid>
                <Grid size={{xs:12, sm:4}}>
                    <MetricCard title="Rabbit Remaining" value={formatNumber(rabbitBacklog)} unit="" color="#a78bfa" loading={loading} subtitle="仅剩余 Rabbit 通道" />
                </Grid>
                <Grid size={{xs:12, sm:4}}>
                    <MetricCard title="DLQ Backlog" value={formatNumber(dlqBacklog)} unit="" color={dlqBacklog > 0 ? 'error.main' : 'success.main'} loading={loading} subtitle="Valkey + Rabbit 死信" />
                </Grid>
            </Grid>
            <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid size={{xs:12, md:4}}>
                    {searchOpsError ? (
                        <Alert severity="warning" variant="outlined">Search Raw 状态加载失败：{errorMessage(searchOpsError) || '请稍后重试'}</Alert>
                    ) : <WorkerLaneCard title="Search Raw Worker" lane={searchLaneState('search.sync.raw') || (searchOpsLoading ? { running: false, lastRunState: '加载中' } : null)} />}
                </Grid>
                <Grid size={{xs:12, md:4}}>
                    {searchOpsError ? (
                        <Alert severity="warning" variant="outlined">Search Unified 状态加载失败：{errorMessage(searchOpsError) || '请稍后重试'}</Alert>
                    ) : <WorkerLaneCard title="Search Unified Worker" lane={searchLaneState('search.sync.unified') || (searchOpsLoading ? { running: false, lastRunState: '加载中' } : null)} />}
                </Grid>
                <Grid size={{xs:12, md:4}}>
                    {aggregationOpsError ? (
                        <Alert severity="warning" variant="outlined">Aggregation 状态加载失败：{errorMessage(aggregationOpsError) || '请稍后重试'}</Alert>
                    ) : <WorkerLaneCard title="Aggregation Worker" lane={aggregationWorkerState || (aggregationOpsLoading ? { running: false, lastRunState: '加载中' } : null)} />}
                </Grid>
            </Grid>
            <Grid container spacing={3} sx={{ mb: 4 }}>
                <Grid size={{xs:12, lg:6}}>
                    <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.paper', height: '100%', overflowY: 'auto', maxHeight: 600 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                            <Typography variant="subtitle2">Valkey 运行队列实时堆积</Typography>
                            <Typography variant="caption" sx={{
                                color: "text.secondary"
                            }}>Rate: 事件桶/分钟</Typography>
                        </Box>

                        {valkeyGroups.map(group => <QueueGroup key={group.title} group={group} queueLaneState={queueLaneState} laneSummary={laneSummary} />)}
                        {!valkeyGroups.length && (
                            <Typography variant="body2" sx={{
                                color: "text.secondary"
                            }}>暂无 Valkey 运行队列指标。</Typography>
                        )}

                    </Paper>
                </Grid>

                <Grid size={{xs:12, lg:6}}>
                    <ResourceTrendChart chartData={chartData} />
                </Grid>
            </Grid>

            <Grid container spacing={3} sx={{ mb: 4 }}>
                <Grid size={{xs:12, lg:6}}>
                    <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.paper', height: '100%', overflowY: 'auto', maxHeight: 520 }}>
                        <Typography variant="subtitle2" sx={{ mb: 2 }}>剩余 Rabbit 通道</Typography>
                        {rabbitGroups.map(group => <QueueGroup key={group.title} group={group} queueLaneState={queueLaneState} laneSummary={laneSummary} />)}
                        {!rabbitGroups.length && (
                            <Typography variant="body2" sx={{
                                color: "text.secondary"
                            }}>暂无 Rabbit 队列指标。</Typography>
                        )}
                    </Paper>
                </Grid>
                <Grid size={{xs:12, lg:6}}>
                    <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.paper', height: '100%', overflowY: 'auto', maxHeight: 520 }}>
                        <Typography variant="subtitle2" sx={{ mb: 2 }}>死信积压</Typography>
                        {dlqGroups.map(group => <QueueGroup key={group.title} group={group} queueLaneState={queueLaneState} laneSummary={laneSummary} />)}
                        {!dlqGroups.length && (
                            <Typography variant="body2" sx={{
                                color: "text.secondary"
                            }}>暂无 DLQ 指标。</Typography>
                        )}
                    </Paper>
                </Grid>
            </Grid>

            <Grid container spacing={4}>
                <Grid size={{xs:12}}>
                    <PerformanceHealthChart chartData={chartData} />
                </Grid>
            </Grid>
        </Box>
    );
}

export default memo(SystemMonitor);
