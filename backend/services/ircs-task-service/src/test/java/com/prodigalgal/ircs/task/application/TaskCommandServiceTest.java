package com.prodigalgal.ircs.task.application;




import com.prodigalgal.ircs.task.domain.TaskRuntimeState;
import com.prodigalgal.ircs.task.infrastructure.JdbcCollectionTaskRepository;
import com.prodigalgal.ircs.task.runtime.TaskProgressStore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TaskCommandServiceTest {

    private final JdbcCollectionTaskRepository taskRepository = org.mockito.Mockito.mock(JdbcCollectionTaskRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationEventPublisher eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final TaskQueueDispatchService taskQueueDispatchService = org.mockito.Mockito.mock(TaskQueueDispatchService.class);
    private final TaskProgressStore progressService = org.mockito.Mockito.mock(TaskProgressStore.class);
    private final RecordingExecutor taskQueueDispatchExecutor = new RecordingExecutor();
    private final TaskCommandService service =
            new TaskCommandService(taskRepository, objectMapper, eventPublisher, taskQueueDispatchService,
                    progressService, taskQueueDispatchExecutor);

    @Test
    void startSubmitsPostCommitDispatchAsynchronouslyAfterTaskIsQueued() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        TaskRuntimeState runtimeState = new TaskRuntimeState(taskId, dataSourceId, "IDLE", true, 1, 1, null);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(runtimeState));
        when(taskRepository.queueRun(runtimeState, false, true)).thenReturn(Optional.of(runId));
        when(taskRepository.existsActiveTaskForDataSource(dataSourceId, taskId)).thenReturn(false);

        assertThatCode(() -> service.start(taskId, false)).doesNotThrowAnyException();

        verify(taskRepository).queueRun(runtimeState, false, true);
        verify(taskQueueDispatchService, never()).dispatchQueuedMaster(runId, false);
        assertThat(taskQueueDispatchExecutor.pending).isNotNull();

        taskQueueDispatchExecutor.runPending();

        verify(taskQueueDispatchService).dispatchQueuedMaster(runId, false);
    }

    @Test
    void startDoesNotBubbleAsyncDispatchFailureAfterTaskIsQueued() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        TaskRuntimeState runtimeState = new TaskRuntimeState(taskId, dataSourceId, "IDLE", true, 1, 1, null);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(runtimeState));
        when(taskRepository.queueRun(runtimeState, false, true)).thenReturn(Optional.of(runId));
        when(taskRepository.existsActiveTaskForDataSource(dataSourceId, taskId)).thenReturn(false);
        doThrow(new IllegalStateException("rabbit unavailable"))
                .when(taskQueueDispatchService).dispatchQueuedMaster(runId, false);

        service.start(taskId, false);

        assertThatCode(taskQueueDispatchExecutor::runPending).doesNotThrowAnyException();
        verify(taskRepository).queueRun(runtimeState, false, true);
        verify(taskQueueDispatchService).dispatchQueuedMaster(runId, false);
    }

    @Test
    void concurrentStartLosingCasDoesNotDispatch() {
        UUID taskId = UUID.randomUUID();
        TaskRuntimeState state = new TaskRuntimeState(taskId, UUID.randomUUID(), "IDLE", true, 1, 1, null);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(state));
        when(taskRepository.queueRun(state, false, false)).thenReturn(Optional.empty());

        assertThat(service.startInternal(taskId, false)).isFalse();
        assertThat(taskQueueDispatchExecutor.pending).isNull();
        verify(taskQueueDispatchService, never()).dispatchQueuedMaster(taskId, false);
    }

    @Test
    void pauseMarksRuntimeMasterAsHeldAfterDbStatusChange() {
        UUID taskId = UUID.randomUUID();

        UUID runId = UUID.randomUUID();
        when(taskRepository.pause(taskId)).thenReturn(Optional.of(runId));

        service.pause(taskId);

        verify(taskRepository).pause(taskId);
        verify(progressService).holdMaster(
                org.mockito.Mockito.eq(runId),
                org.mockito.Mockito.eq("PAUSED"),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any());
    }

    @Test
    void catalogStartRechecksSourceConflictBeforeQueuing() {
        UUID taskId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        TaskRuntimeState state = new TaskRuntimeState(taskId, sourceId, "IDLE", true, 1, 1, null);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(state));
        when(taskRepository.existsCatalogSourceConflict(sourceId, taskId)).thenReturn(true);

        assertThat(service.startCatalog(taskId, sourceId, () -> {})).isFalse();

        verify(taskRepository, never()).queueRun(state, false, true);
        assertThat(taskQueueDispatchExecutor.pending).isNull();
    }

    @Test
    void catalogStartDoesNotQueueTaskWhoseSourceWasChangedByAnOperator() {
        UUID taskId = UUID.randomUUID();
        UUID expectedSource = UUID.randomUUID();
        TaskRuntimeState state = new TaskRuntimeState(taskId, UUID.randomUUID(), "IDLE", true, 1, 1, null);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(state));

        assertThat(service.startCatalog(taskId, expectedSource, () -> {})).isFalse();

        verify(taskRepository, never()).queueRun(state, false, true);
    }

    @Test
    void catalogStartUsesExistingCasAndAsynchronousDispatchAfterOwnershipChecks() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        TaskRuntimeState state = new TaskRuntimeState(taskId, sourceId, "IDLE", true, 1, 1, null);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(state));
        when(taskRepository.queueRun(state, false, true)).thenReturn(Optional.of(runId));
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();

        assertThat(service.startCatalog(taskId, sourceId, checks::incrementAndGet)).isTrue();

        assertThat(checks.get()).isEqualTo(2);
        verify(taskRepository).queueRun(state, false, true);
        assertThat(taskQueueDispatchExecutor.pending).isNotNull();
        verify(taskQueueDispatchService, never()).dispatchQueuedMaster(runId, false);
    }

    @Test
    void catalogStartStopsBeforeQueuingWhenRequestLeaseIsLost() {
        UUID taskId = UUID.randomUUID();

        assertThatThrownBy(() -> service.startCatalog(taskId, UUID.randomUUID(), () -> {
            throw new IllegalStateException("lease lost");
        })).isInstanceOf(IllegalStateException.class).hasMessage("lease lost");

        verify(taskRepository, never()).findRuntimeState(taskId);
        assertThat(taskQueueDispatchExecutor.pending).isNull();
    }

    private static final class RecordingExecutor implements Executor {
        private Runnable pending;

        @Override
        public void execute(Runnable command) {
            this.pending = command;
        }

        void runPending() {
            assertThat(pending).isNotNull();
            pending.run();
        }
    }
}
