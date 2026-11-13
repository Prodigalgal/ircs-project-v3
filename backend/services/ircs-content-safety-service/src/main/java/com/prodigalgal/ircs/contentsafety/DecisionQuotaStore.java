package com.prodigalgal.ircs.contentsafety;

import java.time.Instant;

/** A single system-wide reservation, independent of key, project, node and replica. */
interface DecisionQuotaStore {
    Reservation reserve(long estimatedInputTokens);
    void beginSend(Reservation reservation);
    void reconcile(Reservation reservation, long actualInputTokens);
    void cancelBeforeSend(Reservation reservation);
    void coolDown(Instant until);

    record Reservation(String id, String day, long estimate) {}
}
