// File: ircs-frontend/src/features/settings/SourceDomainList.jsx
import React, { useState, useMemo, useCallback } from 'react';
import {
    Box,
    Typography,
    IconButton,
    TextField,
    Stack,
    Tooltip,
    Paper,
    Alert,
    Chip
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import SmartTable from '../../components/common/SmartTable.jsx';
import ListHeader from '../../components/common/ListHeader.jsx';
import FilterPanel from '../../components/common/FilterPanel.jsx';
import { useGetSourceDomainsQuery, useUpdateSourceDomainMutation } from './sourceDomainsApi.js';
import { useGetAllDataSourcesQuery } from '../data-sources/dataSourcesApi';
import { getTotalElements } from '../../utils/paginationUtils.js';

function SourceDomainList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});
    const [editingId, setEditingId] = useState(null);
    const [editValue, setEditValue] = useState('');
    const [editRemark, setEditRemark] = useState('');

    const { data, isLoading, isFetching, refetch } = useGetSourceDomainsQuery({
        page,
        size: pageSize,
        sort: 'createdAt,desc',
        ...filters
    });

    const { data: dataSources = [] } = useGetAllDataSourcesQuery();

    const [updateDomain, { isLoading: isUpdating }] = useUpdateSourceDomainMutation();

    const handleEditClick = useCallback((row) => {
        setEditingId(row.id);
        setEditValue(row.domainValue);
        setEditRemark(row.remark || '');
    }, []);

    const handleCancelClick = useCallback(() => {
        setEditingId(null);
        setEditValue('');
        setEditRemark('');
    }, []);

    const handleSaveClick = useCallback(async () => {
        if (!editValue.trim()) return alert("域名不能为空");
        try {
            await updateDomain({
                id: editingId,
                domainValue: editValue.trim(),
                remark: editRemark
            }).unwrap();
            setEditingId(null);
        } catch (err) {
            console.error("Failed to update domain", err);
        }
    }, [editValue, editRemark, editingId, updateDomain]);

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const filterFields = useMemo(() => [
        { name: 'keyword', label: '搜索域名/URL', type: 'text', placeholder: '模糊搜索' },
        {
            name: 'dataSourceId',
            label: '数据源',
            type: 'select',
            options: dataSources.map(ds => ({ value: ds.id, label: ds.name }))
        }
    ], [dataSources]);

    const columns = useMemo(() => [
        { field: 'domainHash', headerName: 'Hash (Key)', width: 120, valueGetter: (v) => v ? v.substring(0, 8) + '...' : '-' },
        {
            field: 'domainValue',
            headerName: '真实域名 (Value)',
            width: 300,
            renderCell: ({ row }) => {
                if (editingId === row.id) {
                    return (
                        <TextField
                            size="small"
                            fullWidth
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            placeholder="https://..."
                            autoFocus
                        />
                    );
                }
                return (
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {row.domainValue}
                    </Typography>
                );
            }
        },
        {
            field: 'dataSourceName',
            headerName: '归属数据源 (Auto)',
            width: 200,
            renderCell: ({ value }) => (
                value ? (
                    <Typography variant="body2" color="primary" sx={{
                        fontWeight: "500"
                    }}>
                        {value}
                    </Typography>
                ) : (
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.disabled",
                            fontStyle: 'italic'
                        }}>
                        孤立/手动
                    </Typography>
                )
            )
        },
        {
            field: 'adultRestricted',
            headerName: '成人来源',
            width: 110,
            renderCell: ({ value }) => value
                ? <Chip size="small" color="warning" label="成人" />
                : <Chip size="small" variant="outlined" label="普通" />
        },
        {
            field: 'remark',
            headerName: '备注',
            width: 200,
            renderCell: ({ row }) => {
                if (editingId === row.id) {
                    return (
                        <TextField
                            size="small"
                            fullWidth
                            value={editRemark}
                            onChange={(e) => setEditRemark(e.target.value)}
                        />
                    );
                }
                return row.remark || '-';
            }
        },
        {
            field: 'updatedAt',
            headerName: '更新时间',
            width: 170,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 120,
            align: 'right',
            renderCell: ({ row }) => {
                if (editingId === row.id) {
                    return (
                        <Stack direction="row" spacing={1} sx={{
                            justifyContent: "flex-end"
                        }}>
                            <IconButton size="small" color="primary" onClick={handleSaveClick} disabled={isUpdating}>
                                <SaveIcon />
                            </IconButton>
                            <IconButton size="small" color="error" onClick={handleCancelClick} disabled={isUpdating}>
                                <CancelIcon />
                            </IconButton>
                        </Stack>
                    );
                }
                return (
                    <Tooltip title="修改域名">
                        <IconButton size="small" onClick={() => handleEditClick(row)}>
                            <EditIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                );
            }
        }
    ], [editingId, editValue, editRemark, isUpdating, handleSaveClick, handleCancelClick, handleEditClick]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="播放源域名管理"
                subtitle="此处列出了采集过程中自动发现的所有域名。如果有关联数据源，说明该域名的流量将纳入对应数据源的限流策略中。"
                onRefresh={refetch}
                loading={isLoading || isFetching}
            />

            <FilterPanel
                fields={filterFields}
                onSearch={handleSearch}
                onReset={handleReset}
            />

            <Paper variant="outlined" sx={{ p: 0, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <SmartTable
                    columns={columns}
                    data={data?.content || []}
                    total={getTotalElements(data)}
                    loading={isLoading || isFetching}
                    page={page}
                    pageSize={pageSize}
                    onPageChange={setPage}
                    onPageSizeChange={setPageSize}
                    rowKey="id"
                />
            </Paper>
        </Box>
    );
}

export default SourceDomainList;
