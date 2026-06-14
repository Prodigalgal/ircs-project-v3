package com.prodigalgal.ircs.catalog;

import java.time.Instant;
import java.util.UUID;

public record RawLanguageRead(
        UUID id,
        String sourceValue,
        UUID standardLanguageId,
        String standardLanguageName,
        Instant createdAt,
        Instant updatedAt) {}
