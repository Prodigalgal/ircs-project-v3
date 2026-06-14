package com.prodigalgal.ircs.catalog;

import java.util.UUID;

public record RawGenreRead(
        UUID id,
        String sourceValue,
        UUID standardGenreId,
        String standardGenreName) {}
