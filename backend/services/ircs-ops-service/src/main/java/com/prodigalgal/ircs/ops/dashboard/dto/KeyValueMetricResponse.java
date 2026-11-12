package com.prodigalgal.ircs.ops.dashboard.dto;

public record KeyValueMetricResponse(
        long usedMemoryBytes,
        int connectedClients,
        long opsPerSec,
        String version
) {
}
