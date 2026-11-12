import React from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Collapse,
    Divider,
    IconButton,
    MenuItem,
    Paper,
    Select,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import RefreshIcon from '@mui/icons-material/Refresh';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import {
    useDeleteUnifiedVideoMagnetMutation,
    useGetUnifiedVideoMagnetJobsQuery,
    useGetUnifiedVideoMagnetLinksQuery,
    useLazyGetMagnetProviderRunsQuery,
    useUpdateUnifiedVideoMagnetStatusMutation
} from '../unified-videos/unifiedVideosApi';

const LINK_STATUS_OPTIONS = [
    { value: 'APPROVED', label: '通过', color: 'success' },
    { value: 'CANDIDATE', label: '候选', color: 'warning' },
    { value: 'HIDDEN', label: '隐藏', color: 'default' },
    { value: 'REJECTED', label: '拒绝', color: 'error' },
];

const JOB_STATUS_COLOR = {
    PENDING: 'info',
    RUNNING: 'primary',
    SUCCESS: 'success',
    PARTIAL_SUCCESS: 'warning',
    SKIPPED: 'default',
    FAILED: 'error',
};

function statusOption(status) {
    return LINK_STATUS_OPTIONS.find(item => item.value === status) || LINK_STATUS_OPTIONS[1];
}

function formatTime(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function compactList(value) {
    return Array.isArray(value) && value.length > 0 ? value.join(', ') : '-';
}

function MagnetResourcePanel({
    unifiedVideoId,
    metadataStatus,
    onScrape,
    isScraping,
    title = '磁链资源',
    emptyMessage = '请选择聚合视频后管理磁链资源。'
}) {
    const [expandedJobId, setExpandedJobId] = React.useState(null);
    const [runsByJobId, setRunsByJobId] = React.useState({});
    const [runsErrorByJobId, setRunsErrorByJobId] = React.useState({});
    const linksQuery = useGetUnifiedVideoMagnetLinksQuery(unifiedVideoId, { skip: !unifiedVideoId });
    const jobsQuery = useGetUnifiedVideoMagnetJobsQuery({ id: unifiedVideoId, limit: 12 }, { skip: !unifiedVideoId });
    const [loadRuns, runsQuery] = useLazyGetMagnetProviderRunsQuery();
    const [updateStatus, { isLoading: isUpdatingStatus }] = useUpdateUnifiedVideoMagnetStatusMutation();
    const [deleteMagnet, { isLoading: isDeleting }] = useDeleteUnifiedVideoMagnetMutation();

    const isReady = metadataStatus === 'SYNCED';
    const links = linksQuery.data || [];
    const jobs = jobsQuery.data || [];

    const handleRefresh = () => {
        linksQuery.refetch();
        jobsQuery.refetch();
    };

    const handleToggleRuns = async (jobId) => {
        if (expandedJobId === jobId) {
            setExpandedJobId(null);
            return;
        }
        setExpandedJobId(jobId);
        if (!runsByJobId[jobId]) {
            try {
                const runs = await loadRuns(jobId).unwrap();
                setRunsByJobId(prev => ({ ...prev, [jobId]: runs }));
                setRunsErrorByJobId(prev => ({ ...prev, [jobId]: null }));
            } catch {
                setRunsErrorByJobId(prev => ({ ...prev, [jobId]: 'Provider run 明细加载失败' }));
            }
        }
    };

    const handleStatusChange = async (linkId, status) => {
        await updateStatus({ id: unifiedVideoId, linkId, status }).unwrap();
    };

    const handleDelete = async (linkId) => {
        if (window.confirm('确定删除该磁链资源吗？')) {
            await deleteMagnet({ id: unifiedVideoId, linkId }).unwrap();
        }
    };

    if (!unifiedVideoId) {
        return <Alert severity="info">{emptyMessage}</Alert>;
    }

    return (
        <Stack spacing={2}>
            <Stack
                direction="row"
                spacing={1}
                sx={{
                    justifyContent: "space-between",
                    alignItems: "center"
                }}>
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <Typography variant="subtitle2">{title}</Typography>
                    <Chip
                        label={isReady ? '已富化，可补链' : '待富化'}
                        color={isReady ? 'success' : 'warning'}
                        size="small"
                        variant="outlined"
                    />
                </Stack>
                <Stack direction="row" spacing={1}>
                    <Tooltip title="刷新磁链与任务">
                        <span>
                            <IconButton size="small" onClick={handleRefresh} disabled={linksQuery.isFetching || jobsQuery.isFetching}>
                                <RefreshIcon fontSize="small" />
                            </IconButton>
                        </span>
                    </Tooltip>
                    <Button
                        size="small"
                        variant="contained"
                        startIcon={isScraping ? <CircularProgress size={16} /> : <TravelExploreIcon />}
                        disabled={!isReady || isScraping}
                        onClick={onScrape}
                    >
                        投递补链任务
                    </Button>
                </Stack>
            </Stack>

            {!isReady && (
                <Alert severity="warning">视频富化完成后才会投递磁链补充任务，避免用未稳定元数据误匹配磁链。</Alert>
            )}

            <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>标题</TableCell>
                            <TableCell width={110}>来源</TableCell>
                            <TableCell width={105}>状态</TableCell>
                            <TableCell width={120}>质量</TableCell>
                            <TableCell width={100}>热度</TableCell>
                            <TableCell width={130}>更新时间</TableCell>
                            <TableCell width={72} align="right">操作</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {linksQuery.isLoading && (
                            <TableRow><TableCell colSpan={7}><CircularProgress size={18} /></TableCell></TableRow>
                        )}
                        {!linksQuery.isLoading && links.length === 0 && (
                            <TableRow><TableCell colSpan={7}>暂无磁链资源</TableCell></TableRow>
                        )}
                        {links.map(link => {
                            const option = statusOption(link.status);
                            return (
                                <TableRow key={link.id} hover>
                                    <TableCell>
                                        <Typography variant="body2" noWrap title={link.title}>{link.title || '-'}</Typography>
                                        <Typography variant="caption" noWrap title={link.infoHash} sx={{
                                            color: "text.secondary"
                                        }}>
                                            {link.infoHash}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>{link.providerCode || '-'}</TableCell>
                                    <TableCell>
                                        <Select
                                            size="small"
                                            value={link.status || 'CANDIDATE'}
                                            disabled={isUpdatingStatus}
                                            onChange={(event) => handleStatusChange(link.id, event.target.value)}
                                            sx={{ minWidth: 86 }}
                                        >
                                            {LINK_STATUS_OPTIONS.map(item => (
                                                <MenuItem value={item.value} key={item.value}>{item.label}</MenuItem>
                                            ))}
                                        </Select>
                                        <Chip label={option.label} color={option.color} size="small" sx={{ ml: 0.5 }} />
                                    </TableCell>
                                    <TableCell>{[link.quality, link.resolution, link.sizeLabel].filter(Boolean).join(' / ') || '-'}</TableCell>
                                    <TableCell>{`S ${link.seeders ?? '-'} / L ${link.leechers ?? '-'}`}</TableCell>
                                    <TableCell>{formatTime(link.updatedAt)}</TableCell>
                                    <TableCell align="right">
                                        <Tooltip title="删除磁链">
                                            <span>
                                                <IconButton size="small" color="error" disabled={isDeleting} onClick={() => handleDelete(link.id)}>
                                                    <DeleteIcon fontSize="small" />
                                                </IconButton>
                                            </span>
                                        </Tooltip>
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </TableContainer>

            <Divider />

            <Stack
                direction="row"
                sx={{
                    justifyContent: "space-between",
                    alignItems: "center"
                }}>
                <Typography variant="subtitle2">补链任务</Typography>
                {jobsQuery.isFetching && <CircularProgress size={16} />}
            </Stack>
            <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell width={56}></TableCell>
                            <TableCell width={115}>状态</TableCell>
                            <TableCell width={105}>触发</TableCell>
                            <TableCell>Provider</TableCell>
                            <TableCell width={120}>候选/入库</TableCell>
                            <TableCell width={160}>开始/结束</TableCell>
                            <TableCell>原因</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {!jobsQuery.isLoading && jobs.length === 0 && (
                            <TableRow><TableCell colSpan={7}>暂无补链任务</TableCell></TableRow>
                        )}
                        {jobs.map(job => (
                            <React.Fragment key={job.id}>
                                <TableRow hover>
                                    <TableCell>
                                        <IconButton size="small" onClick={() => handleToggleRuns(job.id)}>
                                            {expandedJobId === job.id ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                        </IconButton>
                                    </TableCell>
                                    <TableCell>
                                        <Chip label={job.status} color={JOB_STATUS_COLOR[job.status] || 'default'} size="small" />
                                    </TableCell>
                                    <TableCell>{job.triggerType || '-'}</TableCell>
                                    <TableCell>{compactList(job.providerCodes)}</TableCell>
                                    <TableCell>{`${job.totalCandidates ?? 0} / ${job.acceptedCount ?? 0}`}</TableCell>
                                    <TableCell>
                                        <Typography variant="caption" sx={{
                                            display: "block"
                                        }}>{formatTime(job.startedAt)}</Typography>
                                        <Typography
                                            variant="caption"
                                            sx={{
                                                color: "text.secondary",
                                                display: "block"
                                            }}>{formatTime(job.finishedAt)}</Typography>
                                    </TableCell>
                                    <TableCell>{job.skippedReason || job.errorMessage || '-'}</TableCell>
                                </TableRow>
                                <TableRow>
                                    <TableCell colSpan={7} sx={{ p: 0, borderBottom: 0 }}>
                                        <Collapse in={expandedJobId === job.id} timeout="auto" unmountOnExit>
                                            <Box sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                                                {runsQuery.isFetching && expandedJobId === job.id && <CircularProgress size={18} />}
                                                {runsErrorByJobId[job.id] && (
                                                    <Alert severity="warning" sx={{ mb: 1 }}>
                                                        {runsErrorByJobId[job.id]}
                                                    </Alert>
                                                )}
                                                {(runsByJobId[job.id] || []).map(run => (
                                                    <Stack
                                                        key={run.id}
                                                        direction="row"
                                                        spacing={1.5}
                                                        sx={{
                                                            alignItems: "center",
                                                            py: 0.5
                                                        }}>
                                                        <Chip label={run.status} size="small" color={run.status === 'SUCCESS' ? 'success' : 'error'} />
                                                        <Typography variant="caption">{run.providerCode}</Typography>
                                                        <Typography variant="caption" sx={{
                                                            color: "text.secondary"
                                                        }}>{run.externalIdType}: {run.externalIdValue}</Typography>
                                                        <Typography variant="caption">候选 {run.candidateCount ?? 0} / 入库 {run.acceptedCount ?? 0}</Typography>
                                                        <Typography variant="caption">{run.durationMs ?? 0} ms</Typography>
                                                        {run.errorMessage && <Typography variant="caption" color="error">{run.errorMessage}</Typography>}
                                                    </Stack>
                                                ))}
                                                {expandedJobId === job.id && !runsQuery.isFetching && !runsErrorByJobId[job.id] && (runsByJobId[job.id] || []).length === 0 && (
                                                    <Typography variant="caption" sx={{
                                                        color: "text.secondary"
                                                    }}>暂无 provider run 明细</Typography>
                                                )}
                                            </Box>
                                        </Collapse>
                                    </TableCell>
                                </TableRow>
                            </React.Fragment>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Stack>
    );
}

export default MagnetResourcePanel;
