package com.prodigalgal.ircs.task;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TaskCommandServiceTest {

    private final JdbcCollectionTaskRepository taskRepository = org.mockito.Mockito.mock(JdbcCollectionTaskRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationEventPublisher eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final TaskQueueDispatchService taskQueueDispatchService = org.mockito.Mockito.mock(TaskQueueDispatchService.class);
    private final TaskCommandService service =
            new TaskCommandService(taskRepository, objectMapper, eventPublisher, taskQueueDispatchService);

    @Test
    void startDoesNotBubblePostCommitDispatchFailureAfterTaskIsQueued() {
        UUID taskId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        TaskRuntimeState runtimeState = new TaskRuntimeState(taskId, dataSourceId, "IDLE", true, 1, 1);
        when(taskRepository.findRuntimeState(taskId)).thenReturn(Optional.of(runtimeState));
        when(taskRepository.existsActiveTaskForDataSource(dataSourceId, taskId)).thenReturn(false);
        doThrow(new IllegalStateException("rabbit unavailable"))
                .when(taskQueueDispatchService).dispatchQueuedMaster(taskId, false);

        assertThatCode(() -> service.start(taskId, false)).doesNotThrowAnyException();

        verify(taskRepository).markQueued(runtimeState, false);
        verify(taskQueueDispatchService).dispatchQueuedMaster(taskId, false);
    }
}
