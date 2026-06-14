package com.prodigalgal.ircs.ops;

public record NotificationMailSendHistorySummaryResponse(
        long totalLast24h,
        long skippedLast24h,
        long sentLast24h,
        long failedLast24h,
        String skippedSemantics,
        String sentSemantics,
        String failedSemantics) {
}
