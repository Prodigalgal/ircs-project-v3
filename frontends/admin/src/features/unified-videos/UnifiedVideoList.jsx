import React, {useCallback, useMemo, useState} from 'react';
import {
    Box,
    Button,
    CircularProgress,
    Divider,
    IconButton,
    ListItemIcon,
    ListItemText,
    Menu,
    MenuItem,
    Stack,
    Typography,
    Chip
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import SendIcon from '@mui/icons-material/Send';
import MagnetIcon from '@mui/icons-material/TravelExplore';
import MergeIcon from '@mui/icons-material/MergeType';

import {
    useBatchDeleteUnifiedVideosMutation,
    useBatchSyncSearchUnifiedVideosMutation,
    useCreateUnifiedVideoMutation,
    useDeleteUnifiedVideoMutation,
    useGetUnifiedVideosQuery,
    useLazyGetUnifiedVideoByIdQuery,
    useMergeUnifiedVideosMutation,
    useRecalculateUnifiedVideoMetadataMutation,
    useScrapeUnifiedVideoMagnetsMutation,
    useUpdateUnifiedVideoMutation
} from './unifiedVideosApi';
import SmartTable from '../../components/common/SmartTable';
import FilterPanel from '../../components/common/FilterPanel';
import ListHeader from '../../components/common/ListHeader';
import UnifiedVideoForm from './UnifiedVideoForm';
import {getTotalElements} from '../../utils/paginationUtils';
import {useGetAllCategoriesQuery} from '../categories/categoriesApi';
import {METADATA_STATUS, getStatusConfig} from '../../utils/constants';

function UnifiedVideoList() {
    // --- 状态管理 ---
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const [filters, setFilters] = useState({});
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);
    const [isLoadingDetails, setIsLoadingDetails] = useState(false);
    const [selectedIds, setSelectedIds] = useState([]);
    const [batchMenuAnchor, setBatchMenuAnchor] = useState(null);
    const [rowMenuAnchor, setRowMenuAnchor] = useState(null);
    const [menuRow, setMenuRow] = useState(null);

    const handleRowMenuClose = useCallback(() => {
        setRowMenuAnchor(null);
        setMenuRow(null);
    }, []);


    // --- 数据查询 ---
    const {data, isLoading, isFetching, refetch} = useGetUnifiedVideosQuery({
        page,
        size: pageSize,
        sort: 'lastTrendAt,desc',
        ...filters
    });

    const {data: categories = []} = useGetAllCategoriesQuery();

    // --- 操作钩子 ---
    const [createUnifiedVideo] = useCreateUnifiedVideoMutation();
    const [deleteUnifiedVideo] = useDeleteUnifiedVideoMutation();
    const [batchDeleteUnifiedVideos] = useBatchDeleteUnifiedVideosMutation();
    const [batchSyncSearch] = useBatchSyncSearchUnifiedVideosMutation();
    const [mergeUnifiedVideos, {isLoading: isMergingUnifiedVideos}] = useMergeUnifiedVideosMutation();
    const [updateUnifiedVideo] = useUpdateUnifiedVideoMutation();
    const [recalculateMetadata] = useRecalculateUnifiedVideoMetadataMutation();
    const [scrapeMagnets, {isLoading: isScrapingMagnets}] = useScrapeUnifiedVideoMagnetsMutation();
    const [triggerGetById] = useLazyGetUnifiedVideoByIdQuery();

    // --- 事件处理函数 ---

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const handleNewClick = useCallback(() => {
        setEditingItem(null);
        setIsLoadingDetails(false);
        setIsFormOpen(true);
    }, []);

    const handleEditClick = useCallback(async (id) => {
        setIsLoadingDetails(true);
        try {
            const detailData = await triggerGetById(id).unwrap();
            setEditingItem(detailData);
            setIsFormOpen(true);
        } catch (e) {
            console.error("Failed to load details", e);
        } finally {
            setIsLoadingDetails(false);
        }
    }, [triggerGetById]);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要删除此聚合视频吗？关联的源视频将被解绑。')) {
            await deleteUnifiedVideo(id).unwrap();
        }
    }, [deleteUnifiedVideo]);

    const handleRecalculate = useCallback(async (id) => {
        handleRowMenuClose();
        if (window.confirm('确定要重新计算此视频的属性吗？')) {
            try {
                await recalculateMetadata(id).unwrap();
                alert("属性重算已完成");
            } catch (e) {
                alert("操作失败: " + (e.data?.message || e.message));
            }
        }
    }, [handleRowMenuClose, recalculateMetadata]);

    const rowsById = useMemo(() => new Map((data?.content || []).map(row => [row.id, row])), [data]);

    const formatMagnetResult = useCallback((result) => {
        const status = result?.status || 'UNKNOWN';
        if (status === 'PENDING') {
            return '磁力补链任务已投递，后台 worker 会异步爬取并写入结果。';
        }
        if (status === 'SKIPPED') {
            return `磁力搜刮已跳过：${result?.skippedReason || '该聚合视频没有可用外部 ID 或标题。'}`;
        }
        const accepted = result?.acceptedCount ?? 0;
        return `磁力搜刮完成：状态 ${status}，新增/刷新 ${accepted} 条资源。`;
    }, []);

    const handleScrapeMagnets = useCallback(async (row) => {
        handleRowMenuClose();
        const id = row?.id;
        if (!id) return;
        if (row?.metadataStatus && row.metadataStatus !== 'SYNCED') {
            alert('该聚合视频尚未完成富化，暂不能补充磁力链接。');
            return;
        }
        if (window.confirm('确定要对该聚合视频投递磁力补链任务吗？后台 worker 会异步爬取并写入结果。')) {
            try {
                const result = await scrapeMagnets(id).unwrap();
                alert(formatMagnetResult(result));
            } catch (e) {
                alert("磁力搜刮失败: " + (e.data?.message || e.message));
            }
        }
    }, [formatMagnetResult, handleRowMenuClose, scrapeMagnets]);

    const handleBatchScrapeMagnets = useCallback(async () => {
        if (selectedIds.length === 0) return;
        setBatchMenuAnchor(null);

        const selectedRows = selectedIds.map(id => rowsById.get(id) || {id});
        const readyRows = selectedRows.filter(row => !row.metadataStatus || row.metadataStatus === 'SYNCED');
        const blockedRows = selectedRows.length - readyRows.length;
        if (readyRows.length === 0) {
            alert('选中的聚合视频尚未完成富化，暂不能补充磁力链接。');
            return;
        }
        if (!window.confirm(`确定对 ${readyRows.length} 个已富化聚合视频投递磁力补链任务吗？${blockedRows > 0 ? `将跳过 ${blockedRows} 个未富化视频。` : ''}`)) {
            return;
        }

        let success = 0;
        let failed = 0;
        let accepted = 0;
        let skipped = 0;

        for (const row of readyRows) {
            try {
                const result = await scrapeMagnets(row.id).unwrap();
                if (result?.status === 'SKIPPED') {
                    skipped += 1;
                } else {
                    success += 1;
                    accepted += result?.acceptedCount ?? 0;
                }
            } catch (e) {
                console.error("Failed to scrape magnets", row.id, e);
                failed += 1;
            }
        }

        alert(`批量磁力补链投递完成：已投递 ${success} 个，跳过 ${skipped + blockedRows} 个，失败 ${failed} 个，已同步新增/刷新 ${accepted} 条资源。`);
        setSelectedIds([]);
    }, [rowsById, scrapeMagnets, selectedIds]);

    const handleMergeSelectedUnifiedVideos = useCallback(async () => {
        if (selectedIds.length < 2) {
            alert('请至少选择两个聚合视频。');
            return;
        }
        setBatchMenuAnchor(null);
        const rootRow = rowsById.get(selectedIds[0]);
        const rootTitle = rootRow?.title || selectedIds[0];
        if (!window.confirm(`确定将选中的 ${selectedIds.length} 个聚合视频一键聚合吗？\n\n保留主记录：${rootTitle}\n其余选中记录会被合并并删除。`)) {
            return;
        }
        try {
            const result = await mergeUnifiedVideos(selectedIds).unwrap();
            alert(`聚合完成：主记录 ${result.rootUnifiedVideoId}，合并 ${result.victimUnifiedVideoIds?.length || 0} 个重复项，关联源视频 ${result.rawVideoIds?.length || 0} 个。`);
            setSelectedIds([]);
            refetch();
        } catch (e) {
            alert("一键聚合失败: " + (e.data?.message || e.message));
        }
    }, [mergeUnifiedVideos, refetch, rowsById, selectedIds]);

    const handleBatchAction = async (type) => {
        if (selectedIds.length === 0) return;
        setBatchMenuAnchor(null);
        try {
            if (type === 'delete') {
                if (window.confirm(`确定删除选中的 ${selectedIds.length} 个聚合视频吗？`)) {
                    await batchDeleteUnifiedVideos(selectedIds).unwrap();
                    setSelectedIds([]);
                }
            } else if (type === 'sync') {
                await batchSyncSearch(selectedIds).unwrap();
                alert(`已提交 ${selectedIds.length} 个聚合视频的同步任务。`);
                setSelectedIds([]);
            } else if (type === 'magnets') {
                await handleBatchScrapeMagnets();
            } else if (type === 'merge') {
                await handleMergeSelectedUnifiedVideos();
            }
        } catch {
            alert('批量操作失败');
        }
    };

    const handleFormSubmit = useCallback(async (formData) => {
        try {
            if (formData.id) {
                await updateUnifiedVideo(formData).unwrap();
            } else {
                await createUnifiedVideo(formData).unwrap();
            }
            setIsFormOpen(false);
            setEditingItem(null);
        } catch (e) {
            alert("保存失败: " + (e.data?.message || e.message));
        }
    }, [updateUnifiedVideo, createUnifiedVideo]);

    const handleRowMenuOpen = (event, row) => {
        setRowMenuAnchor(event.currentTarget);
        setMenuRow(row);
    };


    // --- 表格与筛选配置 ---
    const filterFields = useMemo(() => [
        {name: 'title', label: '聚合标题', type: 'text', placeholder: '支持模糊搜索'},
        {name: 'year', label: '年份', type: 'text', placeholder: '2025'},
        {name: 'area', label: '地区', type: 'text', placeholder: '中国'},
        {name: 'minScore', label: '最低评分', type: 'number', placeholder: '0-10'},
        {
            name: 'categoryId',
            label: '分类',
            type: 'select',
            options: categories.map(c => ({value: c.id, label: c.name}))
        },
        {name: 'genre', label: '题材关键字', type: 'text', placeholder: '如: 悬疑'},
        {name: 'language', label: '语言关键字', type: 'text', placeholder: '如: 粤语'},
        {name: 'actor', label: '演员', type: 'text', placeholder: '演员名称'},
        {name: 'director', label: '导演', type: 'text', placeholder: '导演名称'},
        {
            name: 'hasDoubanId',
            label: '豆瓣 ID',
            type: 'select',
            options: [{value: 'true', label: '已关联'}, {value: 'false', label: '缺失'}]
        },
        {
            name: 'hasTmdbId',
            label: 'TMDB ID',
            type: 'select',
            options: [{value: 'true', label: '已关联'}, {value: 'false', label: '缺失'}]
        },
        {
            name: 'contentVisibility',
            label: '可见性',
            type: 'select',
            options: [
                {value: 'PUBLIC', label: '公开'},
                {value: 'MEMBER', label: '会员'},
                {value: 'ADMIN', label: '管理员'},
                {value: 'DRAFT', label: '草稿'},
                {value: 'HIDDEN', label: '隐藏'}
            ]
        },
        {
            name: 'metadataStatus', label: '数据状态', type: 'select',
            options: Object.keys(METADATA_STATUS).map(k => ({value: k, label: METADATA_STATUS[k].label}))
        }
    ], [categories]);

    const columns = useMemo(() => [
        {
            field: 'title', headerName: '聚合标题', width: 320,
            renderCell: ({row}) => (
                <Box>
                    <Typography variant="body2" noWrap sx={{
                        fontWeight: "500"
                    }}>
                        {row.title}
                        {row.season && <span style={{ color: '#4caf50', marginLeft: 4 }}> S{row.season}</span>}
                        {row.subtitle && <span style={{ color: '#90a4ae' }}> · {row.subtitle}</span>}
                    </Typography>
                    {row.aliasTitle && <Typography
                        variant="caption"
                        noWrap
                        sx={{
                            color: "text.secondary",
                            display: "block"
                        }}>{row.aliasTitle}</Typography>}
                </Box>
            )
        },
        {field: 'categoryName', headerName: '分类', width: 100},
        {field: 'year', headerName: '年份', width: 80},
        {
            field: 'metadataStatus', headerName: '状态', width: 100,
            renderCell: ({value}) => {
                const config = getStatusConfig(METADATA_STATUS, value);
                return <Chip label={config.label} color={config.color} size="small" variant="outlined"/>;
            }
        },
        {
            field: 'lastTrendAt', headerName: '上榜时间', width: 150,
            renderCell: ({value}) => value ?
                <Typography variant="body2">{new Date(value).toLocaleDateString()}</Typography> : '-'
        },
        {field: 'score', headerName: '评分', width: 70},
        {
            field: 'updatedAt', headerName: '更新时间', width: 150,
            valueGetter: (v) => v ? new Date(v).toLocaleDateString() : '-'
        },
        {
            field: 'actions', headerName: '操作', width: 120, align: 'right', sortable: false,
            renderCell: ({row}) => (
                <Stack direction="row" sx={{
                    justifyContent: "flex-end"
                }}>
                    <IconButton size="small" onClick={() => handleEditClick(row.id)} disabled={isLoadingDetails}>
                        {isLoadingDetails && editingItem?.id === row.id ? <CircularProgress size={20}/> :
                            <EditIcon fontSize="small"/>}
                    </IconButton>
                    <IconButton size="small" onClick={(e) => handleRowMenuOpen(e, row)}><MoreVertIcon
                        fontSize="small"/></IconButton>
                </Stack>
            ),
        },
    ], [handleEditClick, isLoadingDetails, editingItem]);

    return (
        <Box sx={{height: '100%', display: 'flex', flexDirection: 'column'}}>
            <ListHeader
                title="聚合视频库 (Unified)"
                subtitle="所有的检索查询均由 Elasticsearch 提供支持，实现毫秒级响应。"
                onRefresh={refetch}
                loading={isLoading || isFetching}
                onAdd={handleNewClick}
                addLabel="新建聚合"
            />

            <Box sx={{mb: 1, display: 'flex', justifyContent: 'flex-end'}}>
                {selectedIds.length > 0 && (
                    <Box>
                        <Button variant="contained" color="primary" onClick={(e) => setBatchMenuAnchor(e.currentTarget)}
                                startIcon={<MoreVertIcon/>} endIcon={<ArrowDropDownIcon/>}>批量操作
                            ({selectedIds.length})</Button>
                        <Menu anchorEl={batchMenuAnchor} open={Boolean(batchMenuAnchor)}
                              onClose={() => setBatchMenuAnchor(null)}>
                            <MenuItem onClick={() => handleBatchAction('sync')}><ListItemIcon><SendIcon fontSize="small"
                                                                                                        color="primary"/></ListItemIcon><ListItemText>立即同步至搜索引擎</ListItemText></MenuItem>
                            <MenuItem onClick={() => handleBatchAction('magnets')} disabled={isScrapingMagnets}><ListItemIcon><MagnetIcon fontSize="small"
                                                                                                                                             color="secondary"/></ListItemIcon><ListItemText>搜刮磁力链接</ListItemText></MenuItem>
                            <MenuItem onClick={() => handleBatchAction('merge')} disabled={selectedIds.length < 2 || isMergingUnifiedVideos}><ListItemIcon><MergeIcon fontSize="small"
                                                                                                                                                                      color="warning"/></ListItemIcon><ListItemText>一键聚合重复视频</ListItemText></MenuItem>
                            <Divider/>
                            <MenuItem onClick={() => handleBatchAction('delete')}><ListItemIcon><DeleteIcon
                                fontSize="small" color="error"/></ListItemIcon><ListItemText
                                sx={{color: 'error.main'}}>批量删除聚合视频</ListItemText></MenuItem>
                        </Menu>
                    </Box>
                )}
            </Box>

            <FilterPanel fields={filterFields} onSearch={handleSearch} onReset={handleReset}/>

            <SmartTable
                columns={columns} data={data?.content || []} total={getTotalElements(data)}
                loading={isLoading || isFetching}
                page={page} pageSize={pageSize} onPageChange={setPage} onPageSizeChange={setPageSize}
                selectionModel={selectedIds} onSelectionChange={setSelectedIds}
            />

            {isFormOpen && (
                <UnifiedVideoForm open={isFormOpen} onClose={() => setIsFormOpen(false)} onSubmit={handleFormSubmit}
                                  unifiedVideo={editingItem}/>
            )}

            {/* 行级菜单 */}
            <Menu anchorEl={rowMenuAnchor} open={Boolean(rowMenuAnchor)} onClose={handleRowMenuClose}>
                <MenuItem onClick={() => handleRecalculate(menuRow?.id)}><ListItemIcon><AutoFixHighIcon fontSize="small"
                                                                                                      color="primary"/></ListItemIcon><ListItemText>重新计算属性</ListItemText></MenuItem>
                <MenuItem onClick={() => handleScrapeMagnets(menuRow)} disabled={isScrapingMagnets}><ListItemIcon><MagnetIcon fontSize="small"
                                                                                                                                   color="secondary"/></ListItemIcon><ListItemText>搜刮磁力链接</ListItemText></MenuItem>
                <Divider/><MenuItem onClick={() => {
                handleDelete(menuRow?.id);
                handleRowMenuClose();
            }}><ListItemIcon><DeleteIcon fontSize="small" color="error"/></ListItemIcon><ListItemText
                sx={{color: 'error.main'}}>删除此聚合记录</ListItemText></MenuItem>
            </Menu>
        </Box>
    );
}

export default UnifiedVideoList;
