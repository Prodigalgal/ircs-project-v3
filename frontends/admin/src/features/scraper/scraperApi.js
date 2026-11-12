import { apiSlice } from '../../app/api/apiSlice';

export const scraperApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        initManualSession: builder.mutation({
            query: (config) => ({
                url: '/scraper/manual/init',
                method: 'POST',
                body: config,
            }),
        }),
    }),
});

export const { useInitManualSessionMutation } = scraperApi;