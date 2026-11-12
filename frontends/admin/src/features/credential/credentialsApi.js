// File: ircs-frontend/src/features/settings/credentialsApi.js
import { apiSlice } from '../../app/api/apiSlice.js';

export const credentialsApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getCredentials: builder.query({
            query: (provider) => ({
                url: '/credentials',
                params: { provider }
            }),
            providesTags: (result) =>
                result
                    ? [...result.map(({ id }) => ({ type: 'Credential', id })), { type: 'Credential', id: 'LIST' }]
                    : [{ type: 'Credential', id: 'LIST' }],
        }),
        getCredentialTemplate: builder.query({
            query: (provider) => ({
                url: '/credentials/templates',
                params: { provider }
            }),
            // Templates are static per release, cache longer
            keepUnusedDataFor: 3600,
        }),
        createCredential: builder.mutation({
            query: (data) => ({
                url: '/credentials',
                method: 'POST',
                body: data
            }),
            invalidatesTags: [{ type: 'Credential', id: 'LIST' }]
        }),
        updateCredential: builder.mutation({
            query: ({ id, ...data }) => ({
                url: `/credentials/${id}`,
                method: 'PUT',
                body: data
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Credential', id },
                { type: 'Credential', id: 'LIST' }
            ]
        }),
        deleteCredential: builder.mutation({
            query: (id) => ({
                url: `/credentials/${id}`,
                method: 'DELETE'
            }),
            invalidatesTags: [{ type: 'Credential', id: 'LIST' }]
        }),
        refreshCredentialPool: builder.mutation({
            query: () => ({
                url: '/credentials/refresh',
                method: 'POST'
            })
        })
    })
});

export const {
    useGetCredentialsQuery,
    useLazyGetCredentialTemplateQuery,
    useCreateCredentialMutation,
    useUpdateCredentialMutation,
    useDeleteCredentialMutation,
    useRefreshCredentialPoolMutation
} = credentialsApi;