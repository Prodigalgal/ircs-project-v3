// File: ircs-frontend/src/app/App.jsx
import React from 'react';
import AppRouter from '../routes/AppRouter';
import GlobalNotification from '../features/common/GlobalNotification';

function App() {
    return (
        <>
            <AppRouter />
            <GlobalNotification />
        </>
    );
}

export default App;