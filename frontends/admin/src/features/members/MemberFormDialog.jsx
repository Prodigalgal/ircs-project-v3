// File: src/features/members/MemberFormDialog.jsx
import React, { useState, useEffect } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, TextField, MenuItem, Divider, Typography,
    Box, Tabs, Tab, Avatar, Chip, TableContainer, Table, TableHead, TableRow, TableCell, TableBody, Paper, TablePagination
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { useUpdateMemberMutation, useGetMemberFavoritesQuery, useGetMemberHistoryQuery } from './membersApi';
import { getTotalElements } from '../../utils/paginationUtils';

const ROLES = [
    { value: 'MEMBER', label: '普通会员' },
    { value: 'ADMIN', label: '管理员' },
];

const STATUSES = [
    { value: 'PENDING_VERIFICATION', label: '待验证' },
    { value: 'ACTIVE', label: '正常' },
    { value: 'BANNED', label: '封禁' },
];

function InteractionTab({ memberId, type }) {
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(10);

    const queryHook = type === 'favorites' ? useGetMemberFavoritesQuery : useGetMemberHistoryQuery;
    const { data, isLoading } = queryHook({ id: memberId, page, size: rowsPerPage }, { skip: !memberId });

    const handleChangePage = (event, newPage) => setPage(newPage);
    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(parseInt(event.target.value, 10));
        setPage(0);
    };

    if (isLoading) return <Typography sx={{ p: 2 }}>加载中...</Typography>;

    if (!data || !data.content || data.content.length === 0) {
        return (
            <Typography
                sx={{
                    color: "text.secondary",
                    p: 2
                }}>暂无记录</Typography>
        );
    }

    return (
        <Box sx={{ width: '100%', overflow: 'hidden' }}>
            <TableContainer sx={{ maxHeight: 400 }}>
                <Table stickyHeader size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>封面</TableCell>
                            <TableCell>标题</TableCell>
                            {type === 'history' && <TableCell>进度</TableCell>}
                            <TableCell>更新时间</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.content.map((row) => (
                            <TableRow key={row.id}>
                                <TableCell>
                                    <Avatar variant="rounded" src={row.coverImageUrl} sx={{ width: 40, height: 60 }} />
                                </TableCell>
                                <TableCell>
                                    <Typography variant="body2" sx={{
                                        fontWeight: "bold"
                                    }}>{row.title}</Typography>
                                    {type === 'history' && <Typography variant="caption" sx={{
                                        color: "text.secondary"
                                    }}>{row.episodeName}</Typography>}
                                </TableCell>
                                {type === 'history' && (
                                    <TableCell>
                                        {Math.floor(row.progressSeconds / 60)}分 / {Math.floor(row.durationSeconds / 60)}分
                                    </TableCell>
                                )}
                                <TableCell>
                                    {row.lastWatchedAt ? new Date(row.lastWatchedAt).toLocaleString() : '-'}
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
            <TablePagination
                rowsPerPageOptions={[10, 20]}
                component="div"
                count={getTotalElements(data)}
                rowsPerPage={rowsPerPage}
                page={page}
                onPageChange={handleChangePage}
                onRowsPerPageChange={handleChangeRowsPerPage}
            />
        </Box>
    );
}

function MemberFormDialog({ open, onClose, member }) {
    const [tabIndex, setTabIndex] = useState(0);
    const [formData, setFormData] = useState({});
    const [updateMember, { isLoading }] = useUpdateMemberMutation();

    useEffect(() => {
        if (open && member) {
            setFormData({
                id: member.id,
                email: member.email || '',
                nickname: member.nickname || '',
                avatarUrl: member.avatarUrl || '',
                role: member.role || 'MEMBER',
                status: member.status || 'ACTIVE',
                experience: member.experience || 0,
                points: member.points || 0,
                checkInStreak: member.checkInStreak || 0,
                password: ''
            });
            setTabIndex(0);
        }
    }, [open, member]);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleNumberChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: parseInt(value) || 0 }));
    };

    const handleSubmit = async () => {
        try {
            const payload = { ...formData };
            if (!payload.password) delete payload.password;
            await updateMember(payload).unwrap();
            onClose();
        } catch {
            alert('更新失败');
        }
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>用户详情: {formData.nickname}</DialogTitle>

            <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
                <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)}>
                    <Tab label="基本信息" />
                    <Tab label="收藏夹" />
                    <Tab label="观看历史" />
                </Tabs>
            </Box>

            <DialogContent sx={{ p: 0 }}>
                {tabIndex === 0 && (
                    <Box sx={{ p: 3 }}>
                        <Grid container spacing={3}>
                            <Grid size={{ xs: 12 }}>
                                <Typography variant="subtitle2" color="primary">基础账户</Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                                <TextField label="电子邮箱" name="email" fullWidth value={formData.email || ''} onChange={handleChange} size="small" />
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField label="昵称" name="nickname" fullWidth value={formData.nickname || ''} onChange={handleChange} size="small" />
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField label="新密码 (留空不修改)" name="password" type="password" fullWidth value={formData.password || ''} onChange={handleChange} size="small" />
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                                <TextField label="头像 URL" name="avatarUrl" fullWidth value={formData.avatarUrl || ''} onChange={handleChange} size="small" />
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField select label="角色" name="role" fullWidth value={formData.role || 'MEMBER'} onChange={handleChange} size="small">
                                    {ROLES.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                                </TextField>
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField select label="账号状态" name="status" fullWidth value={formData.status || 'ACTIVE'} onChange={handleChange} size="small">
                                    {STATUSES.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                                </TextField>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                                <Divider />
                                <Typography variant="subtitle2" color="secondary" sx={{ mt: 1 }}>积分与等级</Typography>
                            </Grid>
                            <Grid size={{ xs: 4 }}>
                                <TextField label="经验值 (XP)" name="experience" type="number" fullWidth value={formData.experience} onChange={handleNumberChange} size="small" />
                            </Grid>
                            <Grid size={{ xs: 4 }}>
                                <TextField label="积分 (Points)" name="points" type="number" fullWidth value={formData.points} onChange={handleNumberChange} size="small" />
                            </Grid>
                            <Grid size={{ xs: 4 }}>
                                <TextField label="连续签到天数" name="checkInStreak" type="number" fullWidth value={formData.checkInStreak} onChange={handleNumberChange} size="small" />
                            </Grid>
                        </Grid>
                    </Box>
                )}

                {tabIndex === 1 && <InteractionTab memberId={formData.id} type="favorites" />}
                {tabIndex === 2 && <InteractionTab memberId={formData.id} type="history" />}

            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>关闭</Button>
                {tabIndex === 0 && <Button onClick={handleSubmit} variant="contained" disabled={isLoading}>保存更改</Button>}
            </DialogActions>
        </Dialog>
    );
}

export default MemberFormDialog;
