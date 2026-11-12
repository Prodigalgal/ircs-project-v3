// File: ircs-frontend/src/app/middleware/errorMiddleware.js
import { isRejectedWithValue } from '@reduxjs/toolkit';
import { showNotification } from '../../features/common/notificationSlice';

export const rtkQueryErrorLogger = (api) => (next) => (action) => {
    if (isRejectedWithValue(action)) {
        console.warn('RTK Query Middleware caught an error:', action.error, action.payload);

        const { payload } = action;

        if (payload?.status === 401) {
            if (window.location.pathname.includes('/login')) {
                const loginMessage =
                    payload.data?.message ||
                    payload.data?.detail ||
                    '登录失败，请检查用户名和密码';
                api.dispatch(showNotification({
                    message: loginMessage,
                    severity: 'error'
                }));
                return;
            }

            localStorage.removeItem('ircs_token');
            api.dispatch(showNotification({
                message: '会话已过期，请重新登录',
                severity: 'warning'
            }));

            window.location.href = '/login';
            return;
        }

        let errorMessage = '发生未知错误';
        let errorSeverity = 'error';

        if (payload) {
            if (payload.data && payload.data.errorKey) {
                const { entityName, errorKey, title } = payload.data;
                errorMessage = `[${entityName}] ${title || errorKey}`;
                if (errorKey === 'idexists') errorMessage = `${entityName} ID 已存在`;
                if (errorKey === 'idinvalid') errorMessage = '无效的 ID';
                if (errorKey === 'idnull') errorMessage = 'ID 不能为空';
            }
            else if (payload.data && payload.data.__nonJsonResponse) {
                errorMessage = payload.data.message || '服务返回了异常响应';
                if (payload.status >= 500 || payload.status === 200) {
                    errorSeverity = 'warning';
                }
            }
            else if (payload.data && payload.data.detail) {
                errorMessage = payload.data.detail;
            }
            else if (payload.data && payload.data.message) {
                errorMessage = payload.data.message;
            }
            else if (payload.status === 'FETCH_ERROR') {
                errorMessage = '网络连接失败，请检查服务器状态';
                errorSeverity = 'warning';
            }
            else if (payload.error) {
                errorMessage = payload.error;
            }
        } else if (action.error && action.error.message) {
            errorMessage = action.error.message;
        }

        api.dispatch(showNotification({
            message: errorMessage,
            severity: errorSeverity
        }));
    }

    return next(action);
};
