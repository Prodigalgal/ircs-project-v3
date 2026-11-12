package com.prodigalgal.ircs.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.prodigalgal.ircs.messaging.MessagePublisher;

class TaskRetryPublisherTest {

    private final MessagePublisher messagePublisher = org.mockito.Mockito.mock(MessagePublisher.class);
    private final TaskRetryPublisher publisher = new TaskRetryPublisher(messagePublisher);

    @Test
    void publishesRetryMessageWithIncrementedRetryCount() {
        Message message = message("{}", 1);

        int retryCount = publisher.publishRetry(QueueTopic.TASK_PAGE, message, new IllegalStateException("temporary"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messagePublisher).publish(eq(QueueTopic.Names.TASK_X), eq(QueueTopic.TASK_PAGE.retryRoutingKey()), messageCaptor.capture());
        assertThat(retryCount).isEqualTo(2);
        assertThat(messageCaptor.getValue().getMessageProperties().getHeaders())
                .containsEntry(TaskRetryPublisher.HEADER_RETRY_COUNT, 2)
                .containsEntry(TaskRetryPublisher.HEADER_DISPOSITION, TaskRetryPublisher.DISPOSITION_RETRY)
                .containsEntry(TaskRetryPublisher.HEADER_ORIGINAL_ROUTING_KEY, QueueTopic.TASK_PAGE.routingKey());
    }

    @Test
    void publishesDlqMessageToSharedDlx() {
        Message message = message("{}", 3);

        publisher.publishDlq(QueueTopic.TASK_DETAIL, message, new IllegalArgumentException("fatal"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messagePublisher).publish(eq(QueueTopic.Names.DLX), eq(QueueTopic.TASK_DETAIL.dlqName()), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageProperties().getHeaders())
                .containsEntry(TaskRetryPublisher.HEADER_RETRY_COUNT, 3)
                .containsEntry(TaskRetryPublisher.HEADER_DISPOSITION, TaskRetryPublisher.DISPOSITION_DLQ);
    }

    private Message message(String payload, int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(TaskRetryPublisher.HEADER_RETRY_COUNT, retryCount);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
