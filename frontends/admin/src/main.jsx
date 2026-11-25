// File: frontends/admin/src/main.jsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import App from './app/App.jsx';
import { store } from './app/store.js';
import AppThemeProvider from './theme/ThemeModeContext.jsx';

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <Provider store={store}>
            <AppThemeProvider>
                <BrowserRouter>
                    <App />
                </BrowserRouter>
            </AppThemeProvider>
        </Provider>
    </React.StrictMode>,
);
