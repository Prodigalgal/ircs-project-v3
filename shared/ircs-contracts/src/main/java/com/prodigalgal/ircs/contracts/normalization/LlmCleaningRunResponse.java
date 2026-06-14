package com.prodigalgal.ircs.contracts.normalization;

public record LlmCleaningRunResponse(
        String kind,
        String status,
        int candidates,
        int mapped,
        int noise,
        String reason
) {
}
