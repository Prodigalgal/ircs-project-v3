import React, { useState } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, TextField, Box, Typography, CircularProgress,
    InputAdornment, Stack, Divider
} from '@mui/material';
import { Lock as LockIcon, VpnKey as KeyIcon } from '@mui/icons-material';
import { useChangePasswordMutation } from '../../auth/authApi';
import { useDispatch } from 'react-redux';
import { showNotification } from '../../common/notificationSlice';

function ChangePasswordDialog({ open, onClose }) {
    const dispatch = useDispatch();
    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const [changePassword, { isLoading }] = useChangePasswordMutation();

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (newPassword !== confirmPassword) {
            dispatch(showNotification({ message: '两次输入的新密码不一致', severity: 'warning' }));
            return;
        }

        try {
            await changePassword({ oldPassword, newPassword }).unwrap();
            dispatch(showNotification({ message: '密码修改成功，请重新登录', severity: 'success' }));
            // 延迟清除 token 强制跳转，让通知显示完整
            setTimeout(() => {
                localStorage.removeItem('ircs_token');
                window.location.href = '/login';
            }, 1000);
        } catch {
            // 错误由 Middleware 处理
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            maxWidth="xs"
            fullWidth
            slotProps={{ paper: { sx: { bgcolor: '#050505', border: '1px solid #333' } } }}
        >
            <DialogTitle sx={{ color: '#fff', borderBottom: '1px solid #222', mb: 2 }}>
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <KeyIcon fontSize="small" />
                    <Typography variant="h6">修改管理密码</Typography>
                </Stack>
            </DialogTitle>

            <form onSubmit={handleSubmit}>
                <DialogContent>
                    <Stack spacing={3}>
                        <TextField
                            label="当前密码"
                            type="password"
                            fullWidth
                            required
                            value={oldPassword}
                            onChange={e => setOldPassword(e.target.value)}
                            size="small"
                        />
                        <Divider sx={{ borderColor: '#222' }} />
                        <TextField
                            label="新密码"
                            type="password"
                            fullWidth
                            required
                            value={newPassword}
                            onChange={e => setNewPassword(e.target.value)}
                            size="small"
                        />
                        <TextField
                            label="确认新密码"
                            type="password"
                            fullWidth
                            required
                            value={confirmPassword}
                            onChange={e => setConfirmPassword(e.target.value)}
                            size="small"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions sx={{ p: 3, pt: 1 }}>
                    <Button onClick={onClose} color="inherit">取消</Button>
                    <Button
                        type="submit"
                        variant="contained"
                        disabled={isLoading}
                        sx={{ bgcolor: '#fff', color: '#000', '&:hover': { bgcolor: '#ccc' } }}
                    >
                        {isLoading ? <CircularProgress size={20} /> : '更新并注销'}
                    </Button>
                </DialogActions>
            </form>
        </Dialog>
    );
}

export default ChangePasswordDialog;
