// File: ircs-frontend/src/features/common/notificationSlice.js
import { createSlice } from '@reduxjs/toolkit';

const initialState = {
    open: false,
    message: '',
    severity: 'info', // 'success' | 'info' | 'warning' | 'error'
};

const notificationSlice = createSlice({
    name: 'notification',
    initialState,
    reducers: {
        showNotification: (state, action) => {
            state.open = true;
            state.message = action.payload.message;
            state.severity = action.payload.severity || 'info';
        },
        hideNotification: (state) => {
            state.open = false;
        },
    },
});

export const { showNotification, hideNotification } = notificationSlice.actions;
export default notificationSlice.reducer;