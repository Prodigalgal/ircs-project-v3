// File: src/features/ops/dlq/DlqList.jsx
import React, { useState, useMemo, useCallback } from 'react';
import { Box, Chip, IconButton, Stack, Tooltip, Button, Menu, MenuItem, ListItemIcon, ListItemText, Divider, Tabs, Tab, Typography } from '@mui/material';
import ReplayIcon from '@mui/icons-material/Replay';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';

import SmartTable from '../../../components/common/SmartTable';
import ListHeader from '../../../components/common/ListHeader';
import FilterPanel from '../../../components/common/FilterPanel';
import DlqDetailDialog from './components/DlqDetailDialog';
import {
    useGetDlqMessagesQuery,
    useRetryDlqMessageMutation,
    useDiscardDlqMessageMutation,
    useBatchRetryDlqMessagesMutation,
    useBatchDiscardDlqMessagesMutation,
    useGetRabbitDlqQueuesQuery,
    useRetryRabbitDlqMutation,
    useDiscardRabbitDlqMutation,
    useGetRuntimeDlqQueuesQuery,
    useRequeueRuntimeDlqMutation
} from './dlqApi';
import { getTotalElements } from '../../../utils/paginationUtils';

const STATUS_COLORS = {
    PENDING: 'error',
    RETRIED: 'success',
    DISCARDED: 'default'
};

function DlqList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({ status: 'PENDING' }); // Default show pending
    const [selectedIds, setSelectedIds] = useState([]);
    const [detailItem, setDetailItem] = useState(null);
    const [batchMenuAnchor, setBatchMenuAnchor] = useState(null);
    const [tabIndex, setTabIndex] = useState(0);

    const { data, isLoading, isFetching, refetch } = useGetDlqMessagesQuery({
        page,
        size: pageSize,
        sort: 'createdAt,desc',
        ...filters
    }, { skip: tabIndex !== 0 });
    const { data: rabbitQueues = [], isLoading: isRabbitLoading, isFetching: isRabbitFetching, refetch: refetchRabbit } = useGetRabbitDlqQueuesQuery(undefined, {
        skip: tabIndex !== 1
    });
    const {
        data: runtimeQueues = [],
        isLoading: isRuntimeLoading,
        isFetching: isRuntimeFetching,
        refetch: refetchRuntime
    } = useGetRuntimeDlqQueuesQuery(undefined, {
        skip: tabIndex !== 2
    });

    const [retryMessage] = useRetryDlqMessageMutation();
    const [discardMessage] = useDiscardDlqMessageMutation();
    const [batchRetry] = useBatchRetryDlqMessagesMutation();
    const [batchDiscard] = useBatchDiscardDlqMessagesMutation();
    const [retryRabbitDlq] = useRetryRabbitDlqMutation();
    const [discardRabbitDlq] = useDiscardRabbitDlqMutation();
    const [requeueRuntimeDlq] = useRequeueRuntimeDlqMutation();

    const handleSearch = (newFilters) => {
        setFilters(newFilters);
        setPage(0);
    };

    const handleRetry = useCallback(async (id) => {
        if (window.confirm('确定要重试此消息吗？')) {
            try {
                await retryMessage(id).unwrap();
            } catch (e) {
                alert('重试失败: ' + (e.data?.message || e.message));
            }
        }
    }, [retryMessage]);

    const handleDiscard = useCallback(async (id) => {
        if (window.confirm('确定要丢弃此消息吗？此操作不可逆。')) {
            await discardMessage(id).unwrap();
        }
    }, [discardMessage]);

    const handleBatchRetry = async () => {
        setBatchMenuAnchor(null);
        if (selectedIds.length === 0) return;
        if (window.confirm(`确定要重试选中的 ${selectedIds.length} 条消息吗？`)) {
            try {
                await batchRetry(selectedIds).unwrap();
                setSelectedIds([]);
            } catch (e) {
                alert('批量重试失败: ' + (e.data?.message || e.message));
            }
        }
    };

    const handleBatchDiscard = async () => {
        setBatchMenuAnchor(null);
        if (selectedIds.length === 0) return;
        if (window.confirm(`确定要批量丢弃选中的 ${selectedIds.length} 条消息吗？此操作不可逆。`)) {
            try {
                await batchDiscard(selectedIds).unwrap();
                setSelectedIds([]);
            } catch (e) {
                alert('批量丢弃失败: ' + (e.data?.message || e.message));
            }
        }
    };

    const handleRabbitAction = useCallback(async (queueName, action) => {
        const limitText = window.prompt(`请输入本次${action === 'retry' ? '重试' : '丢弃'}数量，最大 100`, '1');
        if (!limitText) return;
        const limit = Number(limitText);
        if (!Number.isInteger(limit) || limit <= 0) {
            alert('数量必须是大于 0 的整数。');
            return;
        }
        if (!window.confirm(`确定要${action === 'retry' ? '重试' : '丢弃'} ${queueName} 中最多 ${Math.min(limit, 100)} 条消息吗？`)) {
            return;
        }
        try {
            const request = { queueName, limit };
            const result = action === 'retry'
                ? await retryRabbitDlq(request).unwrap()
                : await discardRabbitDlq(request).unwrap();
            alert(`操作完成：${result.action} affected=${result.affected}`);
        } catch (e) {
            alert('Rabbit DLQ 操作失败: ' + (e.data?.message || e.message));
        }
    }, [retryRabbitDlq, discardRabbitDlq]);

    const handleRuntimeRequeue = useCallback(async (taskType) => {
        const limitText = window.prompt('请输入本次重投数量，最大 100', '1');
        if (!limitText) return;
        const limit = Number(limitText);
        if (!Number.isInteger(limit) || limit <= 0) {
            alert('数量必须是大于 0 的整数。');
            return;
        }
        if (!window.confirm(`确定要把 ${taskType} 的 Runtime DLQ 最多 ${Math.min(limit, 100)} 条重投回 pending 吗？`)) {
            return;
        }
        try {
            const result = await requeueRuntimeDlq({ taskType, limit, maxReplayAttempts: 3 }).unwrap();
            alert(`重投完成：affected=${result.affected}`);
        } catch (e) {
            alert('Runtime DLQ 重投失败: ' + (e.data?.message || e.message));
        }
    }, [requeueRuntimeDlq]);

    const columns = useMemo(() => [
        { field: 'queueName', headerName: '来源队列', width: 250 },
        {
            field: 'exceptionStack',
            headerName: '异常摘要',
            width: 400,
            valueGetter: (v) => v ? v.split('\n')[0] : '-'
        },
        { field: 'retryCount', headerName: '重试次数', width: 100 },
        {
            field: 'status',
            headerName: '状态',
            width: 120,
            renderCell: ({ value }) => (
                <Chip
                    label={value}
                    color={STATUS_COLORS[value] || 'default'}
                    size="small"
                    variant={value === 'PENDING' ? 'filled' : 'outlined'}
                />
            )
        },
        {
            field: 'createdAt',
            headerName: '发生时间',
            width: 180,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 150,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title="查看详情">
                        <IconButton size="small" onClick={() => setDetailItem(row)}>
                            <VisibilityIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    {row.status === 'PENDING' && (
                        <>
                            <Tooltip title="重试">
                                <IconButton size="small" color="primary" onClick={() => handleRetry(row.id)}>
                                    <ReplayIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="丢弃">
                                <IconButton size="small" color="error" onClick={() => handleDiscard(row.id)}>
                                    <DeleteIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                        </>
                    )}
                </Stack>
            )
        }
    ], [handleRetry, handleDiscard]);

    const filterFields = [
        { name: 'queueName', label: '队列名称', type: 'text' },
        { name: 'keyword', label: '错误/消息关键字', type: 'text', placeholder: 'payload / exception / routingKey' },
        {
            name: 'status',
            label: '状态',
            type: 'select',
            options: [
                { value: 'PENDING', label: '待处理 (PENDING)' },
                { value: 'RETRIED', label: '已重试 (RETRIED)' },
                { value: 'DISCARDED', label: '已丢弃 (DISCARDED)' }
            ]
        }
    ];

    const rabbitColumns = useMemo(() => [
        { field: 'displayName', headerName: 'DLQ', width: 220 },
        { field: 'queueName', headerName: '队列名', width: 260 },
        { field: 'sourceQueueName', headerName: '来源队列', width: 260 },
        { field: 'messagesTotal', headerName: 'Total', width: 90 },
        { field: 'messagesReady', headerName: 'Ready', width: 100 },
        { field: 'messagesUnacknowledged', headerName: 'Unacked', width: 100 },
        { field: 'consumers', headerName: 'Consumers', width: 110 },
        {
            field: 'actions',
            headerName: '操作',
            width: 170,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title="重试到原队列">
                        <span>
                            <IconButton
                                size="small"
                                color="primary"
                                disabled={!row.actionSupported || !(row.messagesTotal || row.messagesReady)}
                                onClick={() => handleRabbitAction(row.queueName, 'retry')}
                            >
                                <ReplayIcon fontSize="small" />
                            </IconButton>
                        </span>
                    </Tooltip>
                    <Tooltip title="丢弃消息">
                        <span>
                            <IconButton
                                size="small"
                                color="error"
                                disabled={!row.actionSupported || !(row.messagesTotal || row.messagesReady)}
                                onClick={() => handleRabbitAction(row.queueName, 'discard')}
                            >
                                <DeleteIcon fontSize="small" />
                            </IconButton>
                        </span>
                    </Tooltip>
                </Stack>
            )
        }
    ], [handleRabbitAction]);

    const pendingRabbitCount = rabbitQueues.reduce((sum, queue) => sum + (queue.messagesTotal ?? queue.messagesReady ?? 0), 0);
    const pendingRuntimeCount = runtimeQueues.reduce((sum, queue) => sum + (queue.dlq || 0), 0);
    const runtimeColumns = useMemo(() => [
        { field: 'label', headerName: 'Runtime Work', width: 180 },
        { field: 'taskType', headerName: 'Task Type', width: 240 },
        { field: 'pending', headerName: 'Pending', width: 100 },
        { field: 'inflight', headerName: 'Inflight', width: 100 },
        { field: 'dlq', headerName: 'DLQ', width: 100 },
        { field: 'dlqConsumers', headerName: 'Replayer', width: 100 },
        {
            field: 'sample',
            headerName: '样本',
            width: 360,
            valueGetter: (v, row) => {
                const sample = row.samples?.[0];
                if (!sample) return '-';
                return `${sample.taskId || '-'} ${sample.lastError || ''}`.trim();
            }
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 120,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Tooltip title="重投回原 RuntimeWork pending">
                    <span>
                        <IconButton
                            size="small"
                            color="primary"
                            disabled={!row.dlq}
                            onClick={() => handleRuntimeRequeue(row.taskType)}
                        >
                            <ReplayIcon fontSize="small" />
                        </IconButton>
                    </span>
                </Tooltip>
            )
        }
    ], [handleRuntimeRequeue]);

    const currentRefresh = tabIndex === 0 ? refetch : (tabIndex === 1 ? refetchRabbit : refetchRuntime);
    const currentLoading = tabIndex === 0
        ? (isLoading || isFetching)
        : tabIndex === 1
            ? (isRabbitLoading || isRabbitFetching)
            : (isRuntimeLoading || isRuntimeFetching);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="失败队列与死信治理"
                subtitle="DB 失败记录、Rabbit DLQ 与 Valkey Runtime DLQ 分开处理；Runtime DLQ 只重投，不直接执行业务。"
                onRefresh={currentRefresh}
                loading={currentLoading}
                extraActions={
                    tabIndex === 0 && selectedIds.length > 0 && (
                        <>
                            <Button
                                variant="contained"
                                onClick={(e) => setBatchMenuAnchor(e.currentTarget)}
                                startIcon={<MoreVertIcon />}
                                endIcon={<ArrowDropDownIcon />}
                            >
                                批量操作 ({selectedIds.length})
                            </Button>
                            <Menu
                                anchorEl={batchMenuAnchor}
                                open={Boolean(batchMenuAnchor)}
                                onClose={() => setBatchMenuAnchor(null)}
                            >
                                <MenuItem onClick={handleBatchRetry}>
                                    <ListItemIcon><ReplayIcon fontSize="small" color="primary" /></ListItemIcon>
                                    <ListItemText>批量重试</ListItemText>
                                </MenuItem>
                                <Divider />
                                <MenuItem onClick={handleBatchDiscard}>
                                    <ListItemIcon><DeleteIcon fontSize="small" color="error" /></ListItemIcon>
                                    <ListItemText sx={{ color: 'error.main' }}>批量丢弃</ListItemText>
                                </MenuItem>
                            </Menu>
                        </>
                    )
                }
            />

            <Tabs value={tabIndex} onChange={(e, value) => setTabIndex(value)} sx={{ mb: 2 }}>
                <Tab label="Valkey/DB 失败记录" />
                <Tab label={`Rabbit DLQ${pendingRabbitCount ? ` (${pendingRabbitCount})` : ''}`} />
                <Tab label={`Runtime DLQ${pendingRuntimeCount ? ` (${pendingRuntimeCount})` : ''}`} />
            </Tabs>

            {tabIndex === 0 ? (
                <>
                    <FilterPanel fields={filterFields} onSearch={handleSearch} onReset={() => setFilters({})} initialValues={{ status: 'PENDING' }} />

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
                </>
            ) : tabIndex === 1 ? (
                <>
                    <Typography
                        variant="body2"
                        sx={{
                            color: "text.secondary",
                            mb: 2
                        }}>
                        Rabbit 仅保留给尚未迁移或基础事件广播通道；核心热路径应优先观察 Valkey Runtime Work 指标。
                    </Typography>
                    <SmartTable
                        columns={rabbitColumns}
                        data={rabbitQueues}
                        total={rabbitQueues.length}
                        loading={isRabbitLoading || isRabbitFetching}
                        page={0}
                        pageSize={rabbitQueues.length || 10}
                        onPageChange={() => {}}
                        onPageSizeChange={() => {}}
                        rowKey="queueName"
                    />
                </>
            ) : (
                <>
                    <Typography
                        variant="body2"
                        sx={{
                            color: "text.secondary",
                            mb: 2
                        }}>
                        统一 Runtime DLQ replayer 会按原 taskType 重投回 pending，业务处理仍由 Search、Aggregation、Storage、Audit 等各自 worker 完成。
                    </Typography>
                    <SmartTable
                        columns={runtimeColumns}
                        data={runtimeQueues}
                        total={runtimeQueues.length}
                        loading={isRuntimeLoading || isRuntimeFetching}
                        page={0}
                        pageSize={runtimeQueues.length || 10}
                        onPageChange={() => {}}
                        onPageSizeChange={() => {}}
                        rowKey="taskType"
                    />
                </>
            )}

            <DlqDetailDialog
                open={!!detailItem}
                onClose={() => setDetailItem(null)}
                message={detailItem}
            />
        </Box>
    );
}

export default DlqList;
