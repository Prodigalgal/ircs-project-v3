// File: src/features/ops/traffic/trafficApi.js
import { apiSlice } from '../../../app/api/apiSlice';

export const trafficApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getTrafficStatus: builder.query({
            query: () => '/ops/traffic/status',
            // High-frequency polling for real-time monitoring
            pollingInterval: 2000,
            providesTags: ['TrafficStatus'],
            keepUnusedDataFor: 0
        }),
        resetTrafficSlot: builder.mutation({
            query: (key) => ({
                url: '/ops/traffic/reset',
                method: 'POST',
                body: { key }
            }),
            // Force immediate refresh
            async onQueryStarted(arg, { dispatch, queryFulfilled }) {
                try {
                    await queryFulfilled;
                    dispatch(trafficApi.util.invalidateTags(['TrafficStatus']));
                } catch {
                    // Global RTK middleware reports reset failures.
                }
            }
        })
    }),
});

export const {
    useGetTrafficStatusQuery,
    useResetTrafficSlotMutation
} = trafficApi;
