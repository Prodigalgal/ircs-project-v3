import { apiSlice } from '../../app/api/apiSlice';

export const membersApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getMembers: builder.query({
            query: ({ page, size, sort, keyword, status, adultContentAllowed, minPoints, maxPoints }) => ({
                url: '/members',
                params: { page, size, sort, keyword, status, adultContentAllowed, minPoints, maxPoints },
            }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'Member', id })),
                        { type: 'Member', id: 'LIST' },
                    ]
                    : [{ type: 'Member', id: 'LIST' }],
        }),
        updateMember: builder.mutation({
            query: ({ id, ...data }) => ({
                url: `/members/${id}`,
                method: 'PUT',
                body: data,
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Member', id },
                { type: 'Member', id: 'LIST' }
            ],
        }),
        updateMemberStatus: builder.mutation({
            query: ({ id, status }) => ({
                url: `/members/${id}/status`,
                method: 'PUT',
                body: { status },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Member', id },
                { type: 'Member', id: 'LIST' }
            ],
        }),
        deleteMember: builder.mutation({
            query: (id) => ({
                url: `/members/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'Member', id: 'LIST' }],
        }),

        // New Endpoints for History/Favorites
        getMemberFavorites: builder.query({
            query: ({ id, page, size }) => ({
                url: `/members/${id}/favorites`,
                params: { page, size }
            }),
        }),
        getMemberHistory: builder.query({
            query: ({ id, page, size }) => ({
                url: `/members/${id}/history`,
                params: { page, size }
            }),
        }),
    }),
});

export const {
    useGetMembersQuery,
    useUpdateMemberMutation,
    useUpdateMemberStatusMutation,
    useDeleteMemberMutation,
    useGetMemberFavoritesQuery,
    useGetMemberHistoryQuery
} = membersApi;
