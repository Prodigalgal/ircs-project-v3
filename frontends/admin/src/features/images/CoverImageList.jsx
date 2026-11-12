// File: src/features/images/CoverImageList.jsx
import React, { useState, useMemo, useCallback } from 'react';
import { Box, Button, Chip, IconButton, Link, Stack, Tooltip, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import ReplayIcon from '@mui/icons-material/Replay';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useDeleteCoverImageMutation, useGetCoverImagesQuery, useRetryCoverImageMutation, useSyncCoverImageR2Mutation } from './coverImagesApi';
import SmartTable from '../../components/common/SmartTable';
import FilterPanel from '../../components/common/FilterPanel';
import ImageUploadDialog from './components/ImageUploadDialog';
import RemoteFetchDialog from './components/RemoteFetchDialog';
import ListHeader from '../../components/common/ListHeader';
import { getTotalElements } from '../../utils/paginationUtils';
import { IMAGE_STATUS, STORAGE_TYPE, getStatusConfig } from '../../utils/constants';

const STATUS_COLORS = {
    UNPROCESSED: 'default',
    FETCHING: 'info',
    LOCAL_STORED: 'warning',
    UPLOADING: 'info',
    REMOTE_STORED: 'success',
    FAILED: 'error',
    DEAD: 'error'
};

const STATUS_LABELS = {
    UNPROCESSED: '未处理',
    FETCHING: '下载中',
    LOCAL_STORED: '本地存储',
    UPLOADING: '上传中',
    REMOTE_STORED: 'R2 托管',
    FAILED: '失败',
    DEAD: '已死链'
};

function CoverImageList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});
    const [uploadOpen, setUploadOpen] = useState(false);
    const [fetchOpen, setFetchOpen] = useState(false);

    const { data, isLoading, isFetching, refetch } = useGetCoverImagesQuery({
        page,
        size: pageSize,
        sort: 'updatedAt,desc',
        ...filters
    });

    const [deleteImage] = useDeleteCoverImageMutation();
    const [retryImage] = useRetryCoverImageMutation();
    const [syncR2] = useSyncCoverImageR2Mutation();

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定删除此图片？如果是被视频引用的图片，视频封面可能会失效。')) {
            await deleteImage(id).unwrap();
        }
    }, [deleteImage]);

    const handleSearch = (newFilters) => {
        setFilters(newFilters);
        setPage(0);
    };

    const handleCopy = (text) => {
        navigator.clipboard.writeText(text);
    };

    const columns = useMemo(() => [
        {
            field: 'preview', headerName: '预览', width: 80, sortable: false,
            renderCell: ({ row }) => (
                <Box
                    component="img"
                    // 修正点 1: 始终使用 resolvedUrl
                    src={row.resolvedUrl}
                    alt="thumb"
                    sx={{ width: 40, height: 60, objectFit: 'cover', borderRadius: 0.5, bgcolor: '#000', cursor: 'pointer' }}
                    onError={(e) => e.target.src = 'https://via.placeholder.com/40x60?text=Err'}
                    referrerPolicy="no-referrer"
                    onClick={() => window.open(row.resolvedUrl, '_blank')}
                />
            )
        },
        {
            field: 'sourceDomainValue',
            headerName: '来源 (Origin)',
            width: 200,
            renderCell: ({ value }) => (
                <Typography
                    variant="body2"
                    sx={{
                        color: "text.secondary",
                        fontFamily: 'monospace'
                    }}>
                    {value || '未知 / 手动'}
                </Typography>
            )
        },
        {
            field: 'status', headerName: '状态', width: 120,
            renderCell: ({ value }) => {
                const config = getStatusConfig(IMAGE_STATUS, value);
                return (
                    <Chip
                        label={config.label}
                        color={config.color}
                        size="small"
                        variant={value === 'REMOTE_STORED' ? 'filled' : 'outlined'}
                    />
                );
            }
        },
        {
            field: 'storageType', headerName: '类型', width: 90,
            renderCell: ({ value }) => <Typography variant="caption">{value}</Typography>
        },
        {
            field: 'fileURL',
            headerName: '完整路径/URL',
            width: 350,
            renderCell: ({ row }) => {
                const url = row.resolvedUrl || row.originalUrl;

                return (
                    <Stack
                        direction="row"
                        spacing={1}
                        sx={{
                            alignItems: "center",
                            width: '100%'
                        }}>
                        <Tooltip title={url} placement="top">
                            <Link
                                href={url}
                                target="_blank"
                                rel="noopener"
                                color="inherit"
                                sx={{
                                    fontSize: '0.8rem',
                                    fontFamily: 'monospace',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    display: 'block',
                                    maxWidth: 280
                                }}
                            >
                                {url}
                            </Link>
                        </Tooltip>
                        <IconButton size="small" onClick={() => handleCopy(url)} title="复制链接">
                            <ContentCopyIcon sx={{ fontSize: 14 }} />
                        </IconButton>
                    </Stack>
                );
            }
        },
        {
            field: 'fileSize', headerName: '大小', width: 90,
            valueGetter: (v) => v ? (v / 1024).toFixed(1) + ' KB' : '-'
        },
        {
            field: 'updatedAt',
            headerName: '更新时间',
            width: 170,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
        {
            field: 'actions', headerName: '操作', width: 160, align: 'right', sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" sx={{
                    justifyContent: "flex-end"
                }}>
                    {(row.storageType === 'EXTERNAL' && row.status === 'UNPROCESSED') && (
                        <Tooltip title="立即拉取 (下载到本地)">
                            <IconButton size="small" color="primary" onClick={() => retryImage(row.id)}>
                                <CloudDownloadIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    )}

                    {(row.status === 'FAILED' || row.status === 'DEAD') && (
                        <Tooltip title="重试下载">
                            <IconButton size="small" color="error" onClick={() => retryImage(row.id)}>
                                <ReplayIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    )}

                    {(row.status === 'LOCAL_STORED' && row.storageType === 'LOCAL') && (
                        <Tooltip title="推送到 R2 (Cloudflare)">
                            <IconButton size="small" color="secondary" onClick={() => syncR2(row.id)}>
                                <CloudUploadIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    )}

                    <IconButton size="small" color="default" onClick={() => handleDelete(row.id)}>
                        <DeleteIcon fontSize="small" />
                    </IconButton>
                </Stack>
            )
        }
    ], [handleDelete, retryImage, syncR2]);

    const filterFields = [
        { name: 'url', label: 'URL 关键字', type: 'text' },
        { name: 'sourceDomain', label: '来源域名', type: 'text', placeholder: '如: example.com' },
        {
            name: 'status', label: '状态', type: 'select',
            options: Object.keys(IMAGE_STATUS).map(k => ({ value: k, label: IMAGE_STATUS[k].label }))
        },
        {
            name: 'storageType', label: '存储类型', type: 'select',
            options: Object.keys(STORAGE_TYPE).map(k => ({ value: k, label: STORAGE_TYPE[k] }))
        },
        { name: 'minFileSize', label: '最小大小(Byte)', type: 'number', placeholder: '>= 0' },
        { name: 'maxFileSize', label: '最大大小(Byte)', type: 'number', placeholder: '<= 1048576' }
    ];

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="图片存储管理"
                onAdd={() => setUploadOpen(true)}
                addLabel="手动上传"
                onRefresh={refetch}
                loading={isLoading || isFetching}
                extraActions={
                    <Button variant="outlined" startIcon={<CloudDownloadIcon />} onClick={() => setFetchOpen(true)} size="medium">
                        拉取远程
                    </Button>
                }
            />

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
            />

            <ImageUploadDialog open={uploadOpen} onClose={() => setUploadOpen(false)} />
            <RemoteFetchDialog open={fetchOpen} onClose={() => setFetchOpen(false)} />
        </Box>
    );
}

export default CoverImageList;
