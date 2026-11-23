import { apiSlice } from '../../app/api/apiSlice';

export const catalogEntriesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getCatalogEntries: builder.query({
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
                    ? [...result.content.map(({ id }) => ({ type: 'CatalogEntry', id })), { type: 'CatalogEntry', id: 'LIST' }]
                    : [{ type: 'CatalogEntry', id: 'LIST' }],
        }),
        getCatalogEntryById: builder.query({
            query: (id) => `/unified-videos/${id}`,
            providesTags: (result, error, id) => [{ type: 'CatalogEntry', id }],
        }),
        createCatalogEntry: builder.mutation({
            query: (data) => ({ url: '/unified-videos', method: 'POST', body: data }),
            invalidatesTags: [{ type: 'CatalogEntry', id: 'LIST' }, 'Dashboard'],
        }),
        updateCatalogEntry: builder.mutation({
            query: ({ id, ...data }) => ({ url: `/unified-videos/${id}`, method: 'PUT', body: { ...data, id } }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'CatalogEntry', id },
                { type: 'CatalogEntry', id: 'LIST' },
                { type: 'Video', id: 'LIST' }
            ],
        }),
        deleteCatalogEntry: builder.mutation({
            query: (id) => ({ url: `/unified-videos/${id}`, method: 'DELETE' }),
            invalidatesTags: [
                { type: 'CatalogEntry', id: 'LIST' },
                { type: 'Video', id: 'LIST' },
                'Dashboard'
            ],
        }),
        batchDeleteCatalogEntries: builder.mutation({
            query: (ids) => ({
                url: '/unified-videos/batch-delete',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [
                { type: 'CatalogEntry', id: 'LIST' },
                { type: 'Video', id: 'LIST' },
                'Dashboard'
            ],
        }),
        batchSyncSearchCatalogEntries: builder.mutation({
            query: (ids) => ({
                url: '/unified-videos/batch-sync-search',
                method: 'POST',
                body: { ids }
            })
        }),
        mergeCatalogEntries: builder.mutation({
            query: (ids) => ({
                url: '/aggregation/unified-videos/merge',
                method: 'POST',
                body: { ids }
            }),
            invalidatesTags: [
                { type: 'CatalogEntry', id: 'LIST' },
                { type: 'Video', id: 'LIST' },
                'Dashboard'
            ],
        }),

        recalculateCatalogEntryMetadata: builder.mutation({
            query: (id) => ({
                url: `/unified-videos/${id}/recalculate`,
                method: 'POST'
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CatalogEntry', id },
                { type: 'CatalogEntry', id: 'LIST' }
            ],
        }),
        scrapeCatalogEntryMagnets: builder.mutation({
            query: (id) => ({
                url: `/magnets/search/unified/${id}`,
                method: 'POST'
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CatalogEntry', id },
                { type: 'MagnetContent', id },
                { type: 'MagnetContent', id: `JOBS-${id}` },
            ],
        }),
        getCatalogEntryMagnetLinks: builder.query({
            query: (id) => `/magnets/unified/${id}/links`,
            providesTags: (result, error, id) => [{ type: 'MagnetContent', id }],
        }),
        getCatalogEntryMagnetJobs: builder.query({
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
        updateCatalogEntryMagnetStatus: builder.mutation({
            query: ({ id, linkId, status }) => ({
                url: `/magnets/unified/${id}/links/${linkId}/status`,
                method: 'PUT',
                body: { status },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'MagnetContent', id },
                { type: 'CatalogEntry', id },
            ],
        }),
        deleteCatalogEntryMagnet: builder.mutation({
            query: ({ id, linkId }) => ({
                url: `/magnets/unified/${id}/links/${linkId}`,
                method: 'DELETE',
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'MagnetContent', id },
                { type: 'CatalogEntry', id },
            ],
        }),
    }),
});

export const {
    useGetCatalogEntriesQuery,
    useLazyGetCatalogEntriesQuery,
    useGetCatalogEntryByIdQuery,
    useLazyGetCatalogEntryByIdQuery,
    useCreateCatalogEntryMutation,
    useUpdateCatalogEntryMutation,
    useDeleteCatalogEntryMutation,
    useBatchDeleteCatalogEntriesMutation,
    useBatchSyncSearchCatalogEntriesMutation,
    useMergeCatalogEntriesMutation,
    useRecalculateCatalogEntryMetadataMutation,
    useScrapeCatalogEntryMagnetsMutation,
    useGetCatalogEntryMagnetLinksQuery,
    useGetCatalogEntryMagnetJobsQuery,
    useLazyGetMagnetProviderRunsQuery,
    useUpdateCatalogEntryMagnetStatusMutation,
    useDeleteCatalogEntryMagnetMutation
} = catalogEntriesApi;
