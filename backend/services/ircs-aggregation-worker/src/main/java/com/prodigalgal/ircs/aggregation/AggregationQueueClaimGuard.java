package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/** Keeps the whole claimed batch visible while serial chunks execute; never revives an expired token. */
@Slf4j
final class AggregationQueueClaimGuard implements AutoCloseable {
    private static final ThreadLocal<AggregationQueueClaimGuard> CURRENT = new ThreadLocal<>();
    private final AggregationWorkDeferrer deferrer;
    private final Duration visibility;
    private final Runnable heartbeat;
    private final LongSupplier nanoTime;
    private final Map<RuntimeWorkItem, Claim> claims = new LinkedHashMap<>();
    private final Thread renewer;
    private volatile boolean closed;

    AggregationQueueClaimGuard(AggregationWorkDeferrer deferrer, List<RuntimeWorkItem> items,
            Duration visibility, Runnable heartbeat) {
        this(deferrer, items, visibility, heartbeat, System::nanoTime);
    }

    AggregationQueueClaimGuard(AggregationWorkDeferrer deferrer, List<RuntimeWorkItem> items,
            Duration visibility, Runnable heartbeat, LongSupplier nanoTime) {
        if (CURRENT.get() != null) { throw new IllegalStateException("Nested aggregation claim guard"); }
        this.deferrer = deferrer;
        this.visibility = visibility;
        this.heartbeat = heartbeat;
        this.nanoTime = nanoTime;
        for (RuntimeWorkItem item : items) { claims.put(item, new Claim()); }
        renewNow();
        CURRENT.set(this);
        renewer = Thread.ofVirtual().name("ircs-aggregation-claim-renewal").start(() -> {
            try {
                Duration interval = Duration.ofMillis(Math.max(1, visibility.toMillis() / 3));
                while (!closed) {
                    Thread.sleep(interval);
                    if (!closed) { renewNow(); }
                }
            } catch (InterruptedException ex) {
                if (!closed) {
                    log.warn("Aggregation claim renewer interrupted before close", ex);
                    invalidateAll();
                }
            }
        });
    }

    static void checkCurrentOwnership() {
        AggregationQueueClaimGuard guard = CURRENT.get();
        if (guard == null) { return; }
        for (Claim claim : guard.claims.values()) {
            ClaimState snapshot = claim.state;
            if (snapshot.active() && !snapshot.ownedAt(guard.nanoTime.getAsLong())) {
                throw new IllegalStateException("Aggregation queue claim ownership lost");
            }
        }
    }

    static boolean ownsCurrent(RuntimeWorkItem item) {
        AggregationQueueClaimGuard guard = CURRENT.get();
        return guard != null && guard.owns(item);
    }

    static boolean finishCurrent(RuntimeWorkItem item, BooleanSupplier transition) {
        AggregationQueueClaimGuard guard = CURRENT.get();
        return guard != null && guard.finish(item, transition);
    }

    boolean owns(RuntimeWorkItem item) {
        ClaimState snapshot = claims.get(item).state;
        return snapshot.active() && snapshot.ownedAt(nanoTime.getAsLong());
    }

    boolean finish(RuntimeWorkItem item, BooleanSupplier transition) {
        Claim claim = claims.get(item);
        synchronized (claim) {
            try {
                return owns(item) && transition.getAsBoolean();
            } catch (RuntimeException ex) {
                log.warn("Aggregation queue transition failed; leaving recovery to visibility timeout: taskId={}",
                        item.taskId(), ex);
                return false;
            } finally {
                ClaimState snapshot = claim.state;
                claim.state = new ClaimState(false, snapshot.owned(), snapshot.validUntilNanos());
            }
        }
    }

    void renewNow() {
        for (Map.Entry<RuntimeWorkItem, Claim> entry : claims.entrySet()) {
            Claim claim = entry.getValue();
            synchronized (claim) {
                ClaimState snapshot = claim.state;
                if (closed || !snapshot.active() || !snapshot.owned()) { continue; }
                long started = nanoTime.getAsLong();
                boolean owned;
                long validUntilNanos = snapshot.validUntilNanos();
                try {
                    owned = deferrer.renew(entry.getKey(), visibility);
                    validUntilNanos = started + visibility.toNanos();
                    if (nanoTime.getAsLong() - validUntilNanos >= 0) { owned = false; }
                } catch (RuntimeException ex) {
                    owned = false;
                    log.warn("Aggregation claim renewal failed: taskId={}", entry.getKey().taskId(), ex);
                }
                // Publish owner and deadline together. Transaction checks keep reading the prior
                // deadline while Redis is blocked, without waiting on this transition monitor.
                claim.state = new ClaimState(true, owned, validUntilNanos);
                if (!owned) {
                    log.warn("Aggregation claim renewal rejected: taskId={}, submissionId={}, attempt={}",
                            entry.getKey().taskId(), entry.getKey().submissionId(), entry.getKey().attempt());
                }
            }
        }
        try { heartbeat.run(); }
        catch (RuntimeException ex) { log.warn("Aggregation consumer heartbeat failed", ex); }
    }

    private void invalidateAll() {
        for (Claim claim : claims.values()) {
            synchronized (claim) {
                ClaimState snapshot = claim.state;
                claim.state = new ClaimState(snapshot.active(), false, snapshot.validUntilNanos());
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        renewer.interrupt();
        try {
            if (!renewer.join(Duration.ofSeconds(5))) {
                throw new IllegalStateException("Aggregation claim renewer did not stop within 5 seconds");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping aggregation claim renewer", ex);
        } finally {
            CURRENT.remove();
        }
    }

    private static final class Claim {
        private volatile ClaimState state = new ClaimState(true, true, 0);
    }

    private record ClaimState(boolean active, boolean owned, long validUntilNanos) {
        boolean ownedAt(long nowNanos) {
            return owned && nowNanos - validUntilNanos < 0;
        }
    }
}
