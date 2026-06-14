package com.prodigalgal.ircs.catalog;

import java.util.UUID;

public record RawLanguageSummary(
        UUID id,
        String sourceValue,
        UUID standardLanguageId,
        String standardName,
        String standardCode,
        String englishName,
        String nativeName) {}
