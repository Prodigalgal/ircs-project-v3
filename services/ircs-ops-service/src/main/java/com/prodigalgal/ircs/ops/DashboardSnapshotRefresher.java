package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.lease.ClusterLease;
import com.prodigalgal.ircs.common.lease.ClusterLeaseService;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class DashboardSnapshotRefresher {

    private static final String LEASE_NAME = "ops-dashboard-snapshot-refresher";

    private final DashboardSnapshotComposer composer;
    private final DashboardSnapshotCacheService cacheService;
    private final ObjectProvider<ClusterLeaseService> clusterLeaseServiceProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SnapshotRefreshState lastState = SnapshotRefreshState.never();

    @Value("${app.ops.dashboard.snapshot.enabled:true}")
    private boolean enabled;

    @Value("${app.ops.dashboard.snapshot.default-task-runtime-limit:50}")
    private int defaultTaskRuntimeLimit;

    @Value("${app.ops.dashboard.snapshot.lease-ttl:PT10S}")
    private Duration leaseTtl;

    @Value("${app.ops.dashboard.snapshot.cluster-lease.enabled:true}")
    private boolean clusterLeaseEnabled;

    @Value("${spring.application.name:ircs-ops-service}")
    private String applicationName;

    DashboardSnapshotRefresher(
            DashboardSnapshotComposer composer,
            DashboardSnapshotCacheService cacheService,
            ObjectProvider<ClusterLeaseService> clusterLeaseServiceProvider) {
        this.composer = composer;
        this.cacheService = cacheService;
        this.clusterLeaseServiceProvider = clusterLeaseServiceProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmup() {
        Thread warmup = new Thread(this::refreshDefaultSnapshot, "dashboard-snapshot-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }

    @Scheduled(
            initialDelayString = "#{T(java.time.Duration).parse('${app.ops.dashboard.snapshot.refresh-interval:PT2S}').toMillis()}",
            fixedDelayString = "#{T(java.time.Duration).parse('${app.ops.dashboard.snapshot.refresh-interval:PT2S}').toMillis()}")
    void refreshDefaultSnapshot() {
        refreshDefaultSnapshot(false);
    }

    void refreshAsync(int taskRuntimeLimit) {
        if (!enabled || running.get()) {
            return;
        }
        Thread refresh = new Thread(
                () -> refreshWithLease(Math.max(1, taskRuntimeLimit), false),
                "dashboard-snapshot-refresh-on-demand");
        refresh.setDaemon(true);
        refresh.start();
    }

    SnapshotRefreshState lastState() {
        return lastState;
    }

    DashboardStreamSnapshot composeSnapshot(int taskRuntimeLimit) {
        return composer.compose(Math.max(1, taskRuntimeLimit));
    }

    private void refreshDefaultSnapshot(boolean force) {
        refreshWithLease(Math.max(1, defaultTaskRuntimeLimit), force);
    }

    private void refreshWithLease(int taskRuntimeLimit, boolean force) {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        Instant startedAt = Instant.now();
        ClusterLease lease = null;
        try {
            if (!force && clusterLeaseEnabled && leaseService() != null) {
                lease = acquireLease().orElse(null);
                if (lease == null) {
                    lastState = SnapshotRefreshState.skipped(startedAt, "LEASE_NOT_ACQUIRED");
                    return;
                }
            }
            DashboardStreamSnapshot snapshot = refresh(taskRuntimeLimit);
            lastState = SnapshotRefreshState.success(startedAt, Instant.now(), snapshot);
        } catch (RuntimeException ex) {
            lastState = SnapshotRefreshState.failed(startedAt, Instant.now(), ex);
            log.debug("Dashboard snapshot refresh failed: {}", ex.getMessage());
        } finally {
            releaseLease(lease);
            running.set(false);
        }
    }

    DashboardStreamSnapshot refresh(int taskRuntimeLimit) {
        DashboardStreamSnapshot snapshot = composer.compose(Math.max(1, taskRuntimeLimit));
        if (!cacheService.write(taskRuntimeLimit, snapshot)) {
            throw new IllegalStateException("dashboard snapshot cache write failed");
        }
        return snapshot;
    }

    private Optional<ClusterLease> acquireLease() {
        ClusterLeaseService leaseService = leaseService();
        if (leaseService == null) {
            return Optional.empty();
        }
        try {
            return leaseService.tryAcquire(
                    LEASE_NAME,
                    WorkerInstanceIds.resolve(applicationName, null),
                    positiveOr(leaseTtl, Duration.ofSeconds(10)));
        } catch (RuntimeException ex) {
            log.debug("Dashboard snapshot lease acquire failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private void releaseLease(ClusterLease lease) {
        if (lease == null) {
            return;
        }
        ClusterLeaseService leaseService = leaseService();
        if (leaseService == null) {
            return;
        }
        try {
            leaseService.release(lease);
        } catch (RuntimeException ex) {
            log.debug("Dashboard snapshot lease release failed: {}", ex.getMessage());
        }
    }

    private ClusterLeaseService leaseService() {
        return clusterLeaseServiceProvider == null ? null : clusterLeaseServiceProvider.getIfUnique();
    }

    private Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    record SnapshotRefreshState(
            Instant startedAt,
            Instant finishedAt,
            boolean success,
            String status,
            long sequence,
            boolean partial) {

        static SnapshotRefreshState never() {
            return new SnapshotRefreshState(null, null, false, "NEVER_RUN", 0, true);
        }

        static SnapshotRefreshState skipped(Instant startedAt, String reason) {
            return new SnapshotRefreshState(startedAt, Instant.now(), false, reason, 0, true);
        }

        static SnapshotRefreshState success(
                Instant startedAt,
                Instant finishedAt,
                DashboardStreamSnapshot snapshot) {
            return new SnapshotRefreshState(
                    startedAt,
                    finishedAt,
                    true,
                    "OK",
                    snapshot == null ? 0 : snapshot.sequence(),
                    snapshot == null || snapshot.partial());
        }

        static SnapshotRefreshState failed(Instant startedAt, Instant finishedAt, RuntimeException ex) {
            return new SnapshotRefreshState(
                    startedAt,
                    finishedAt,
                    false,
                    ex.getClass().getSimpleName(),
                    0,
                    true);
        }
    }
}
