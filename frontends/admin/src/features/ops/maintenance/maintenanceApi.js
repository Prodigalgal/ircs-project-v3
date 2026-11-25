// File: src/features/ops/maintenance/maintenanceApi.js
import { apiSlice } from '../../../app/api/apiSlice';

export const maintenanceApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getActiveMaintenanceSession: builder.query({
            query: () => '/ops/maintenance/active',
            providesTags: ['MaintenanceSession'],
            // Short polling to keep UI sync with background task status
            pollingInterval: 3000,
            keepUnusedDataFor: 0
        }),
        initSanitizeTask: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/sanitize/init',
                method: 'POST',
            }),
            invalidatesTags: ['MaintenanceSession']
        }),
        initAggregationResetTask: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/aggregation/reset-init',
                method: 'POST',
            }),
            invalidatesTags: ['MaintenanceSession']
        }),

        // --- New Endpoint ---
        initUnifiedRecalculateTask: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/unified/recalculate-init',
                method: 'POST',
            }),
            invalidatesTags: ['MaintenanceSession']
        }),

        initAdultAssessmentBackfillTask: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/aggregation/adult-assessment-backfill/init',
                method: 'POST',
            }),
            invalidatesTags: ['MaintenanceSession']
        }),

        triggerTrendSync: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/trend/sync',
                method: 'POST',
            }),
        }),
        // --- Search Maintenance ---
        reindexRawVideos: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/search/reindex-raw/init',
                method: 'POST',
            }),
            invalidatesTags: ['MaintenanceSession']
        }),
        reindexUnifiedVideos: builder.mutation({
            query: () => ({
                url: '/ops/maintenance/search/reindex-unified/init',
                method: 'POST',
            }),
            invalidatesTags: ['MaintenanceSession']
        }),
    }),
});

export const {
    useGetActiveMaintenanceSessionQuery,
    useInitSanitizeTaskMutation,
    useInitAggregationResetTaskMutation,
    useInitUnifiedRecalculateTaskMutation, // Exported
    useInitAdultAssessmentBackfillTaskMutation,
    useTriggerTrendSyncMutation,
    useReindexRawVideosMutation,
    useReindexUnifiedVideosMutation
} = maintenanceApi;
