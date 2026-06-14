package com.prodigalgal.ircs.ops;

public record RedisMetricResponse(
        long usedMemoryBytes,
        int connectedClients,
        long opsPerSec,
        String version
) {
}
