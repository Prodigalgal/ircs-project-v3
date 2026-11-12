import { apiSlice } from '../../app/api/apiSlice';

export const mediaRequestBatchesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getMediaRequestBatches: builder.query({
            query: (params) => ({ url: '/media-request-batches', params }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'MediaRequestBatch', id })),
                        { type: 'MediaRequestBatch', id: 'LIST' },
                    ]
                    : [{ type: 'MediaRequestBatch', id: 'LIST' }],
        }),
        getMediaRequestBatch: builder.query({
            query: (id) => `/media-request-batches/${id}`,
            providesTags: (result, error, id) => [{ type: 'MediaRequestBatch', id }],
        }),
        getMediaRequestBatchTasks: builder.query({
            query: ({ id, page = 0, size = 20 }) => ({
                url: `/media-request-batches/${id}/collection-tasks`,
                params: { page, size },
            }),
            providesTags: (result, error, { id }) => [{ type: 'MediaRequestBatch', id }],
        }),
        startMediaRequestBatch: builder.mutation({
            query: (id) => ({
                url: `/media-request-batches/${id}/start`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'MediaRequestBatch', id },
                { type: 'MediaRequestBatch', id: 'LIST' },
                { type: 'CollectionTask', id: 'LIST' },
                'Dashboard',
            ],
        }),
        cancelMediaRequestBatch: builder.mutation({
            query: (id) => ({
                url: `/media-request-batches/${id}/cancel`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'MediaRequestBatch', id },
                { type: 'MediaRequestBatch', id: 'LIST' },
                { type: 'CollectionTask', id: 'LIST' },
            ],
        }),
    }),
});

export const {
    useGetMediaRequestBatchesQuery,
    useGetMediaRequestBatchQuery,
    useGetMediaRequestBatchTasksQuery,
    useStartMediaRequestBatchMutation,
    useCancelMediaRequestBatchMutation,
} = mediaRequestBatchesApi;
