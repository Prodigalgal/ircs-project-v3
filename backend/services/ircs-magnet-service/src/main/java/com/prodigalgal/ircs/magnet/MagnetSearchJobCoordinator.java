package com.prodigalgal.ircs.magnet;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Short database-only transitions. Network calls must stay outside these callbacks. */
@Service
public final class MagnetSearchJobCoordinator {

    private static final int DUPLICATE_BATCH_LIMIT = 1000;
    private static final Set<String> OPEN_STATUSES = Set.of("PENDING", "RUNNING");
    private final JdbcMagnetRepository repository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public MagnetSearchJobCoordinator(JdbcMagnetRepository repository, JdbcTemplate jdbc,
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(30);
    }

    public record Enqueued(MagnetSearchJobSummary job, boolean created) { }
    public record Claim(MagnetSearchJobSummary job, Long generation, boolean acquired) { }
    public record Lease(UUID jobId, UUID catalogEntryId, long generation, Duration duration) {
        public Lease {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(catalogEntryId, "catalogEntryId");
            validDuration(duration);
        }
    }

    public Enqueued enqueue(UUID catalogEntryId, String trigger, List<MagnetExternalIdQuery> queries) {
        return transactions.execute(transaction -> {
            lockTarget(catalogEntryId);
            List<UUID> open = openJobs(catalogEntryId);
            if (!open.isEmpty()) {
                return new Enqueued(job(open.getFirst()), false);
            }
            return new Enqueued(repository.createPendingSearchJob(catalogEntryId, trigger, queries), true);
        });
    }

    public Claim claim(UUID jobId, UUID catalogEntryId, List<MagnetExternalIdQuery> queries, Duration duration) {
        validDuration(duration);
        return transactions.execute(transaction -> {
            lockTarget(catalogEntryId);
            JobState requested = lockJob(jobId);
            checkTarget(requested, catalogEntryId);
            if (!OPEN_STATUSES.contains(requested.status())) {
                return new Claim(job(jobId), null, false);
            }
            Instant now = databaseNow();
            List<UUID> active = jdbc.queryForList("""
                    select id from magnet_discovery_jobs
                    where catalog_entry_id = ? and status = 'RUNNING' and updated_at > ?
                    limit 1
                    """, UUID.class, catalogEntryId, Timestamp.from(now.minus(duration)));
            if (!active.isEmpty()) {
                throw new MagnetJobBusyException("MAGNET_SEARCH_JOB_ALREADY_RUNNING");
            }
            repository.markSearchJobRunning(jobId, queries).orElseThrow(
                    () -> new MagnetJobBusyException("MAGNET_SEARCH_JOB_NOT_CLAIMABLE"));
            // Use the database clock for the heartbeat, not a worker's wall clock.
            jdbc.update("update magnet_discovery_jobs set updated_at = clock_timestamp() where id = ?", jobId);
            long generation = lockJob(jobId).generation();
            jdbc.update("""
                    update magnet_discovery_jobs
                    set status = 'SKIPPED', skipped_reason = ?, finished_at = clock_timestamp(),
                        updated_at = clock_timestamp(), version = coalesce(version, 0) + 1
                    where catalog_entry_id = ? and id <> ? and status = 'RUNNING' and updated_at <= ?
                    """, "STALE_DUPLICATE_OF:" + jobId, catalogEntryId, jobId, Timestamp.from(now.minus(duration)));
            jdbc.update("""
                    update magnet_discovery_jobs
                    set status = 'SKIPPED', skipped_reason = ?, finished_at = clock_timestamp(),
                        updated_at = clock_timestamp(), version = coalesce(version, 0) + 1
                    where id in (
                        select pending.id from magnet_discovery_jobs pending
                        where pending.catalog_entry_id = ? and pending.status = 'PENDING' and pending.id <> ?
                          and not exists (select 1 from magnet_discovery_provider_runs history where history.job_id = pending.id)
                        order by pending.created_at asc, pending.id asc limit ?
                    ) and status = 'PENDING'
                    """, "DUPLICATE_OF:" + jobId, catalogEntryId, jobId, DUPLICATE_BATCH_LIMIT);
            return new Claim(job(jobId), generation, true);
        });
    }

    public boolean renew(Lease lease) {
        return Boolean.TRUE.equals(transactions.execute(transaction -> {
            lockTarget(lease.catalogEntryId());
            JobState current = lockJob(lease.jobId());
            if (!owns(lease, current, databaseNow())) return false;
            jdbc.update("update magnet_discovery_jobs set updated_at = clock_timestamp() where id = ?", lease.jobId());
            return true;
        }));
    }

    /** Holds row locks only around a bounded database write, with fencing before and after it. */
    public <T> T withinLease(Lease lease, Supplier<T> databaseWrite) {
        Objects.requireNonNull(databaseWrite, "databaseWrite");
        return transactions.execute(transaction -> {
            MagnetQueueClaimGuard.checkCurrentOwnership();
            // All operations take UV then job locks, including inserts referencing the UV FK.
            lockTarget(lease.catalogEntryId());
            JobState before = lockJob(lease.jobId());
            if (!owns(lease, before, databaseNow())) throw lost();
            MagnetQueueClaimGuard.checkCurrentOwnership();
            T result = databaseWrite.get();
            JobState after = lockJob(lease.jobId());
            Instant now = databaseNow();
            boolean sameGeneration = after.generation() == lease.generation() && after.status().equals("RUNNING");
            boolean completed = after.generation() == lease.generation() + 1 && !after.status().equals("RUNNING");
            if (!before.updatedAt().plus(lease.duration()).isAfter(now)
                    || !after.catalogEntryId().equals(lease.catalogEntryId()) || !(sameGeneration || completed)) {
                throw lost();
            }
            MagnetQueueClaimGuard.checkCurrentOwnership();
            return result;
        });
    }

    public MagnetSearchJobSummary retry(Lease lease, String reason) {
        return withinLease(lease, () -> {
            jdbc.update("""
                    update magnet_discovery_jobs
                    set status = 'PENDING', finished_at = null, error_message = ?,
                        updated_at = clock_timestamp(), version = coalesce(version, 0) + 1
                    where id = ?
                    """, reason, lease.jobId());
            return job(lease.jobId());
        });
    }

    public MagnetSearchJobSummary finish(Lease lease, List<String> providerCodes, int totalCandidates,
                                         int acceptedCount, int failedRuns) {
        return withinLease(lease, () -> repository.finishSearchJob(lease.jobId(), providerCodes,
                totalCandidates, acceptedCount, failedRuns));
    }

    public MagnetSearchJobSummary fail(Lease lease, String reason) {
        return withinLease(lease, () -> {
            jdbc.update("""
                    update magnet_discovery_jobs set status = 'FAILED', error_message = ?,
                        finished_at = clock_timestamp(), updated_at = clock_timestamp(), version = coalesce(version, 0) + 1
                    where id = ?
                    """, reason, lease.jobId());
            return job(lease.jobId());
        });
    }

    /** Reconciles an abandoned durable job with a terminal queue record; never steals a live execution. */
    public boolean failAbandoned(UUID jobId, UUID catalogEntryId, long generation, Instant olderThan, String reason) {
        return Boolean.TRUE.equals(transactions.execute(transaction -> {
            lockTarget(catalogEntryId);
            JobState current = lockJob(jobId);
            if (!current.catalogEntryId().equals(catalogEntryId) || current.generation() != generation
                    || !OPEN_STATUSES.contains(current.status()) || current.updatedAt().isAfter(olderThan)) { return false; }
            return jdbc.update("""
                    update magnet_discovery_jobs set status = 'FAILED', error_message = ?, finished_at = clock_timestamp(),
                        updated_at = clock_timestamp(), version = coalesce(version, 0) + 1 where id = ?
                    """, reason, jobId) == 1;
        }));
    }

    private void lockTarget(UUID catalogEntryId) {
        List<UUID> target = jdbc.queryForList("select id from catalog_entries where id = ? for update",
                UUID.class, catalogEntryId);
        if (target.isEmpty()) throw new IllegalArgumentException("MAGNET_CATALOG_ENTRY_NOT_FOUND");
    }

    private JobState lockJob(UUID jobId) {
        List<JobState> rows = jdbc.query("""
                select id, catalog_entry_id, status, coalesce(version, 0) as generation, updated_at
                from magnet_discovery_jobs where id = ? for update
                """, (rs, row) -> new JobState(rs.getObject("id", UUID.class),
                rs.getObject("catalog_entry_id", UUID.class), rs.getString("status"),
                rs.getLong("generation"), rs.getTimestamp("updated_at").toInstant()), jobId);
        if (rows.isEmpty()) throw new IllegalArgumentException("MAGNET_SEARCH_JOB_NOT_FOUND");
        return rows.getFirst();
    }

    private List<UUID> openJobs(UUID catalogEntryId) {
        return jdbc.queryForList("""
                select id from magnet_discovery_jobs
                where catalog_entry_id = ? and status in ('PENDING', 'RUNNING')
                order by case status when 'RUNNING' then 0 else 1 end, created_at asc, id asc limit 1
                """, UUID.class, catalogEntryId);
    }

    private MagnetSearchJobSummary job(UUID jobId) {
        return repository.findSearchJob(jobId).orElseThrow();
    }

    private Instant databaseNow() {
        return Objects.requireNonNull(jdbc.queryForObject("select clock_timestamp()", Timestamp.class)).toInstant();
    }

    private static boolean owns(Lease lease, JobState state, Instant now) {
        return state.catalogEntryId().equals(lease.catalogEntryId()) && state.status().equals("RUNNING")
                && state.generation() == lease.generation() && state.updatedAt().plus(lease.duration()).isAfter(now);
    }

    private static void checkTarget(JobState job, UUID catalogEntryId) {
        if (!job.catalogEntryId().equals(catalogEntryId)) throw new IllegalArgumentException("MAGNET_JOB_TARGET_MISMATCH");
    }

    private static void validDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("MAGNET_LEASE_DURATION_MUST_BE_POSITIVE");
        }
    }

    private static MagnetLeaseLostException lost() { return new MagnetLeaseLostException("MAGNET_SEARCH_JOB_LEASE_LOST"); }
    private record JobState(UUID id, UUID catalogEntryId, String status, long generation, Instant updatedAt) { }

    public static final class MagnetJobBusyException extends IllegalStateException {
        public MagnetJobBusyException(String message) { super(message); }
    }

    public static final class MagnetLeaseLostException extends IllegalStateException {
        public MagnetLeaseLostException(String message) { super(message); }
    }
}
