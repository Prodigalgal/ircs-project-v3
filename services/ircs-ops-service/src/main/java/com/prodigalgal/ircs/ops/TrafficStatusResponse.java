package com.prodigalgal.ircs.ops;

import java.util.List;

public record TrafficStatusResponse(
        List<TrafficSlotResponse> globalLimiters,
        List<TrafficSlotResponse> credentialLimiters) {
}

