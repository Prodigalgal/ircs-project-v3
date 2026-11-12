import React, { useCallback, useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    Stack,
    TextField,
    Tooltip,
    Typography
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DeleteIcon from '@mui/icons-material/Delete';
import VpnKeyIcon from '@mui/icons-material/VpnKey';

import ListHeader from '../../components/common/ListHeader.jsx';
import SmartTable from '../../components/common/SmartTable.jsx';
import {
    useCreateApiTokenMutation,
    useGetApiTokensQuery,
    useRevokeApiTokenMutation
} from './apiTokensApi.js';

const STATUS_CONFIG = {
    ACTIVE: { label: '启用', color: 'success' },
    REVOKED: { label: '已撤销', color: 'default' },
    EXPIRED: { label: '已过期', color: 'warning' },
};

const formatDateTime = (value) => {
    if (!value) {
        return '-';
    }
    return new Date(value).toLocaleString();
};

function ApiTokenList() {
    const { data: tokens = [], isLoading, isFetching, refetch } = useGetApiTokensQuery();
    const [createApiToken, { isLoading: isCreating }] = useCreateApiTokenMutation();
    const [revokeApiToken, { isLoading: isRevoking }] = useRevokeApiTokenMutation();

    const [createOpen, setCreateOpen] = useState(false);
    const [tokenName, setTokenName] = useState('');
    const [createdToken, setCreatedToken] = useState(null);
    const [revokeTarget, setRevokeTarget] = useState(null);
    const [copyState, setCopyState] = useState('idle');

    const closeCreateDialog = useCallback(() => {
        setCreateOpen(false);
        setTokenName('');
    }, []);

    const handleCreate = useCallback(async () => {
        const name = tokenName.trim();
        if (!name) {
            return;
        }
        try {
            const result = await createApiToken({ name }).unwrap();
            setCreatedToken(result);
            closeCreateDialog();
        } catch (error) {
            alert('生成失败: ' + (error.data?.message || error.message));
        }
    }, [closeCreateDialog, createApiToken, tokenName]);

    const handleCopy = useCallback(async () => {
        if (!createdToken?.token) {
            return;
        }
        try {
            await navigator.clipboard.writeText(createdToken.token);
            setCopyState('copied');
            window.setTimeout(() => setCopyState('idle'), 1500);
        } catch {
            setCopyState('failed');
            window.setTimeout(() => setCopyState('idle'), 1500);
        }
    }, [createdToken]);

    const closeCreatedDialog = useCallback(() => {
        setCreatedToken(null);
        setCopyState('idle');
    }, []);

    const handleRevoke = useCallback(async () => {
        if (!revokeTarget) {
            return;
        }
        try {
            await revokeApiToken(revokeTarget.id).unwrap();
            setRevokeTarget(null);
        } catch (error) {
            alert('撤销失败: ' + (error.data?.message || error.message));
        }
    }, [revokeApiToken, revokeTarget]);

    const columns = useMemo(() => [
        {
            field: 'name',
            headerName: '名称',
            width: 220,
            renderCell: ({ value }) => (
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <VpnKeyIcon color="primary" fontSize="small" />
                    <Typography sx={{
                        fontWeight: "600"
                    }}>{value}</Typography>
                </Stack>
            )
        },
        {
            field: 'tokenPrefix',
            headerName: 'Prefix',
            width: 210,
            renderCell: ({ value }) => (
                <Typography sx={{ fontFamily: 'monospace' }}>{value}</Typography>
            )
        },
        {
            field: 'status',
            headerName: '状态',
            width: 100,
            renderCell: ({ value }) => {
                const config = STATUS_CONFIG[value] || { label: value, color: 'default' };
                return <Chip size="small" label={config.label} color={config.color} variant="outlined" />;
            }
        },
        {
            field: 'createdBy',
            headerName: '创建者',
            width: 180,
            renderCell: ({ value }) => value || '-'
        },
        {
            field: 'createdAt',
            headerName: '创建时间',
            width: 180,
            valueGetter: formatDateTime
        },
        {
            field: 'lastUsedAt',
            headerName: '最近使用',
            width: 180,
            valueGetter: formatDateTime
        },
        {
            field: 'revokedAt',
            headerName: '撤销时间',
            width: 180,
            valueGetter: formatDateTime
        },
        {
            field: 'actions',
            headerName: '操作',
            width: 100,
            align: 'right',
            sortable: false,
            renderCell: ({ row }) => (
                <Stack direction="row" spacing={1} sx={{
                    justifyContent: "flex-end"
                }}>
                    <Tooltip title={row.status === 'ACTIVE' ? '撤销 Token' : 'Token 已不可用'}>
                        <span>
                            <IconButton
                                size="small"
                                color="error"
                                disabled={row.status !== 'ACTIVE' || isRevoking}
                                onClick={() => setRevokeTarget(row)}
                            >
                                <DeleteIcon fontSize="small" />
                            </IconButton>
                        </span>
                    </Tooltip>
                </Stack>
            )
        }
    ], [isRevoking]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="API Token"
                subtitle="后台自动化访问令牌"
                onAdd={() => setCreateOpen(true)}
                addLabel="生成 Token"
                onRefresh={refetch}
                loading={isLoading || isFetching}
            />

            <SmartTable
                columns={columns}
                data={tokens}
                total={tokens.length}
                loading={isLoading || isFetching}
                page={0}
                pageSize={100}
                onPageChange={() => {}}
                onPageSizeChange={() => {}}
                noDataMessage="暂无 API Token"
            />

            <Dialog open={createOpen} onClose={closeCreateDialog} fullWidth maxWidth="sm">
                <DialogTitle>生成 API Token</DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        fullWidth
                        required
                        margin="dense"
                        label="名称"
                        value={tokenName}
                        onChange={(event) => setTokenName(event.target.value)}
                        onKeyDown={(event) => {
                            if (event.key === 'Enter') {
                                event.preventDefault();
                                handleCreate();
                            }
                        }}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={closeCreateDialog}>取消</Button>
                    <Button
                        variant="contained"
                        onClick={handleCreate}
                        disabled={isCreating || !tokenName.trim()}
                    >
                        生成
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={Boolean(createdToken)} onClose={closeCreatedDialog} fullWidth maxWidth="md">
                <DialogTitle>API Token 已生成</DialogTitle>
                <DialogContent>
                    <Stack spacing={2}>
                        <Alert severity="warning">Token 明文仅显示一次。</Alert>
                        <TextField
                            fullWidth
                            multiline
                            minRows={2}
                            value={createdToken?.token || ''}
                            slotProps={{
                                input: {
                                    readOnly: true,
                                    sx: { fontFamily: 'monospace' }
                                }
                            }}
                        />
                        {copyState === 'failed' && (
                            <Alert severity="error">复制失败，请手动选中内容。</Alert>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button
                        startIcon={<ContentCopyIcon />}
                        variant="outlined"
                        onClick={handleCopy}
                    >
                        {copyState === 'copied' ? '已复制' : '复制'}
                    </Button>
                    <Button variant="contained" onClick={closeCreatedDialog}>关闭</Button>
                </DialogActions>
            </Dialog>

            <Dialog open={Boolean(revokeTarget)} onClose={() => setRevokeTarget(null)} fullWidth maxWidth="xs">
                <DialogTitle>撤销 API Token</DialogTitle>
                <DialogContent>
                    <Typography>
                        确定撤销 “{revokeTarget?.name}” 吗？
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setRevokeTarget(null)}>取消</Button>
                    <Button color="error" variant="contained" onClick={handleRevoke} disabled={isRevoking}>
                        撤销
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

export default ApiTokenList;
