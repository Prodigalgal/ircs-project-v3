package com.prodigalgal.ircs.ops.queue.dlq.rabbit;

import com.prodigalgal.ircs.ops.queue.domain.QueueTopicDescriptor;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementMessageSample;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementQueueClient;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementQueueSnapshot;
import com.prodigalgal.ircs.ops.infrastructure.rabbit.RabbitManagementQueues;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitDlqService {

    private static final int MAX_ACTION_LIMIT = 100;
    private static final int MAX_SAMPLE_LIMIT = 5;
    private static final int RECEIVE_TIMEOUT_MS = 100;
    private static final int BODY_PREVIEW_LIMIT = 500;
    private static final String HEADER_RETRY_COUNT = "x-ircs-retry-count";
    private static final String HEADER_DISPOSITION = "x-ircs-disposition";
    private static final String HEADER_ERROR_CLASS = "x-ircs-error-class";
    private static final String HEADER_ERROR_MESSAGE = "x-ircs-error-message";
    private static final String HEADER_MANUAL_ACTION = "x-ircs-manual-dlq-action";
    private static final String HEADER_MANUAL_ACTION_AT = "x-ircs-manual-dlq-action-at";
    private static final String OPS_CONFIG_CHANGED_QUEUE = "q.ops.config_changed";

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitManagementQueueClient queueClient;

    public List<RabbitDlqQueueResponse> listQueues() {
        return listQueues(0);
    }

    public List<RabbitDlqQueueResponse> listQueues(int sampleLimit) {
        int safeSampleLimit = safeSampleLimit(sampleLimit);
        return nativeQueueSnapshots()
                .map(brokerQueues -> listNativeQueues(brokerQueues, safeSampleLimit))
                .orElseGet(this::listKnownQueues);
    }

    private List<RabbitDlqQueueResponse> listNativeQueues(
            Map<String, RabbitManagementQueueSnapshot> brokerQueues,
            int sampleLimit) {
        Map<String, RabbitDlqDefinition> definitions = knownDefinitions();
        return brokerQueues.keySet().stream()
                .filter(RabbitDlqService::isDlqQueue)
                .sorted()
                .map(queueName -> queueResponse(
                        definitions.getOrDefault(queueName, unmanagedDefinition(queueName)),
                        brokerQueues,
                        sampleLimit))
                .toList();
    }

    private List<RabbitDlqQueueResponse> listKnownQueues() {
        Map<String, RabbitManagementQueueSnapshot> brokerQueues = Map.of();
        Map<String, RabbitDlqDefinition> definitions = knownDefinitions();
        return definitions.values().stream()
                .sorted(Comparator.comparing(RabbitDlqDefinition::queueName))
                .map(definition -> queueResponse(definition, brokerQueues, 0))
                .toList();
    }

    public RabbitDlqActionResponse retry(String queueName, int limit) {
        return drain(resolveTopic(queueName), limit, "retry");
    }

    public RabbitDlqActionResponse discard(String queueName, int limit) {
        return drain(resolveTopic(queueName), limit, "discard");
    }

    private RabbitDlqActionResponse drain(QueueTopic topic, int limit, String action) {
        int requested = safeLimit(limit);
        List<RabbitDlqMessageSample> samples = new ArrayList<>();
        int affected = 0;
        for (int i = 0; i < requested; i++) {
            Message message = rabbitTemplate.receive(topic.dlqName(), RECEIVE_TIMEOUT_MS);
            if (message == null) {
                break;
            }
            affected++;
            if (samples.size() < 5) {
                samples.add(sample(message));
            }
            if ("retry".equals(action)) {
                rabbitTemplate.send(topic.exchange(), topic.routingKey(), markManualAction(message, action));
            }
        }
        return new RabbitDlqActionResponse(
                topic.dlqName(),
                action,
                requested,
                affected,
                queueResponse(knownDefinition(topic), nativeQueueSnapshots().orElse(Map.of()), 0),
                samples);
    }

    private QueueTopic resolveTopic(String queueName) {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("queueName is required");
        }
        return Arrays.stream(QueueTopic.values())
                .filter(topic -> topic.dlqName().equals(queueName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Rabbit DLQ queue: " + queueName));
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return Math.min(limit, MAX_ACTION_LIMIT);
    }

    private int safeSampleLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, MAX_SAMPLE_LIMIT);
    }

    private RabbitDlqQueueResponse queueResponse(
            RabbitDlqDefinition definition,
            Map<String, RabbitManagementQueueSnapshot> brokerQueues,
            int sampleLimit) {
        RabbitManagementQueueSnapshot snapshot = brokerQueues.get(definition.queueName());
        if (snapshot == null) {
            QueueState state = queueState(definition.queueName());
            snapshot = new RabbitManagementQueueSnapshot(
                    definition.queueName(),
                    state.messageCount(),
                    0,
                    state.messageCount(),
                    state.consumerCount());
        }
        List<RabbitDlqMessageSample> samples = sampleLimit <= 0 || snapshot.messagesTotal() <= 0
                ? List.of()
                : queueClient.sampleMessages(definition.queueName(), sampleLimit).stream()
                        .map(this::sample)
                        .toList();
        return new RabbitDlqQueueResponse(
                definition.topic(),
                definition.displayName(),
                definition.queueName(),
                definition.sourceQueueName(),
                definition.exchange(),
                definition.routingKey(),
                snapshot.messagesReady(),
                snapshot.messagesUnacknowledged(),
                snapshot.messagesTotal(),
                snapshot.consumers(),
                definition.actionSupported(),
                samples);
    }

    private Optional<Map<String, RabbitManagementQueueSnapshot>> nativeQueueSnapshots() {
        return queueClient.fetchQueueSnapshots()
                .map(RabbitManagementQueues::byName);
    }

    private Map<String, RabbitDlqDefinition> knownDefinitions() {
        Map<String, RabbitDlqDefinition> definitions = new LinkedHashMap<>();
        Arrays.stream(QueueTopic.values())
                .map(this::knownDefinition)
                .forEach(definition -> definitions.put(definition.queueName(), definition));
        RabbitDlqDefinition configChangedDefinition = new RabbitDlqDefinition(
                "OPS_CONFIG_CHANGED",
                "系统配置变更 DLQ",
                OPS_CONFIG_CHANGED_QUEUE + ".dlq",
                OPS_CONFIG_CHANGED_QUEUE,
                QueueTopic.Names.DOMAIN_EVENT_X,
                null,
                false);
        definitions.put(configChangedDefinition.queueName(), configChangedDefinition);
        return definitions;
    }

    private RabbitDlqDefinition knownDefinition(QueueTopic topic) {
        QueueTopicDescriptor descriptor = QueueTopicDescriptor.from(topic);
        return new RabbitDlqDefinition(
                topic.name(),
                descriptor.displayName() + " DLQ",
                topic.dlqName(),
                topic.queueName(),
                topic.exchange(),
                topic.routingKey(),
                true);
    }

    private static RabbitDlqDefinition unmanagedDefinition(String queueName) {
        return new RabbitDlqDefinition(
                queueName,
                queueName,
                queueName,
                sourceQueueName(queueName),
                null,
                null,
                false);
    }

    private static boolean isDlqQueue(String queueName) {
        return queueName != null && queueName.endsWith(".dlq");
    }

    private static String sourceQueueName(String queueName) {
        return isDlqQueue(queueName)
                ? queueName.substring(0, queueName.length() - ".dlq".length())
                : queueName;
    }

    private QueueState queueState(String queueName) {
        try {
            Properties props = rabbitAdmin.getQueueProperties(queueName);
            if (props == null) {
                return QueueState.empty();
            }
            return new QueueState(
                    toInt(props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)),
                    toInt(props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)));
        } catch (RuntimeException ignored) {
            return QueueState.empty();
        }
    }

    private Message markManualAction(Message message, String action) {
        return MessageBuilder.fromMessage(message)
                .setHeader(HEADER_MANUAL_ACTION, action)
                .setHeader(HEADER_MANUAL_ACTION_AT, Instant.now().toString())
                .build();
    }

    private RabbitDlqMessageSample sample(Message message) {
        return new RabbitDlqMessageSample(
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getCorrelationId(),
                numberHeader(message, HEADER_RETRY_COUNT),
                textHeader(message, HEADER_DISPOSITION),
                textHeader(message, HEADER_ERROR_CLASS),
                textHeader(message, HEADER_ERROR_MESSAGE),
                message.getBody().length,
                bodyPreview(message));
    }

    private RabbitDlqMessageSample sample(RabbitManagementMessageSample sample) {
        return new RabbitDlqMessageSample(
                sample.messageId(),
                sample.correlationId(),
                sample.retryCount(),
                sample.disposition(),
                sample.errorClass(),
                sample.errorMessage(),
                sample.bodyBytes(),
                sample.bodyPreview());
    }

    private Integer numberHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String textHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? null : String.valueOf(value);
    }

    private String bodyPreview(Message message) {
        String text = new String(message.getBody(), StandardCharsets.UTF_8);
        if (text.length() <= BODY_PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, BODY_PREVIEW_LIMIT);
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private record QueueState(int messageCount, int consumerCount) {
        static QueueState empty() {
            return new QueueState(0, 0);
        }
    }

    private record RabbitDlqDefinition(
            String topic,
            String displayName,
            String queueName,
            String sourceQueueName,
            String exchange,
            String routingKey,
            boolean actionSupported
    ) {
    }
}
