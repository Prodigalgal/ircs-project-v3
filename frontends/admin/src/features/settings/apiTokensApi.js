import { apiSlice } from '../../app/api/apiSlice.js';

export const apiTokensApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getApiTokens: builder.query({
            query: () => '/auth/api-tokens',
            providesTags: (result) =>
                result
                    ? [
                        ...result.map(({ id }) => ({ type: 'AdminApiToken', id })),
                        { type: 'AdminApiToken', id: 'LIST' }
                    ]
                    : [{ type: 'AdminApiToken', id: 'LIST' }],
        }),
        createApiToken: builder.mutation({
            query: (body) => ({
                url: '/auth/api-tokens',
                method: 'POST',
                body
            }),
            invalidatesTags: [{ type: 'AdminApiToken', id: 'LIST' }]
        }),
        revokeApiToken: builder.mutation({
            query: (id) => ({
                url: `/auth/api-tokens/${id}`,
                method: 'DELETE'
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'AdminApiToken', id },
                { type: 'AdminApiToken', id: 'LIST' }
            ]
        })
    })
});

export const {
    useGetApiTokensQuery,
    useCreateApiTokenMutation,
    useRevokeApiTokenMutation
} = apiTokensApi;
