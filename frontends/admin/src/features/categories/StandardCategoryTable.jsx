// File: ircs-frontend/src/features/categories/StandardCategoryTable.jsx
import React, { useState, useCallback, useMemo } from 'react';
import { Box, IconButton, Stack, Typography } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import {
    useGetCategoriesQuery,
    useCreateCategoryMutation,
    useUpdateCategoryMutation,
    useDeleteCategoryMutation
} from './categoriesApi';
import CategoryForm from './CategoryForm';
import SmartTable from '../../components/common/SmartTable';
import FilterPanel from '../../components/common/FilterPanel';
import ListHeader from '../../components/common/ListHeader';
import { getTotalElements } from '../../utils/paginationUtils';

function StandardCategoryTable() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);
    const [filters, setFilters] = useState({});

    const { data, isLoading, isFetching, refetch } = useGetCategoriesQuery({
        page,
        size: pageSize,
        sort: 'createdAt,desc',
        ...filters
    });

    const [createCategory] = useCreateCategoryMutation();
    const [updateCategory] = useUpdateCategoryMutation();
    const [deleteCategory] = useDeleteCategoryMutation();

    const handleNew = useCallback(() => { setEditingItem(null); setIsFormOpen(true); }, []);
    const handleEdit = useCallback((item) => { setEditingItem(item); setIsFormOpen(true); }, []);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要删除吗？')) {
            await deleteCategory(id).unwrap();
        }
    }, [deleteCategory]);

    const handleFormSubmit = useCallback(async (formData) => {
        try {
            if (formData.id) {
                await updateCategory(formData).unwrap();
            } else {
                await createCategory(formData).unwrap();
            }
            setIsFormOpen(false);
        } catch (e) {
            alert("操作失败: " + (e.data?.message || e.message));
        }
    }, [createCategory, updateCategory]);

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const columns = useMemo(() => [
        { field: 'name', headerName: '分类名称', width: 200 },
        {
            field: 'slug',
            headerName: '标识符 (Slug)',
            width: 200,
            renderCell: ({ value }) => (
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
                    {value}
                </Typography>
            )
        },
        { field: 'createdAt', headerName: '创建时间', width: 200, valueGetter: (val) => val ? new Date(val).toLocaleString() : '-' },
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
                    <IconButton size="small" onClick={() => handleEdit(row)}><EditIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => handleDelete(row.id)} color="error"><DeleteIcon fontSize="small" /></IconButton>
                </Stack>
            ),
        },
    ], [handleEdit, handleDelete]);

    const filterFields = useMemo(() => [
        { name: 'name', label: '分类名称', type: 'text', placeholder: '支持模糊搜索' },
        { name: 'slug', label: '分类编码', type: 'text', placeholder: '如: movie / series' }
    ], []);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="标准分类"
                onAdd={handleNew}
                addLabel="新建标准分类"
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
            <CategoryForm open={isFormOpen} onClose={() => setIsFormOpen(false)} onSubmit={handleFormSubmit} item={editingItem} />
        </Box>
    );
}

export default StandardCategoryTable;
