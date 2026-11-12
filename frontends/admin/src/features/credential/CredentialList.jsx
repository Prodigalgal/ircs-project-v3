// File: ircs-frontend/src/features/credential/CredentialList.jsx
import React, { useCallback, useState, useMemo } from 'react';
import {
    Box, Typography, Tabs, Tab, IconButton, Stack, Chip, Button, Tooltip, Paper
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SyncIcon from '@mui/icons-material/Sync';
import KeyIcon from '@mui/icons-material/Key';
import MailIcon from '@mui/icons-material/Mail';
import MovieFilterIcon from '@mui/icons-material/MovieFilter';
import StorageIcon from '@mui/icons-material/Storage';
import PsychologyIcon from '@mui/icons-material/Psychology';
import SpeedIcon from '@mui/icons-material/Speed';

import ListHeader from '../../components/common/ListHeader.jsx';
import SmartTable from '../../components/common/SmartTable.jsx';
import CredentialFormDialog from './components/CredentialFormDialog.jsx';
import {
    useGetCredentialsQuery,
    useCreateCredentialMutation,
    useUpdateCredentialMutation,
    useDeleteCredentialMutation,
    useRefreshCredentialPoolMutation
} from './credentialsApi.js';

const PROVIDERS = [
    { value: 'OPENAI', label: 'OpenAI Compatible (LLM)', icon: <PsychologyIcon /> },
    { value: 'TMDB', label: 'TMDB', icon: <MovieFilterIcon /> },
    { value: 'MAIL', label: 'Mail (SMTP)', icon: <MailIcon /> },
    { value: 'R2', label: 'Cloudflare R2', icon: <StorageIcon /> },
];

function CredentialList() {
    const [tabValue, setTabValue] = useState('OPENAI');
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);

    // Queries (Add Polling for Real-time metrics)
    const { data: credentials = [], isLoading, refetch } = useGetCredentialsQuery(tabValue, {
        pollingInterval: 3000 // 3秒刷新一次
    });

    // Mutations
    const [createCredential] = useCreateCredentialMutation();
    const [updateCredential] = useUpdateCredentialMutation();
    const [deleteCredential] = useDeleteCredentialMutation();
    const [refreshPool] = useRefreshCredentialPoolMutation();

    const handleTabChange = (_event, newValue) => {
        setTabValue(newValue);
    };

    const handleNew = () => {
        setEditingItem(null);
        setIsFormOpen(true);
    };

    const handleEdit = (item) => {
        setEditingItem(item);
        setIsFormOpen(true);
    };

    const handleDelete = useCallback(async (id) => {
        if (window.confirm('确定要删除此凭证吗？')) {
            try {
                await deleteCredential(id).unwrap();
            } catch (e) {
                alert("删除失败: " + e.message);
            }
        }
    }, [deleteCredential]);

    const handleFormSubmit = async (data) => {
        try {
            if (data.id) {
                await updateCredential({ id: data.id, ...data }).unwrap();
            } else {
                await createCredential(data).unwrap();
            }
            setIsFormOpen(false);
        } catch (e) {
            alert("保存失败: " + (e.data?.message || e.message));
        }
    };

    const handleRefreshPool = async () => {
        try {
            await refreshPool().unwrap();
            alert("凭证池已刷新，所有客户端已重建。");
        } catch {
            alert("刷新失败");
        }
    };

    const columns = useMemo(() => {
        const baseColumns = [
            { field: 'name', headerName: '别名', width: 200, renderCell: ({value}) => <Typography sx={{
                fontWeight: "bold"
            }}>{value}</Typography> },
        ];

        if (tabValue === 'MAIL') {
            baseColumns.push({
                field: 'mailEndpoint',
                headerName: '邮箱 / SMTP',
                width: 260,
                renderCell: ({ row }) => {
                    const payload = row.payload || {};
                    const host = payload.smtp_host || '未配置SMTP';
                    const port = payload.smtp_port || '';
                    return (
                        <Box>
                            <Typography variant="body2" sx={{
                                fontWeight: "600"
                            }}>{payload.username || '未配置邮箱'}</Typography>
                            <Typography variant="caption" sx={{
                                color: "text.secondary"
                            }}>
                                {port ? `${host}:${port}` : host}
                            </Typography>
                        </Box>
                    );
                }
            });
        }

        return [
        ...baseColumns,
            {
                field: 'priority', headerName: '优先级', width: 80, align: 'center',
                renderCell: ({value}) => <Chip label={value} size="small" color={value > 0 ? "primary" : "default"} variant="outlined" />
            },
        {
            field: 'enabled', headerName: '状态', width: 100,
            renderCell: ({value}) => <Chip label={value ? "启用" : "禁用"} color={value ? "success" : "error"} size="small" />
        },
        {
            field: 'realtime', headerName: '实时速率', width: 160,
            renderCell: ({ row }) => (
                <Stack
                    direction="row"
                    spacing={1}
                    sx={{
                        alignItems: "center",
                        color: 'info.main'
                    }}>
                    <SpeedIcon fontSize="small" />
                    <Box>
                        <Typography
                            variant="caption"
                            sx={{
                                display: "block",
                                fontWeight: "bold"
                            }}>
                            {row.currentTps || 0} TPS
                        </Typography>
                        <Typography
                            variant="caption"
                            sx={{
                                display: "block",
                                color: "text.secondary"
                            }}>
                            {row.currentTpm || 0} TPM
                        </Typography>
                    </Box>
                </Stack>
            )
        },
        {
            field: 'usage', headerName: '消耗 (今日/当月)', width: 200,
            renderCell: ({row}) => (
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <Typography variant="body2">{row.currentDayUsage || 0} / {row.dayLimit === 0 ? '∞' : row.dayLimit}</Typography>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>|</Typography>
                    <Typography variant="body2">{row.currentMonthUsage || 0} / {row.monthLimit === 0 ? '∞' : row.monthLimit}</Typography>
                </Stack>
            )
        },
        { field: 'remark', headerName: '备注', width: 200, flex: 1 },
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
                    <Tooltip title="编辑">
                        <IconButton size="small" onClick={() => handleEdit(row)}>
                            <EditIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="删除">
                        <IconButton size="small" color="error" onClick={() => handleDelete(row.id)}>
                            <DeleteIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Stack>
            ),
        },
    ];
    }, [handleDelete, tabValue]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="凭证管理 (Credential Pool)"
                subtitle="集中管理所有第三方服务的 API Key。系统将根据优先级和配额策略自动轮询使用。"
                onAdd={handleNew}
                addLabel={`新建 ${tabValue} 凭证`}
                onRefresh={refetch}
                loading={isLoading} // Polling 时不显示 loading 遮罩，只在初始加载显示
                extraActions={
                    <Button
                        startIcon={<SyncIcon />}
                        variant="outlined"
                        color="secondary"
                        onClick={handleRefreshPool}
                    >
                        刷新连接池
                    </Button>
                }
            />

            <Paper sx={{ mb: 2 }}>
                <Tabs
                    value={tabValue}
                    onChange={handleTabChange}
                    indicatorColor="primary"
                    textColor="primary"
                    variant="fullWidth"
                >
                    {PROVIDERS.map(p => (
                        <Tab key={p.value} value={p.value} label={p.label} icon={p.icon} iconPosition="start" />
                    ))}
                </Tabs>
            </Paper>

            <SmartTable
                columns={columns}
                data={credentials}
                total={credentials.length}
                loading={isLoading && !credentials.length} // 避免 polling 闪烁
                page={0}
                pageSize={100}
                onPageChange={() => {}}
                onPageSizeChange={() => {}}
            />

            <CredentialFormDialog
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleFormSubmit}
                item={editingItem}
                provider={tabValue}
            />
        </Box>
    );
}

export default CredentialList;
