import { apiSlice } from '../../app/api/apiSlice';

export const videosApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getVideos: builder.query({
            query: ({
                        page, size, sort,
                        title, categoryId, enrichmentStatus, normalizationStatus,
                        aggregationStatus,
                        year, area, minScore, isMissingSlug,
                        dataSourceId, sourceCategoryName, genre, language
                    }) => ({
                url: '/raw-videos',
                params: {
                    page, size, sort,
                    title, categoryId, enrichmentStatus, normalizationStatus, aggregationStatus,
                    year, area, minScore, isMissingSlug,
                    dataSourceId, sourceCategoryName, genre, language
                },
            }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'Video', id })),
                        { type: 'Video', id: 'LIST' },
                    ]
                    : [{ type: 'Video', id: 'LIST' }],
        }),
        getVideoById: builder.query({
            query: (id) => `/raw-videos/${id}`,
            providesTags: (result, error, id) => [{ type: 'Video', id }],
        }),
        createVideo: builder.mutation({
            query: (videoData) => ({
                url: '/raw-videos',
                method: 'POST',
                body: videoData,
            }),
            invalidatesTags: [{ type: 'Video', id: 'LIST' }, 'Dashboard'],
        }),
        updateVideo: builder.mutation({
            query: ({ id, ...videoData }) => ({
                url: `/raw-videos/${id}`,
                method: 'PUT',
                body: { ...videoData, id },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Video', id },
                { type: 'Video', id: 'LIST' },
                { type: 'UnifiedVideo', id: 'LIST' },
                'Dashboard'
            ],
        }),
        deleteVideo: builder.mutation({
            query: (id) => ({
                url: `/raw-videos/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [
                { type: 'Video', id: 'LIST' },
                { type: 'UnifiedVideo', id: 'LIST' },
                'Dashboard'
            ],
        }),
        batchDeleteVideos: builder.mutation({
            query: (ids) => ({
                url: '/raw-videos/batch-delete',
                method: 'POST',
                body: { ids },
            }),
            invalidatesTags: [
                { type: 'Video', id: 'LIST' },
                { type: 'UnifiedVideo', id: 'LIST' },
                'Dashboard'
            ],
        }),
        batchSyncSearchVideos: builder.mutation({
            query: (ids) => ({
                url: '/raw-videos/batch-sync-search',
                method: 'POST',
                body: { ids }
            })
        }),

        reNormalizeVideo: builder.mutation({
            query: (id) => ({
                url: `/raw-videos/${id}/re-normalize`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [{ type: 'Video', id }, 'Dashboard'],
        }),
        reEnrichVideo: builder.mutation({
            query: (id) => ({
                url: `/raw-videos/${id}/re-enrich`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [{ type: 'Video', id }, 'Dashboard'],
        }),
        reFetchVideo: builder.mutation({
            query: (id) => ({
                url: `/raw-videos/${id}/re-fetch`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [{ type: 'Video', id }, 'Dashboard'],
        }),

        batchReNormalizeVideos: builder.mutation({
            query: (ids) => ({
                url: '/raw-videos/batch-re-normalize',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [{ type: 'Video', id: 'LIST' }, 'Dashboard'],
        }),
        batchReEnrichVideos: builder.mutation({
            query: (ids) => ({
                url: '/raw-videos/batch-re-enrich',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [{ type: 'Video', id: 'LIST' }, 'Dashboard'],
        }),
        batchReFetchVideos: builder.mutation({
            query: (ids) => ({
                url: '/raw-videos/batch-re-fetch',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [{ type: 'Video', id: 'LIST' }, 'Dashboard'],
        }),
    }),
});

export const {
    useGetVideosQuery,
    useLazyGetVideosQuery,
    useGetVideoByIdQuery,
    useLazyGetVideoByIdQuery,
    useCreateVideoMutation,
    useUpdateVideoMutation,
    useDeleteVideoMutation,
    useBatchDeleteVideosMutation,
    useBatchSyncSearchVideosMutation,
    useReNormalizeVideoMutation,
    useReEnrichVideoMutation,
    useReFetchVideoMutation,
    useBatchReNormalizeVideosMutation,
    useBatchReEnrichVideosMutation,
    useBatchReFetchVideosMutation,
} = videosApi;
