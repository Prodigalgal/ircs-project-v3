package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.contracts.maintenance.MaintenanceGateMode;
import java.time.Instant;
import java.util.UUID;

record MaintenanceGateDraft(
        UUID id,
        Instant now,
        String operationKey,
        String ownerService,
        String resourceType,
        String resourceScope,
        MaintenanceGateMode mode,
        String reason,
        String requestedBy,
        String correlationId,
        Instant expiresAt
) {
}
