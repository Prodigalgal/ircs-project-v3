package com.prodigalgal.ircs.interaction;

import java.util.UUID;

public record MediaRequestSubmitRequest(
        String title,
        Integer releaseYear,
        String extraInfo,
        UUID clientRequestId) {

    public MediaRequestSubmitRequest(String title, Integer releaseYear, String extraInfo) {
        this(title, releaseYear, extraInfo, null);
    }
}
