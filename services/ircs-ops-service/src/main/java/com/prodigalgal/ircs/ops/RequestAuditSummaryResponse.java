package com.prodigalgal.ircs.ops;

public record RequestAuditSummaryResponse(
        long totalLast24h,
        long errorsLast24h,
        long slowLast24h,
        Long maxDurationMsLast24h) {
}

