// ircs-frontend/src/features/settings/systemConfigsApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const systemConfigsApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getConfigs: builder.query({
            query: ({ page, size, sort, keyword }) => ({
                url: '/configs',
                params: { page, size, sort, keyword },
            }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ key }) => ({ type: 'SystemConfig', id: key })),
                        { type: 'SystemConfig', id: 'LIST' },
                    ]
                    : [{ type: 'SystemConfig', id: 'LIST' }],
        }),
        getConfigByKey: builder.query({
            query: (key) => `/configs/${key}`,
            providesTags: (result, error, key) => [{ type: 'SystemConfig', id: key }],
        }),
        createConfig: builder.mutation({
            query: (config) => ({
                url: '/configs',
                method: 'POST',
                body: config,
            }),
            invalidatesTags: [{ type: 'SystemConfig', id: 'LIST' }],
        }),
        updateConfig: builder.mutation({
            query: ({ key, ...config }) => ({
                url: `/configs/${key}`,
                method: 'PUT',
                body: { ...config, key },
            }),
            invalidatesTags: (result, error, { key }) => [
                { type: 'SystemConfig', id: key },
                { type: 'SystemConfig', id: 'LIST' }
            ],
        }),
        deleteConfig: builder.mutation({
            query: (key) => ({
                url: `/configs/${key}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'SystemConfig', id: 'LIST' }],
        }),
        testConnection: builder.mutation({
            query: ({ type, params }) => ({
                url: `/configs/test/${type}`,
                method: 'POST',
                body: params
            }),
        }),
        getRestartCapabilities: builder.query({
            query: () => '/ops/service-restarts/capabilities',
            providesTags: [{ type: 'SystemConfig', id: 'RESTART_CAPABILITIES' }],
        }),
        restartServices: builder.mutation({
            query: ({ services, reason }) => ({
                url: '/ops/service-restarts',
                method: 'POST',
                body: { services, reason }
            }),
        }),
    }),
});

export const {
    useGetConfigsQuery,
    useGetConfigByKeyQuery,
    useCreateConfigMutation,
    useUpdateConfigMutation,
    useDeleteConfigMutation,
    useTestConnectionMutation,
    useGetRestartCapabilitiesQuery,
    useRestartServicesMutation
} = systemConfigsApi;
