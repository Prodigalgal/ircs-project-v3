package com.prodigalgal.ircs.ops;

public record QueueMetricResponse(
        String name,
        String key,
        String color,
        int messageCount,
        int consumerCount,
        RateKind rateKind,
        RateMetricResponse rate
) {
}
