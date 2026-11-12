// File: ircs-frontend/src/features/messages/messagesApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const messagesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getMessages: builder.query({
            query: ({ page, size, sort, keyword, status, public: publicMessage }) => ({
                url: '/messages',
                params: { page, size, sort, keyword, status, public: publicMessage },
            }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'Message', id })),
                        { type: 'Message', id: 'LIST' },
                    ]
                    : [{ type: 'Message', id: 'LIST' }],
        }),
        replyMessage: builder.mutation({
            query: ({ id, reply }) => ({
                url: `/messages/${id}/reply`,
                method: 'POST',
                body: { reply },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Message', id },
                { type: 'Message', id: 'LIST' }
            ],
        }),
        toggleMessageVisibility: builder.mutation({
            query: ({ id, isPublic }) => ({
                url: `/messages/${id}/visibility`,
                method: 'PUT',
                body: { public: isPublic },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Message', id },
                { type: 'Message', id: 'LIST' }
            ],
        }),
        deleteMessage: builder.mutation({
            query: (id) => ({
                url: `/messages/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'Message', id: 'LIST' }],
        }),
    }),
});

export const {
    useGetMessagesQuery,
    useReplyMessageMutation,
    useToggleMessageVisibilityMutation,
    useDeleteMessageMutation
} = messagesApi;
