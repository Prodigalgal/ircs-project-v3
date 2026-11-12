import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

const parseApiResponse = async (response) => {
    const text = await response.text();

    if (!text) {
        return null;
    }

    const trimmed = text.trim();
    const contentType = response.headers.get('content-type') || '';
    const looksJson = contentType.includes('json') || trimmed.startsWith('{') || trimmed.startsWith('[');

    if (looksJson) {
        try {
            return JSON.parse(text);
        } catch {
            return {
                __nonJsonResponse: true,
                message: `接口返回了无效 JSON (${response.status})`,
                detail: trimmed.slice(0, 300)
            };
        }
    }

    return {
        __nonJsonResponse: true,
        message: response.status >= 500
            ? `服务暂不可用 (${response.status})`
            : `接口返回了非 JSON 内容 (${response.status})`,
        detail: trimmed.slice(0, 300),
        contentType
    };
};

export const apiSlice = createApi({
    reducerPath: 'api',
    keepUnusedDataFor: 180,
    baseQuery: fetchBaseQuery({
        baseUrl: '/api/v1',
        prepareHeaders: (headers) => {
            const token = localStorage.getItem('ircs_token');
            if (token) {
                headers.set('Authorization', `Bearer ${token}`);
            }
            return headers;
        },
        responseHandler: parseApiResponse,
        validateStatus: (response, body) =>
            response.status >= 200 &&
            response.status < 300 &&
            !(body && typeof body === 'object' && body.__nonJsonResponse),
    }),
    refetchOnMountOrArgChange: 120,
    tagTypes: [
        'Category',
        'Video',
        'DataSource',
        'CollectionTask',
        'Dashboard',
        'DashboardTrend',
        'DashboardDistributions',
        'DashboardCoverage',
        'DashboardEfficiency',
        'SystemConfig',
        'SourceDomain',
        'Resolver',
        'Genre',
        'StandardLanguage',
        'UnifiedVideo',
        'Playlist',
        'CoverImage',
        'FailedMessage',
        'RequestAudit',
        'Area',
        'Member',
        'Credential',
        'AdminApiToken',
        'RuntimeDlq',
        'OpsAlert',
        'NotificationDelivery',
        'MagnetContent',
        'MediaRequestBatch'
    ],
    endpoints: () => ({}),
});
