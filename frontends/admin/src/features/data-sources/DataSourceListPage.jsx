// File: ircs-frontend/src/features/data-sources/DataSourceListPage.jsx
import React, { useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Chip, IconButton, Tooltip, Typography, Stack } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import { useDeleteDataSourceMutation, useGetDataSourcesQuery, useLazyGetDataSourceByIdQuery } from './dataSourcesApi';
import { initializeWizardForEdit, resetWizard } from "./wizard/dataSourceWizardSlice";
import DataSourceWizardPage from "./wizard/DataSourceWizardPage.jsx";
import SmartTable from '../../components/common/SmartTable';
import { useDispatch } from "react-redux";
import { getTotalElements } from '../../utils/paginationUtils';

function DataSourceListPage() {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const [isWizardOpen, setIsWizardOpen] = useState(false);

    const { data, isLoading } = useGetDataSourcesQuery({ page, size: pageSize });
    const [deleteDataSource] = useDeleteDataSourceMutation();
    const [triggerGetById, { isFetching: isFetchingDetails }] = useLazyGetDataSourceByIdQuery();

    const handleNewClick = useCallback(() => {
        dispatch(resetWizard());
        setIsWizardOpen(true);
    }, [dispatch]);

    const handleEditClick = useCallback(async (id) => {
        try {
            const dataSource = await triggerGetById(id).unwrap();
            dispatch(initializeWizardForEdit(dataSource));
            setIsWizardOpen(true);
        } catch (error) {
            console.error("Failed to load data source details:", error);
            alert("加载详情失败，请重试");
        }
    }, [triggerGetById, dispatch]);

    const handleDeleteClick = useCallback(async (id) => {
        if (window.confirm('确定要删除此数据源吗？此操作不可撤销。')) {
            await deleteDataSource(id).unwrap();
        }
    }, [deleteDataSource]);

    const handleCreateTaskClick = useCallback((dataSourceId, dataSourceName) => {
        navigate('/collection-tasks', {
            state: { new_task_for_ds_id: dataSourceId, new_task_for_ds_name: dataSourceName }
        });
    }, [navigate]);

    const columns = useMemo(() => [
        { field: 'id', headerName: 'ID', width: 250 },
        { field: 'name', headerName: '名称', width: 250 },
        { field: 'baseUrl', headerName: 'API 根地址', width: 350 },
        {
            field: 'adultRestricted',
            headerName: '成人限制',
            width: 120,
            renderCell: ({ value }) => value
                ? <Chip size="small" color="warning" label="成人" />
                : <Chip size="small" variant="outlined" label="普通" />
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 200,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={0} sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title="为此数据源创建任务">
                        <IconButton size="small" onClick={() => handleCreateTaskClick(row.id, row.name)} color="primary">
                            <PlaylistAddIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="编辑">
                        <IconButton size="small" onClick={() => handleEditClick(row.id)} disabled={isFetchingDetails}>
                            <EditIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="删除">
                        <IconButton size="small" onClick={() => handleDeleteClick(row.id)} color="error">
                            <DeleteIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Stack>
            ),
        },
    ], [handleCreateTaskClick, handleEditClick, handleDeleteClick, isFetchingDetails]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant="h5">数据源管理</Typography>
                <Button variant="contained" startIcon={<AddIcon />} onClick={handleNewClick}>新建数据源</Button>
            </Box>

            <SmartTable
                columns={columns}
                data={data?.content || []}
                total={getTotalElements(data)}
                loading={isLoading}
                page={page}
                pageSize={pageSize}
                onPageChange={setPage}
                onPageSizeChange={setPageSize}
            />

            {isWizardOpen && (
                <DataSourceWizardPage
                    open={isWizardOpen}
                    onClose={() => setIsWizardOpen(false)}
                />
            )}
        </Box>
    );
}

export default DataSourceListPage;
