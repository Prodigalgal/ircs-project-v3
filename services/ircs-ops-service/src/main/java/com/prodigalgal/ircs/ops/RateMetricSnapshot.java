package com.prodigalgal.ircs.ops;

import java.util.List;

record RateMetricSnapshot(
        RateMetricWindowCalculator calculator,
        List<RateMetricWindowCalculator.RateBucketPoint> buckets,
        long nowMillis) {

    RateMetricResponse metric(String metricKey, String label, String... sourceMetricKeys) {
        return calculator.calculate(metricKey, label, buckets, nowMillis, sourceMetricKeys);
    }

    RateMetricResponse empty(String metricKey, String label) {
        return calculator.empty(metricKey, label);
    }
}
