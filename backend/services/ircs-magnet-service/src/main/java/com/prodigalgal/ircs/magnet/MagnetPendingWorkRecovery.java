package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.lease.ClusterLeaseGuard;
import com.prodigalgal.ircs.common.lock.DistributedLockService;
import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded durable-job reconciliation. Queue failures are repaired by the service, not an operator script. */
@Component
@Slf4j
final class MagnetPendingWorkRecovery {
    private final JdbcTemplate jdbc;
    private final JdbcMagnetRepository repository;
    private final MagnetSearchJobCoordinator coordinator;
    private final MagnetWorkPublisher publisher;
    private final MagnetWorkSubmissionStore submissions;
    private final DistributedLockService locks;
    private final boolean enabled;
    private final int batchSize;
    private final long maxQueueDepth;
    private final Duration leaseDuration;
    private final String owner = UUID.randomUUID().toString();
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("ircs-magnet-recovery-");
    private final AtomicBoolean running = new AtomicBoolean();
    private Instant afterCreatedAt = Instant.EPOCH;
    private UUID afterId = new UUID(0, 0);

    MagnetPendingWorkRecovery(JdbcTemplate jdbc, JdbcMagnetRepository repository, MagnetSearchJobCoordinator coordinator,
            ObjectProvider<MagnetWorkPublisher> publisher, MagnetWorkSubmissionStore submissions, DistributedLockService locks,
            @Value("${app.magnet.recovery.enabled:true}") boolean enabled,
            @Value("${app.magnet.recovery.batch-size:100}") int batchSize,
            @Value("${app.magnet.recovery.max-queue-depth:2000}") long maxQueueDepth,
            @Value("${app.magnet.execution.lease:PT2M}") Duration leaseDuration) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.coordinator = coordinator;
        this.publisher = publisher == null ? null : publisher.getIfAvailable();
        this.submissions = submissions;
        this.locks = locks;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
        this.maxQueueDepth = Math.max(1, maxQueueDepth);
        this.leaseDuration = leaseDuration == null || leaseDuration.compareTo(Duration.ofSeconds(10)) < 0
                ? Duration.ofMinutes(2) : leaseDuration;
    }

    @Scheduled(initialDelayString = "${app.magnet.recovery.initial-delay-ms:30000}",
            fixedDelayString = "${app.magnet.recovery.fixed-delay-ms:60000}")
    void scheduled() {
        if (!enabled || !running.compareAndSet(false, true)) { return; }
        ScheduledTriggers.submit(executor, () -> {
            try {
                locks.runWithLock("lock:magnet:pending-recovery", owner, Duration.ofMinutes(2), this::runBatch);
            } finally { running.set(false); }
        }, log, "magnet.pending-recovery");
    }

    int runBatch() {
        if (!enabled || publisher == null) { return 0; }
        Instant cutoff = Instant.now().minus(leaseDuration).minusSeconds(5);
        List<PendingJob> jobs = jdbc.query("""
                select id, unified_video_id, coalesce(version, 0) as generation, created_at
                from magnet_search_jobs
                where status in ('PENDING', 'RUNNING') and updated_at < ?
                  and (created_at, id) > (?, ?)
                order by created_at, id limit ?
                """, (rs, row) -> new PendingJob(rs.getObject("id", UUID.class), rs.getObject("unified_video_id", UUID.class),
                        rs.getLong("generation"), rs.getTimestamp("created_at").toInstant()),
                Timestamp.from(cutoff), Timestamp.from(afterCreatedAt), afterId, batchSize);
        int recovered = 0;
        for (PendingJob job : jobs) {
            ClusterLeaseGuard.checkCurrentOwnership();
            String state = submissions.status(MagnetWorkTypes.searchTaskId(job.id()));
            if ("PERMANENT_FAILURE".equals(state) || "SUCCESS".equals(state)) {
                if (coordinator.failAbandoned(job.id(), job.unifiedVideoId(), job.generation(), cutoff,
                        "MAGNET_QUEUE_TERMINAL_STATE:" + state)) { recovered++; }
            } else if (state == null && publisher.backlogDepth() < maxQueueDepth) {
                var current = repository.findSearchJob(job.id());
                if (current.isPresent() && ("PENDING".equals(current.get().status()) || "RUNNING".equals(current.get().status()))) {
                    publisher.enqueue(current.get());
                    recovered++;
                }
            }
            afterCreatedAt = job.createdAt();
            afterId = job.id();
        }
        if (jobs.size() < batchSize) { afterCreatedAt = Instant.EPOCH; afterId = new UUID(0, 0); }
        if (recovered > 0) { log.info("Magnet durable job reconciliation: scanned={}, recovered={}", jobs.size(), recovered); }
        return recovered;
    }

    @PreDestroy
    void close() { executor.shutdownNow(); }

    private record PendingJob(UUID id, UUID unifiedVideoId, long generation, Instant createdAt) { }
}
