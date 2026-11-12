// File: src/features/ops/dlq/dlqApi.js
import { apiSlice } from '../../../app/api/apiSlice';

export const dlqApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        // Note: GET /ops/dlq list endpoint is used by frontend but not in current API docs
        getDlqMessages: builder.query({
            query: ({ page, size, sort, status, queueName }) => ({
                url: '/ops/dlq',
                params: { page, size, sort, status, queueName },
            }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'FailedMessage', id })),
                        { type: 'FailedMessage', id: 'LIST' },
                    ]
                    : [{ type: 'FailedMessage', id: 'LIST' }],
        }),
        retryDlqMessage: builder.mutation({
            query: (id) => ({
                url: `/ops/dlq/${id}/retry`,
                method: 'POST',
            }),
            invalidatesTags: [{ type: 'FailedMessage', id: 'LIST' }],
        }),
        batchRetryDlqMessages: builder.mutation({
            query: (ids) => ({
                url: '/ops/dlq/batch-retry',
                method: 'POST',
                body: { ids },
            }),
            invalidatesTags: [{ type: 'FailedMessage', id: 'LIST' }],
        }),
        discardDlqMessage: builder.mutation({
            query: (id) => ({
                url: `/ops/dlq/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'FailedMessage', id: 'LIST' }],
        }),
        batchDiscardDlqMessages: builder.mutation({
            query: (ids) => ({
                url: '/ops/dlq/batch-discard',
                method: 'POST',
                body: { ids },
            }),
            invalidatesTags: [{ type: 'FailedMessage', id: 'LIST' }],
        }),
        getRabbitDlqQueues: builder.query({
            query: () => '/ops/rabbit-dlq',
            providesTags: ['RabbitDlq'],
            pollingInterval: 5000,
        }),
        retryRabbitDlq: builder.mutation({
            query: ({ queueName, limit }) => ({
                url: '/ops/rabbit-dlq/retry',
                method: 'POST',
                params: { queueName, limit },
            }),
            invalidatesTags: ['RabbitDlq'],
        }),
        discardRabbitDlq: builder.mutation({
            query: ({ queueName, limit }) => ({
                url: '/ops/rabbit-dlq/discard',
                method: 'POST',
                params: { queueName, limit },
            }),
            invalidatesTags: ['RabbitDlq'],
        }),
        getRuntimeDlqQueues: builder.query({
            query: () => '/ops/runtime-dlq',
            providesTags: ['RuntimeDlq'],
            pollingInterval: 5000,
        }),
        requeueRuntimeDlq: builder.mutation({
            query: ({ taskType, limit, maxReplayAttempts }) => ({
                url: '/ops/runtime-dlq/requeue',
                method: 'POST',
                params: { taskType, limit, maxReplayAttempts },
            }),
            invalidatesTags: ['RuntimeDlq'],
        }),
    }),
});

export const {
    useGetDlqMessagesQuery,
    useRetryDlqMessageMutation,
    useBatchRetryDlqMessagesMutation,
    useDiscardDlqMessageMutation,
    useBatchDiscardDlqMessagesMutation,
    useGetRabbitDlqQueuesQuery,
    useRetryRabbitDlqMutation,
    useDiscardRabbitDlqMutation,
    useGetRuntimeDlqQueuesQuery,
    useRequeueRuntimeDlqMutation
} = dlqApi;
