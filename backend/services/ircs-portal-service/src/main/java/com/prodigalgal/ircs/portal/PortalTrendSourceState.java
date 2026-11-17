package com.prodigalgal.ircs.portal;

import java.time.Instant;

public record PortalTrendSourceState(String provider, String board, String mediaType, String window,
        String status, String errorCode, Instant lastAttemptAt, Instant lastSuccessAt, int itemCount, boolean stale) {
}
