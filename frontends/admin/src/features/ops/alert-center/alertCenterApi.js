import { apiSlice } from '../../../app/api/apiSlice';

export const alertCenterApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getOpsAlertIncidents: builder.query({
            query: ({ page = 0, size = 20, sort = 'lastSeenAt,desc', status, severity }) => ({
                url: '/ops-alert/incidents',
                params: { page, size, sort, status, severity },
            }),
            providesTags: ['OpsAlert'],
        }),
        getOpsAlertEvents: builder.query({
            query: ({ page = 0, size = 20, sort = 'createdAt,desc', severity, source }) => ({
                url: '/ops-alert/events',
                params: { page, size, sort, severity, source },
            }),
            providesTags: ['OpsAlert'],
        }),
        getHealingActions: builder.query({
            query: ({ page = 0, size = 20, sort = 'createdAt,desc', status, dryRun }) => ({
                url: '/ops-alert/healing-actions',
                params: { page, size, sort, status, dryRun },
            }),
            providesTags: ['OpsAlert'],
        }),
        getNotificationDeliverySummary: builder.query({
            query: () => '/ops/notification-mail-send-history/summary',
            providesTags: ['NotificationDelivery'],
        }),
        getNotificationDeliveries: builder.query({
            query: ({ page = 0, size = 20, sort = 'createdAt,desc', status }) => ({
                url: '/ops/notification-mail-send-history',
                params: { page, size, sort, status },
            }),
            providesTags: ['NotificationDelivery'],
        }),
        runLowRiskHealing: builder.mutation({
            query: (body) => ({
                url: '/ops/self-healing/low-risk/run',
                method: 'POST',
                body,
            }),
            invalidatesTags: ['OpsAlert'],
        }),
        runServiceRestartHealing: builder.mutation({
            query: (body) => ({
                url: '/ops/self-healing/service-restart/run',
                method: 'POST',
                body,
            }),
            invalidatesTags: ['OpsAlert'],
        }),
        getServiceRestartCapabilitiesForAlertCenter: builder.query({
            query: () => '/ops/service-restarts/capabilities',
            providesTags: ['OpsAlert'],
        }),
    }),
});

export const {
    useGetOpsAlertIncidentsQuery,
    useGetOpsAlertEventsQuery,
    useGetHealingActionsQuery,
    useGetNotificationDeliverySummaryQuery,
    useGetNotificationDeliveriesQuery,
    useRunLowRiskHealingMutation,
    useRunServiceRestartHealingMutation,
    useGetServiceRestartCapabilitiesForAlertCenterQuery,
} = alertCenterApi;
