package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
    private static final int RECEIVE_TIMEOUT_MS = 100;
    private static final int BODY_PREVIEW_LIMIT = 500;
    private static final String HEADER_RETRY_COUNT = "x-ircs-retry-count";
    private static final String HEADER_DISPOSITION = "x-ircs-disposition";
    private static final String HEADER_ERROR_CLASS = "x-ircs-error-class";
    private static final String HEADER_ERROR_MESSAGE = "x-ircs-error-message";
    private static final String HEADER_MANUAL_ACTION = "x-ircs-manual-dlq-action";
    private static final String HEADER_MANUAL_ACTION_AT = "x-ircs-manual-dlq-action-at";

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;

    public List<RabbitDlqQueueResponse> listQueues() {
        return Arrays.stream(QueueTopic.values())
                .sorted(Comparator.comparing(QueueTopic::dlqName))
                .map(this::queueResponse)
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
                queueResponse(topic),
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

    private RabbitDlqQueueResponse queueResponse(QueueTopic topic) {
        QueueTopicDescriptor descriptor = QueueTopicDescriptor.from(topic);
        QueueState state = queueState(topic.dlqName());
        return new RabbitDlqQueueResponse(
                topic.name(),
                descriptor.displayName() + " DLQ",
                topic.dlqName(),
                topic.queueName(),
                topic.exchange(),
                topic.routingKey(),
                state.messageCount(),
                state.consumerCount());
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
}
