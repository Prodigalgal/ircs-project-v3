package com.prodigalgal.ircs.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.contracts.task.TaskMasterSnapshot;
import com.prodigalgal.ircs.contracts.task.TaskPageMessage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskQueueDispatchServiceTest {

    private final JdbcCollectionTaskRepository taskRepository = org.mockito.Mockito.mock(JdbcCollectionTaskRepository.class);
    private final TaskMasterSnapshotService snapshotService = org.mockito.Mockito.mock(TaskMasterSnapshotService.class);
    private final TaskProgressRedisService progressService = org.mockito.Mockito.mock(TaskProgressRedisService.class);
    private final TaskQueuePublisher queuePublisher = org.mockito.Mockito.mock(TaskQueuePublisher.class);
    private final TaskLogService taskLogService = org.mockito.Mockito.mock(TaskLogService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void dispatchesQueuedMasterIntoPageTasksAndRedisSnapshot() {
        UUID taskId = UUID.randomUUID();
        TaskExecutionPlan plan = plan(taskId, 1, 3, 1);
        when(taskRepository.findExecutionPlan(taskId)).thenReturn(Optional.of(plan));
        TaskQueueDispatchService service = service(true, 200);

        service.dispatchQueuedMaster(taskId, false);

        ArgumentCaptor<TaskMasterSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TaskMasterSnapshot.class);
        verify(snapshotService).put(snapshotCaptor.capture());
        TaskMasterSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.masterTaskId()).isEqualTo(taskId);
        assertThat(snapshot.status()).isEqualTo("QUEUED");
        assertThat(snapshot.pageScheduled()).isEqualTo(3);
        assertThat(snapshot.startPage()).isEqualTo(1);
        assertThat(snapshot.endPage()).isEqualTo(3);

        ArgumentCaptor<TaskPageMessage> pageCaptor = ArgumentCaptor.forClass(TaskPageMessage.class);
        verify(queuePublisher, org.mockito.Mockito.times(3)).publishPage(pageCaptor.capture());
        assertThat(pageCaptor.getAllValues())
                .extracting(TaskPageMessage::pageNumber)
                .containsExactly(1, 2, 3);
        assertThat(pageCaptor.getAllValues())
                .allSatisfy(message -> {
                    assertThat(message.masterTaskId()).isEqualTo(taskId);
                    assertThat(message.correlationId()).isEqualTo(taskId.toString());
                    assertThat(message.attempt()).isZero();
                    assertThat(message.options().keyword()).isEqualTo("codex");
                });
    }

    @Test
    void capsInitialPageDispatchWindow() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findExecutionPlan(taskId)).thenReturn(Optional.of(plan(taskId, 5, 20, 5)));
        TaskQueueDispatchService service = service(true, 4);

        service.dispatchQueuedMaster(taskId, true);

        ArgumentCaptor<TaskPageMessage> pageCaptor = ArgumentCaptor.forClass(TaskPageMessage.class);
        verify(queuePublisher, org.mockito.Mockito.times(4)).publishPage(pageCaptor.capture());
        assertThat(pageCaptor.getAllValues())
                .extracting(TaskPageMessage::pageNumber)
                .containsExactly(5, 6, 7, 8);
    }

    @Test
    void disabledQueueFailsMasterWithoutLocalRunnerFallback() {
        UUID taskId = UUID.randomUUID();
        TaskQueueDispatchService service = service(false, 200);

        service.dispatchQueuedMaster(taskId, false);

        verify(taskRepository).fail(
                org.mockito.Mockito.eq(taskId),
                org.mockito.Mockito.contains("no local runner fallback"));
        verify(queuePublisher, never()).publishPage(org.mockito.Mockito.any());
        verify(snapshotService, never()).put(org.mockito.Mockito.any());
    }

    private TaskQueueDispatchService service(boolean enabled, int initialPageWindow) {
        return new TaskQueueDispatchService(
                taskRepository,
                snapshotService,
                progressService,
                queuePublisher,
                taskLogService,
                objectMapper,
                enabled,
                initialPageWindow,
                0);
    }

    private TaskExecutionPlan plan(UUID taskId, Integer startPage, Integer endPage, Integer currentPage) {
        return new TaskExecutionPlan(
                taskId,
                "Codex task",
                UUID.randomUUID(),
                "QUEUED",
                true,
                startPage,
                endPage,
                currentPage,
                "codex",
                0,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                "{}");
    }
}
