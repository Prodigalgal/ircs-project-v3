// File: ircs-frontend/src/features/collection-tasks/CollectionTaskList.jsx
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
    Box, Typography, IconButton, Chip, Switch, Tooltip, Stack, LinearProgress, Divider, Button, CircularProgress
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import PauseIcon from '@mui/icons-material/Pause';
import StopIcon from '@mui/icons-material/Stop';
import TimerIcon from '@mui/icons-material/Timer';
import AssessmentIcon from '@mui/icons-material/Assessment';

import CollectionTaskForm from './CollectionTaskForm';
import TaskDetailDialog from './TaskDetailDialog';
import ListHeader from '../../components/common/ListHeader';
import FilterPanel from '../../components/common/FilterPanel';
import {
    useGetCollectionTasksQuery,
    useCreateCollectionTaskMutation,
    useUpdateCollectionTaskMutation,
    useDeleteCollectionTaskMutation,
    useStartTaskMutation,
    useResumeTaskMutation,
    usePauseTaskMutation,
    useStopTaskMutation,
    useLazyGetCollectionTaskByIdQuery
} from './tasksApi';
import { useGetAllDataSourcesQuery } from '../data-sources/dataSourcesApi';
import SmartTable from '../../components/common/SmartTable';
import { getTotalElements } from '../../utils/paginationUtils';
import { TASK_STATUS, getStatusConfig } from '../../utils/constants';
import { useTaskProgress } from '../../shared/hooks/useTaskProgress';

const BATCH_ACTION_CONCURRENCY = 3;

async function runLimitedBatch(items, limit, worker) {
    const failures = [];
    let cursor = 0;

    async function runNext() {
        while (cursor < items.length) {
            const item = items[cursor];
            cursor += 1;
            try {
                await worker(item);
            } catch (error) {
                failures.push({ item, error });
            }
        }
    }

    const workerCount = Math.min(Math.max(1, limit), items.length);
    await Promise.all(Array.from({ length: workerCount }, runNext));
    return failures;
}

function TaskProgressCell({ task }) {
    const progress = useTaskProgress(task);

    if (!progress) return null;

    const {
        processed, discovered, statTotalFound, inserted, updated, ignored, failed,
        progressPercent, isIndeterminate, etaText,
        currentPage, configEndPage, computedEndPage, hasBreakdownStats
    } = progress;
    const endPageDisplay = configEndPage === 0 ? (statTotalFound > 0 ? computedEndPage : '?') : configEndPage;

    let progressVariant = 'determinate';
    let barColor = 'primary';

    switch (task.status) {
        case 'IDLE':
            barColor = 'primary';
            break;
        case 'QUEUED':
            progressVariant = 'indeterminate';
            barColor = 'warning';
            break;
        case 'PAUSED':
            barColor = 'warning';
            break;
        case 'STOPPING':
            barColor = 'error';
            break;
        case 'COMPLETED':
            barColor = 'success';
            break;
        case 'FAILED':
            barColor = 'error';
            break;
        default:
            barColor = 'primary';
    }

    if (isIndeterminate) progressVariant = 'indeterminate';

    let helperText;
    if (task.status === 'IDLE') helperText = '等待调度';
    else if (task.status === 'QUEUED') helperText = '排队中...';
    else if (task.status === 'COMPLETED') {
        helperText = `完成 (耗时 ${progress.durationFormatted})`;
    } else if (task.status === 'FAILED') helperText = '任务失败';
    else {
        helperText = `已发现约 ${discovered}${statTotalFound > 0 ? ` / ~${statTotalFound}` : ''} · DT完成 ${processed}${etaText ? ` · 剩 ${etaText}` : ''}`;
    }

    const pulseAnimation = {
        animation: task.status === 'RUNNING' ? 'pulse 1.5s infinite ease-in-out' : 'none',
        '@keyframes pulse': {
            '0%': { opacity: 1 },
            '50%': { opacity: 0.6 },
            '100%': { opacity: 1 },
        }
    };

    return (
        <Box sx={{ width: '100%' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                <Typography variant="caption" sx={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <TimerIcon sx={{ fontSize: 14 }} color="action" />
                    页码: {currentPage} / {endPageDisplay}
                </Typography>
            </Box>

            <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <Box sx={{ width: '100%', mr: 1, ...pulseAnimation }}>
                    <LinearProgress
                        variant={progressVariant}
                        value={progressPercent}
                        color={barColor}
                        sx={{ height: 6, borderRadius: 1, bgcolor: 'rgba(255,255,255,0.08)' }}
                    />
                </Box>
                {progressVariant === 'determinate' && (
                    <Box sx={{ minWidth: 35 }}>
                        <Typography variant="caption" sx={{
                            color: "text.secondary"
                        }}>{`${progressPercent}%`}</Typography>
                    </Box>
                )}
            </Box>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 0.5 }}>
                {hasBreakdownStats ? (
                    <Stack direction="row" spacing={1.5}>
                        <Typography variant="caption" sx={{ color: '#66bb6a', fontSize: '0.7rem' }} title="新增数">
                            +{inserted}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#42a5f5', fontSize: '0.7rem' }} title="更新数">
                            ~{updated}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#ffa726', fontSize: '0.7rem' }} title="跳过数">
                            -{ignored}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#ef5350', fontSize: '0.7rem', fontWeight: failed > 0 ? 'bold' : 'normal' }} title="失败数">
                            !{failed}
                        </Typography>
                    </Stack>
                ) : (
                    <Box />
                )}

                <Typography
                    variant="caption"
                    sx={{
                        color: "text.secondary",
                        fontSize: '0.7rem'
                    }}>
                    {helperText}
                </Typography>
            </Box>
        </Box>
    );
}

function CollectionTaskList() {
    const navigate = useNavigate();
    const location = useLocation();

    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingTask, setEditingTask] = useState(null);
    const [selectedIds, setSelectedIds] = useState([]);
    const [filters, setFilters] = useState({});
    const [detailTaskId, setDetailTaskId] = useState(null);

    const [isLoadingDetail, setIsLoadingDetail] = useState(false);
    const [pendingTaskAction, setPendingTaskAction] = useState(null);
    const [pendingBatchAction, setPendingBatchAction] = useState(null);

    const { data, isLoading, isFetching, refetch } = useGetCollectionTasksQuery(
        { page, size: pageSize, sort: 'createdAt,desc', ...filters },
        { pollingInterval: 5000 }
    );

    const { data: dataSources = [], isLoading: isDataSourcesLoading } = useGetAllDataSourcesQuery();

    const [createTask] = useCreateCollectionTaskMutation();
    const [updateTask] = useUpdateCollectionTaskMutation();
    const [deleteTask] = useDeleteCollectionTaskMutation();
    const [startTask] = useStartTaskMutation();
    const [resumeTask] = useResumeTaskMutation();
    const [pauseTask] = usePauseTaskMutation();
    const [stopTask] = useStopTaskMutation();

    const [triggerGetById] = useLazyGetCollectionTaskByIdQuery();

    const handleNewClick = useCallback((initialData = null) => {
        setEditingTask(initialData);
        setIsFormOpen(true);
    }, []);

    const handleEditClick = useCallback(async (row) => {
        setIsLoadingDetail(true);
        try {
            const fullTask = await triggerGetById(row.id).unwrap();
            setEditingTask(fullTask);
            setIsFormOpen(true);
        } catch (e) {
            console.error("Failed to load task details for editing", e);
            alert("加载任务详情失败，请重试");
        } finally {
            setIsLoadingDetail(false);
        }
    }, [triggerGetById]);

    const handleDetailClick = useCallback((id) => {
        setDetailTaskId(id);
    }, []);

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const runTaskAction = useCallback(async (id, actionType, actionFn) => {
        const actionKey = `${actionType}:${id}`;
        setPendingTaskAction(actionKey);
        try {
            await actionFn(id).unwrap();
            refetch();
        } catch (error) {
            console.error("Task action failed", error);
            alert("任务操作请求失败，请刷新后检查任务状态。");
        } finally {
            setPendingTaskAction(null);
        }
    }, [refetch]);

    const handleStartClick = useCallback(async (id) => {
        if (window.confirm("确定要【重新启动】任务吗？这将会清空此任务之前的统计数据和进度。")) {
            await runTaskAction(id, 'start', startTask);
        }
    }, [runTaskAction, startTask]);

    const handleResumeClick = useCallback(async (id) => {
        await runTaskAction(id, 'resume', resumeTask);
    }, [runTaskAction, resumeTask]);

    const handlePauseClick = useCallback(async (id) => {
        await runTaskAction(id, 'pause', pauseTask);
    }, [runTaskAction, pauseTask]);

    const handleStopClick = useCallback(async (id) => {
        await runTaskAction(id, 'stop', stopTask);
    }, [runTaskAction, stopTask]);

    useEffect(() => {
        if (location.state?.new_task_for_ds_id) {
            const { new_task_for_ds_id, new_task_for_ds_name } = location.state;
            handleNewClick({
                dataSourceId: new_task_for_ds_id,
                name: `采集任务 - ${new_task_for_ds_name}`
            });
            navigate(location.pathname, { replace: true, state: {} });
        }
    }, [location, navigate, handleNewClick]);

    const handleFormSubmit = useCallback(async (formData) => {
        if (formData.id) await updateTask(formData).unwrap();
        else await createTask(formData).unwrap();
        setIsFormOpen(false);
        setEditingTask(null);
    }, [updateTask, createTask]);

    const handleBatchAction = useCallback(async (actionType) => {
        if (selectedIds.length === 0) return;

        let actionFn;
        let confirmMsg = '';

        switch (actionType) {
            case 'start':
                actionFn = startTask;
                confirmMsg = `确定要【重新开始】选中的 ${selectedIds.length} 个任务吗？所有进度将被清空。`;
                break;
            case 'resume':
                actionFn = resumeTask;
                confirmMsg = `确定要【恢复】选中的 ${selectedIds.length} 个任务吗？`;
                break;
            case 'pause':
                actionFn = pauseTask;
                confirmMsg = `确定要【暂停】选中的 ${selectedIds.length} 个任务吗？`;
                break;
            case 'stop':
                actionFn = stopTask;
                confirmMsg = `确定要【停止】选中的 ${selectedIds.length} 个任务吗？`;
                break;
            default:
                return;
        }

        if (confirmMsg && !window.confirm(confirmMsg)) return;

        const ids = [...selectedIds];
        setPendingBatchAction(actionType);
        try {
            const failures = await runLimitedBatch(
                ids,
                BATCH_ACTION_CONCURRENCY,
                (id) => actionFn(id).unwrap()
            );
            if (failures.length > 0) {
                console.error("Batch action failed", failures);
                alert(`部分任务操作失败：成功 ${ids.length - failures.length} 个，失败 ${failures.length} 个，请检查状态。`);
            } else {
                setSelectedIds([]);
            }
            refetch();
        } catch (error) {
            console.error("Batch action failed", error);
            alert("部分任务操作可能失败，请检查状态。");
        } finally {
            setPendingBatchAction(null);
        }
    }, [selectedIds, startTask, resumeTask, pauseTask, stopTask, refetch]);

    const filterFields = useMemo(() => [
        { name: 'name', label: '任务名称', type: 'text', placeholder: '支持模糊搜索' },
        {
            name: 'status', label: '运行状态', type: 'select',
            options: Object.keys(TASK_STATUS).map(k => ({ value: k, label: TASK_STATUS[k].label }))
        },
        {
            name: 'dataSourceId', label: '所属数据源', type: 'select',
            options: dataSources.map(ds => ({ value: ds.id, label: ds.name }))
        },
        {
            name: 'enabled', label: '调度状态', type: 'select',
            options: [
                { value: 'true', label: '启用 (CRON)' },
                { value: 'false', label: '禁用/手动' }
            ]
        }
    ], [dataSources]);

    const columns = useMemo(() => [
        {
            field: 'name', headerName: '任务名称', width: 220,
            renderCell: ({ row }) => (
                <Box>
                    <Typography variant="body2" sx={{
                        fontWeight: "500"
                    }}>{row.name}</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>{row.dataSourceName}</Typography>
                </Box>
            )
        },
        {
            field: 'status', headerName: '状态', width: 100,
            renderCell: ({ value }) => {
                const config = getStatusConfig(TASK_STATUS, value);
                return (
                    <Chip
                        label={config.label}
                        size="small"
                        color={config.color}
                        variant={value === 'IDLE' ? 'outlined' : 'filled'}
                        sx={{ fontWeight: 'bold' }}
                    />
                );
            }
        },
        {
            field: 'progress', headerName: '运行概况', width: 350,
            renderCell: ({ row }) => <TaskProgressCell task={row} />
        },
        {
            field: 'enabled', headerName: 'CRON', width: 70, align: 'center',
            renderCell: ({ row }) => (
                <Switch
                    checked={row.enabled}
                    onChange={async () => await updateTask({...row, enabled: !row.enabled})}
                    size="small"
                    color="success"
                    disabled={!row.cronExpression}
                />
            )
        },
        {
            field: 'actions', headerName: '操作', width: 220, sortable: false, align: 'right',
            renderCell: ({ row }) => {
                const isRunning = row.status === 'RUNNING' || row.status === 'QUEUED';
                const isIdle = row.status === 'IDLE';
                const canResume = !isIdle && !isRunning;
                const rowActionPending = pendingTaskAction?.endsWith(`:${row.id}`);
                const isActionPending = (actionType) => pendingTaskAction === `${actionType}:${row.id}`;

                return (
                    <Stack direction="row" spacing={0.5} sx={{
                        justifyContent: "flex-end"
                    }}>
                        <Tooltip title="查看详情与日志">
                            <IconButton size="small" onClick={() => handleDetailClick(row.id)}>
                                <AssessmentIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>

                        <Divider orientation="vertical" flexItem variant="middle" sx={{ mx: 1, height: 20, my: 'auto' }} />

                        {isIdle && (
                            <Tooltip title="启动任务">
                                <IconButton size="small" color="primary" disabled={rowActionPending} onClick={() => handleStartClick(row.id)}>
                                    {isActionPending('start') ? <CircularProgress size={18} /> : <PlayArrowIcon fontSize="small"/>}
                                </IconButton>
                            </Tooltip>
                        )}

                        {canResume && (
                            <>
                                <Tooltip title="继续采集 (保留进度)">
                                    <IconButton size="small" color="success" disabled={rowActionPending} onClick={() => handleResumeClick(row.id)}>
                                        {isActionPending('resume') ? <CircularProgress size={18} /> : <PlayArrowIcon fontSize="small"/>}
                                    </IconButton>
                                </Tooltip>
                                <Tooltip title="重新开始 (清空进度)">
                                    <IconButton size="small" color="warning" disabled={rowActionPending} onClick={() => handleStartClick(row.id)}>
                                        {isActionPending('start') ? <CircularProgress size={18} /> : <RestartAltIcon fontSize="small"/>}
                                    </IconButton>
                                </Tooltip>
                            </>
                        )}

                        {isRunning && (
                            <Tooltip title="暂停">
                                <IconButton size="small" color="warning" disabled={rowActionPending} onClick={() => handlePauseClick(row.id)}>
                                    {isActionPending('pause') ? <CircularProgress size={18} /> : <PauseIcon fontSize="small"/>}
                                </IconButton>
                            </Tooltip>
                        )}
                        {(isRunning || row.status === 'PAUSED') && (
                            <Tooltip title="停止">
                                <IconButton size="small" color="error" disabled={rowActionPending} onClick={() => handleStopClick(row.id)}>
                                    {isActionPending('stop') ? <CircularProgress size={18} /> : <StopIcon fontSize="small"/>}
                                </IconButton>
                            </Tooltip>
                        )}

                        <IconButton
                            size="small"
                            disabled={isRunning || isLoadingDetail || rowActionPending}
                            onClick={() => handleEditClick(row)}
                        >
                            {isLoadingDetail && editingTask?.id === row.id ? <CircularProgress size={20} /> : <EditIcon fontSize="small" />}
                        </IconButton>
                        <IconButton size="small" disabled={isRunning || rowActionPending} onClick={() => deleteTask(row.id)} color="error">
                            <DeleteIcon fontSize="small" />
                        </IconButton>
                    </Stack>
                );
            }
        },
    ], [handleStartClick, handleResumeClick, handlePauseClick, handleStopClick, pendingTaskAction, deleteTask, updateTask, handleDetailClick, handleEditClick, isLoadingDetail, editingTask]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="采集任务"
                onAdd={() => handleNewClick()}
                addLabel="新建任务"
                onRefresh={refetch}
                loading={isLoading || isFetching}
                extraActions={
                    selectedIds.length > 0 && (
                        <Stack direction="row" spacing={1}>
                            <Button variant="contained" color="warning" size="small" startIcon={pendingBatchAction === 'start' ? <CircularProgress size={16} color="inherit" /> : <RestartAltIcon />} onClick={() => handleBatchAction('start')} disabled={Boolean(pendingBatchAction)}>
                                重新开始 ({selectedIds.length})
                            </Button>
                            <Button variant="contained" color="success" size="small" startIcon={pendingBatchAction === 'resume' ? <CircularProgress size={16} color="inherit" /> : <PlayArrowIcon />} onClick={() => handleBatchAction('resume')} disabled={Boolean(pendingBatchAction)}>
                                继续采集
                            </Button>
                            <Button variant="contained" color="warning" size="small" startIcon={pendingBatchAction === 'pause' ? <CircularProgress size={16} color="inherit" /> : <PauseIcon />} onClick={() => handleBatchAction('pause')} disabled={Boolean(pendingBatchAction)}>
                                暂停
                            </Button>
                            <Button variant="contained" color="error" size="small" startIcon={pendingBatchAction === 'stop' ? <CircularProgress size={16} color="inherit" /> : <StopIcon />} onClick={() => handleBatchAction('stop')} disabled={Boolean(pendingBatchAction)}>
                                停止
                            </Button>
                        </Stack>
                    )
                }
            />

            <FilterPanel
                fields={filterFields}
                onSearch={handleSearch}
                onReset={handleReset}
            />

            <SmartTable
                columns={columns}
                data={data?.content || []}
                total={getTotalElements(data)}
                loading={isLoading || isFetching}
                page={page}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={setPageSize}
                selectionModel={selectedIds}
                onSelectionChange={setSelectedIds}
            />

            <CollectionTaskForm
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleFormSubmit}
                task={editingTask}
                dataSources={dataSources}
                isDataSourcesLoading={isDataSourcesLoading}
            />

            <TaskDetailDialog
                open={!!detailTaskId}
                taskId={detailTaskId}
                onClose={() => setDetailTaskId(null)}
            />
        </Box>
    );
}

export default CollectionTaskList;
