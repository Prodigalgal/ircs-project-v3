package com.prodigalgal.ircs.catalog;

import java.util.UUID;

public record RawValueMappingSummary(
        UUID id,
        String sourceValue,
        UUID standardId,
        String standardName,
        String standardCode) {}
