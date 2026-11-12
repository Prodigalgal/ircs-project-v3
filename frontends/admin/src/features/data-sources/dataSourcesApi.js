import { apiSlice } from '../../app/api/apiSlice';

export const dataSourcesApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getDataSources: builder.query({
            query: (params) => ({ url: '/data-sources', params }),
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({ id }) => ({ type: 'DataSource', id })),
                        { type: 'DataSource', id: 'LIST' },
                    ]
                    : [{ type: 'DataSource', id: 'LIST' }],
        }),
        getAllDataSources: builder.query({
            query: () => ({ url: '/data-sources', params: { page: 0, size: 1000 } }),
            transformResponse: (response) => response.content,
            providesTags: [{ type: 'DataSource', id: 'LIST' }],
        }),
        getDataSourceById: builder.query({
            query: (id) => `/data-sources/${id}`,
            providesTags: (result, error, id) => [{ type: 'DataSource', id }],
        }),
        createDataSource: builder.mutation({
            query: (dataSource) => ({
                url: '/data-sources',
                method: 'POST',
                body: dataSource,
            }),
            invalidatesTags: [{ type: 'DataSource', id: 'LIST' }],
        }),
        updateDataSource: builder.mutation({
            query: ({ id, data }) => ({
                url: `/data-sources/${id}`,
                method: 'PUT',
                body: { ...data, id },
            }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'DataSource', id },
                { type: 'DataSource', id: 'LIST' }
            ],
        }),
        deleteDataSource: builder.mutation({
            query: (id) => ({
                url: `/data-sources/${id}`,
                method: 'DELETE',
            }),
            invalidatesTags: [{ type: 'DataSource', id: 'LIST' }],
        }),
        fetchSampleData: builder.mutation({
            query: (requestPayload) => ({
                url: '/data-sources/fetch-sample',
                method: 'POST',
                body: requestPayload,
            }),
        }),
    }),
});

export const {
    useGetDataSourcesQuery,
    useGetAllDataSourcesQuery,
    useGetDataSourceByIdQuery,
    useLazyGetDataSourceByIdQuery,
    useCreateDataSourceMutation,
    useUpdateDataSourceMutation,
    useDeleteDataSourceMutation,
    useFetchSampleDataMutation,
} = dataSourcesApi;
