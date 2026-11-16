package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.lock.DistributedLockService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MagnetPendingWorkRecoveryTest {
    private MagnetPendingWorkRecovery recovery;

    @AfterEach
    void closeExecutor() {
        if (recovery != null) recovery.close();
    }

    @Test
    void disabledAndMissingPublisherDoNotReadDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MagnetPendingWorkRecovery disabled = recovery(jdbc, false, mockPublisherProvider(null), mock(MagnetWorkSubmissionStore.class));
        assertThat(disabled.runBatch()).isZero();
        disabled.close();
        recovery = recovery(jdbc, true, mockPublisherProvider(null), mock(MagnetWorkSubmissionStore.class));

        assertThat(recovery.runBatch()).isZero();
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void queueFullDoesNotRepublishJobs() {
        UUID id = UUID.randomUUID();
        JdbcTemplate jdbc = jdbcWithJobs(List.of(new JobRow(id, UUID.randomUUID(), 1, Instant.now().minusSeconds(600))));
        MagnetWorkPublisher publisher = mock(MagnetWorkPublisher.class);
        when(publisher.backlogDepth()).thenReturn(20L);
        MagnetWorkSubmissionStore submissions = submissionsWithStatus(null);
        recovery = recovery(jdbc, mock(JdbcMagnetRepository.class), mock(MagnetSearchJobCoordinator.class), true,
                mockPublisherProvider(publisher), submissions, mock(DistributedLockService.class), 10);

        assertThat(recovery.runBatch()).isZero();
        verify(publisher).backlogDepth();
        verify(publisher, never()).enqueue(any());
    }

    @Test
    void missingQueueRecordRepublishesTheSameDurableJob() {
        UUID jobId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        Instant created = Instant.now().minusSeconds(600);
        JdbcTemplate jdbc = jdbcWithJobs(List.of(new JobRow(jobId, videoId, 3, created)));
        MagnetSearchJobSummary summary = summary(jobId, videoId);
        JdbcMagnetRepository repository = mock(JdbcMagnetRepository.class);
        when(repository.findSearchJob(jobId)).thenReturn(Optional.of(summary));
        MagnetWorkPublisher publisher = mock(MagnetWorkPublisher.class);
        when(publisher.backlogDepth()).thenReturn(0L);
        recovery = recovery(jdbc, repository, mock(MagnetSearchJobCoordinator.class), true,
                mockPublisherProvider(publisher), submissionsWithStatus(null), mock(DistributedLockService.class), 10);

        assertThat(recovery.runBatch()).isEqualTo(1);
        verify(publisher).enqueue(summary);
    }

    @Test
    void terminalQueueStatesOnlyFailAbandonedDurableJobAndNeverRepublish() {
        for (String state : List.of("PERMANENT_FAILURE", "SUCCESS")) {
            UUID jobId = UUID.randomUUID();
            UUID videoId = UUID.randomUUID();
            JdbcTemplate jdbc = jdbcWithJobs(List.of(new JobRow(jobId, videoId, 7, Instant.now().minusSeconds(600))));
            MagnetSearchJobCoordinator coordinator = mock(MagnetSearchJobCoordinator.class);
            when(coordinator.failAbandoned(any(), any(), anyLong(), any(Instant.class), anyString())).thenReturn(true);
            MagnetWorkPublisher publisher = mock(MagnetWorkPublisher.class);
            recovery = recovery(jdbc, mock(JdbcMagnetRepository.class), coordinator, true,
                    mockPublisherProvider(publisher), submissionsWithStatus(state), mock(DistributedLockService.class), 10);

            assertThat(recovery.runBatch()).isEqualTo(1);
            org.mockito.ArgumentCaptor<Instant> expiry = org.mockito.ArgumentCaptor.forClass(Instant.class);
            verify(coordinator).failAbandoned(org.mockito.ArgumentMatchers.eq(jobId), org.mockito.ArgumentMatchers.eq(videoId),
                    org.mockito.ArgumentMatchers.eq(7L), expiry.capture(),
                    org.mockito.ArgumentMatchers.eq("MAGNET_QUEUE_TERMINAL_STATE:" + state));
            assertThat(expiry.getValue()).isBefore(Instant.now().minusSeconds(120));
            verify(publisher, never()).enqueue(any());
            recovery.close();
        }
    }

    @Test
    void pendingOrProcessingQueueRecordsHaveNoRecoverySideEffects() {
        for (String state : List.of("PENDING", "PROCESSING")) {
            UUID jobId = UUID.randomUUID();
            JdbcTemplate jdbc = jdbcWithJobs(List.of(new JobRow(jobId, UUID.randomUUID(), 1, Instant.now().minusSeconds(600))));
            MagnetSearchJobCoordinator coordinator = mock(MagnetSearchJobCoordinator.class);
            MagnetWorkPublisher publisher = mock(MagnetWorkPublisher.class);
            recovery = recovery(jdbc, mock(JdbcMagnetRepository.class), coordinator, true,
                    mockPublisherProvider(publisher), submissionsWithStatus(state), mock(DistributedLockService.class), 10);
            assertThat(recovery.runBatch()).isZero();
            verify(coordinator, never()).failAbandoned(any(), any(), anyLong(), any(Instant.class), anyString());
            verify(publisher, never()).enqueue(any());
            verify(publisher, never()).backlogDepth();
            recovery.close();
        }
    }

    @Test
    void queryFailureDoesNotPublishJobs() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MagnetWorkPublisher publisher = mock(MagnetWorkPublisher.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class, Answers.RETURNS_DEEP_STUBS);
            when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
            when(rs.getObject("unified_video_id", UUID.class)).thenReturn(UUID.randomUUID());
            when(rs.getLong("generation")).thenReturn(1L);
            when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now().minusSeconds(600)));
            try { mapper.mapRow(rs, 0); } catch (Exception ex) { throw new IllegalStateException(ex); }
            throw new IllegalStateException("synthetic mapping failure");
        });
        recovery = recovery(jdbc, true, mockPublisherProvider(publisher),
                submissionsWithStatus("PENDING"));
        org.assertj.core.api.Assertions.assertThatThrownBy(recovery::runBatch).isInstanceOf(IllegalStateException.class);
        verify(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(publisher, never()).enqueue(any());
    }

    private MagnetPendingWorkRecovery recovery(JdbcTemplate jdbc, boolean enabled,
            ObjectProvider<MagnetWorkPublisher> publisher, MagnetWorkSubmissionStore submissions) {
        return recovery(jdbc, mock(JdbcMagnetRepository.class), mock(MagnetSearchJobCoordinator.class), enabled,
                publisher, submissions, mock(DistributedLockService.class), 10);
    }

    private MagnetPendingWorkRecovery recovery(JdbcTemplate jdbc, JdbcMagnetRepository repository,
            MagnetSearchJobCoordinator coordinator, boolean enabled, ObjectProvider<MagnetWorkPublisher> publisher,
            MagnetWorkSubmissionStore submissions, DistributedLockService locks, long maxDepth) {
        return new MagnetPendingWorkRecovery(jdbc, repository, coordinator, publisher, submissions, locks,
                enabled, 20, maxDepth, Duration.ofMinutes(2));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<MagnetWorkPublisher> mockPublisherProvider(MagnetWorkPublisher publisher) {
        ObjectProvider<MagnetWorkPublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        return provider;
    }

    private MagnetWorkSubmissionStore submissionsWithStatus(String status) {
        MagnetWorkSubmissionStore submissions = mock(MagnetWorkSubmissionStore.class);
        when(submissions.status(anyString())).thenReturn(status);
        return submissions;
    }

    private JdbcTemplate jdbcWithJobs(List<JobRow> jobs) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") RowMapper<Object> mapper = invocation.getArgument(1);
            java.util.ArrayList<Object> mapped = new java.util.ArrayList<>();
            for (int index = 0; index < jobs.size(); index++) {
                JobRow job = jobs.get(index);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("id", UUID.class)).thenReturn(job.id());
                when(rs.getObject("unified_video_id", UUID.class)).thenReturn(job.videoId());
                when(rs.getLong("generation")).thenReturn(job.generation());
                when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(job.createdAt()));
                try { mapped.add(mapper.mapRow(rs, index)); } catch (Exception ex) { throw new IllegalStateException(ex); }
            }
            return mapped;
        });
        return jdbc;
    }

    private MagnetSearchJobSummary summary(UUID id, UUID videoId) {
        return new MagnetSearchJobSummary(id, videoId, "manual", "PENDING", List.of(), List.of(), null, null,
                0, 0, 0, null, null, List.of(), null, null);
    }

    private record JobRow(UUID id, UUID videoId, long generation, Instant createdAt) { }
}
