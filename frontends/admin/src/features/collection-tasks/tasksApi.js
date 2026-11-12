import { apiSlice } from '../../app/api/apiSlice';

export const tasksApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getCollectionTasks: builder.query({
            query: (params) => ({ url: '/collection-tasks', params }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'CollectionTask', id })),
                        { type: 'CollectionTask', id: 'LIST' },
                    ]
                    : [{ type: 'CollectionTask', id: 'LIST' }],
        }),
        getCollectionTaskById: builder.query({
            query: (id) => `/collection-tasks/${id}`,
            providesTags: (result, error, id) => [{ type: 'CollectionTask', id }],
        }),

        getCollectionTaskRuntime: builder.query({
            query: ({ id, pageOffset = 0, pageLimit = 100, detailLimit = 20 }) => ({
                url: `/collection-tasks/${id}/runtime`,
                params: { pageOffset, pageLimit, detailLimit }
            }),
            providesTags: (result, error, { id }) => [{ type: 'CollectionTask', id }],
        }),

        createCollectionTask: builder.mutation({
            query: (task) => ({
                url: '/collection-tasks',
                method: 'POST',
                body: task,
            }),
            invalidatesTags: [{ type: 'CollectionTask', id: 'LIST' }, 'Dashboard'],
        }),

        updateCollectionTask: builder.mutation({
            query: ({ id, ...task }) => ({
                url: `/collection-tasks/${id}`,
                method: 'PUT',
                body: { ...task, id },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'CollectionTask', id },
                { type: 'CollectionTask', id: 'LIST' }
            ],
        }),

        deleteCollectionTask: builder.mutation({
            query: (id) => ({
                url: `/collection-tasks/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'CollectionTask', id: 'LIST' }, 'Dashboard'],
        }),

        startTask: builder.mutation({
            query: (id) => ({
                url: `/collection-tasks/${id}/start`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CollectionTask', id },
                'Dashboard'
            ],
        }),

        resumeTask: builder.mutation({
            query: (id) => ({
                url: `/collection-tasks/${id}/resume`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CollectionTask', id },
                'Dashboard'
            ],
        }),

        pauseTask: builder.mutation({
            query: (id) => ({
                url: `/collection-tasks/${id}/pause`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CollectionTask', id }
            ],
        }),

        stopTask: builder.mutation({
            query: (id) => ({
                url: `/collection-tasks/${id}/stop`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CollectionTask', id }
            ],
        }),

        getTaskLogs: builder.query({
            query: ({ id, offset = 0, limit = 100 }) => ({
                url: `/collection-tasks/${id}/logs`,
                params: { offset, limit }
            }),
        }),
    }),
});

export const {
    useGetCollectionTasksQuery,
    useGetCollectionTaskByIdQuery,
    useLazyGetCollectionTaskByIdQuery,
    useGetCollectionTaskRuntimeQuery,
    useCreateCollectionTaskMutation,
    useUpdateCollectionTaskMutation,
    useDeleteCollectionTaskMutation,
    useStartTaskMutation,
    useResumeTaskMutation,
    usePauseTaskMutation,
    useStopTaskMutation,
    useGetTaskLogsQuery
} = tasksApi;
