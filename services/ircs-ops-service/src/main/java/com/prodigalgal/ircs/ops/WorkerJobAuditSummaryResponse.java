package com.prodigalgal.ircs.ops;

public record WorkerJobAuditSummaryResponse(
        long totalLast24h,
        long failedLast24h,
        long succeededLast24h,
        Long maxDurationMsLast24h) {
}
