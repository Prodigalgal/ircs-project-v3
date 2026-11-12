package com.prodigalgal.ircs.ops.queue.dlq.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.messaging.MessagePublisher;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementMessageSample;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueClient;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueSnapshot;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueues;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;

class DeadLetterServiceTest {

    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final BrokerManagementQueueClient queueClient = mock(BrokerManagementQueueClient.class);
    private final RuntimeConfigService runtimeConfig = mock(RuntimeConfigService.class);
    private final MessagePublisher messagePublisher = mock(MessagePublisher.class);
    private final DeadLetterService service = new DeadLetterService(
            rabbitAdmin,
            rabbitTemplate,
            queueClient,
            runtimeConfig,
            messagePublisher);

    @Test
    void replayAcknowledgesOnlyAfterConfirmedPublishAndStartsFreshRetryBudget() throws Exception {
        Channel channel = messageChannel(QueueTopic.TASK_DETAIL);

        assertThat(service.retry(QueueTopic.TASK_DETAIL.dlqName(), 1).affected()).isEqualTo(1);

        var order = inOrder(channel, messagePublisher);
        order.verify(channel).basicGet(QueueTopic.TASK_DETAIL.dlqName(), false);
        var message = org.mockito.ArgumentCaptor.forClass(Message.class);
        order.verify(messagePublisher).publish(eq(QueueTopic.TASK_DETAIL.exchange()), eq(QueueTopic.TASK_DETAIL.routingKey()), message.capture());
        order.verify(channel).basicAck(42L, false);
        assertThat(message.getValue().getBody()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        assertThat(message.getValue().getMessageProperties().getHeaders().get("x-ircs-retry-count")).isEqualTo(0);
        assertThat(message.getValue().getMessageProperties().getHeaders().get("x-ircs-manual-dlq-replay-count")).isEqualTo(1);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void failedReplayRequeuesOriginalWithoutAcknowledgingIt() throws Exception {
        Channel channel = messageChannel(QueueTopic.INGEST_VIDEO);
        var failure = new AmqpException("broker confirm failed");
        doThrow(failure).when(messagePublisher).publish(any(), any(), any());

        assertThatThrownBy(() -> service.retry(QueueTopic.INGEST_VIDEO.dlqName(), 1)).isSameAs(failure);

        verify(channel).basicNack(42L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void explicitDiscardAcknowledgesWithoutPublishing() throws Exception {
        Channel channel = messageChannel(QueueTopic.INGEST_VIDEO);

        assertThat(service.discard(QueueTopic.INGEST_VIDEO.dlqName(), 1).affected()).isEqualTo(1);

        verify(channel).basicAck(42L, false);
        verifyNoInteractions(messagePublisher);
    }

    private Channel messageChannel(QueueTopic topic) throws Exception {
        defaultDlqPerformanceConfig();
        Channel channel = mock(Channel.class);
        var properties = new AMQP.BasicProperties.Builder().headers(Map.of("x-ircs-retry-count", 3)).build();
        when(channel.basicGet(topic.dlqName(), false)).thenReturn(new GetResponse(
                new Envelope(42L, true, topic.exchange(), topic.routingKey()), properties,
                "payload".getBytes(StandardCharsets.UTF_8), 0));
        when(rabbitTemplate.execute(org.mockito.ArgumentMatchers.<ChannelCallback<Message>>any()))
                .thenAnswer(invocation -> invocation.<ChannelCallback<Message>>getArgument(0).doInRabbit(channel));
        when(queueClient.fetchQueueSnapshots()).thenReturn(Optional.empty());
        return channel;
    }

    @Test
    void listQueuesUsesNativeRabbitDlqSnapshotsAndIncludesUnmanagedDlqs() {
        defaultDlqPerformanceConfig();
        when(queueClient.fetchQueueSnapshots()).thenReturn(Optional.of(new BrokerManagementQueues(null, List.of(
                new BrokerManagementQueueSnapshot(QueueTopic.TASK_DETAIL.dlqName(), 7, 5, 12, 0),
                new BrokerManagementQueueSnapshot("q.legacy.custom.dlq", 3, 2, 5, 0),
                new BrokerManagementQueueSnapshot("q.task.detail", 99, 0, 99, 1)
        ))));

        List<DeadLetterQueueResponse> responses = service.listQueues();

        DeadLetterQueueResponse taskDetail = find(responses, QueueTopic.TASK_DETAIL.dlqName());
        assertThat(taskDetail.topic()).isEqualTo(QueueTopic.TASK_DETAIL.name());
        assertThat(taskDetail.messagesReady()).isEqualTo(7);
        assertThat(taskDetail.messagesUnacknowledged()).isEqualTo(5);
        assertThat(taskDetail.messagesTotal()).isEqualTo(12);
        assertThat(taskDetail.actionSupported()).isTrue();

        DeadLetterQueueResponse unmanaged = find(responses, "q.legacy.custom.dlq");
        assertThat(unmanaged.sourceQueueName()).isEqualTo("q.legacy.custom");
        assertThat(unmanaged.messagesTotal()).isEqualTo(5);
        assertThat(unmanaged.actionSupported()).isFalse();
        verifyNoInteractions(rabbitAdmin);
    }

    @Test
    void listQueuesFallsBackToRabbitAdminForKnownTopologyWhenManagementUnavailable() {
        defaultDlqPerformanceConfig();
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 9);
        props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0);
        when(queueClient.fetchQueueSnapshots()).thenReturn(Optional.empty());
        when(rabbitAdmin.getQueueProperties(QueueTopic.INGEST_VIDEO.dlqName())).thenReturn(props);

        DeadLetterQueueResponse ingestDlq = find(service.listQueues(), QueueTopic.INGEST_VIDEO.dlqName());

        assertThat(ingestDlq.messagesReady()).isEqualTo(9);
        assertThat(ingestDlq.messagesUnacknowledged()).isZero();
        assertThat(ingestDlq.messagesTotal()).isEqualTo(9);
        assertThat(ingestDlq.actionSupported()).isTrue();
    }

    @Test
    void listQueuesIncludesNonDestructiveSamplesWhenRequested() {
        defaultDlqPerformanceConfig();
        when(queueClient.fetchQueueSnapshots()).thenReturn(Optional.of(new BrokerManagementQueues(null, List.of(
                new BrokerManagementQueueSnapshot(QueueTopic.TASK_DETAIL.dlqName(), 2, 0, 2, 0),
                new BrokerManagementQueueSnapshot(QueueTopic.INGEST_VIDEO.dlqName(), 0, 0, 0, 0)
        ))));
        when(queueClient.sampleMessages(QueueTopic.TASK_DETAIL.dlqName(), 2)).thenReturn(List.of(
                new BrokerManagementMessageSample(
                        "message-1",
                        "correlation-1",
                        3,
                        "dlq",
                        "java.lang.IllegalStateException",
                        "HTTP fetch failed",
                        701,
                        "{\"sourceVid\":\"1538515\"}")
        ));

        DeadLetterQueueResponse taskDetail = find(service.listQueues(2), QueueTopic.TASK_DETAIL.dlqName());
        DeadLetterQueueResponse ingest = find(service.listQueues(2), QueueTopic.INGEST_VIDEO.dlqName());

        assertThat(taskDetail.samples()).hasSize(1);
        DeadLetterMessageSample sample = taskDetail.samples().get(0);
        assertThat(sample.retryCount()).isEqualTo(3);
        assertThat(sample.errorMessage()).isEqualTo("HTTP fetch failed");
        assertThat(sample.bodyPreview()).contains("1538515");
        assertThat(ingest.samples()).isEmpty();
    }

    @Test
    void listQueuesOnlySamplesMostBackloggedDlqsWithinConfiguredQueueLimit() {
        when(runtimeConfig.boundedIntValue(
                "app.ops.rabbit-dlq.sampled-queue-limit",
                3,
                0,
                20)).thenReturn(1);
        when(runtimeConfig.durationValue(
                "app.ops.rabbit-dlq.queue-snapshot-cache-ttl",
                Duration.ofSeconds(3))).thenReturn(Duration.ZERO);
        when(runtimeConfig.durationValue(
                "app.ops.rabbit-dlq.sample-cache-ttl",
                Duration.ofSeconds(5))).thenReturn(Duration.ZERO);
        when(queueClient.fetchQueueSnapshots()).thenReturn(Optional.of(new BrokerManagementQueues(null, List.of(
                new BrokerManagementQueueSnapshot(QueueTopic.INGEST_VIDEO.dlqName(), 8, 0, 8, 0),
                new BrokerManagementQueueSnapshot(QueueTopic.TASK_DETAIL.dlqName(), 21, 0, 21, 0),
                new BrokerManagementQueueSnapshot(QueueTopic.SEND_MAIL.dlqName(), 3, 0, 3, 0)
        ))));
        when(queueClient.sampleMessages(QueueTopic.TASK_DETAIL.dlqName(), 2)).thenReturn(List.of(samplePayload()));

        List<DeadLetterQueueResponse> responses = service.listQueues(2);

        assertThat(find(responses, QueueTopic.TASK_DETAIL.dlqName()).samples()).hasSize(1);
        assertThat(find(responses, QueueTopic.INGEST_VIDEO.dlqName()).samples()).isEmpty();
        assertThat(find(responses, QueueTopic.SEND_MAIL.dlqName()).samples()).isEmpty();
        verify(queueClient, times(1)).sampleMessages(QueueTopic.TASK_DETAIL.dlqName(), 2);
    }

    @Test
    void listQueuesReusesShortLivedQueueAndSampleCaches() {
        defaultDlqPerformanceConfig();
        when(queueClient.fetchQueueSnapshots()).thenReturn(Optional.of(new BrokerManagementQueues(null, List.of(
                new BrokerManagementQueueSnapshot(QueueTopic.TASK_DETAIL.dlqName(), 2, 0, 2, 0)
        ))));
        when(queueClient.sampleMessages(QueueTopic.TASK_DETAIL.dlqName(), 2)).thenReturn(List.of(samplePayload()));

        service.listQueues(2);
        service.listQueues(2);

        verify(queueClient, times(1)).fetchQueueSnapshots();
        verify(queueClient, times(1)).sampleMessages(QueueTopic.TASK_DETAIL.dlqName(), 2);
    }

    private DeadLetterQueueResponse find(List<DeadLetterQueueResponse> responses, String queueName) {
        return responses.stream()
                .filter(response -> response.queueName().equals(queueName))
                .findFirst()
                .orElseThrow();
    }

    private void defaultDlqPerformanceConfig() {
        when(runtimeConfig.boundedIntValue(
                "app.ops.rabbit-dlq.sampled-queue-limit",
                3,
                0,
                20)).thenReturn(3);
        when(runtimeConfig.durationValue(
                "app.ops.rabbit-dlq.queue-snapshot-cache-ttl",
                Duration.ofSeconds(3))).thenReturn(Duration.ofSeconds(3));
        when(runtimeConfig.durationValue(
                "app.ops.rabbit-dlq.sample-cache-ttl",
                Duration.ofSeconds(5))).thenReturn(Duration.ofSeconds(5));
    }

    private BrokerManagementMessageSample samplePayload() {
        return new BrokerManagementMessageSample(
                "message-1",
                "correlation-1",
                3,
                "dlq",
                "java.lang.IllegalStateException",
                "HTTP fetch failed",
                701,
                "{\"sourceVid\":\"1538515\"}");
    }
}
