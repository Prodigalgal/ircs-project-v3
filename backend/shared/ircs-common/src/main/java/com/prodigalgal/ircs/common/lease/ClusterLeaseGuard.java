package com.prodigalgal.ircs.common.lease;

import com.prodigalgal.ircs.common.concurrent.VirtualThreadExecutors;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renews a synchronous owner's lease. Checks stop subsequent work; they are not a database fencing token. */
public final class ClusterLeaseGuard implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClusterLeaseGuard.class);
    private static final ThreadLocal<List<ClusterLeaseGuard>> CURRENT = ThreadLocal.withInitial(ArrayList::new);
    private final ClusterLeaseService service;
    private final ClusterLease lease;
    private final Duration ttl;
    private final Thread owner = Thread.currentThread();
    private final Thread renewer;
    private volatile boolean stopped;
    private boolean renewing;
    private volatile boolean closed;
    private volatile IllegalStateException failure;
    private volatile long validUntilNanos;

    public ClusterLeaseGuard(ClusterLeaseService service, ClusterLease lease, Duration ttl) {
        this.service = java.util.Objects.requireNonNull(service);
        this.lease = java.util.Objects.requireNonNull(lease);
        if (ttl == null || !ttl.isPositive()) {
            throw new IllegalArgumentException("lease TTL must be positive");
        }
        this.ttl = ttl;
        Duration remaining = lease.ttlRemaining(Instant.now());
        if (!remaining.isPositive()) {
            throw new IllegalStateException("Cannot execute with an expired cluster lease: " + lease.name());
        }
        validUntilNanos = System.nanoTime() + Math.min(ttl.toNanos(), remaining.toNanos());
        CURRENT.get().add(this);
        Duration interval = Duration.ofMillis(Math.max(1L, ttl.toMillis() / 3L));
        renewer = VirtualThreadExecutors.threadFactory("ircs-lease-renewal-").newThread(() -> {
            while (!stopped) {
                try {
                    Thread.sleep(interval);
                    synchronized (this) {
                        if (stopped) {
                            return;
                        }
                        renewing = true;
                    }
                    try {
                        renewOwnership();
                    } finally {
                        synchronized (this) {
                            renewing = false;
                        }
                    }
                } catch (InterruptedException ex) {
                    if (!stopped) {
                        lost(ex);
                    }
                    return;
                } catch (RuntimeException ex) {
                    lost(ex);
                    return;
                }
            }
        });
        renewer.start();
    }

    public static void checkCurrentOwnership() {
        List<ClusterLeaseGuard> guards = CURRENT.get();
        try {
            for (ClusterLeaseGuard guard : guards) {
                guard.checkOwnership();
            }
        } finally {
            if (guards.isEmpty()) {
                CURRENT.remove();
            }
        }
    }

    public void checkOwnership() {
        if (failure != null) {
            throw ownershipFailure();
        }
        if (System.nanoTime() - validUntilNanos >= 0L) {
            lost(null);
            throw ownershipFailure();
        }
    }

    private void renewOwnership() {
        checkOwnership();
        long started = System.nanoTime();
        if (!service.renew(lease, ttl)) {
            throw new IllegalStateException("Lease token no longer matches: " + lease.name());
        }
        validUntilNanos = started + ttl.toNanos();
        checkOwnership();
    }

    private IllegalStateException ownershipFailure() {
        // A fresh wrapper is required: try-with-resources may also fail in close(),
        // and Java rejects suppressing an exception onto the very same instance.
        return new IllegalStateException(failure.getMessage(), failure);
    }

    private synchronized void lost(Throwable cause) {
        if (failure == null) {
            failure = new IllegalStateException("Distributed lock ownership lost: " + lease.name(), cause);
            log.error("Cluster lease renewal failed; interrupting owner: name={}, owner={}",
                    lease.name(), lease.ownerId(), cause);
        }
        if (!closed) {
            owner.interrupt();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            stopped = true;
            // Do not cancel an in-flight Redis renewal merely because the action finished.
            if (!renewing) {
                renewer.interrupt();
            }
        }
        boolean interrupted = false;
        try {
            long stopDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (renewer.isAlive() && System.nanoTime() - stopDeadline < 0L) {
                try {
                    renewer.join(Duration.ofMillis(100));
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
            if (renewer.isAlive()) {
                renewer.interrupt();
                lost(new IllegalStateException("Lease renewal did not stop within 5 seconds"));
            }
            if (failure == null) {
                java.util.concurrent.FutureTask<Void> validation = new java.util.concurrent.FutureTask<>(() -> {
                    renewOwnership();
                    return null;
                });
                Thread verification = VirtualThreadExecutors.threadFactory("ircs-lease-final-check-").newThread(validation);
                verification.start();
                try {
                    checkOwnership();
                    long budget = Math.min(Duration.ofSeconds(5).toNanos(), Math.max(1L, validUntilNanos - System.nanoTime()));
                    validation.get(budget, java.util.concurrent.TimeUnit.NANOSECONDS);
                } catch (InterruptedException ex) {
                    interrupted = true;
                    lost(ex);
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException ex) {
                    lost(ex);
                } finally {
                    verification.interrupt();
                }
            }
            if (failure != null) {
                throw ownershipFailure();
            }
        } finally {
            closed = true;
            List<ClusterLeaseGuard> guards = CURRENT.get();
            guards.remove(this);
            if (guards.isEmpty()) {
                CURRENT.remove();
            }
            if (interrupted) {
                owner.interrupt();
            }
        }
    }
}
