package com.prodigalgal.ircs.catalog;

import java.util.UUID;

public record RawCategoryQuery(
        String sourceName,
        UUID dataSourceId,
        Boolean isUnmapped) {}
