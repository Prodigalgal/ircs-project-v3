// File: ircs-frontend/src/features/resolvers/ResolverList.jsx
import React, { useState, useMemo, useCallback } from 'react';
import { Box, IconButton, Stack, Chip, Tooltip } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SmartTable from '../../components/common/SmartTable';
import ListHeader from '../../components/common/ListHeader';
import ResolverFormDialog from './components/ResolverFormDialog';
import { useGetResolversQuery, useCreateResolverMutation, useUpdateResolverMutation, useDeleteResolverMutation } from './resolversApi';
import { getTotalElements } from '../../utils/paginationUtils';

function ResolverList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);

    const { data, isLoading, isFetching, refetch } = useGetResolversQuery({
        page,
        size: pageSize,
        sort: 'updatedAt,desc'
    });

    const [createResolver] = useCreateResolverMutation();
    const [updateResolver] = useUpdateResolverMutation();
    const [deleteResolver] = useDeleteResolverMutation();

    const handleNew = useCallback(() => {
        setEditingItem(null);
        setIsFormOpen(true);
    }, []);

    const handleEdit = useCallback((item) => {
        setEditingItem(item);
        setIsFormOpen(true);
    }, []);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要删除此解析源吗？')) {
            await deleteResolver(id).unwrap();
        }
    }, [deleteResolver]);

    const handleSubmit = useCallback(async (formData) => {
        if (formData.id) {
            await updateResolver(formData).unwrap();
        } else {
            await createResolver(formData).unwrap();
        }
        setIsFormOpen(false);
    }, [createResolver, updateResolver]);

    const columns = useMemo(() => [
        { field: 'name', headerName: '解析商名称', width: 200 },
        {
            field: 'isActive',
            headerName: '状态',
            width: 100,
            renderCell: ({ value }) => (
                <Chip
                    label={value ? '启用' : '禁用'}
                    color={value ? 'success' : 'default'}
                    size="small"
                    variant={value ? 'filled' : 'outlined'}
                />
            )
        },
        {
            field: 'lines',
            headerName: '线路配置',
            width: 400,
            renderCell: ({ value }) => {
                if (!value || value.length === 0) return '-';
                return (
                    <Stack direction="row" spacing={1}>
                        {value.map((line, idx) => (
                            <Tooltip key={idx} title={line.url}>
                                <Chip label={line.name} size="small" variant="outlined" />
                            </Tooltip>
                        ))}
                    </Stack>
                );
            }
        },
        { field: 'remark', headerName: '备注', width: 200 },
        {
            field: 'updatedAt',
            headerName: '更新时间',
            width: 180,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 150,
            align: 'right',
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1} sx={{
                    justifyContent: "flex-end"
                }}>
                    <IconButton size="small" onClick={() => handleEdit(row)}>
                        <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" color="error" onClick={() => handleDelete(row.id)}>
                        <DeleteIcon fontSize="small" />
                    </IconButton>
                </Stack>
            )
        }
    ], [handleDelete, handleEdit]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="解析源管理"
                subtitle="配置用于播放非直链视频的第三方解析接口。"
                onAdd={handleNew}
                addLabel="新建解析源"
                onRefresh={refetch}
                loading={isLoading || isFetching}
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

            <ResolverFormDialog
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleSubmit}
                item={editingItem}
            />
        </Box>
    );
}

export default ResolverList;