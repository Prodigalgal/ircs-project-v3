import React, { useMemo, useState } from 'react';
import {
    Box,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    Grid,
    IconButton,
    Paper,
    Stack,
    TextField,
    Tooltip,
    Typography
} from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import SpeedIcon from '@mui/icons-material/Speed';
import TimerIcon from '@mui/icons-material/Timer';

import SmartTable from '../../../components/common/SmartTable';
import ListHeader from '../../../components/common/ListHeader';
import FilterPanel from '../../../components/common/FilterPanel';
import { getTotalElements } from '../../../utils/paginationUtils';
import {
    useGetRequestAuditLogsQuery,
    useGetRequestAuditSummaryQuery
} from './requestAuditApi';

const STATUS_COLORS = {
    2: 'success',
    3: 'info',
    4: 'warning',
    5: 'error'
};

const METHOD_COLORS = {
    GET: 'info',
    POST: 'success',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'error'
};

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function formatDuration(value) {
    if (value == null) return '-';
    if (value < 1000) return `${value} ms`;
    return `${(value / 1000).toFixed(2)} s`;
}

function toIso(value) {
    if (!value) return undefined;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function SummaryCard({ icon, label, value, detail, color = 'primary' }) {
    return (
        <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
            <Stack spacing={1}>
                <Stack
                    direction="row"
                    spacing={1}
                    sx={{
                        alignItems: "center",
                        color: `${color}.main`
                    }}>
                    {icon}
                    <Typography variant="caption">{label}</Typography>
                </Stack>
                <Typography variant="h5" sx={{
                    fontWeight: "bold"
                }}>{value ?? 0}</Typography>
                <Typography variant="caption" sx={{
                    color: "text.secondary"
                }}>{detail}</Typography>
            </Stack>
        </Paper>
    );
}

function DetailRow({ label, value }) {
    return (
        <Box>
            <Typography variant="caption" sx={{
                color: "text.secondary"
            }}>{label}</Typography>
            <Typography variant="body2" sx={{ wordBreak: 'break-all', fontFamily: label === 'traceId' ? 'monospace' : 'inherit' }}>
                {value || '-'}
            </Typography>
        </Box>
    );
}

function RequestAuditPage() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});
    const [detailItem, setDetailItem] = useState(null);

    const queryFilters = useMemo(() => ({
        ...filters,
        from: toIso(filters.from),
        to: toIso(filters.to),
        statusCode: filters.statusCode ? Number(filters.statusCode) : undefined
    }), [filters]);

    const { data: summary, isLoading: isSummaryLoading, refetch: refetchSummary } = useGetRequestAuditSummaryQuery();
    const { data, isLoading, isFetching, refetch } = useGetRequestAuditLogsQuery({
        page,
        size: pageSize,
        sort: 'createdAt,desc',
        ...queryFilters
    });

    const refreshAll = () => {
        refetchSummary();
        refetch();
    };

    const handleSearch = (newFilters) => {
        setFilters(newFilters);
        setPage(0);
    };

    const columns = useMemo(() => [
        {
            field: 'createdAt',
            headerName: '时间',
            width: 180,
            valueGetter: (v) => formatDate(v)
        },
        {
            field: 'method',
            headerName: '方法',
            width: 90,
            renderCell: ({ value }) => (
                <Chip label={value} color={METHOD_COLORS[value] || 'default'} size="small" variant="outlined" />
            )
        },
        {
            field: 'path',
            headerName: '接口',
            width: 360,
            renderCell: ({ row }) => (
                <Box title={row.queryString ? `${row.path}?${row.queryString}` : row.path}>
                    <Typography variant="body2" noWrap>{row.path}</Typography>
                    {row.queryString && (
                        <Typography variant="caption" noWrap sx={{
                            color: "text.secondary"
                        }}>
                            ?{row.queryString}
                        </Typography>
                    )}
                </Box>
            )
        },
        {
            field: 'statusCode',
            headerName: '状态',
            width: 90,
            renderCell: ({ value }) => {
                const statusGroup = Math.floor((value || 0) / 100);
                return <Chip label={value} color={STATUS_COLORS[statusGroup] || 'default'} size="small" />;
            }
        },
        {
            field: 'durationMs',
            headerName: '耗时',
            width: 100,
            valueGetter: (v) => formatDuration(v)
        },
        { field: 'username', headerName: '用户', width: 130, valueGetter: (v) => v || '-' },
        { field: 'clientIp', headerName: 'IP', width: 150, valueGetter: (v) => v || '-' },
        {
            field: 'actions',
            headerName: '详情',
            width: 80,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Tooltip title="查看详情">
                    <IconButton size="small" onClick={() => setDetailItem(row)}>
                        <VisibilityIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
            )
        }
    ], []);

    const filterFields = [
        {
            name: 'method',
            label: '请求方法',
            type: 'select',
            options: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(value => ({ value, label: value }))
        },
        { name: 'path', label: '接口路径', type: 'text', placeholder: '/api/v1/ops' },
        { name: 'username', label: '用户', type: 'text' },
        { name: 'clientIp', label: 'IP', type: 'text' },
        {
            name: 'statusClass',
            label: '状态分类',
            type: 'select',
            options: [
                { value: '2xx', label: '成功 2xx' },
                { value: '3xx', label: '跳转 3xx' },
                { value: '4xx', label: '客户端错误 4xx' },
                { value: '5xx', label: '服务端错误 5xx' }
            ]
        },
        { name: 'statusCode', label: '状态码', type: 'number' },
        {
            name: 'from',
            label: '开始时间',
            render: (value, handleChange) => (
                <TextField
                    fullWidth
                    size="small"
                    type="datetime-local"
                    name="from"
                    label="开始时间"
                    value={value || ''}
                    onChange={handleChange}
                    slotProps={{
                        inputLabel: { shrink: true }
                    }}
                />
            )
        },
        {
            name: 'to',
            label: '结束时间',
            render: (value, handleChange) => (
                <TextField
                    fullWidth
                    size="small"
                    type="datetime-local"
                    name="to"
                    label="结束时间"
                    value={value || ''}
                    onChange={handleChange}
                    slotProps={{
                        inputLabel: { shrink: true }
                    }}
                />
            )
        }
    ];

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="请求审计"
                subtitle="按请求维度记录后台访问、操作入口、状态码、耗时与来源。"
                onRefresh={refreshAll}
                loading={isLoading || isFetching || isSummaryLoading}
            />

            <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<QueryStatsIcon fontSize="small" />} label="24小时请求" value={summary?.totalLast24h} detail="所有 /api/v1 请求" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<ErrorOutlineIcon fontSize="small" />} label="24小时错误" value={summary?.errorsLast24h} detail="HTTP 4xx / 5xx" color="error" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<SpeedIcon fontSize="small" />} label="24小时慢请求" value={summary?.slowLast24h} detail="耗时 >= 3000ms" color="warning" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<TimerIcon fontSize="small" />} label="最长耗时" value={formatDuration(summary?.maxDurationMsLast24h)} detail="最近 24 小时" color="info" />
                </Grid>
            </Grid>

            <FilterPanel fields={filterFields} onSearch={handleSearch} onReset={() => setFilters({})} />

            <SmartTable
                columns={columns}
                data={data?.content || []}
                total={getTotalElements(data)}
                loading={isLoading || isFetching}
                page={page}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={setPageSize}
                noDataMessage="暂无请求审计记录"
            />

            <Dialog open={!!detailItem} onClose={() => setDetailItem(null)} maxWidth="md" fullWidth>
                <DialogTitle>请求详情</DialogTitle>
                <DialogContent dividers>
                    {detailItem && (
                        <Stack spacing={2}>
                            <Grid container spacing={2}>
                                <Grid size={{ xs: 12, md: 6 }}><DetailRow label="时间" value={formatDate(detailItem.createdAt)} /></Grid>
                                <Grid size={{ xs: 12, md: 3 }}><DetailRow label="方法" value={detailItem.method} /></Grid>
                                <Grid size={{ xs: 12, md: 3 }}><DetailRow label="状态码" value={String(detailItem.statusCode)} /></Grid>
                                <Grid size={{ xs: 12, md: 6 }}><DetailRow label="用户" value={detailItem.username} /></Grid>
                                <Grid size={{ xs: 12, md: 3 }}><DetailRow label="IP" value={detailItem.clientIp} /></Grid>
                                <Grid size={{ xs: 12, md: 3 }}><DetailRow label="耗时" value={formatDuration(detailItem.durationMs)} /></Grid>
                            </Grid>
                            <Divider />
                            <DetailRow label="path" value={detailItem.path} />
                            <DetailRow label="query" value={detailItem.queryString} />
                            <DetailRow label="userAgent" value={detailItem.userAgent} />
                            <DetailRow label="traceId" value={detailItem.traceId} />
                            <DetailRow label="error" value={detailItem.errorMessage} />
                        </Stack>
                    )}
                </DialogContent>
            </Dialog>
        </Box>
    );
}

export default RequestAuditPage;
