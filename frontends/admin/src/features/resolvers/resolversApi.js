// File: ircs-frontend/src/features/settings/resolversApi.js
import { apiSlice } from '../../app/api/apiSlice.js';

export const resolversApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getResolvers: builder.query({
            query: () => '/resolvers/active',
            providesTags: (result) =>
                result
                    ? [...result.map(({ id }) => ({ type: 'Resolver', id })), { type: 'Resolver', id: 'LIST' }]
                    : [{ type: 'Resolver', id: 'LIST' }],
        }),
        getResolverById: builder.query({
            query: (id) => `/resolvers/${id}`,
            providesTags: (result, error, id) => [{ type: 'Resolver', id }],
        }),
        // Note: POST /resolvers is not in current API docs, but kept for frontend compatibility
        createResolver: builder.mutation({
            query: (data) => ({
                url: '/resolvers',
                method: 'POST',
                body: data,
            }),
            invalidatesTags: [{ type: 'Resolver', id: 'LIST' }],
        }),
        updateResolver: builder.mutation({
            query: ({ id, ...data }) => ({
                url: `/resolvers/${id}`,
                method: 'PUT',
                body: data,
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Resolver', id },
                { type: 'Resolver', id: 'LIST' }
            ],
        }),
        deleteResolver: builder.mutation({
            query: (id) => ({
                url: `/resolvers/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'Resolver', id: 'LIST' }],
        }),
    }),
});

export const {
    useGetResolversQuery,
    useGetResolverByIdQuery,
    useCreateResolverMutation,
    useUpdateResolverMutation,
    useDeleteResolverMutation
} = resolversApi;