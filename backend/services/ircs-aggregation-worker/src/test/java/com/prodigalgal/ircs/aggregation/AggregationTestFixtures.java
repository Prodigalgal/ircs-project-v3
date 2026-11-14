package com.prodigalgal.ircs.aggregation;

/**
 * Shared construction helpers for aggregation tests. {@link AggregationMatchingStrategy} now depends
 * on {@link AggregationSafetyPolicy} for external-id validity, so tests build both through one place
 * instead of repeating literal policy arguments at every call site.
 */
final class AggregationTestFixtures {

    private static final String DEFAULT_REJECTED_EXTERNAL_IDS = "douban:123";

    private AggregationTestFixtures() {
    }

    static AggregationSafetyPolicy safetyPolicy() {
        return new AggregationSafetyPolicy(true, DEFAULT_REJECTED_EXTERNAL_IDS);
    }

    static AggregationMatchingStrategy matchingStrategy() {
        return new AggregationMatchingStrategy(safetyPolicy());
    }
}
