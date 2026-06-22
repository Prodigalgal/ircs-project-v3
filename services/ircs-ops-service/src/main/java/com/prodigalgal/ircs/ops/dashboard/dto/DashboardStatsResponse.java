package com.prodigalgal.ircs.ops.dashboard.dto;

public record DashboardStatsResponse(
        long rawCountDb,
        long rawCountEs,
        long unifiedCountDb,
        long unifiedCountEs,
        long totalTasks,
        long pendingNormalization,
        long pendingEnrichment,
        long normalizationFailed,
        long enrichmentMissingDouban,
        long enrichmentMissingTmdb,
        long imageDownloadFailed,
        long imageDeadLink
) {
}
