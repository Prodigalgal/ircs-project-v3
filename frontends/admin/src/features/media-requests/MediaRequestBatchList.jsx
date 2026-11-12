import React, { useCallback, useMemo, useState } from 'react';
import {
    Box,
    Chip,
    CircularProgress,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CancelIcon from '@mui/icons-material/Cancel';
import ListAltIcon from '@mui/icons-material/ListAlt';
import ListHeader from '../../components/common/ListHeader.jsx';
import FilterPanel from '../../components/common/FilterPanel.jsx';
import SmartTable from '../../components/common/SmartTable.jsx';
import { getTotalElements } from '../../utils/paginationUtils.js';
import {
    useCancelMediaRequestBatchMutation,
    useGetMediaRequestBatchQuery,
    useGetMediaRequestBatchesQuery,
    useGetMediaRequestBatchTasksQuery,
    useStartMediaRequestBatchMutation,
} from './mediaRequestBatchesApi.js';

const STATUS_CONFIG = {
    READY: { label: '等待执行', color: 'warning' },
    RUNNING: { label: '执行中', color: 'info' },
    COMPLETED: { label: '已完成', color: 'success' },
    PARTIAL: { label: '部分失败', color: 'warning' },
    FAILED: { label: '失败', color: 'error' },
    CANCELLED: { label: '已取消', color: 'default' },
};

const ITEM_STATUS_LABELS = {
    READY: '等待执行',
    SCHEDULED: '已投递',
    SKIPPED_EXISTS: '内部已存在',
    FAILED: '失败',
    CANCELLED: '已取消',
};

function formatTime(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function statusChip(status) {
    const config = STATUS_CONFIG[status] || { label: status || '-', color: 'default' };
    return <Chip size="small" color={config.color} label={config.label} variant={status === 'READY' ? 'outlined' : 'filled'} />;
}

function MediaRequestBatchList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const [filters, setFilters] = useState({});
    const [detailBatchId, setDetailBatchId] = useState(null);
    const [pendingAction, setPendingAction] = useState(null);

    const { data, isLoading, isFetching, refetch } = useGetMediaRequestBatchesQuery(
        { page, size: pageSize, sort: 'createdAt,desc', ...filters },
        { pollingInterval: 5000 }
    );
    const [startBatch] = useStartMediaRequestBatchMutation();
    const [cancelBatch] = useCancelMediaRequestBatchMutation();

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const runAction = useCallback(async (id, action, confirmText) => {
        if (confirmText && !window.confirm(confirmText)) return;
        setPendingAction(`${action}:${id}`);
        try {
            if (action === 'start') {
                await startBatch(id).unwrap();
            } else {
                await cancelBatch(id).unwrap();
            }
            refetch();
        } catch (error) {
            console.error('Media request batch action failed', error);
            alert('求片任务操作失败，请刷新后检查状态。');
        } finally {
            setPendingAction(null);
        }
    }, [cancelBatch, refetch, startBatch]);

    const filterFields = useMemo(() => [
        {
            name: 'status',
            label: '状态',
            type: 'select',
            options: Object.entries(STATUS_CONFIG).map(([value, config]) => ({ value, label: config.label })),
        },
    ], []);

    const columns = useMemo(() => [
        {
            field: 'id',
            headerName: '批次',
            width: 230,
            renderCell: ({ row }) => (
                <Box>
                    <Typography variant="body2" sx={{
                        fontWeight: 600
                    }}>{row.id}</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>创建 {formatTime(row.createdAt)}</Typography>
                </Box>
            ),
        },
        {
            field: 'status',
            headerName: '状态',
            width: 100,
            renderCell: ({ value }) => statusChip(value),
        },
        {
            field: 'requestCount',
            headerName: '求片',
            width: 90,
            renderCell: ({ row }) => (
                <Box>
                    <Typography variant="body2">{row.requestCount}</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>条请求</Typography>
                </Box>
            ),
        },
        {
            field: 'summary',
            headerName: '执行概况',
            width: 260,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1}>
                    <Chip size="small" color="success" variant="outlined" label={`投递 ${row.scheduledCount}`} />
                    <Chip size="small" color="info" variant="outlined" label={`已存在 ${row.skippedCount}`} />
                    <Chip size="small" color={row.failedCount > 0 ? 'error' : 'default'} variant="outlined" label={`失败 ${row.failedCount}`} />
                </Stack>
            ),
        },
        {
            field: 'updatedAt',
            headerName: '更新时间',
            width: 160,
            renderCell: ({ value }) => <Typography variant="body2">{formatTime(value)}</Typography>,
        },
        {
            field: 'lastErrorMessage',
            headerName: '错误',
            width: 220,
            renderCell: ({ value }) => (
                <Typography variant="caption" color={value ? 'error.main' : 'text.disabled'} noWrap>
                    {value || '-'}
                </Typography>
            ),
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 150,
            sortable: false,
            align: 'right',
            renderCell: ({ row }) => {
                const isReady = row.status === 'READY';
                const isPendingStart = pendingAction === `start:${row.id}`;
                const isPendingCancel = pendingAction === `cancel:${row.id}`;
                return (
                    <Stack direction="row" spacing={0.5} sx={{
                        justifyContent: "flex-end"
                    }}>
                        <Tooltip title="查看详情">
                            <IconButton size="small" onClick={() => setDetailBatchId(row.id)}>
                                <ListAltIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="执行批次">
                            <span>
                                <IconButton
                                    size="small"
                                    color="primary"
                                    disabled={!isReady || Boolean(pendingAction)}
                                    onClick={() => runAction(row.id, 'start', '确定执行该求片批次吗？执行前会先查询内部是否已存在。')}
                                >
                                    {isPendingStart ? <CircularProgress size={18} /> : <PlayArrowIcon fontSize="small" />}
                                </IconButton>
                            </span>
                        </Tooltip>
                        <Tooltip title="取消批次">
                            <span>
                                <IconButton
                                    size="small"
                                    color="error"
                                    disabled={!isReady || Boolean(pendingAction)}
                                    onClick={() => runAction(row.id, 'cancel', '确定取消该求片批次吗？未开始的底层任务会一并暂停。')}
                                >
                                    {isPendingCancel ? <CircularProgress size={18} /> : <CancelIcon fontSize="small" />}
                                </IconButton>
                            </span>
                        </Tooltip>
                    </Stack>
                );
            },
        },
    ], [pendingAction, runAction]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="求片任务"
                subtitle="系统定时归集用户求片后形成待执行批次，管理员执行前可取消"
                onRefresh={refetch}
                loading={isLoading || isFetching}
            />
            <FilterPanel fields={filterFields} onSearch={handleSearch} onReset={handleReset} />
            <SmartTable
                columns={columns}
                data={data?.content || []}
                total={getTotalElements(data)}
                loading={isLoading || isFetching}
                page={page}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={setPageSize}
                noDataMessage="暂无求片批次"
            />
            <BatchDetailDialog batchId={detailBatchId} onClose={() => setDetailBatchId(null)} />
        </Box>
    );
}

function BatchDetailDialog({ batchId, onClose }) {
    const open = Boolean(batchId);
    const { data: batch, isFetching } = useGetMediaRequestBatchQuery(batchId, { skip: !open });
    const { data: tasks } = useGetMediaRequestBatchTasksQuery({ id: batchId, page: 0, size: 20 }, { skip: !open });

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
            <DialogTitle>求片批次详情</DialogTitle>
            <DialogContent dividers>
                {isFetching || !batch ? (
                    <Stack
                        sx={{
                            alignItems: "center",
                            py: 4
                        }}><CircularProgress /></Stack>
                ) : (
                    <Stack spacing={2}>
                        <Stack direction="row" spacing={1} sx={{
                            alignItems: "center"
                        }}>
                            {statusChip(batch.status)}
                            <Typography variant="body2" sx={{
                                color: "text.secondary"
                            }}>{batch.id}</Typography>
                        </Stack>
                        <Typography variant="caption" sx={{
                            color: "text.secondary"
                        }}>
                            创建 {formatTime(batch.createdAt)} · 更新 {formatTime(batch.updatedAt)}
                        </Typography>
                        <Box>
                            <Typography variant="subtitle2" sx={{ mb: 1 }}>求片明细</Typography>
                            <Stack spacing={1}>
                                {batch.items.map((item) => (
                                    <Box key={item.id} sx={{ p: 1, border: 1, borderColor: 'divider', borderRadius: 1 }}>
                                        <Stack direction="row" spacing={1} sx={{
                                            justifyContent: "space-between"
                                        }}>
                                            <Typography variant="body2" sx={{
                                                fontWeight: 600
                                            }}>
                                                {item.title}{item.releaseYear ? ` (${item.releaseYear})` : ''}
                                            </Typography>
                                            <Chip size="small" label={ITEM_STATUS_LABELS[item.status] || item.status} />
                                        </Stack>
                                        <Typography variant="caption" sx={{
                                            color: "text.secondary"
                                        }}>
                                            合并请求 {item.requestCount} 次 · 投递任务 {item.scheduledTaskCount}
                                            {item.existingVideoId ? ` · 已存在 ${item.existingVideoSource}:${item.existingVideoId}` : ''}
                                        </Typography>
                                        {item.lastErrorMessage && (
                                            <Typography
                                                variant="caption"
                                                sx={{
                                                    color: "error.main",
                                                    display: "block"
                                                }}>
                                                {item.lastErrorMessage}
                                            </Typography>
                                        )}
                                    </Box>
                                ))}
                            </Stack>
                        </Box>
                        <Box>
                            <Typography variant="subtitle2" sx={{ mb: 1 }}>底层采集任务</Typography>
                            <Stack spacing={0.75}>
                                {(tasks?.content || []).map((task) => (
                                    <Box key={task.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                                        <Typography variant="caption" noWrap>{task.name}</Typography>
                                        <Typography variant="caption" sx={{
                                            color: "text.secondary"
                                        }}>{task.status}</Typography>
                                    </Box>
                                ))}
                                {(!tasks?.content || tasks.content.length === 0) && (
                                    <Typography variant="caption" sx={{
                                        color: "text.secondary"
                                    }}>暂无底层采集任务</Typography>
                                )}
                            </Stack>
                        </Box>
                    </Stack>
                )}
            </DialogContent>
        </Dialog>
    );
}

export default MediaRequestBatchList;
