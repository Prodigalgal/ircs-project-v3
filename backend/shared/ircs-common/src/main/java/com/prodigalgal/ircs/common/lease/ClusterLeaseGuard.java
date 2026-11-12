package com.prodigalgal.ircs.common.lease;

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
        renewer = Thread.ofVirtual().name("ircs-lease-renewal").start(() -> {
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
            throw failure;
        }
        if (System.nanoTime() - validUntilNanos >= 0L) {
            lost(null);
            throw failure;
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
                Thread verification = Thread.ofVirtual().name("ircs-lease-final-check").start(validation);
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
                throw failure;
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
