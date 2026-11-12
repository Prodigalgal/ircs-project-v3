// File: src/features/members/MemberList.jsx
import React, { useState, useMemo, useCallback } from 'react';
import { Box, Chip, IconButton, Stack, Tooltip } from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import GppBadIcon from '@mui/icons-material/GppBad';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import EditIcon from '@mui/icons-material/Edit';

import SmartTable from '../../components/common/SmartTable';
import ListHeader from '../../components/common/ListHeader';
import FilterPanel from '../../components/common/FilterPanel';
import MemberFormDialog from './MemberFormDialog'; // Import Form
import { useGetMembersQuery, useUpdateMemberStatusMutation, useDeleteMemberMutation } from './membersApi';
import { getTotalElements } from '../../utils/paginationUtils';

const STATUS_CONFIG = {
    ACTIVE: { label: '正常', color: 'success', icon: <VerifiedUserIcon fontSize="small" /> },
    BANNED: { label: '已封禁', color: 'error', icon: <GppBadIcon fontSize="small" /> },
    PENDING_VERIFICATION: { label: '待验证', color: 'warning', icon: <HourglassEmptyIcon fontSize="small" /> }
};

function MemberList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});

    // Edit Dialog State
    const [editingMember, setEditingMember] = useState(null);
    const [isFormOpen, setIsFormOpen] = useState(false);

    const { data, isLoading, isFetching, refetch } = useGetMembersQuery({
        page,
        size: pageSize,
        sort: 'createdAt,desc',
        ...filters
    });

    const [updateStatus] = useUpdateMemberStatusMutation();
    const [deleteMember] = useDeleteMemberMutation();

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const handleEdit = useCallback((row) => {
        setEditingMember(row);
        setIsFormOpen(true);
    }, []);

    const handleBanToggle = useCallback(async (row) => {
        const newStatus = row.status === 'BANNED' ? 'ACTIVE' : 'BANNED';
        const action = newStatus === 'ACTIVE' ? '解封' : '封禁';
        if (window.confirm(`确定要${action}用户 ${row.email} 吗？`)) {
            try {
                await updateStatus({ id: row.id, status: newStatus }).unwrap();
            } catch (e) {
                alert('操作失败: ' + (e.data?.message || e.message));
            }
        }
    }, [updateStatus]);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要彻底删除此用户吗？此操作不可恢复。')) {
            try {
                await deleteMember(id).unwrap();
            } catch {
                alert('删除失败');
            }
        }
    }, [deleteMember]);

    const columns = useMemo(() => [
        {
            field: 'avatarUrl',
            headerName: '头像',
            width: 70,
            sortable: false,
            renderCell: ({ value }) => (
                <Box
                    component="img"
                    src={value}
                    alt="avatar"
                    sx={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover' }}
                    onError={(event) => { event.currentTarget.src = 'https://via.placeholder.com/32'; }}
                />
            )
        },
        { field: 'nickname', headerName: '昵称', width: 150 },
        { field: 'email', headerName: '邮箱', width: 250 },
        {
            field: 'level',
            headerName: '等级',
            width: 80,
            align: 'center',
            renderCell: ({ value }) => (
                <Chip label={`Lv.${value}`} size="small" variant="outlined" color="primary" sx={{ fontWeight: 'bold' }} />
            )
        },
        {
            field: 'points',
            headerName: '积分',
            width: 80,
            align: 'right',
            renderCell: ({ value }) => (
                <Box sx={{ fontFamily: 'monospace', fontWeight: '500', color: 'warning.main' }}>
                    {value?.toLocaleString()}
                </Box>
            )
        },
        {
            field: 'status',
            headerName: '状态',
            width: 120,
            renderCell: ({ value }) => {
                const conf = STATUS_CONFIG[value] || { label: value, color: 'default' };
                return (
                    <Chip
                        icon={conf.icon}
                        label={conf.label}
                        color={conf.color}
                        size="small"
                        variant={value === 'ACTIVE' ? 'filled' : 'outlined'}
                    />
                );
            }
        },
        {
            field: 'createdAt',
            headerName: '注册时间',
            width: 180,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
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
                    <Tooltip title="编辑详情">
                        <IconButton size="small" onClick={() => handleEdit(row)}>
                            <EditIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    {row.status === 'BANNED' ? (
                        <Tooltip title="解封">
                            <IconButton size="small" color="success" onClick={() => handleBanToggle(row)}>
                                <CheckCircleIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    ) : (
                        <Tooltip title="封禁">
                            <IconButton size="small" color="warning" onClick={() => handleBanToggle(row)}>
                                <BlockIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    )}
                    <Tooltip title="删除">
                        <IconButton size="small" color="error" onClick={() => handleDelete(row.id)}>
                            <DeleteIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Stack>
            )
        }
    ], [handleBanToggle, handleDelete, handleEdit]);

    const filterFields = [
        { name: 'keyword', label: '搜索邮箱/昵称', type: 'text' },
        {
            name: 'status',
            label: '状态',
            type: 'select',
            options: [
                { value: 'ACTIVE', label: '正常' },
                { value: 'BANNED', label: '已封禁' },
                { value: 'PENDING_VERIFICATION', label: '待验证' }
            ]
        },
        {
            name: 'adultContentAllowed',
            label: '成人类目权限',
            type: 'boolean'
        },
        { name: 'minPoints', label: '最低积分', type: 'number', placeholder: '>= 0' },
        { name: 'maxPoints', label: '最高积分', type: 'number', placeholder: '<= 9999' }
    ];

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="会员管理"
                subtitle="管理前台注册用户，处理违规账号"
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

            <MemberFormDialog
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                member={editingMember}
            />
        </Box>
    );
}

export default MemberList;
