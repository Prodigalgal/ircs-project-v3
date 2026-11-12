// File: ircs-frontend/src/features/auth/authApi.js
import {apiSlice} from '../../app/api/apiSlice';

export const authApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getPoWChallenge: builder.query({
            query: (scope = 'admin.login') => `/auth/pow?scope=${encodeURIComponent(scope)}`,
            keepUnusedDataFor: 0,
            cachePolicy: 'no-cache'
        }),
        login: builder.mutation({
            query: ({ powHeaders, ...credentials }) => ({
                url: '/auth/login',
                method: 'POST',
                headers: powHeaders || {},
                body: credentials,
            }),
            invalidatesTags: ['Dashboard'],
        }),
        changePassword: builder.mutation({
            query: (data) => ({
                url: '/auth/change-password',
                method: 'POST',
                body: data,
            }),
        }),
    }),
});

export const {
    useGetPoWChallengeQuery,
    useLazyGetPoWChallengeQuery,
    useLoginMutation,
    useChangePasswordMutation
} = authApi;
