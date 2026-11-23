package com.prodigalgal.ircs.common.aggregation;

import java.util.UUID;

public final class AggregationWorkTypes {

    public static final String SOURCE_RECORD = "aggregation.raw-video";

    private AggregationWorkTypes() {
    }

    public static String taskId(UUID sourceRecordId) {
        if (sourceRecordId == null) {
            throw new IllegalArgumentException("sourceRecordId is required");
        }
        return "raw_video:" + sourceRecordId;
    }
}
