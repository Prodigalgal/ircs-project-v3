// File: ircs-frontend/src/app/store.js
import { configureStore } from '@reduxjs/toolkit';
import { apiSlice } from './api/apiSlice';
import dataSourceWizardReducer from '../features/data-sources/wizard/dataSourceWizardSlice';
import notificationReducer from '../features/common/notificationSlice';
import { rtkQueryErrorLogger } from './middleware/errorMiddleware';

export const store = configureStore({
    reducer: {
        // RTK Query reducer
        [apiSlice.reducerPath]: apiSlice.reducer,

        // 全局 UI 状态
        notification: notificationReducer,

        // 业务模块 UI 状态
        dataSourceWizard: dataSourceWizardReducer,
    },
    // 添加 RTK Query 中间件 + 自定义错误拦截器
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware()
            .concat(apiSlice.middleware)
            .concat(rtkQueryErrorLogger),
});