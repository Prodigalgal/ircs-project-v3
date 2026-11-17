package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.concurrent.VirtualThreadExecutors;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/** Extends the database execution lease while provider HTTP and traffic waits run without a transaction. */
@Slf4j
final class MagnetJobExecutionGuard implements AutoCloseable {
    private final MagnetSearchJobCoordinator coordinator;
    private final MagnetSearchJobCoordinator.Lease lease;
    private final Thread renewer;
    private volatile boolean closed;
    private volatile boolean owned = true;
    private volatile long validUntilNanos;

    MagnetJobExecutionGuard(MagnetSearchJobCoordinator coordinator, MagnetSearchJobCoordinator.Lease lease) {
        this.coordinator = coordinator;
        this.lease = lease;
        renewNow();
        checkOwnership();
        renewer = VirtualThreadExecutors.threadFactory("ircs-magnet-job-lease-").newThread(() -> {
            try {
                Duration interval = Duration.ofMillis(Math.max(1, lease.duration().toMillis() / 3));
                while (!closed) {
                    Thread.sleep(interval);
                    if (!closed) { renewNow(); }
                }
            } catch (InterruptedException ex) {
                if (!closed) { owned = false; Thread.currentThread().interrupt(); }
            }
        });
        renewer.start();
    }

    void checkOwnership() {
        MagnetQueueClaimGuard.checkCurrentOwnership();
        if (!owned || System.nanoTime() - validUntilNanos >= 0) {
            throw new MagnetSearchJobCoordinator.MagnetLeaseLostException("MAGNET_SEARCH_JOB_LEASE_LOST");
        }
    }

    private void renewNow() {
        long started = System.nanoTime();
        try {
            boolean renewed = coordinator.renew(lease);
            long deadline = started + lease.duration().toNanos();
            if (!renewed || System.nanoTime() - deadline >= 0) { owned = false; }
            else { validUntilNanos = deadline; }
        } catch (RuntimeException ex) {
            owned = false;
            log.warn("Magnet job lease renewal failed: jobId={}", lease.jobId(), ex);
        }
    }

    @Override
    public void close() {
        closed = true;
        renewer.interrupt();
        try {
            if (!renewer.join(Duration.ofSeconds(5))) {
                throw new IllegalStateException("Magnet job lease renewer did not stop");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping magnet job lease renewer", ex);
        }
    }
}
