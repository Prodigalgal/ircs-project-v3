// File: ircs-frontend/src/features/common/commonApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const commonApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getTimezones: builder.query({
            query: () => '/common/timezones',
            keepUnusedDataFor: 86400,
        }),
    }),
});

export const { useGetTimezonesQuery } = commonApi;