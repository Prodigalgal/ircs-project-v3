// File: ircs-frontend/src/features/messages/MessageList.jsx
import React, { useState, useMemo, useCallback } from 'react';
import { Box, IconButton, Stack, Tooltip, Chip, Avatar, Typography, Switch, FormControlLabel } from '@mui/material';
import ReplyIcon from '@mui/icons-material/Reply';
import DeleteIcon from '@mui/icons-material/Delete';
import SmartTable from '../../components/common/SmartTable';
import ListHeader from '../../components/common/ListHeader';
import FilterPanel from '../../components/common/FilterPanel';
import ReplyDialog from './components/ReplyDialog';
import { useGetMessagesQuery, useReplyMessageMutation, useDeleteMessageMutation, useToggleMessageVisibilityMutation } from './messagesApi';
import { getTotalElements } from '../../utils/paginationUtils';

const STATUS_CONFIG = {
    PENDING: { label: '待回复', color: 'warning' },
    REPLIED: { label: '已回复', color: 'success' },
    CLOSED: { label: '已关闭', color: 'default' }
};

function MessageList() {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [filters, setFilters] = useState({});

    const [isReplyOpen, setIsReplyOpen] = useState(false);
    const [selectedMessage, setSelectedMessage] = useState(null);

    const { data, isLoading, isFetching, refetch } = useGetMessagesQuery({
        page,
        size: pageSize,
        sort: 'createdAt,desc',
        ...filters
    });

    const [replyMessage] = useReplyMessageMutation();
    const [toggleVisibility] = useToggleMessageVisibilityMutation();
    const [deleteMessage] = useDeleteMessageMutation();

    const handleSearch = useCallback((newFilters) => {
        setFilters(newFilters);
        setPage(0);
    }, []);

    const handleReset = useCallback(() => {
        setFilters({});
        setPage(0);
    }, []);

    const handleReplyClick = useCallback((row) => {
        setSelectedMessage(row);
        setIsReplyOpen(true);
    }, []);

    const handleReplySubmit = useCallback(async (id, replyContent) => {
        try {
            await replyMessage({ id, reply: replyContent }).unwrap();
            setIsReplyOpen(false);
        } catch (e) {
            alert('回复失败: ' + (e.data?.message || e.message));
        }
    }, [replyMessage]);

    const handleVisibilityChange = useCallback(async (id, currentStatus) => {
        try {
            await toggleVisibility({ id, isPublic: !currentStatus }).unwrap();
        } catch (e) {
            console.error("Visibility toggle failed", e);
        }
    }, [toggleVisibility]);

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要删除此留言吗？')) {
            await deleteMessage(id).unwrap();
        }
    }, [deleteMessage]);

    const columns = useMemo(() => [
        {
            field: 'member',
            headerName: '用户',
            width: 200,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <Avatar src={row.memberAvatarUrl} sx={{ width: 24, height: 24 }} />
                    <Box>
                        <Typography variant="body2">{row.memberNickname}</Typography>
                        <Typography variant="caption" sx={{
                            color: "text.secondary"
                        }}>{row.memberEmail}</Typography>
                    </Box>
                </Stack>
            )
        },
        {
            field: 'content',
            headerName: '留言内容',
            width: 300,
            renderCell: ({ value }) => (
                <Tooltip title={value}>
                    <Typography variant="body2" noWrap>{value}</Typography>
                </Tooltip>
            )
        },
        {
            field: 'reply',
            headerName: '回复',
            width: 300,
            renderCell: ({ value }) => (
                value ? (
                    <Tooltip title={value}>
                        <Typography variant="body2" noWrap color="primary">{value}</Typography>
                    </Tooltip>
                ) : '-'
            )
        },
        {
            field: 'status',
            headerName: '状态',
            width: 100,
            renderCell: ({ value }) => {
                const conf = STATUS_CONFIG[value] || { label: value, color: 'default' };
                return <Chip label={conf.label} color={conf.color} size="small" variant="outlined" />;
            }
        },
        {
            field: 'public',
            headerName: '上墙',
            width: 100,
            align: 'center',
            renderCell: ({ row }) => (
                <Switch
                    size="small"
                    checked={row.public}
                    onChange={() => handleVisibilityChange(row.id, row.public)}
                    color="success"
                />
            )
        },
        {
            field: 'createdAt',
            headerName: '提交时间',
            width: 180,
            valueGetter: (v) => v ? new Date(v).toLocaleString() : '-'
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 120,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1} sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title="回复">
                        <IconButton size="small" color="primary" onClick={() => handleReplyClick(row)}>
                            <ReplyIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="删除">
                        <IconButton size="small" color="error" onClick={() => handleDelete(row.id)}>
                            <DeleteIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Stack>
            )
        }
    ], [handleReplyClick, handleDelete, handleVisibilityChange]);

    const filterFields = [
        { name: 'keyword', label: '搜索内容/用户', type: 'text' },
        {
            name: 'status',
            label: '状态',
            type: 'select',
            options: [
                { value: 'PENDING', label: '待回复' },
                { value: 'REPLIED', label: '已回复' },
                { value: 'CLOSED', label: '已关闭' }
            ]
        },
        {
            name: 'public',
            label: '是否上墙',
            type: 'boolean'
        }
    ];

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="用户留言反馈"
                subtitle="查看并回复用户的反馈与建议，审核留言上墙"
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

            <ReplyDialog
                open={isReplyOpen}
                onClose={() => setIsReplyOpen(false)}
                message={selectedMessage}
                onSubmit={handleReplySubmit}
            />
        </Box>
    );
}

export default MessageList;
