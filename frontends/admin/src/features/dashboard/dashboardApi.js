// File: ircs-frontend/src/features/dashboard/dashboardApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const dashboardApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getDashboardStats: builder.query({
            query: () => '/dashboard/statistics',
            providesTags: ['Dashboard'],
            pollingInterval: 60000,
        }),
        getDashboardTrend: builder.query({
            query: (days = 14) => ({
                url: '/dashboard/trend',
                params: { days }
            }),
            providesTags: (result, error, days) => [{ type: 'DashboardTrend', id: days }],
            pollingInterval: 300000,
        }),
        getDashboardDistributions: builder.query({
            query: () => '/dashboard/distributions',
            providesTags: ['DashboardDistributions'],
            pollingInterval: 300000,
        }),
        getDashboardCoverage: builder.query({
            query: () => '/dashboard/coverage',
            providesTags: ['DashboardCoverage'],
            pollingInterval: 300000,
        }),
        getDashboardEfficiency: builder.query({
            query: () => '/dashboard/efficiency',
            providesTags: ['DashboardEfficiency'],
            pollingInterval: 300000,
        }),
        refreshDashboard: builder.mutation({
            query: (days = 14) => ({
                url: '/dashboard/refresh',
                method: 'POST',
                params: { days }
            }),
            invalidatesTags: (result, error, days) => [
                'Dashboard',
                { type: 'DashboardTrend', id: days },
                'DashboardDistributions',
                'DashboardCoverage',
                'DashboardEfficiency',
            ],
        }),
        getRealTimeMetrics: builder.query({
            query: () => '/dashboard/metrics',
            keepUnusedDataFor: 30
        }),
        getTaskRuntimeOverview: builder.query({
            query: (limit = 50) => ({
                url: '/dashboard/task-runtime',
                params: { limit }
            }),
            keepUnusedDataFor: 30
        }),
        getSearchOpsStats: builder.query({
            query: () => '/dashboard/search-ops',
            keepUnusedDataFor: 30
        }),
        getAggregationOpsStats: builder.query({
            query: () => '/dashboard/aggregation-ops',
            keepUnusedDataFor: 30
        }),
        getSourceQualityReport: builder.query({
            query: () => '/dashboard/source-quality',
            providesTags: ['SourceQuality'],
            pollingInterval: 300000
        })
    }),
});

export const {
    useGetDashboardStatsQuery,
    useGetDashboardTrendQuery,
    useGetDashboardDistributionsQuery,
    useGetDashboardCoverageQuery,
    useGetDashboardEfficiencyQuery,
    useRefreshDashboardMutation,
    useGetRealTimeMetricsQuery,
    useGetTaskRuntimeOverviewQuery,
    useGetSearchOpsStatsQuery,
    useGetAggregationOpsStatsQuery,
    useGetSourceQualityReportQuery
} = dashboardApi;
