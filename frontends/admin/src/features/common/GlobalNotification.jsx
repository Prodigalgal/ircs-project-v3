// File: ircs-frontend/src/features/common/GlobalNotification.jsx
import React from 'react';
import { Snackbar, Alert } from '@mui/material';
import { useSelector, useDispatch } from 'react-redux';
import { hideNotification } from './notificationSlice';

/**
 * 全局通知组件
 * 挂载在 App 根节点，响应 Redux 中的通知状态
 */
function GlobalNotification() {
    const dispatch = useDispatch();
    const { open, message, severity } = useSelector((state) => state.notification);

    const handleClose = (event, reason) => {
        if (reason === 'clickaway') {
            return;
        }
        dispatch(hideNotification());
    };

    return (
        <Snackbar
            open={open}
            autoHideDuration={6000}
            onClose={handleClose}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        >
            <Alert onClose={handleClose} severity={severity} variant="filled" sx={{ width: '100%' }}>
                {message}
            </Alert>
        </Snackbar>
    );
}

export default GlobalNotification;