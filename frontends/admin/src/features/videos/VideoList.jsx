import React, {useCallback, useMemo, useState} from 'react';
import {
    Box,
    Button,
    Chip,
    Divider,
    IconButton,
    ListItemIcon,
    ListItemText,
    Menu,
    MenuItem,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import CloudSyncIcon from '@mui/icons-material/CloudSync';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import SendIcon from '@mui/icons-material/Send';

import {
    useBatchDeleteVideosMutation,
    useBatchReEnrichVideosMutation,
    useBatchReFetchVideosMutation,
    useBatchReNormalizeVideosMutation,
    useCreateVideoMutation,
    useDeleteVideoMutation,
    useGetVideosQuery,
    useLazyGetVideoByIdQuery,
    useUpdateVideoMutation,
    useBatchSyncSearchVideosMutation
} from './videosApi';

import {useGetAllCategoriesQuery} from '../categories/categoriesApi';
import {useGetAllDataSourcesQuery} from '../data-sources/dataSourcesApi';
import VideoForm from './VideoForm';
import VideoPlayerDialog from '../../components/common/VideoPlayerDialog';
import SmartTable from '../../components/common/SmartTable';
import FilterPanel from '../../components/common/FilterPanel';
import ListHeader from '../../components/common/ListHeader';
import {getTotalElements} from '../../utils/paginationUtils';
import {ENRICH_STATUS, getStatusConfig, NORMALIZATION_STATUS, AGGREGATION_STATUS} from '../../utils/constants';

function VideoList() {
    // --- 状态管理 ---
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [sortModel, setSortModel] = useState({field: 'createdAt', order: 'desc'});
    const [selectedIds, setSelectedIds] = useState([]);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingVideo, setEditingVideo] = useState(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [batchMenuAnchor, setBatchMenuAnchor] = useState(null);
    const [filters, setFilters] = useState({});
    const [playerOpen, setPlayerOpen] = useState(false);
    const [playingVideo, setPlayingVideo] = useState(null);

    // --- 数据查询 ---
    const {data: dataSources = []} = useGetAllDataSourcesQuery();
    const {data: categories = []} = useGetAllCategoriesQuery();

    const {data, isLoading, isFetching, refetch} = useGetVideosQuery({
        page,
        size: pageSize,
        sort: `${sortModel.field},${sortModel.order}`,
        ...filters
    });

    // --- 操作钩子 ---
    const [createVideo] = useCreateVideoMutation();
    const [updateVideo] = useUpdateVideoMutation();
    const [deleteVideo] = useDeleteVideoMutation();
    const [batchDeleteVideos] = useBatchDeleteVideosMutation();
    const [batchReNormalize] = useBatchReNormalizeVideosMutation();
    const [batchReEnrich] = useBatchReEnrichVideosMutation();
    const [batchReFetch] = useBatchReFetchVideosMutation();
    const [batchSyncSearch] = useBatchSyncSearchVideosMutation();
    const [triggerGetVideoById] = useLazyGetVideoByIdQuery();

    // --- 事件处理 ---
    const handleNewClick = useCallback(() => {
        setEditingVideo(null);
        setIsDetailLoading(false);
        setIsFormOpen(true);
    }, []);

    const handleEditClick = useCallback((row) => {
        setEditingVideo({ id: row.id, title: row.title });
        setIsDetailLoading(true);
        setIsFormOpen(true);

        triggerGetVideoById(row.id)
            .unwrap()
            .then((fullVideoData) => {
                setEditingVideo(fullVideoData);
            })
            .catch((error) => {
                console.error("Failed to load video details", error);
                setIsFormOpen(false);
            })
            .finally(() => {
                setIsDetailLoading(false);
            });
    }, [triggerGetVideoById]);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要删除此视频记录吗？')) {
            await deleteVideo(id).unwrap();
        }
    }, [deleteVideo]);

    const handleBatchAction = async (actionType) => {
        if (selectedIds.length === 0) return;
        setBatchMenuAnchor(null);
        try {
            if (actionType === 'delete') {
                if (window.confirm(`确定要彻底删除选中的 ${selectedIds.length} 个原始视频吗？`)) {
                    await batchDeleteVideos({ids: selectedIds}).unwrap();
                    setSelectedIds([]);
                }
            } else if (actionType === 'sync') {
                await batchSyncSearch(selectedIds).unwrap();
                alert(`已提交 ${selectedIds.length} 个视频的 ES 强制同步任务。`);
                setSelectedIds([]);
            } else if (actionType === 'normalize') {
                await batchReNormalize(selectedIds).unwrap();
                alert('已提交批量归一化任务');
                setSelectedIds([]);
            } else if (actionType === 'enrich') {
                await batchReEnrich(selectedIds).unwrap();
                alert('已提交批量搜刮任务');
                setSelectedIds([]);
            } else if (actionType === 'fetch') {
                if (window.confirm(`警告：这将重新抓取源站数据！确定继续吗？`)) {
                    await batchReFetch(selectedIds).unwrap();
                    alert('已提交批量重抓任务');
                    setSelectedIds([]);
                }
            }
        } catch (e) {
            console.error("Batch action failed", e);
            alert("操作失败");
        }
    };

    const handlePlayClick = useCallback((row) => {
        setPlayingVideo(row);
        setPlayerOpen(true);
    }, []);

    const handleFormSubmit = useCallback(async (videoData) => {
        if (videoData.id) await updateVideo(videoData).unwrap();
        else await createVideo(videoData).unwrap();
        setIsFormOpen(false);
        setEditingVideo(null);
    }, [updateVideo, createVideo]);

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const filterFields = useMemo(() => [
        {name: 'title', label: '视频标题', type: 'text', placeholder: '搜标题或VID'},
        {name: 'year', label: '年份', type: 'text', placeholder: '2025'},
        {name: 'area', label: '地区', type: 'text', placeholder: '如: 美国'},
        {name: 'minScore', label: '最低评分', type: 'number', placeholder: '0-10'},
        {
            name: 'isMissingSlug', label: '外部ID状态', type: 'select',
            options: [{value: 'true', label: '缺失 (Douban/TMDB)'}, {value: 'false', label: '信息完整'}]
        },
        {
            name: 'categoryId', label: '标准分类', type: 'select',
            options: categories.map(cat => ({value: cat.id, label: cat.name}))
        },
        {name: 'genre', label: '题材关键字', type: 'text', placeholder: '如: 科幻'},
        {name: 'language', label: '语言关键字', type: 'text', placeholder: '如: 英语'},
        {
            name: 'dataSourceId', label: '所属数据源', type: 'select',
            options: dataSources.map(ds => ({value: ds.id, label: ds.name}))
        },
        {
            name: 'sourceCategoryName',
            label: '源分类名称',
            type: 'text',
            placeholder: '源站返回的分类名称'
        },
        {
            name: 'normalizationStatus', label: '归一化状态', type: 'select',
            options: Object.keys(NORMALIZATION_STATUS).map(k => ({value: k, label: NORMALIZATION_STATUS[k].label}))
        },
        {
            name: 'enrichmentStatus', label: '搜刮状态', type: 'select',
            options: Object.keys(ENRICH_STATUS).map(k => ({value: k, label: ENRICH_STATUS[k].label}))
        },
        {
            name: 'aggregationStatus', label: '聚合状态', type: 'select',
            options: Object.keys(AGGREGATION_STATUS).map(k => ({value: k, label: AGGREGATION_STATUS[k].label}))
        }
    ], [categories, dataSources]);

    // --- 定义表格列 ---
    const columns = useMemo(() => [
        {
            field: 'title', headerName: '视频标题', width: 320, sortable: true,
            renderCell: ({row}) => (
                <Box sx={{ width: '100%' }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                        {row.title}
                        {(row.season !== null && row.season !== undefined) && (
                            <span style={{ color: '#4caf50', marginLeft: 4 }}> S{row.season}</span>
                        )}
                        {row.subtitle && <span style={{ color: '#90a4ae' }}> · {row.subtitle}</span>}
                    </Typography>
                    {row.aliasTitle && (
                        <Typography
                            variant="caption"
                            noWrap
                            sx={{
                                color: "text.secondary",
                                display: "block"
                            }}>
                            {row.aliasTitle}
                        </Typography>
                    )}
                </Box>
            )
        },
        {
            field: 'categoryName', headerName: '分类/来源', width: 150,
            renderCell: ({row}) => (
                <Box>
                    <Typography variant="body2">{row.categoryName || '-'}</Typography>
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.secondary",
                            display: 'block'
                        }}>
                        {row.dataSourceName}
                    </Typography>
                </Box>
            )
        },
        {
            field: 'specs', headerName: '规格/时长', width: 100, sortable: false,
            renderCell: ({row}) => (
                <Box>
                    <Typography variant="caption" sx={{
                        display: "block"
                    }}>{row.totalEpisodes || '-'}</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>{row.duration || '-'}</Typography>
                </Box>
            )
        },
        {field: 'year', headerName: '年份', width: 70},
        {field: 'area', headerName: '地区', width: 100},
        {field: 'score', headerName: '评分', width: 60},
        {
            field: 'normalizationStatus', headerName: '清洗', width: 100,
            renderCell: ({value}) => {
                const config = getStatusConfig(NORMALIZATION_STATUS, value);
                return <Chip label={config.label} color={config.color} size="small" variant="outlined"/>;
            }
        },
        {
            field: 'enrichmentStatus', headerName: '搜刮', width: 100,
            renderCell: ({value}) => {
                const config = getStatusConfig(ENRICH_STATUS, value);
                return <Chip label={config.label} color={config.color} size="small" variant="outlined"/>;
            }
        },
        {
            field: 'aggregationStatus', headerName: '聚合', width: 100,
            renderCell: ({value}) => {
                const config = getStatusConfig(AGGREGATION_STATUS, value);
                return <Chip label={config.label} color={config.color} size="small" variant="outlined"/>;
            }
        },
        {
            field: 'createdAt', headerName: '入库时间', width: 160,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
        {
            field: 'actions', headerName: '操作', width: 140, align: 'right', sortable: false,
            renderCell: ({row}) => (
                <Stack direction="row" spacing={0.5} sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title="预览视频">
                        <IconButton size="small" onClick={() => handlePlayClick(row)} color="success">
                            <PlayCircleIcon fontSize="small"/>
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="详情编辑">
                        <IconButton size="small" onClick={() => handleEditClick(row)}>
                            <EditIcon fontSize="small"/>
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="彻底删除">
                        <IconButton size="small" onClick={() => handleDelete(row.id)} color="error">
                            <DeleteIcon fontSize="small"/>
                        </IconButton>
                    </Tooltip>
                </Stack>
            )
        },
    ], [handleEditClick, handleDelete, handlePlayClick]);

    return (
        <Box sx={{height: '100%', display: 'flex', flexDirection: 'column'}}>
            <ListHeader
                title="源视频库 (Raw Videos)"
                subtitle="所有的检索查询均由 Elasticsearch 提供支持，实现毫秒级响应。"
                onRefresh={refetch}
                loading={isLoading || isFetching}
                onAdd={handleNewClick}
                addLabel="手动入库"
            />

            <Box sx={{ mb: 1.5, display: 'flex', justifyContent: 'flex-end' }}>
                {selectedIds.length > 0 && (
                    <Box>
                        <Button
                            variant="contained"
                            color="primary"
                            onClick={(e) => setBatchMenuAnchor(e.currentTarget)}
                            startIcon={<MoreVertIcon/>}
                            endIcon={<ArrowDropDownIcon/>}
                        >
                            批量处理 ({selectedIds.length})
                        </Button>
                        <Menu
                            anchorEl={batchMenuAnchor}
                            open={Boolean(batchMenuAnchor)}
                            onClose={() => setBatchMenuAnchor(null)}
                        >
                            <MenuItem onClick={() => handleBatchAction('sync')}>
                                <ListItemIcon><SendIcon fontSize="small" color="primary"/></ListItemIcon>
                                <ListItemText>立即同步至搜索引擎</ListItemText>
                            </MenuItem>
                            <Divider />
                            <MenuItem onClick={() => handleBatchAction('normalize')}>
                                <ListItemIcon><CleaningServicesIcon fontSize="small"/></ListItemIcon>
                                <ListItemText>触发重新归一化</ListItemText>
                            </MenuItem>
                            <MenuItem onClick={() => handleBatchAction('enrich')}>
                                <ListItemIcon><AutoFixHighIcon fontSize="small"/></ListItemIcon>
                                <ListItemText>触发重新搜刮元数据</ListItemText>
                            </MenuItem>
                            <MenuItem onClick={() => handleBatchAction('fetch')}>
                                <ListItemIcon><CloudSyncIcon fontSize="small" color="warning"/></ListItemIcon>
                                <ListItemText sx={{color: 'warning.main'}}>从源站重新抓取数据</ListItemText>
                            </MenuItem>
                            <Divider/>
                            <MenuItem onClick={() => handleBatchAction('delete')}>
                                <ListItemIcon><DeleteIcon fontSize="small" color="error"/></ListItemIcon>
                                <ListItemText sx={{color: 'error.main'}}>批量彻底删除</ListItemText>
                            </MenuItem>
                        </Menu>
                    </Box>
                )}
            </Box>

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
                sortModel={sortModel}
                onSortChange={setSortModel}
                selectionModel={selectedIds}
                onSelectionChange={setSelectedIds}
            />

            {isFormOpen && (
                <VideoForm
                    open={isFormOpen}
                    onClose={() => setIsFormOpen(false)}
                    onSubmit={handleFormSubmit}
                    video={editingVideo}
                    isLoading={isDetailLoading}
                />
            )}

            <VideoPlayerDialog
                open={playerOpen}
                onClose={() => setPlayerOpen(false)}
                videoId={playingVideo?.id}
                videoTitle={playingVideo?.title}
                type="SOURCE"
            />
        </Box>
    );
}

export default VideoList;
