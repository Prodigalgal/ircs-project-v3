package com.prodigalgal.ircs.ops.queue.dlq.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementQueueClient;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementQueueSnapshot;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementQueues;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitDlqServiceTest {

    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitManagementQueueClient queueClient = mock(RabbitManagementQueueClient.class);
    private final RabbitDlqService service = new RabbitDlqService(rabbitAdmin, rabbitTemplate, queueClient);

    @Test
    void listQueuesUsesNativeRabbitDlqSnapshotsAndIncludesUnmanagedDlqs() {
        when(queueClient.fetchQueues()).thenReturn(Optional.of(new RabbitManagementQueues(null, List.of(
                new RabbitManagementQueueSnapshot(QueueTopic.TASK_DETAIL.dlqName(), 7, 5, 12, 0),
                new RabbitManagementQueueSnapshot("q.legacy.custom.dlq", 3, 2, 5, 0),
                new RabbitManagementQueueSnapshot("q.task.detail", 99, 0, 99, 1)
        ))));

        List<RabbitDlqQueueResponse> responses = service.listQueues();

        RabbitDlqQueueResponse taskDetail = find(responses, QueueTopic.TASK_DETAIL.dlqName());
        assertThat(taskDetail.topic()).isEqualTo(QueueTopic.TASK_DETAIL.name());
        assertThat(taskDetail.messagesReady()).isEqualTo(7);
        assertThat(taskDetail.messagesUnacknowledged()).isEqualTo(5);
        assertThat(taskDetail.messagesTotal()).isEqualTo(12);
        assertThat(taskDetail.actionSupported()).isTrue();

        RabbitDlqQueueResponse unmanaged = find(responses, "q.legacy.custom.dlq");
        assertThat(unmanaged.sourceQueueName()).isEqualTo("q.legacy.custom");
        assertThat(unmanaged.messagesTotal()).isEqualTo(5);
        assertThat(unmanaged.actionSupported()).isFalse();
    }

    @Test
    void listQueuesFallsBackToRabbitAdminForKnownTopologyWhenManagementUnavailable() {
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 9);
        props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0);
        when(queueClient.fetchQueues()).thenReturn(Optional.empty());
        when(rabbitAdmin.getQueueProperties(QueueTopic.INGEST_VIDEO.dlqName())).thenReturn(props);

        RabbitDlqQueueResponse ingestDlq = find(service.listQueues(), QueueTopic.INGEST_VIDEO.dlqName());

        assertThat(ingestDlq.messagesReady()).isEqualTo(9);
        assertThat(ingestDlq.messagesUnacknowledged()).isZero();
        assertThat(ingestDlq.messagesTotal()).isEqualTo(9);
        assertThat(ingestDlq.actionSupported()).isTrue();
    }

    private RabbitDlqQueueResponse find(List<RabbitDlqQueueResponse> responses, String queueName) {
        return responses.stream()
                .filter(response -> response.queueName().equals(queueName))
                .findFirst()
                .orElseThrow();
    }
}
