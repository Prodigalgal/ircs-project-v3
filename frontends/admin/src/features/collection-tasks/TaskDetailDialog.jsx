import React, { useRef, useEffect } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Typography,
    Grid, Paper, Chip, LinearProgress, Stack, IconButton, Tooltip,
    Alert, Table, TableBody, TableCell, TableContainer, TableHead, TableRow
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import TimerIcon from '@mui/icons-material/Timer';
import SpeedIcon from '@mui/icons-material/Speed';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import { useGetCollectionTaskByIdQuery, useGetCollectionTaskRuntimeQuery, useGetTaskLogsQuery } from './tasksApi';
import { useTaskProgress } from '../../shared/hooks/useTaskProgress';
import { TASK_STATUS, getStatusConfig } from '../../utils/constants';

function StatCard({ label, value, icon, subValue, color = 'text.primary' }) {
    return (
        <Paper variant="outlined" sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1, color: 'text.secondary' }}>
                {icon && <Box component="span" sx={{ mr: 1, display: 'flex' }}>{icon}</Box>}
                <Typography variant="caption" sx={{
                    fontWeight: "bold"
                }}>{label}</Typography>
            </Box>
            <Typography variant="h5" color={color} sx={{
                fontWeight: "500"
            }}>
                {value}
            </Typography>
            {subValue && (
                <Typography
                    variant="caption"
                    sx={{
                        color: "text.secondary",
                        mt: 0.5
                    }}>
                    {subValue}
                </Typography>
            )}
        </Paper>
    );
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString();
}

function percent(done, total) {
    const safeTotal = Number(total || 0);
    if (safeTotal <= 0) return 0;
    return Math.min(100, Math.round((Number(done || 0) / safeTotal) * 100));
}

function pendingCount(total, done) {
    return Math.max(0, Number(total || 0) - Number(done || 0));
}

function asArray(value) {
    if (Array.isArray(value)) return value;
    return [];
}

function asPageList(value) {
    if (Array.isArray(value)) return value;
    if (value && typeof value === 'object') return Object.values(value);
    return [];
}

function formatDateTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleString();
}

function formatAge(value, referenceValue) {
    if (!value) return '-';
    const date = new Date(value);
    const reference = referenceValue ? new Date(referenceValue) : new Date();
    if (Number.isNaN(date.getTime()) || Number.isNaN(reference.getTime())) return '-';
    const seconds = Math.max(0, Math.round((reference.getTime() - date.getTime()) / 1000));
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes}m`;
    const hours = Math.round(minutes / 60);
    if (hours < 48) return `${hours}h`;
    return `${Math.round(hours / 24)}d`;
}

function formatRate(count, startValue, endValue) {
    const start = startValue ? new Date(startValue) : null;
    const end = endValue ? new Date(endValue) : new Date();
    if (!start || Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return '-';
    const minutes = Math.max(1 / 60, (end.getTime() - start.getTime()) / 60000);
    return `${(Number(count || 0) / minutes).toFixed(1)}/min`;
}

function pageProgress(page) {
    if (Number.isFinite(Number(page.progressPercent))) {
        return Math.min(100, Math.max(0, Number(page.progressPercent)));
    }
    return percent(page.detailCompleted, page.detailScheduled);
}

function pagePending(page) {
    if (Number.isFinite(Number(page.detailBacklog))) {
        return Math.max(0, Number(page.detailBacklog));
    }
    return pendingCount(page.detailScheduled, page.detailCompleted);
}

function pageStatusColor(page) {
    if (page.attentionLevel === 'ERROR' || page.detailFailed > 0) return 'warning';
    if (page.status === 'COMPLETED') return 'success';
    if (page.attentionLevel === 'RUNNING' || page.status === 'RUNNING') return 'info';
    return 'default';
}

function shortId(value) {
    if (!value) return '-';
    return value.length > 10 ? value.slice(0, 8) : value;
}

function formatIdList(values = [], max = 3) {
    if (!values.length) return '-';
    const visible = values.slice(0, max).map(shortId).join(', ');
    return values.length > max ? `${visible} +${values.length - max}` : visible;
}

function failedDetailEntries(page) {
    const errors = page.failedDetailErrors || {};
    const ids = asArray(page.failedDetailTaskIds);
    if (ids.length === 0) return [];
    return ids.map((id) => [id, errors[id] || page.lastError || '']);
}

function failedDetailSummary(page) {
    const entries = failedDetailEntries(page);
    if (entries.length === 0) return page.lastError || '-';
    const [id, error] = entries[0];
    return `${shortId(id)}${error ? ` · ${error}` : ''}`;
}

function MetricCell({ label, value, subValue, color = 'text.primary' }) {
    return (
        <Box sx={{ p: 1.25, border: '1px solid', borderColor: 'divider', borderRadius: 1, minHeight: 78 }}>
            <Typography
                variant="caption"
                sx={{
                    color: "text.secondary",
                    fontWeight: "bold"
                }}>
                {label}
            </Typography>
            <Typography variant="h6" color={color} sx={{ mt: 0.5, lineHeight: 1.15 }}>
                {value}
            </Typography>
            {subValue && (
                <Typography
                    variant="caption"
                    sx={{
                        color: "text.secondary",
                        display: 'block',
                        mt: 0.5
                    }}>
                    {subValue}
                </Typography>
            )}
        </Box>
    );
}

function QueueRuntimePanel({ runtime, loading }) {
    if (loading && !runtime) {
        return (
            <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle2" gutterBottom>MT/PT/DT 队列视图</Typography>
                <LinearProgress />
            </Paper>
        );
    }

    if (!runtime?.redisSnapshotPresent && !runtime?.redisStatePresent) {
        return (
            <Alert severity="info" variant="outlined" sx={{ fontSize: '0.8rem' }}>
                暂无 MT/PT/DT 运行态快照
            </Alert>
        );
    }

    const master = runtime.master || {};
    const pages = asPageList(runtime.pages);
    const detailProgress = percent(master.detailCompleted, master.detailScheduled);
    const pageDiscoveryProgress = percent(master.pageDiscovered, master.pageScheduled);
    const detailPending = pendingCount(master.detailScheduled, master.detailCompleted);
    const pageDiscoveryPending = pendingCount(master.pageScheduled, master.pageDiscovered);
    const detailSuccessRate = percent(master.detailSucceeded, master.detailCompleted);
    const detailFailureRate = percent(master.detailFailed, master.detailCompleted);
    const visibleStats = pages.reduce((acc, page) => {
        if (page.detailFailed > 0) acc.failed += 1;
        if (page.detailScheduled <= 0) {
            acc.empty += 1;
        } else if (page.detailCompleted >= page.detailScheduled) {
            acc.completed += 1;
        } else if (page.detailCompleted > 0) {
            acc.running += 1;
        } else {
            acc.waiting += 1;
        }
        acc.detailBacklog += pagePending(page);
        acc.failedDetails += Number(page.failedDetailTaskCount || page.detailFailed || 0);
        return acc;
    }, { completed: 0, running: 0, waiting: 0, empty: 0, failed: 0, detailBacklog: 0, failedDetails: 0 });
    const attentionPages = pages
        .filter((page) => page.attentionLevel === 'ERROR' || page.detailFailed > 0 || page.lastError || pagePending(page) > 0)
        .sort((a, b) => {
            const failureDiff = Number(b.detailFailed || 0) - Number(a.detailFailed || 0);
            if (failureDiff !== 0) return failureDiff;
            const failedSampleDiff = Number(b.failedDetailTaskCount || 0) - Number(a.failedDetailTaskCount || 0);
            if (failedSampleDiff !== 0) return failedSampleDiff;
            return pagePending(b) - pagePending(a);
        })
        .slice(0, 6);
    const totalPageCount = runtime.totalPageCount ?? 0;
    const hasMorePages = totalPageCount > runtime.visiblePageCount;
    const refreshedAt = runtime.refreshedAt || new Date().toISOString();
    const updatedAt = master.updatedAt || runtime.refreshedAt;

    return (
        <Paper variant="outlined" sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                <Box>
                    <Typography variant="subtitle2" sx={{
                        fontWeight: "bold"
                    }}>MT/PT/DT 队列视图</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>
                        {master.status || '-'} · {runtime.redisStatePresent ? 'Redis state' : 'Redis snapshot'}
                    </Typography>
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.secondary",
                            display: 'block'
                        }}>
                        刷新 {formatDateTime(runtime.refreshedAt)} · 状态延迟 {formatAge(updatedAt, refreshedAt)}
                    </Typography>
                </Box>
                <Chip
                    size="small"
                    label={`PT ${formatNumber(master.pageDiscovered)} / ${formatNumber(master.pageScheduled)}`}
                    color={master.detailFailed > 0 ? 'warning' : 'primary'}
                    variant="outlined"
                />
            </Box>

            <Grid container spacing={1.5} sx={{ mb: 2 }}>
                <Grid size={{ xs: 6, sm: 4 }}>
                    <MetricCell
                        label="PT 发现"
                        value={`${pageDiscoveryProgress}%`}
                        subValue={`${formatNumber(master.pageDiscovered)} / ${formatNumber(master.pageScheduled)} · 待发现 ${formatNumber(pageDiscoveryPending)}`}
                    />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                    <MetricCell
                        label="DT 完成"
                        value={`${detailProgress}%`}
                        subValue={`${formatNumber(master.detailCompleted)} / ${formatNumber(master.detailScheduled)} · backlog ${formatNumber(detailPending)}`}
                    />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                    <MetricCell
                        label="DT 成功率"
                        value={`${detailSuccessRate}%`}
                        subValue={`成功 ${formatNumber(master.detailSucceeded)} · 失败率 ${detailFailureRate}%`}
                        color={master.detailFailed > 0 ? 'warning.main' : 'success.main'}
                    />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                    <MetricCell
                        label="失败 DT"
                        value={formatNumber(master.detailFailed)}
                        subValue={`失败 PT ${formatNumber(master.pageFailed || visibleStats.failed)} · 样本 ${formatNumber(visibleStats.failedDetails)}`}
                        color={master.detailFailed > 0 ? 'error.main' : 'text.primary'}
                    />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                    <MetricCell
                        label="处理速率"
                        value={formatRate(master.detailCompleted, master.queuedAt, updatedAt)}
                        subValue={`启动 ${formatDateTime(master.queuedAt)}`}
                    />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                    <MetricCell
                        label="源站规模"
                        value={formatNumber(master.totalItems || 0)}
                        subValue={`页数 ${formatNumber(master.totalPages || 0)}`}
                    />
                </Grid>
            </Grid>

            <Box sx={{ mb: 1.5 }}>
                <Typography
                    variant="caption"
                    sx={{
                        color: "text.secondary",
                        fontWeight: "bold"
                    }}>
                    PT 当前窗口分布
                </Typography>
                <Stack
                    direction="row"
                    spacing={1}
                    useFlexGap
                    sx={{
                        flexWrap: "wrap",
                        mt: 0.75
                    }}>
                    <Chip size="small" label={`完成 ${formatNumber(visibleStats.completed)}`} color="success" variant="outlined" />
                    <Chip size="small" label={`运行 ${formatNumber(visibleStats.running)}`} color="info" variant="outlined" />
                    <Chip size="small" label={`等待 ${formatNumber(visibleStats.waiting)}`} variant="outlined" />
                    <Chip size="small" label={`空页 ${formatNumber(visibleStats.empty)}`} variant="outlined" />
                    <Chip size="small" label={`异常 ${formatNumber(visibleStats.failed)}`} color={visibleStats.failed > 0 ? 'warning' : 'default'} variant="outlined" />
                    <Chip size="small" label={`失败 DT 样本 ${formatNumber(visibleStats.failedDetails)}`} color={visibleStats.failedDetails > 0 ? 'error' : 'default'} variant="outlined" />
                    <Chip size="small" label={`窗口 DT backlog ${formatNumber(visibleStats.detailBacklog)}`} variant="outlined" />
                </Stack>
            </Box>

            {attentionPages.length > 0 && (
                <Box sx={{ mb: 1.5 }}>
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.secondary",
                            fontWeight: "bold"
                        }}>
                        需要关注的 PT
                    </Typography>
                    <TableContainer sx={{ mt: 0.75, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell width={72}>页码</TableCell>
                                    <TableCell width={110}>进度</TableCell>
                                    <TableCell width={86}>Backlog</TableCell>
                                    <TableCell width={70}>失败</TableCell>
                                    <TableCell width={130}>失败 DT</TableCell>
                                    <TableCell>错误摘要</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {attentionPages.map((page) => (
                                    <TableRow key={page.pageTaskId}>
                                        <TableCell>{page.pageNumber ?? '-'}</TableCell>
                                        <TableCell>
                                            <Stack spacing={0.5}>
                                                <Typography variant="caption">{pageProgress(page)}%</Typography>
                                                <LinearProgress variant="determinate" value={pageProgress(page)} color={page.detailFailed > 0 ? 'warning' : 'primary'} />
                                            </Stack>
                                        </TableCell>
                                        <TableCell>{formatNumber(pagePending(page))}</TableCell>
                                        <TableCell sx={{ color: page.detailFailed > 0 ? 'error.main' : 'text.secondary' }}>
                                            {formatNumber(page.detailFailed)}
                                        </TableCell>
                                        <TableCell>
                                            <Typography
                                                variant="caption"
                                                component="div"
                                                sx={{ fontFamily: 'Consolas, monospace', color: (page.failedDetailTaskCount || 0) > 0 ? 'error.main' : 'text.secondary' }}
                                                title={(page.failedDetailTaskIds || []).join(', ')}
                                            >
                                                {formatIdList(page.failedDetailTaskIds || [])}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography
                                                variant="caption"
                                                component="div"
                                                sx={{ maxWidth: 300, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                                                title={failedDetailEntries(page).map(([id, error]) => `${id}: ${error}`).join('\n') || page.lastError || page.pageTaskId}
                                            >
                                                {failedDetailSummary(page)}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Box>
            )}

            <TableContainer sx={{ maxHeight: 320, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                <Table size="small" stickyHeader>
                    <TableHead>
                        <TableRow>
                            <TableCell width={72}>页码</TableCell>
                            <TableCell width={110}>状态</TableCell>
                            <TableCell width={150}>DT 进度</TableCell>
                            <TableCell width={86}>Backlog</TableCell>
                            <TableCell width={120}>成功/失败</TableCell>
                            <TableCell width={150}>更新时间</TableCell>
                            <TableCell>DT 样本</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {pages.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={7}>
                                    <Typography variant="caption" sx={{
                                        color: "text.secondary"
                                    }}>暂无 PT 明细</Typography>
                                </TableCell>
                            </TableRow>
                        ) : pages.map((page, index) => (
                            <TableRow key={page.pageTaskId || `${page.pageNumber ?? 'page'}-${index}`}>
                                <TableCell>{page.pageNumber ?? '-'}</TableCell>
                                <TableCell>
                                    <Chip
                                        size="small"
                                        label={page.status || '-'}
                                        color={pageStatusColor(page)}
                                        variant="outlined"
                                    />
                                </TableCell>
                                <TableCell>
                                    <Stack spacing={0.5}>
                                        <Typography variant="caption">
                                            {formatNumber(page.detailCompleted)} / {formatNumber(page.detailScheduled)} ({pageProgress(page)}%)
                                        </Typography>
                                        <LinearProgress variant="determinate" value={pageProgress(page)} color={page.detailFailed > 0 ? 'warning' : 'primary'} />
                                    </Stack>
                                </TableCell>
                                <TableCell>
                                    {formatNumber(pagePending(page))}
                                </TableCell>
                                <TableCell>
                                    <Typography variant="caption" sx={{ color: '#66bb6a' }}>
                                        {formatNumber(page.detailSucceeded)}
                                    </Typography>
                                    <Typography variant="caption" sx={{ mx: 0.5, color: 'text.disabled' }}>/</Typography>
                                    <Typography variant="caption" sx={{ color: page.detailFailed > 0 ? '#ef5350' : 'text.secondary' }}>
                                        {formatNumber(page.detailFailed)}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Typography variant="caption" sx={{
                                        color: "text.secondary"
                                    }}>
                                        {formatDateTime(page.updatedAt)}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Stack spacing={0.25}>
                                        <Typography
                                            variant="caption"
                                            component="div"
                                            sx={{
                                                maxWidth: 260,
                                                whiteSpace: 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                fontFamily: 'Consolas, monospace'
                                            }}
                                            title={asArray(page.completedDetailTaskIds).join(', ')}
                                        >
                                            done: {formatIdList(asArray(page.completedDetailTaskIds))}
                                        </Typography>
                                        {asArray(page.failedDetailTaskIds).length > 0 && (
                                            <Tooltip
                                                title={failedDetailEntries(page).map(([id, error]) => `${id}: ${error}`).join('\n')}
                                                placement="top"
                                                arrow
                                            >
                                                <Typography
                                                    variant="caption"
                                                    component="div"
                                                    sx={{
                                                        maxWidth: 260,
                                                        whiteSpace: 'nowrap',
                                                        overflow: 'hidden',
                                                        textOverflow: 'ellipsis',
                                                        fontFamily: 'Consolas, monospace',
                                                        color: 'error.main'
                                                    }}
                                                >
                                                    failed: {formatIdList(asArray(page.failedDetailTaskIds))}
                                                </Typography>
                                            </Tooltip>
                                        )}
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>

            {hasMorePages && (
                <Typography
                    variant="caption"
                    sx={{
                        color: "text.secondary",
                        display: 'block',
                        mt: 1
                    }}>
                    已显示 {runtime.visiblePageCount} / {totalPageCount} 个运行态 PT；如需继续排查，可扩大 runtime pageLimit 或增加分页入口。
                </Typography>
            )}
        </Paper>
    );
}

function TaskDetailDialog({ open, onClose, taskId }) {
    const logEndRef = useRef(null);

    const { data: task, isLoading } = useGetCollectionTaskByIdQuery(taskId, {
        skip: !open || !taskId,
        pollingInterval: 3000,
    });

    const { data: runtime, isLoading: isRuntimeLoading } = useGetCollectionTaskRuntimeQuery(
        { id: taskId, pageOffset: 0, pageLimit: 200, detailLimit: 20 },
        { skip: !open || !taskId, pollingInterval: 2000 }
    );

    const { data: logs = [] } = useGetTaskLogsQuery(
        { id: taskId, offset: 0, limit: 200 },
        { skip: !open || !taskId, pollingInterval: 2000 }
    );

    const progress = useTaskProgress(task);

    useEffect(() => {
        if (logEndRef.current) {
            logEndRef.current.scrollIntoView({ behavior: "smooth" });
        }
    }, [logs]);

    const handleCopyError = () => {
        if (task?.lastErrorMessage) {
            navigator.clipboard.writeText(task.lastErrorMessage);
        }
    };

    if (!open) return null;

    const statusConfig = getStatusConfig(TASK_STATUS, task?.status);

    const getEndTimeDisplay = () => {
        if (task?.statEndTime) {
            return new Date(task.statEndTime).toLocaleString();
        }
        if (['RUNNING', 'QUEUED', 'PAUSED', 'STOPPING'].includes(task?.status)) {
            return <span style={{ color: '#4caf50', fontStyle: 'italic' }}>执行中...</span>;
        }
        return '-';
    };

    const getLogColor = (level) => {
        switch (level) {
            case 'ERROR': return '#ff5252';
            case 'WARN': return '#ffb74d';
            case 'SKIP': return '#ffa726';
            case 'INFO': return '#69f0ae';
            default: return '#b0bec5';
        }
    };

    const pageEndDisplay = progress
        ? (progress.configEndPage === 0 ? progress.computedEndPage : progress.configEndPage)
        : '-';

    return (
        <Dialog open={open} onClose={onClose} maxWidth="xl" fullWidth>
            <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', pb: 1 }}>
                <Box>
                    <Typography variant="h6">任务详情与运行监控</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>ID: {taskId}</Typography>
                </Box>
                <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
            </DialogTitle>

            <DialogContent dividers sx={{ p: 2, maxHeight: 'calc(100vh - 150px)' }}>
                {isLoading || !task || !progress ? (
                    <LinearProgress />
                ) : (
                    <Stack spacing={2}>
                        <Paper variant="outlined" sx={{ p: 2 }}>
                            <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
                                <Box sx={{ minWidth: 0 }}>
                                    <Stack
                                        direction="row"
                                        spacing={1.5}
                                        useFlexGap
                                        sx={{
                                            alignItems: "center",
                                            flexWrap: "wrap"
                                        }}>
                                        <Typography variant="subtitle1" sx={{
                                            fontWeight: "bold"
                                        }}>{task.name}</Typography>
                                        <Chip
                                            label={statusConfig.label}
                                            color={statusConfig.color}
                                            size="small"
                                            variant={task.status === 'RUNNING' ? 'filled' : 'outlined'}
                                        />
                                    </Stack>
                                    <Typography variant="caption" sx={{
                                        color: "text.secondary"
                                    }}>
                                        开始: {task.statStartTime ? new Date(task.statStartTime).toLocaleString() : '-'} · 结束: {getEndTimeDisplay()}
                                    </Typography>
                                </Box>
                                <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
                                    <Typography
                                        variant="caption"
                                        sx={{
                                            display: "block",
                                            color: "text.secondary"
                                        }}>
                                        当前进度 (页码)
                                    </Typography>
                                    <Typography variant="body2" sx={{
                                        fontWeight: "bold"
                                    }}>
                                        {progress.currentPage} / {pageEndDisplay}
                                        <span style={{ color: '#888' }}> ({progress.progressPercent}%)</span>
                                    </Typography>
                                </Box>
                            </Box>
                            <LinearProgress
                                variant={progress.isIndeterminate ? "indeterminate" : "determinate"}
                                value={progress.progressPercent}
                                sx={{ height: 10, borderRadius: 5 }}
                                color={statusConfig.color === 'default' ? 'primary' : statusConfig.color}
                            />
                        </Paper>

                        <Grid container spacing={2}>
                            <Grid size={{ xs: 6, md: 2.4 }}>
                                <StatCard
                                    label="已发现约"
                                    value={formatNumber(progress.discovered)}
                                    icon={<PlayCircleOutlineIcon fontSize="small" />}
                                    subValue={progress.statTotalFound > 0 ? `总量约 ${formatNumber(progress.statTotalFound)}` : ''}
                                />
                            </Grid>
                            <Grid size={{ xs: 6, md: 2.4 }}>
                                <StatCard
                                    label="DT 完成"
                                    value={formatNumber(progress.processed)}
                                    icon={<CheckCircleIcon fontSize="small" />}
                                    subValue={`成功率 ${progress.successRate}%`}
                                />
                            </Grid>
                            <Grid size={{ xs: 6, md: 2.4 }}>
                                <StatCard
                                    label="平均速率"
                                    value={progress.speed}
                                    icon={<SpeedIcon fontSize="small" />}
                                    subValue="DT/秒"
                                />
                            </Grid>
                            <Grid size={{ xs: 6, md: 2.4 }}>
                                <StatCard
                                    label="运行耗时"
                                    value={progress.durationFormatted}
                                    icon={<TimerIcon fontSize="small" />}
                                    subValue={progress.etaText ? `剩余约 ${progress.etaText}` : ''}
                                />
                            </Grid>
                            <Grid size={{ xs: 6, md: 2.4 }}>
                                <StatCard
                                    label="失败数"
                                    value={formatNumber(progress.failed)}
                                    color={progress.failed > 0 ? 'error.main' : 'text.primary'}
                                    icon={<ErrorIcon fontSize="small" />}
                                />
                            </Grid>
                        </Grid>

                        <Paper variant="outlined" sx={{ p: 2 }}>
                            <Typography
                                variant="caption"
                                sx={{
                                    color: "text.secondary",
                                    fontWeight: "bold"
                                }}>
                                入库结果分布 (Insert / Update / Ignore / Fail)
                            </Typography>
                            <Box sx={{ display: 'flex', height: 24, borderRadius: 1, overflow: 'hidden', bgcolor: 'action.disabledBackground', mt: 1 }}>
                                <Tooltip title={`新增: ${progress.inserted}`}>
                                    <Box sx={{ width: `${progress.distribution.ins}%`, bgcolor: '#66bb6a', transition: 'width 0.5s' }} />
                                </Tooltip>
                                <Tooltip title={`更新: ${progress.updated}`}>
                                    <Box sx={{ width: `${progress.distribution.upd}%`, bgcolor: '#42a5f5', transition: 'width 0.5s' }} />
                                </Tooltip>
                                <Tooltip title={`跳过: ${progress.ignored}`}>
                                    <Box sx={{ width: `${progress.distribution.ign}%`, bgcolor: '#ffa726', transition: 'width 0.5s' }} />
                                </Tooltip>
                                <Tooltip title={`失败: ${progress.failed}`}>
                                    <Box sx={{ width: `${progress.distribution.fail}%`, bgcolor: '#ef5350', transition: 'width 0.5s' }} />
                                </Tooltip>
                            </Box>
                            <Stack
                                direction="row"
                                spacing={2}
                                useFlexGap
                                sx={{
                                    flexWrap: "wrap",
                                    mt: 0.75
                                }}>
                                <Typography variant="caption" sx={{ color: '#66bb6a' }}>新增: {formatNumber(progress.inserted)}</Typography>
                                <Typography variant="caption" sx={{ color: '#42a5f5' }}>更新: {formatNumber(progress.updated)}</Typography>
                                <Typography variant="caption" sx={{ color: '#ffa726' }}>跳过: {formatNumber(progress.ignored)}</Typography>
                                <Typography variant="caption" sx={{ color: '#ef5350' }}>失败: {formatNumber(progress.failed)}</Typography>
                            </Stack>
                        </Paper>

                        <QueueRuntimePanel runtime={runtime} loading={isRuntimeLoading} />

                        {task.lastErrorMessage && (
                            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'rgba(244,67,54,0.05)' }}>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.75 }}>
                                    <Typography variant="subtitle2" color="error">最近一次致命错误</Typography>
                                    <Button
                                        size="small"
                                        startIcon={<ContentCopyIcon />}
                                        onClick={handleCopyError}
                                        sx={{ fontSize: '0.7rem' }}
                                    >
                                        复制日志
                                    </Button>
                                </Box>
                                <Box
                                    sx={{
                                        color: '#ff8a80',
                                        fontFamily: 'monospace',
                                        fontSize: '0.75rem',
                                        maxHeight: 120,
                                        overflow: 'auto',
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-all'
                                    }}
                                >
                                    {task.lastErrorMessage}
                                </Box>
                            </Paper>
                        )}

                        <Box>
                            <Typography variant="caption" sx={{ fontWeight: 'bold', mb: 1, color: 'text.secondary', display: 'block' }}>
                                实时运行审计日志 (Redis Top 200)
                            </Typography>
                            <Box sx={{
                                bgcolor: '#000',
                                border: '1px solid #333',
                                borderRadius: 1,
                                p: 1.5,
                                overflowY: 'auto',
                                minHeight: 260,
                                maxHeight: 360,
                                fontFamily: 'Consolas, monospace',
                                fontSize: '0.75rem',
                                lineHeight: 1.6
                            }}>
                                {logs.length === 0 && (
                                    <Typography
                                        sx={{
                                            color: "text.disabled",
                                            fontStyle: 'italic',
                                            textAlign: 'center',
                                            mt: 8
                                        }}>
                                        暂无日志数据，等待任务执行...
                                    </Typography>
                                )}
                                {logs.slice().reverse().map((log, index) => (
                                    <div key={index} style={{ color: getLogColor(log.level) }}>
                                        <span style={{ color: '#555', marginRight: 8 }}>
                                            [{new Date(log.timestamp).toLocaleTimeString()}]
                                        </span>
                                        <span style={{ display: 'inline-block', width: 40, fontWeight: 'bold' }}>
                                            {log.level}
                                        </span>
                                        <span style={{ color: '#00bcd4', marginRight: 8 }}>
                                            [{log.sourceVid || 'SYSTEM'}]
                                        </span>
                                        {log.message}
                                    </div>
                                ))}
                                <div ref={logEndRef} />
                            </Box>
                        </Box>
                    </Stack>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>关闭</Button>
            </DialogActions>
        </Dialog>
    );
}

export default TaskDetailDialog;
