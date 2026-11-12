// File: ircs-frontend/src/features/playlists/playlistsApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const playlistsApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getPlaylists: builder.query({
            query: (params) => ({ url: '/playlists', params }),
            providesTags: (result) =>
                result
                    ? [...result.content.map(({ id }) => ({ type: 'Playlist', id })), { type: 'Playlist', id: 'LIST' }]
                    : [{ type: 'Playlist', id: 'LIST' }],
        }),
        getPlaylistById: builder.query({
            query: (id) => `/playlists/${id}`,
            providesTags: (result, error, id) => [{ type: 'Playlist', id }],
        }),
        createPlaylist: builder.mutation({
            query: (data) => ({ url: '/playlists', method: 'POST', body: data }),
            invalidatesTags: [{ type: 'Playlist', id: 'LIST' }, { type: 'Video', id: 'LIST' }],
        }),
        updatePlaylist: builder.mutation({
            query: ({ id, ...data }) => ({ url: `/playlists/${id}`, method: 'PUT', body: { ...data, id } }),
            invalidatesTags: (result, error, { id }) => [
                { type: 'Playlist', id },
                { type: 'Playlist', id: 'LIST' },
                { type: 'Video', id: 'LIST' }
            ],
        }),
        deletePlaylist: builder.mutation({
            query: (id) => ({ url: `/playlists/${id}`, method: 'DELETE' }),
            invalidatesTags: [{ type: 'Playlist', id: 'LIST' }, { type: 'Video', id: 'LIST' }],
        }),
    }),
});

export const {
    useGetPlaylistsQuery,
    useGetPlaylistByIdQuery,
    useCreatePlaylistMutation,
    useUpdatePlaylistMutation,
    useDeletePlaylistMutation,
    useLazyGetPlaylistsQuery
} = playlistsApi;