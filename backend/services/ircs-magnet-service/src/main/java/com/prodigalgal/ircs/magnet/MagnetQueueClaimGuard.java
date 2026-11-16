package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/** Renews every item in a claimed batch and fences writes after claim loss. */
@Slf4j
final class MagnetQueueClaimGuard implements AutoCloseable {
    private static final ThreadLocal<MagnetQueueClaimGuard> CURRENT = new ThreadLocal<>();

    private final MagnetWorkLeaseStore leaseStore;
    private final Duration visibility;
    private final Runnable heartbeat;
    private final LongSupplier nanoTime;
    private final Map<RuntimeWorkItem, Claim> claims = new LinkedHashMap<>();
    private final Thread renewer;
    private volatile boolean closed;

    MagnetQueueClaimGuard(
            MagnetWorkLeaseStore leaseStore,
            List<RuntimeWorkItem> items,
            Duration visibility,
            Runnable heartbeat) {
        this(leaseStore, items, visibility, heartbeat, System::nanoTime);
    }

    MagnetQueueClaimGuard(
            MagnetWorkLeaseStore leaseStore,
            List<RuntimeWorkItem> items,
            Duration visibility,
            Runnable heartbeat,
            LongSupplier nanoTime) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested magnet queue claim guard");
        }
        this.leaseStore = leaseStore;
        this.visibility = visibility;
        this.heartbeat = heartbeat;
        this.nanoTime = nanoTime;
        long nowNanos = nanoTime.getAsLong();
        long wallNow = System.currentTimeMillis();
        for (RuntimeWorkItem item : items) {
            long remainingMillis = item.visibleAt() == null
                    ? 0
                    : Math.max(0, item.visibleAt().toEpochMilli() - wallNow);
            claims.put(item, new Claim(new ClaimState(true, true, nowNanos + Duration.ofMillis(remainingMillis).toNanos())));
        }
        renewNow();
        CURRENT.set(this);
        renewer = Thread.ofVirtual().name("ircs-magnet-claim-renewal").start(this::renewInBackground);
    }

    static void checkCurrentOwnership() {
        MagnetQueueClaimGuard guard = CURRENT.get();
        if (guard == null) {
            return;
        }
        for (Claim claim : guard.claims.values()) {
            ClaimState snapshot = claim.state;
            if (snapshot.active() && !snapshot.ownedAt(guard.nanoTime.getAsLong())) {
                throw new IllegalStateException("Magnet queue claim ownership lost");
            }
        }
    }

    static boolean ownsCurrent(RuntimeWorkItem item) {
        MagnetQueueClaimGuard guard = CURRENT.get();
        return guard != null && guard.owns(item);
    }

    static boolean finishCurrent(RuntimeWorkItem item, BooleanSupplier transition) {
        MagnetQueueClaimGuard guard = CURRENT.get();
        return guard != null && guard.finish(item, transition);
    }

    boolean owns(RuntimeWorkItem item) {
        Claim claim = claims.get(item);
        return claim != null && claim.state.ownedAt(nanoTime.getAsLong());
    }

    boolean finish(RuntimeWorkItem item, BooleanSupplier transition) {
        Claim claim = claims.get(item);
        if (claim == null) {
            return false;
        }
        synchronized (claim) {
            try {
                return owns(item) && transition.getAsBoolean();
            } catch (RuntimeException ex) {
                log.warn("Magnet queue transition failed; leaving recovery to visibility timeout: taskId={}",
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
                if (closed || !snapshot.active() || !snapshot.owned()) {
                    continue;
                }
                long started = nanoTime.getAsLong();
                boolean owned;
                long validUntil = snapshot.validUntilNanos();
                try {
                    owned = leaseStore.renew(entry.getKey(), visibility);
                    validUntil = started + visibility.toNanos();
                    if (nanoTime.getAsLong() - validUntil >= 0) {
                        owned = false;
                    }
                } catch (RuntimeException ex) {
                    owned = false;
                    log.warn("Magnet claim renewal failed: taskId={}", entry.getKey().taskId(), ex);
                }
                claim.state = new ClaimState(true, owned, validUntil);
                if (!owned) {
                    log.warn("Magnet claim renewal rejected: taskId={}, submissionId={}, attempt={}",
                            entry.getKey().taskId(), entry.getKey().submissionId(), entry.getKey().attempt());
                }
            }
        }
        try {
            heartbeat.run();
        } catch (RuntimeException ex) {
            log.warn("Magnet worker heartbeat failed", ex);
        }
    }

    private void renewInBackground() {
        try {
            Duration interval = Duration.ofMillis(Math.max(1, visibility.toMillis() / 3));
            while (!closed) {
                Thread.sleep(interval);
                if (!closed) {
                    renewNow();
                }
            }
        } catch (InterruptedException ex) {
            if (!closed) {
                log.warn("Magnet claim renewer interrupted before close", ex);
                invalidateAll();
            }
        }
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
                throw new IllegalStateException("Magnet claim renewer did not stop within 5 seconds");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping magnet claim renewer", ex);
        } finally {
            CURRENT.remove();
        }
    }

    private static final class Claim {
        private volatile ClaimState state;

        private Claim(ClaimState state) {
            this.state = state;
        }
    }

    private record ClaimState(boolean active, boolean owned, long validUntilNanos) {
        boolean ownedAt(long nowNanos) {
            return active && owned && nowNanos - validUntilNanos < 0;
        }
    }
}
