package com.prodigalgal.ircs.ops;

import jakarta.validation.constraints.Size;

public record MaintenanceGateCloseRequest(@Size(max = 500) String reason) {
}
