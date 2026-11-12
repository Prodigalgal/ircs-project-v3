
import { apiSlice } from '../../app/api/apiSlice.js';

export const standardLanguagesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getStandardLanguages: builder.query({
            query: () => '/standard-languages',
            providesTags: [{ type: 'StandardLanguage', id: 'LIST' }],
        }),
        createStandardLanguage: builder.mutation({
            query: (data) => ({
                url: '/standard-languages',
                method: 'POST',
                body: data,
            }),
            invalidatesTags: [{ type: 'StandardLanguage', id: 'LIST' }],
        }),
    }),
});

export const {
    useGetStandardLanguagesQuery,
    useCreateStandardLanguageMutation
} = standardLanguagesApi;