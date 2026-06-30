package com.prodigalgal.ircs.task.job;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleRequest;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.task.application.TrendDiscoveryTaskService;
import com.prodigalgal.ircs.task.domain.MediaRequestCandidate;
import com.prodigalgal.ircs.task.infrastructure.JdbcCollectionTaskRepository;
import com.prodigalgal.ircs.task.infrastructure.TaskDistributedLockRunner;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MediaRequestSchedulerTest {

    private final JdbcCollectionTaskRepository taskRepository = org.mockito.Mockito.mock(JdbcCollectionTaskRepository.class);
    private final TrendDiscoveryTaskService trendDiscoveryTaskService = org.mockito.Mockito.mock(TrendDiscoveryTaskService.class);
    private final MediaRequestScheduler scheduler = scheduler();

    @Test
    void schedulesClaimedMediaRequestsThroughTrendDiscovery() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(taskRepository.claimPendingMediaRequests(20)).thenReturn(List.of(
                new MediaRequestCandidate(firstId, "黑客帝国", 1999, 4),
                new MediaRequestCandidate(secondId, "异形", null, 2)));
        when(trendDiscoveryTaskService.schedule(any(), eq("media-request:" + firstId)))
                .thenReturn(new TrendDiscoveryScheduleResponse(
                        "trend-discovery",
                        2,
                        1,
                        2,
                        0,
                        2,
                        List.of(),
                        List.of()));

        scheduler.schedulePendingMediaRequestsLocked();

        ArgumentCaptor<TrendDiscoveryScheduleRequest> requestCaptor =
                ArgumentCaptor.forClass(TrendDiscoveryScheduleRequest.class);
        verify(trendDiscoveryTaskService).schedule(requestCaptor.capture(), eq("media-request:" + firstId));
        TrendDiscoveryScheduleRequest request = requestCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.keywords()).containsExactly("黑客帝国 1999", "异形");
        org.assertj.core.api.Assertions.assertThat(request.startPage()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(request.endPage()).isEqualTo(1);
        verify(taskRepository).markMediaRequestsScheduled(List.of(firstId, secondId));
    }

    @Test
    void returnsClaimedRequestsToPendingWhenTrendDiscoveryReportsErrors() {
        UUID requestId = UUID.randomUUID();
        when(taskRepository.claimPendingMediaRequests(20))
                .thenReturn(List.of(new MediaRequestCandidate(requestId, "失败片", null, 1)));
        when(trendDiscoveryTaskService.schedule(any(), eq("media-request:" + requestId)))
                .thenReturn(new TrendDiscoveryScheduleResponse(
                        "trend-discovery",
                        1,
                        1,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of("source unavailable")));

        scheduler.schedulePendingMediaRequestsLocked();

        verify(taskRepository).markMediaRequestsPending(List.of(requestId), "source unavailable");
        verify(taskRepository, never()).markMediaRequestsScheduled(any());
    }

    @Test
    void returnsClaimedRequestsToPendingWhenTrendDiscoveryThrows() {
        UUID requestId = UUID.randomUUID();
        when(taskRepository.claimPendingMediaRequests(20))
                .thenReturn(List.of(new MediaRequestCandidate(requestId, "异常片", null, 1)));
        when(trendDiscoveryTaskService.schedule(any(), eq("media-request:" + requestId)))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(scheduler::schedulePendingMediaRequestsLocked)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(taskRepository).markMediaRequestsPending(List.of(requestId), "boom");
    }

    private MediaRequestScheduler scheduler() {
        MediaRequestScheduler result = new MediaRequestScheduler(
                taskRepository,
                trendDiscoveryTaskService,
                TaskDistributedLockRunner.local());
        ReflectionTestUtils.setField(result, "batchSize", 20);
        ReflectionTestUtils.setField(result, "startPage", 1);
        ReflectionTestUtils.setField(result, "endPage", 1);
        ReflectionTestUtils.setField(result, "requestFixedDelayMs", 0);
        ReflectionTestUtils.setField(result, "maxDataSources", 0);
        return result;
    }
}
