import { apiSlice } from '../../app/api/apiSlice';

export const areasApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        // --- Standard Areas ---
        getStandardAreas: builder.query({
            query: () => '/standard-areas',
            providesTags: (result) =>
                result
                    ? [...result.map(({ id }) => ({ type: 'Area', id })), { type: 'Area', id: 'LIST' }]
                    : [{ type: 'Area', id: 'LIST' }],
        }),
        getStandardAreasPage: builder.query({
            query: ({ page, size, sort }) => ({
                url: '/standard-areas/page',
                params: { page, size, sort },
            }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'Area', id })), { type: 'Area', id: 'LIST' }]
                    : [{ type: 'Area', id: 'LIST' }],
        }),
        createStandardArea: builder.mutation({
            query: (data) => ({ url: '/standard-areas', method: 'POST', body: data }),
            invalidatesTags: [{ type: 'Area', id: 'LIST' }],
        }),
    }),
});

export const {
    useGetStandardAreasQuery,
    useGetStandardAreasPageQuery,
    useCreateStandardAreaMutation
} = areasApi;
