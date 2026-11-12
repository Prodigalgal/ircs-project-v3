// ircs-frontend/src/features/images/coverImagesApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const coverImagesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        // Note: GET /cover-images list endpoint is used by frontend but not in current API docs
        getCoverImages: builder.query({
            query: (params) => ({
                url: '/cover-images',
                params
            }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'CoverImage', id })), { type: 'CoverImage', id: 'LIST' }]
                    : [{ type: 'CoverImage', id: 'LIST' }],
        }),
        getCoverImageById: builder.query({
            query: (id) => `/cover-images/${id}`,
            providesTags: (result, error, id) => [{ type: 'CoverImage', id }],
        }),
        // Note: POST /cover-images/upload endpoint is used by frontend but not in current API docs
        uploadCoverImage: builder.mutation({
            query: (formData) => ({
                url: '/cover-images/upload',
                method: 'POST',
                body: formData,
            }),
            invalidatesTags: [{ type: 'CoverImage', id: 'LIST' }],
        }),
        fetchCoverImage: builder.mutation({
            query: (url) => ({
                url: '/cover-images/fetch',
                method: 'POST',
                body: { url },
            }),
            invalidatesTags: [{ type: 'CoverImage', id: 'LIST' }],
        }),
        syncCoverImageR2: builder.mutation({
            query: (id) => ({
                url: `/cover-images/${id}/sync-r2`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CoverImage', id },
                { type: 'CoverImage', id: 'LIST' }
            ],
        }),
        retryCoverImage: builder.mutation({
            query: (id) => ({
                url: `/cover-images/${id}/retry`,
                method: 'POST',
            }),
            invalidatesTags: (result, error, id) => [
                { type: 'CoverImage', id },
                { type: 'CoverImage', id: 'LIST' }
            ],
        }),
        deleteCoverImage: builder.mutation({
            query: (id) => ({
                url: `/cover-images/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'CoverImage', id: 'LIST' }],
        }),
    }),
});

export const {
    useGetCoverImagesQuery,
    useGetCoverImageByIdQuery,
    useUploadCoverImageMutation,
    useFetchCoverImageMutation,
    useSyncCoverImageR2Mutation,
    useRetryCoverImageMutation,
    useDeleteCoverImageMutation
} = coverImagesApi;