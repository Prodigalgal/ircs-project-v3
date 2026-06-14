package com.prodigalgal.ircs.ops;

import java.util.UUID;

public record MaintenanceSessionInfo(
        UUID sessionId,
        String taskName,
        long startTime,
        boolean finished
) {
}
