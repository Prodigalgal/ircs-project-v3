// File: ircs-frontend/src/features/categories/categoriesApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const categoriesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getCategories: builder.query({
            query: ({ page, size, sort, name, slug }) => ({
                url: '/standard-categories/page',
                params: { page, size, sort, name, slug },
            }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'Category', id })), { type: 'Category', id: 'LIST' }]
                    : [{ type: 'Category', id: 'LIST' }],
        }),
        getAllCategories: builder.query({
            query: () => ({ url: '/standard-categories/all' }),
            providesTags: [{ type: 'Category', id: 'LIST' }],
        }),
        getCategoryById: builder.query({
            query: (id) => `/standard-categories/${id}`,
            providesTags: (result, error, id) => [{ type: 'Category', id }],
        }),
        createCategory: builder.mutation({
            query: (category) => ({ url: '/standard-categories', method: 'POST', body: category }),
            invalidatesTags: [{ type: 'Category', id: 'LIST' }],
        }),
        updateCategory: builder.mutation({
            query: ({ id, ...category }) => ({ url: `/standard-categories/${id}`, method: 'PUT', body: category }),
            invalidatesTags: (result, error, { id }) => [{ type: 'Category', id }, { type: 'Category', id: 'LIST' }],
        }),
        deleteCategory: builder.mutation({
            query: (id) => ({ url: `/standard-categories/${id}`, method: 'DELETE' }),
            invalidatesTags: [{ type: 'Category', id: 'LIST' }],
        }),

    }),
});

export const {
    useGetCategoriesQuery,
    useGetAllCategoriesQuery,
    useGetCategoryByIdQuery,
    useCreateCategoryMutation,
    useUpdateCategoryMutation,
    useDeleteCategoryMutation,
    useLazyGetCategoriesQuery
} = categoriesApi;
