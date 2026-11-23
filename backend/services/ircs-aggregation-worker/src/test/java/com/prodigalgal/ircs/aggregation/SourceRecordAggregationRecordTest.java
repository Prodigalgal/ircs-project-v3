package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRecordAggregationRecordTest {

    @Test
    void eligibleWhenNormalizedAndEnrichmentFinished() {
        assertTrue(record("READY", "SUCCESS", "PENDING", "Codex").isAggregationEligible());
        assertTrue(record("READY", "FAILED", "PROCESSING", "Codex").isAggregationEligible());
    }

    @Test
    void ineligibleWhenLifecyclePrerequisitesAreMissing() {
        assertFalse(record("PENDING", "SUCCESS", "PENDING", "Codex").isAggregationEligible());
        assertFalse(record("READY", "PENDING", "PENDING", "Codex").isAggregationEligible());
        assertFalse(record("READY", "SUCCESS", "BOUND", "Codex").isAggregationEligible());
        assertFalse(record("READY", "SUCCESS", "PENDING", "").isAggregationEligible());
    }

    private SourceRecordAggregationRecord record(
            String normalizationStatus,
            String enrichmentStatus,
            String aggregationStatus,
            String title) {
        return new SourceRecordAggregationRecord(
                UUID.randomUUID(),
                title,
                null,
                null,
                "2026",
                new BigDecimal("8.1"),
                LocalDate.of(2026, 6, 4),
                null,
                null,
                null,
                "电影",
                null,
                null,
                null,
                null,
                null,
                null,
                normalizationStatus,
                enrichmentStatus,
                aggregationStatus);
    }
}
