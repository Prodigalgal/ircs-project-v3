import { apiSlice } from '../../../app/api/apiSlice';

export const requestAuditApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getRequestAuditSummary: builder.query({
            query: () => '/ops/request-audit/summary',
            providesTags: ['RequestAudit']
        }),
        getRequestAuditLogs: builder.query({
            query: ({
                page = 0,
                size = 20,
                sort = 'createdAt,desc',
                username,
                method,
                path,
                statusCode,
                statusClass,
                clientIp,
                from,
                to
            }) => ({
                url: '/ops/request-audit',
                params: {
                    page,
                    size,
                    sort,
                    username,
                    method,
                    path,
                    statusCode,
                    statusClass,
                    clientIp,
                    from,
                    to
                }
            }),
            providesTags: ['RequestAudit']
        })
    })
});

export const {
    useGetRequestAuditSummaryQuery,
    useGetRequestAuditLogsQuery
} = requestAuditApi;
