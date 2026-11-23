package com.prodigalgal.ircs.common.aggregation;

import java.util.UUID;

public record AggregationWorkPayload(
        UUID sourceRecordId,
        String pipelineVersion,
        String sourceService,
        String reason) {
}
