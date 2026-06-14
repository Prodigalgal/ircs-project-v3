package com.prodigalgal.ircs.catalog;

import java.time.Instant;
import java.util.UUID;

public record RawCategoryRead(
        UUID id,
        String sourceCode,
        String sourceName,
        UUID dataSourceId,
        String dataSourceName,
        UUID categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt) {}
