// ircs-frontend/vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    build: {
        rollupOptions: {
            output: {
                manualChunks(id) {
                    const normalizedId = id.replaceAll('\\', '/');
                    const inPackage = (packageName) => normalizedId.includes(`/node_modules/${packageName}/`);

                    if (!normalizedId.includes('node_modules')) {
                        return undefined;
                    }

                    if (inPackage('@vidstack/react')) {
                        return 'vendor-player';
                    }

                    if (
                        inPackage('react') ||
                        inPackage('react-dom') ||
                        inPackage('scheduler')
                    ) {
                        return 'vendor-react-core';
                    }

                    if (
                        inPackage('react-router-dom') ||
                        inPackage('react-router')
                    ) {
                        return 'vendor-router';
                    }

                    if (
                        inPackage('@reduxjs/toolkit') ||
                        inPackage('redux') ||
                        inPackage('react-redux') ||
                        inPackage('immer') ||
                        inPackage('reselect')
                    ) {
                        return 'vendor-state';
                    }

                    if (inPackage('@emotion/react') || inPackage('@emotion/styled') || inPackage('@emotion/cache')) {
                        return 'vendor-emotion';
                    }

                    if (inPackage('@mui/icons-material')) {
                        return 'vendor-mui-icons';
                    }

                    if (inPackage('@mui/x-date-pickers')) {
                        return 'vendor-date-pickers';
                    }

                    if (normalizedId.includes('/node_modules/@mui/')) {
                        return 'vendor-mui-core';
                    }

                    if (normalizedId.includes('/recharts/') || normalizedId.includes('/d3-')) {
                        return 'vendor-charts';
                    }

                    return 'vendor';
                },
            },
        },
    },
    server: {
        host: '0.0.0.0',
        port: 3000,
        proxy: {
            '/api/v1': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/media': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
