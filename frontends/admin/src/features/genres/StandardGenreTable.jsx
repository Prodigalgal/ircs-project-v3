// File: ircs-frontend/src/features/genres/StandardGenreTable.jsx
import React, { useState, useCallback, useMemo } from 'react';
import {
    Box, Button, Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Stack, IconButton, Tooltip
} from '@mui/material';
import MovieIcon from '@mui/icons-material/Movie';
import { useGetGenresPageQuery, useCreateGenreMutation } from './genresApi';
import SmartTable from '../../components/common/SmartTable';
import ListHeader from '../../components/common/ListHeader';
import FilterPanel from '../../components/common/FilterPanel';
import GenreVideoDialog from './components/GenreVideoDialog';
import { getTotalElements } from '../../utils/paginationUtils';

function GenreForm({ open, onClose, onSubmit }) {
    const [name, setName] = useState('');

    const handleSubmit = () => {
        if (!name.trim()) return alert("名称不能为空");
        onSubmit({ name });
        setName('');
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle>新建标准题材</DialogTitle>
            <DialogContent dividers>
                <TextField
                    autoFocus
                    margin="dense"
                    label="题材名称"
                    fullWidth
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="如: 科幻"
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={handleSubmit}>创建</Button>
            </DialogActions>
        </Dialog>
    );
}

function StandardGenreTable() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});
    const [isFormOpen, setIsFormOpen] = useState(false);

    const [videoDialogState, setVideoDialogState] = useState({ open: false, genre: null });

    const { data, isLoading, isFetching, refetch } = useGetGenresPageQuery({
        page,
        size: pageSize,
        sort: 'name,asc',
        ...filters
    });
    const [createGenre] = useCreateGenreMutation();

    const handleSubmit = async (data) => {
        await createGenre(data).unwrap();
        setIsFormOpen(false);
    };

    const handleViewVideos = useCallback((row) => {
        setVideoDialogState({ open: true, genre: row });
    }, []);

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const filterFields = useMemo(() => [
        { name: 'name', label: '题材名称', type: 'text', placeholder: '支持模糊搜索' },
        { name: 'code', label: '题材编码', type: 'text', placeholder: '如: sci-fi / drama' }
    ], []);

    const columns = useMemo(() => [
        { field: 'name', headerName: '题材名称', width: 200 },
        { field: 'id', headerName: 'ID', width: 300, valueGetter: (v) => v || '-' },
        {
            field: 'actions',
            headerName: '操作',
            width: 150,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1} sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title="查看关联视频">
                        <IconButton size="small" onClick={() => handleViewVideos(row)} color="info">
                            <MovieIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Stack>
            ),
        },
    ], [handleViewVideos]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="标准题材库"
                onAdd={() => setIsFormOpen(true)}
                addLabel="新建标准题材"
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

            <GenreForm
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleSubmit}
            />

            <GenreVideoDialog
                open={videoDialogState.open}
                onClose={() => setVideoDialogState({ open: false, genre: null })}
                genre={videoDialogState.genre}
                type="STANDARD"
            />
        </Box>
    );
}

export default StandardGenreTable;
