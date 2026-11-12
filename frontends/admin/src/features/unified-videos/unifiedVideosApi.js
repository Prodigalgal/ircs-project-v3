import { apiSlice } from '../../app/api/apiSlice';

export const unifiedVideosApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getUnifiedVideos: builder.query({
            query: ({
                        page, size, sort,
                        title, categoryId, year, area, minScore,
                        hasDoubanId, hasTmdbId, contentVisibility,
                        metadataStatus, genre, language, actor, director
                    }) => ({
                url: '/unified-videos',
                params: {
                    page, size, sort,
                    title, categoryId, year, area, minScore,
                    hasDoubanId, hasTmdbId, contentVisibility,
                    metadataStatus, genre, language, actor, director
                },
            }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'UnifiedVideo', id })), { type: 'UnifiedVideo', id: 'LIST' }]
                    : [{ type: 'UnifiedVideo', id: 'LIST' }],
        }),
        getUnifiedVideoById: builder.query({
            query: (id) => `/unified-videos/${id}`,
            providesTags: (result, error, id) => [{ type: 'UnifiedVideo', id }],
        }),
        createUnifiedVideo: builder.mutation({
            query: (data) => ({ url: '/unified-videos', method: 'POST', body: data }),
            invalidatesTags: [{ type: 'UnifiedVideo', id: 'LIST' }, 'Dashboard'],
        }),
        updateUnifiedVideo: builder.mutation({
            query: ({ id, ...data }) => ({ url: `/unified-videos/${id}`, method: 'PUT', body: { ...data, id } }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'UnifiedVideo', id },
                { type: 'UnifiedVideo', id: 'LIST' },
                { type: 'Video', id: 'LIST' }
            ],
        }),
        deleteUnifiedVideo: builder.mutation({
            query: (id) => ({ url: `/unified-videos/${id}`, method: 'DELETE' }),
            invalidatesTags: [
                { type: 'UnifiedVideo', id: 'LIST' },
                { type: 'Video', id: 'LIST' },
                'Dashboard'
            ],
        }),
        batchDeleteUnifiedVideos: builder.mutation({
            query: (ids) => ({
                url: '/unified-videos/batch-delete',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [
                { type: 'UnifiedVideo', id: 'LIST' },
                { type: 'Video', id: 'LIST' },
                'Dashboard'
            ],
        }),
        batchSyncSearchUnifiedVideos: builder.mutation({
            query: (ids) => ({
                url: '/unified-videos/batch-sync-search',
                method: 'POST',
                body: { ids }
            })
        }),
        mergeUnifiedVideos: builder.mutation({
            query: (ids) => ({
                url: '/aggregation/unified-videos/merge',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [
                { type: 'UnifiedVideo', id: 'LIST' },
                { type: 'Video', id: 'LIST' },
                'Dashboard'
            ],
        }),

        recalculateUnifiedVideoMetadata: builder.mutation({
            query: (id) => ({
                url: `/unified-videos/${id}/recalculate`,
                method: 'POST'
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'UnifiedVideo', id },
                { type: 'UnifiedVideo', id: 'LIST' }
            ],
        }),
        scrapeUnifiedVideoMagnets: builder.mutation({
            query: (id) => ({
                url: `/magnets/search/unified/${id}`,
                method: 'POST'
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'UnifiedVideo', id },
                { type: 'MagnetContent', id },
                { type: 'MagnetContent', id: `JOBS-${id}` },
            ],
        }),
        getUnifiedVideoMagnetLinks: builder.query({
            query: (id) => `/magnets/unified/${id}/links`,
            providesTags: (result, error, id) => [{ type: 'MagnetContent', id }],
        }),
        getUnifiedVideoMagnetJobs: builder.query({
            query: ({ id, limit = 20 }) => ({
                url: `/magnets/unified/${id}/jobs`,
                params: { limit },
            }),
            providesTags: (result, error, { id }) => [{ type: 'MagnetContent', id: `JOBS-${id}` }],
        }),
        getMagnetProviderRuns: builder.query({
            query: (jobId) => `/magnets/search-jobs/${jobId}/runs`,
            providesTags: (result, error, jobId) => [{ type: 'MagnetContent', id: `RUNS-${jobId}` }],
        }),
        updateUnifiedVideoMagnetStatus: builder.mutation({
            query: ({ id, linkId, status }) => ({
                url: `/magnets/unified/${id}/links/${linkId}/status`,
                method: 'PUT',
                body: { status },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'MagnetContent', id },
                { type: 'UnifiedVideo', id },
            ],
        }),
        deleteUnifiedVideoMagnet: builder.mutation({
            query: ({ id, linkId }) => ({
                url: `/magnets/unified/${id}/links/${linkId}`,
                method: 'DELETE',
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'MagnetContent', id },
                { type: 'UnifiedVideo', id },
            ],
        }),
    }),
});

export const {
    useGetUnifiedVideosQuery,
    useLazyGetUnifiedVideosQuery,
    useGetUnifiedVideoByIdQuery,
    useLazyGetUnifiedVideoByIdQuery,
    useCreateUnifiedVideoMutation,
    useUpdateUnifiedVideoMutation,
    useDeleteUnifiedVideoMutation,
    useBatchDeleteUnifiedVideosMutation,
    useBatchSyncSearchUnifiedVideosMutation,
    useMergeUnifiedVideosMutation,
    useRecalculateUnifiedVideoMetadataMutation,
    useScrapeUnifiedVideoMagnetsMutation,
    useGetUnifiedVideoMagnetLinksQuery,
    useGetUnifiedVideoMagnetJobsQuery,
    useLazyGetMagnetProviderRunsQuery,
    useUpdateUnifiedVideoMagnetStatusMutation,
    useDeleteUnifiedVideoMagnetMutation
} = unifiedVideosApi;
