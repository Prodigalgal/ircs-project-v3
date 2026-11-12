// File: ircs-frontend/src/features/genres/genresApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const genresApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getGenres: builder.query({
            query: () => '/standard-genres',
            providesTags: (result) =>
                result
                    ? [...result.map(({ id }) => ({ type: 'Genre', id })), { type: 'Genre', id: 'LIST' }]
                    : [{ type: 'Genre', id: 'LIST' }],
        }),
        getGenresPage: builder.query({
            query: ({ page, size, sort, name, code }) => ({
                url: '/standard-genres/page',
                params: { page, size, sort, name, code },
            }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'Genre', id })), { type: 'Genre', id: 'LIST' }]
                    : [{ type: 'Genre', id: 'LIST' }],
        }),
        getGenreById: builder.query({
            query: (id) => `/standard-genres/${id}`,
            providesTags: (result, error, id) => [{ type: 'Genre', id }],
        }),
        createGenre: builder.mutation({
            query: (data) => ({ url: '/standard-genres', method: 'POST', body: data }),
            invalidatesTags: [{ type: 'Genre', id: 'LIST' }],
        }),
        updateGenre: builder.mutation({
            query: ({ id, ...data }) => ({
                url: `/standard-genres/${id}`,
                method: 'PUT',
                body: { ...data, id },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Genre', id },
                { type: 'Genre', id: 'LIST' }
            ],
        }),
        deleteGenre: builder.mutation({
            query: (id) => ({ url: `/standard-genres/${id}`, method: 'DELETE' }),
            invalidatesTags: [{ type: 'Genre', id: 'LIST' }],
        }),

    }),
});

export const {
    useGetGenresQuery,
    useLazyGetGenresQuery,
    useGetGenresPageQuery,
    useLazyGetGenresPageQuery,
    useGetGenreByIdQuery,
    useCreateGenreMutation,
    useUpdateGenreMutation,
    useDeleteGenreMutation
} = genresApi;
