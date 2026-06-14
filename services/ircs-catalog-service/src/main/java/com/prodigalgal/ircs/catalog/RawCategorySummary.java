package com.prodigalgal.ircs.catalog;

import java.util.UUID;

public record RawCategorySummary(
        UUID id,
        UUID dataSourceId,
        String dataSourceName,
        String sourceCode,
        String sourceName,
        UUID categoryId,
        String categoryName,
        String categorySlug) {}
