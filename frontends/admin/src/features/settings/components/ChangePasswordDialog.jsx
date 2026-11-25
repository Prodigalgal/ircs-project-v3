// File: frontends/admin/src/features/settings/components/ChangePasswordDialog.jsx
import React, { useState } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    TextField,
    Typography,
    CircularProgress,
    Stack,
    Divider,
} from '@mui/material';
import { VpnKey as KeyIcon } from '@mui/icons-material';
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
            setTimeout(() => {
                localStorage.removeItem('ircs_token');
                window.location.href = '/login';
            }, 1000);
        } catch {
            // 错误由 Middleware 处理
        }
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <KeyIcon fontSize="small" color="primary" />
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        修改管理密码
                    </Typography>
                </Stack>
            </DialogTitle>

            <form onSubmit={handleSubmit}>
                <DialogContent sx={{ pt: 3 }}>
                    <Stack spacing={2.25}>
                        <TextField
                            label="当前密码"
                            type="password"
                            fullWidth
                            required
                            value={oldPassword}
                            onChange={(e) => setOldPassword(e.target.value)}
                            size="small"
                        />
                        <Divider />
                        <TextField
                            label="新密码"
                            type="password"
                            fullWidth
                            required
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            size="small"
                        />
                        <TextField
                            label="确认新密码"
                            type="password"
                            fullWidth
                            required
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            size="small"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 2.5, pt: 1 }}>
                    <Button onClick={onClose} color="inherit">
                        取消
                    </Button>
                    <Button type="submit" variant="contained" color="primary" disabled={isLoading}>
                        {isLoading ? <CircularProgress size={18} color="inherit" /> : '更新并注销'}
                    </Button>
                </DialogActions>
            </form>
        </Dialog>
    );
}

export default ChangePasswordDialog;
