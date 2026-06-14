package com.prodigalgal.ircs.catalog;

import java.time.Instant;
import java.util.UUID;

public record RawAreaRead(
        UUID id,
        String sourceValue,
        UUID standardAreaId,
        String standardAreaName,
        Instant createdAt,
        Instant updatedAt) {}
