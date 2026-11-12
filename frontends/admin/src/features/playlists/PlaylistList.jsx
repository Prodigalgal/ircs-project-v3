import React, { useState, useCallback, useMemo } from 'react';
import {
    Box, IconButton, Stack, Button
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import { useGetPlaylistsQuery, useDeletePlaylistMutation, useUpdatePlaylistMutation, useCreatePlaylistMutation } from './playlistsApi';
import { useLazyGetVideosQuery } from '../videos/videosApi';
import SmartTable from '../../components/common/SmartTable';
import FilterPanel from '../../components/common/FilterPanel';
import ListHeader from '../../components/common/ListHeader';
import { getTotalElements } from '../../utils/paginationUtils';
import PlaylistManagerDialog from './components/PlaylistManagerDialog';

import { debounce } from '@mui/material/utils';
import { TextField, Autocomplete, CircularProgress, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';

function VideoSearchSelect({ value, onChange, disabled }) {
    const [open, setOpen] = useState(false);
    const [options, setOptions] = useState([]);
    const [inputValue, setInputValue] = useState('');

    const [triggerSearch, { data, isFetching }] = useLazyGetVideosQuery();

    const fetchDebounced = useMemo(() => debounce((input) => {
        triggerSearch({ title: input, page: 0, size: 20 });
    }, 400), [triggerSearch]);

    React.useEffect(() => {
        if (open) fetchDebounced(inputValue);
    }, [inputValue, open, fetchDebounced]);

    React.useEffect(() => {
        if (data?.content) setOptions(data.content);
    }, [data]);

    return (
        <Autocomplete
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            onInputChange={(e, val) => setInputValue(val)}
            isOptionEqualToValue={(opt, val) => opt.id === val.id}
            getOptionLabel={(opt) => `${opt.title} (${opt.year || '-'})`}
            options={options}
            loading={isFetching}
            value={value}
            onChange={(e, val) => onChange(val)}
            disabled={disabled}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label="所属视频 (必选)"
                    margin="normal"
                    fullWidth
                    required
                    slotProps={{
                        ...params.slotProps,

                        input: {
                            ...params.slotProps.input,
                            endAdornment: (
                                <React.Fragment>
                                    {isFetching ? <CircularProgress color="inherit" size={20} /> : null}
                                    {params.slotProps.input.endAdornment}
                                </React.Fragment>
                            )
                        }
                    }}
                />
            )}
        />
    );
}

// 简单的创建弹窗，只选视频和名称，创建后跳转到编辑
function SimpleCreateDialog({ open, onClose, onSubmit }) {
    const [name, setName] = useState('');
    const [video, setVideo] = useState(null);

    const handleSubmit = () => {
        if (!name || !video) return alert('请补全信息');
        onSubmit({ name, videoId: video.id });
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle>新建播放列表</DialogTitle>
            <DialogContent dividers>
                <VideoSearchSelect value={video} onChange={setVideo} />
                <TextField
                    label="列表名称" fullWidth margin="normal"
                    value={name} onChange={e => setName(e.target.value)}
                    placeholder="例如: 蓝光"
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={handleSubmit}>创建</Button>
            </DialogActions>
        </Dialog>
    );
}

function PlaylistList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});

    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [editingPlaylist, setEditingPlaylist] = useState(null);

    const { data, isLoading, isFetching, refetch } = useGetPlaylistsQuery({
        page,
        size: pageSize,
        sort: 'updatedAt,desc',
        ...filters
    });

    const [deletePlaylist] = useDeletePlaylistMutation();
    const [updatePlaylist] = useUpdatePlaylistMutation();
    const [createPlaylist] = useCreatePlaylistMutation();

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定删除？')) await deletePlaylist(id).unwrap();
    }, [deletePlaylist]);

    const handleEdit = useCallback((item) => {
        setEditingPlaylist(item);
    }, []);

    const handleUpdateSubmit = useCallback(async (data) => {
        await updatePlaylist(data).unwrap();
        setEditingPlaylist(null);
    }, [updatePlaylist]);

    const handleCreateSubmit = useCallback(async (data) => {
        await createPlaylist(data).unwrap();
        setIsCreateOpen(false);
        refetch();
    }, [createPlaylist, refetch]);

    const columns = useMemo(() => [
        { field: 'id', headerName: 'ID', width: 250 },
        { field: 'name', headerName: '列表名称', width: 150 },
        { field: 'videoTitle', headerName: '所属视频', width: 250, valueGetter: (v) => v || '-' },
        {
            field: 'episodes', headerName: '剧集数', width: 100,
            valueGetter: (v) => v ? v.length : 0
        },
        { field: 'updatedAt', headerName: '更新时间', width: 180, valueGetter: (v) => v ? new Date(v).toLocaleString() : '-' },
        {
            field: 'actions', headerName: '操作', width: 150, align: 'right', sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1} sx={{
                    justifyContent: "flex-end"
                }}>
                    <IconButton size="small" onClick={() => handleEdit(row)} color="primary">
                        <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" onClick={() => handleDelete(row.id)} color="error">
                        <DeleteIcon fontSize="small" />
                    </IconButton>
                </Stack>
            ),
        },
    ], [handleEdit, handleDelete]);

    const filterFields = [
        { name: 'name', label: '列表名称', type: 'text', placeholder: '搜索播放列表名 (如: M3U8)' },
        { name: 'videoTitle', label: '所属视频标题', type: 'text', placeholder: '搜索关联的视频标题' }
    ];

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="播放列表管理"
                onAdd={() => setIsCreateOpen(true)}
                addLabel="新建列表"
                onRefresh={refetch}
                loading={isLoading || isFetching}
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
            />

            <SimpleCreateDialog
                open={isCreateOpen}
                onClose={() => setIsCreateOpen(false)}
                onSubmit={handleCreateSubmit}
            />

            <PlaylistManagerDialog
                open={!!editingPlaylist}
                onClose={() => setEditingPlaylist(null)}
                onSubmit={handleUpdateSubmit}
                playlist={editingPlaylist}
            />
        </Box>
    );
}
export default PlaylistList;
