import React from 'react';
import {
    Box,
    Chip,
    LinearProgress,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography
} from '@mui/material';
import Grid from '@mui/material/Grid';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import MemoryIcon from '@mui/icons-material/Memory';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import TaskAltIcon from '@mui/icons-material/TaskAlt';

const ATTENTION_META = {
    ERROR: { label: '异常', color: 'error', icon: <ErrorOutlineIcon fontSize="small" /> },
    RUNNING: { label: '运行', color: 'primary', icon: <PlayCircleOutlineIcon fontSize="small" /> },
    WAITING: { label: '等待', color: 'warning', icon: <HourglassEmptyIcon fontSize="small" /> },
    OK: { label: '正常', color: 'success', icon: <TaskAltIcon fontSize="small" /> }
};

function formatNumber(value) {
    const number = Number(value || 0);
    if (number >= 1000000) return `${(number / 1000000).toFixed(1)}M`;
    if (number >= 1000) return `${(number / 1000).toFixed(1)}k`;
    return number.toLocaleString();
}

function formatDateTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleString();
}

function formatAge(value, baseValue) {
    if (!value) return '-';
    const at = new Date(value).getTime();
    const base = baseValue ? new Date(baseValue).getTime() : Date.now();
    if (Number.isNaN(at) || Number.isNaN(base)) return '-';
    const seconds = Math.max(0, Math.floor((base - at) / 1000));
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m`;
    const hours = Math.floor(minutes / 60);
    return `${hours}h`;
}

function percent(done, total) {
    const safeTotal = Number(total || 0);
    if (safeTotal <= 0) return 0;
    return Math.min(100, Math.round((Number(done || 0) * 100) / safeTotal));
}

function StatBlock({ label, value, sub, color = 'text.primary' }) {
    return (
        <Paper variant="outlined" sx={{ p: 1.5, height: '100%' }}>
            <Typography
                variant="caption"
                sx={{
                    color: "text.secondary",
                    display: 'block',
                    mb: 0.5
                }}>
                {label}
            </Typography>
            <Typography variant="h6" color={color} sx={{ fontWeight: 700, lineHeight: 1.1 }}>
                {formatNumber(value)}
            </Typography>
            {sub && (
                <Typography
                    variant="caption"
                    sx={{
                        color: "text.disabled",
                        display: 'block',
                        mt: 0.5
                    }}>
                    {sub}
                </Typography>
            )}
        </Paper>
    );
}

function AttentionChip({ value }) {
    const meta = ATTENTION_META[value] || ATTENTION_META.OK;
    return (
        <Chip
            size="small"
            color={meta.color}
            variant="outlined"
            icon={meta.icon}
            label={meta.label}
            sx={{ height: 24, '& .MuiChip-label': { px: 0.75 } }}
        />
    );
}

function TaskRuntimeOverview({ overview, loading }) {
    const rows = overview?.activeMasters || [];
    const visibleRows = rows.slice(0, 8);
    const detailProgress = percent(overview?.detailCompleted, overview?.detailScheduled);

    return (
        <Paper variant="outlined" sx={{ p: 2.5, mb: 4 }}>
            {loading && <LinearProgress sx={{ mx: -2.5, mt: -2.5, mb: 2.5 }} />}

            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, mb: 2 }}>
                <Box>
                    <Stack
                        direction="row"
                        spacing={1}
                        sx={{
                            alignItems: "center",
                            mb: 0.5
                        }}>
                        <MemoryIcon color="primary" fontSize="small" />
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                            采集任务运行态
                        </Typography>
                    </Stack>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>
                        active MT {formatNumber(overview?.activeMasterCount)} · dirty {formatNumber(overview?.dirtyMasterCount)} · 刷新 {formatDateTime(overview?.refreshedAt)}
                    </Typography>
                </Box>
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', justifyContent: 'flex-end', rowGap: 1 }}>
                    {Object.entries(overview?.attentionCounts || {}).map(([key, value]) => (
                        <Chip key={key} size="small" label={`${ATTENTION_META[key]?.label || key} ${formatNumber(value)}`} />
                    ))}
                </Stack>
            </Box>

            <Grid container spacing={2} sx={{ mb: 2.5 }}>
                <Grid size={{ xs: 6, md: 3 }}>
                    <StatBlock label="PT 发现/计划" value={overview?.pageDiscovered} sub={`计划 ${formatNumber(overview?.pageScheduled)}`} />
                </Grid>
                <Grid size={{ xs: 6, md: 3 }}>
                    <StatBlock label="PT 失败" value={overview?.pageFailed} color={(overview?.pageFailed || 0) > 0 ? 'error.main' : 'success.main'} />
                </Grid>
                <Grid size={{ xs: 6, md: 3 }}>
                    <StatBlock label="DT 完成" value={overview?.detailCompleted} sub={`计划 ${formatNumber(overview?.detailScheduled)}`} />
                </Grid>
                <Grid size={{ xs: 6, md: 3 }}>
                    <StatBlock label="DT backlog" value={overview?.detailBacklog} color={(overview?.detailBacklog || 0) > 0 ? 'warning.main' : 'success.main'} />
                </Grid>
            </Grid>

            <Box sx={{ mb: 2.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>DT 总进度</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>{detailProgress}%</Typography>
                </Box>
                <LinearProgress
                    variant="determinate"
                    value={detailProgress}
                    sx={{ height: 7, borderRadius: 4 }}
                />
            </Box>

            <Table size="small" sx={{ '& td, & th': { borderColor: 'divider' } }}>
                <TableHead>
                    <TableRow>
                        <TableCell sx={{ width: '28%' }}>MT</TableCell>
                        <TableCell>状态</TableCell>
                        <TableCell align="right">PT</TableCell>
                        <TableCell align="right">DT</TableCell>
                        <TableCell align="right">Backlog</TableCell>
                        <TableCell>更新</TableCell>
                        <TableCell>错误</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {visibleRows.map((task) => (
                        <TableRow key={task.masterTaskId} hover>
                            <TableCell>
                                <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                                    {task.taskName || task.masterTaskId}
                                </Typography>
                                <Typography
                                    variant="caption"
                                    noWrap
                                    sx={{
                                        color: "text.disabled",
                                        fontFamily: 'monospace'
                                    }}>
                                    {task.masterTaskId}
                                </Typography>
                            </TableCell>
                            <TableCell>
                                <Stack direction="row" spacing={1} sx={{
                                    alignItems: "center"
                                }}>
                                    <AttentionChip value={task.attentionLevel} />
                                    <Typography variant="caption" sx={{
                                        color: "text.secondary"
                                    }}>{task.status || '-'}</Typography>
                                </Stack>
                            </TableCell>
                            <TableCell align="right">
                                <Typography variant="body2">{formatNumber(task.pageDiscovered)} / {formatNumber(task.pageScheduled)}</Typography>
                                <Typography variant="caption" color={task.pageFailed > 0 ? 'error.main' : 'text.disabled'}>
                                    fail {formatNumber(task.pageFailed)}
                                </Typography>
                            </TableCell>
                            <TableCell align="right">
                                <Typography variant="body2">{formatNumber(task.detailCompleted)} / {formatNumber(task.detailScheduled)}</Typography>
                                <Typography variant="caption" color={task.detailFailed > 0 ? 'error.main' : 'text.disabled'}>
                                    fail {formatNumber(task.detailFailed)}
                                </Typography>
                            </TableCell>
                            <TableCell align="right">
                                <Typography variant="body2" color={task.detailBacklog > 0 ? 'warning.main' : 'success.main'}>
                                    {formatNumber(task.detailBacklog)}
                                </Typography>
                                <Typography variant="caption" sx={{
                                    color: "text.disabled"
                                }}>{task.progressPercent || 0}%</Typography>
                            </TableCell>
                            <TableCell>
                                <Typography variant="caption" sx={{
                                    color: "text.secondary"
                                }}>
                                    {formatAge(task.updatedAt, overview?.refreshedAt)}
                                </Typography>
                            </TableCell>
                            <TableCell sx={{ maxWidth: 260 }}>
                                <Typography variant="caption" color={task.lastError ? 'error.main' : 'text.disabled'} noWrap>
                                    {task.lastError || '-'}
                                </Typography>
                            </TableCell>
                        </TableRow>
                    ))}
                    {!visibleRows.length && (
                        <TableRow>
                            <TableCell colSpan={7}>
                                <Typography
                                    variant="body2"
                                    sx={{
                                        color: "text.secondary",
                                        py: 2,
                                        textAlign: 'center'
                                    }}>
                                    暂无 active MT
                                </Typography>
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </Paper>
    );
}

export default TaskRuntimeOverview;
