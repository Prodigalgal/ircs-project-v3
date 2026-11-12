// ircs-frontend/src/features/settings/sourceDomainsApi.js
import { apiSlice } from '../../app/api/apiSlice.js';

export const sourceDomainsApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getSourceDomains: builder.query({
            query: (params) => ({
                url: '/source-domains',
                params
            }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'SourceDomain', id })), { type: 'SourceDomain', id: 'LIST' }]
                    : [{ type: 'SourceDomain', id: 'LIST' }],
        }),
        getSourceDomainById: builder.query({
            query: (id) => `/source-domains/${id}`,
            providesTags: (result, error, id) => [{ type: 'SourceDomain', id }],
        }),
        updateSourceDomain: builder.mutation({
            query: ({ id, ...data }) => ({
                url: `/source-domains/${id}`,
                method: 'PUT',
                body: { ...data, id },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'SourceDomain', id },
                { type: 'SourceDomain', id: 'LIST' },
                { type: 'Video' },
                { type: 'Playlist' }
            ],
        }),
    }),
});

export const {
    useGetSourceDomainsQuery,
    useGetSourceDomainByIdQuery,
    useUpdateSourceDomainMutation
} = sourceDomainsApi;