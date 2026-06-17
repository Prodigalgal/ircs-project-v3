package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTaskCounts;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemMetricsService {

    private final RabbitAdmin rabbitAdmin;
    private final RedisConnectionFactory redisConnectionFactory;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<PipelineValkeyQueue> pipelineValkeyQueueProvider;
    private final Map<String, Long> previousQueueCounts = new ConcurrentHashMap<>();

    @PostConstruct
    void registerRabbitQueueGauges() {
        for (QueueTopic topic : QueueTopic.values()) {
            registerQueueGauges(topic, topic.queueName(), "main", false);
            registerQueueGauges(topic, topic.retryName(), "retry", false);
            registerQueueGauges(topic, topic.dlqName(), "dlq", true);
        }
        registerValkeyPipelineGauges(PipelineTaskTypes.NORMALIZE_VIDEO);
        registerValkeyPipelineGauges(PipelineTaskTypes.ENRICH_METADATA);
        registerValkeyPipelineGauges(PipelineTaskTypes.METADATA_PROVIDER);
    }

    public SystemMetricsResponse currentMetrics() {
        Runtime runtime = Runtime.getRuntime();
        File disk = new File(".");
        List<QueueGroupResponse> queueGroups = queueGroups();
        QueueMetricResponse ingest = findQueue(queueGroups, QueueTopic.INGEST_VIDEO.name());
        QueueMetricResponse image = findQueue(queueGroups, QueueTopic.DOWNLOAD_IMAGE.name());
        PipelineValkeyTaskCounts normalize = pipelineCounts(PipelineTaskTypes.NORMALIZE_VIDEO);
        PipelineValkeyTaskCounts enrich = pipelineCounts(PipelineTaskTypes.ENRICH_METADATA);

        return new SystemMetricsResponse(
                0,
                0,
                0,
                messageCount(ingest),
                safeInt(normalize.activeBacklog()),
                safeInt(enrich.activeBacklog()),
                messageCount(image),
                queueGroups,
                queueGroups.stream()
                        .filter(group -> !group.dlq())
                        .flatMap(group -> group.queues().stream())
                        .mapToLong(QueueMetricResponse::messageCount)
                        .sum(),
                redisMetric(),
                runtime.freeMemory(),
                disk.getFreeSpace(),
                false,
                Thread.activeCount(),
                Map.of("data", List.of()));
    }

    public void resetThroughputWindow() {
        previousQueueCounts.clear();
    }

    private List<QueueGroupResponse> queueGroups() {
        Map<String, List<QueueMetricResponse>> normalGroups = new LinkedHashMap<>();
        List<QueueMetricResponse> dlqs = new ArrayList<>();

        for (QueueTopic topic : QueueTopic.values()) {
            QueueTopicDescriptor descriptor = QueueTopicDescriptor.from(topic);
            QueueMetricResponse normal = queueMetric(
                    descriptor.displayName(),
                    topic.name(),
                    descriptor.color(),
                    topic.queueName());
            normalGroups.computeIfAbsent(descriptor.group(), ignored -> new ArrayList<>()).add(normal);

            dlqs.add(queueMetric(
                    descriptor.displayName() + " DLQ",
                    "DLQ_" + topic.name(),
                    "#dc2626",
                    topic.dlqName()));
        }
        List<QueueMetricResponse> pipelineQueues = valkeyPipelineQueues();
        if (!pipelineQueues.isEmpty()) {
            normalGroups.put("Valkey Pipeline", pipelineQueues);
        }

        List<QueueGroupResponse> groups = new ArrayList<>();
        normalGroups.forEach((title, queues) -> groups.add(new QueueGroupResponse(title, false, queues)));
        List<QueueMetricResponse> valkeyDlqs = valkeyPipelineDlqs();
        if (!valkeyDlqs.isEmpty()) {
            dlqs.addAll(valkeyDlqs);
        }
        groups.add(new QueueGroupResponse("死信队列", true, dlqs));
        return groups;
    }

    private List<QueueMetricResponse> valkeyPipelineQueues() {
        List<QueueMetricResponse> queues = new ArrayList<>();
        addValkeyQueueMetrics(queues, "Normalize Pending", "VALKEY_NORMALIZE_PENDING", "#0284c7",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.PENDING);
        addValkeyQueueMetrics(queues, "Normalize Inflight", "VALKEY_NORMALIZE_INFLIGHT", "#0ea5e9",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.INFLIGHT);
        addValkeyQueueMetrics(queues, "Metadata Dispatch Pending", "VALKEY_ENRICH_PENDING", "#7c3aed",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.PENDING);
        addValkeyQueueMetrics(queues, "Metadata Dispatch Inflight", "VALKEY_ENRICH_INFLIGHT", "#8b5cf6",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.INFLIGHT);
        addValkeyQueueMetrics(queues, "Metadata Provider Pending", "VALKEY_PROVIDER_PENDING", "#059669",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.PENDING);
        addValkeyQueueMetrics(queues, "Metadata Provider Inflight", "VALKEY_PROVIDER_INFLIGHT", "#10b981",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.INFLIGHT);
        return queues;
    }

    private List<QueueMetricResponse> valkeyPipelineDlqs() {
        List<QueueMetricResponse> dlqs = new ArrayList<>();
        addValkeyQueueMetrics(dlqs, "Normalize Valkey DLQ", "VALKEY_NORMALIZE_DLQ", "#dc2626",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.DLQ);
        addValkeyQueueMetrics(dlqs, "Metadata Dispatch Valkey DLQ", "VALKEY_ENRICH_DLQ", "#dc2626",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.DLQ);
        addValkeyQueueMetrics(dlqs, "Metadata Provider Valkey DLQ", "VALKEY_PROVIDER_DLQ", "#dc2626",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.DLQ);
        return dlqs;
    }

    private void addValkeyQueueMetrics(
            List<QueueMetricResponse> target,
            String name,
            String key,
            String color,
            String taskType,
            PipelineMetricRole role) {
        PipelineValkeyTaskCounts counts = pipelineCounts(taskType);
        long current = switch (role) {
            case PENDING -> counts.pending();
            case INFLIGHT -> counts.inflight();
            case DLQ -> counts.dlq();
        };
        long previous = previousQueueCounts.getOrDefault(key, current);
        long throughput = Math.max(0, current - previous);
        previousQueueCounts.put(key, current);
        target.add(new QueueMetricResponse(name, key, color, safeInt(current), 0, throughput));
    }

    private QueueMetricResponse queueMetric(String name, String key, String color, String queueName) {
        QueueState state = queueState(queueName);
        long current = state.messageCount();
        long previous = previousQueueCounts.getOrDefault(queueName, current);
        long throughput = Math.max(0, current - previous);
        previousQueueCounts.put(queueName, current);
        return new QueueMetricResponse(
                name,
                key,
                color,
                state.messageCount(),
                state.consumerCount(),
                throughput);
    }

    private QueueState queueState(String queueName) {
        try {
            Properties props = rabbitAdmin.getQueueProperties(queueName);
            if (props == null) {
                return QueueState.empty();
            }
            Object messages = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            Object consumers = props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);
            return new QueueState(toInt(messages), toInt(consumers));
        } catch (Exception ignored) {
            return QueueState.empty();
        }
    }

    private RedisMetricResponse redisMetric() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Properties info = connection.serverCommands().info();
            if (info == null) {
                return null;
            }
            return new RedisMetricResponse(
                    parseLong(info.getProperty("used_memory")),
                    (int) parseLong(info.getProperty("connected_clients")),
                    parseLong(info.getProperty("instantaneous_ops_per_sec")),
                    info.getProperty("redis_version"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private PipelineValkeyTaskCounts pipelineCounts(String taskType) {
        PipelineValkeyQueue queue = pipelineValkeyQueueProvider.getIfAvailable();
        if (queue == null) {
            return emptyPipelineCounts();
        }
        try {
            return queue.counts(taskType);
        } catch (Exception ignored) {
            return emptyPipelineCounts();
        }
    }

    private QueueMetricResponse findQueue(List<QueueGroupResponse> groups, String key) {
        for (QueueGroupResponse group : groups) {
            for (QueueMetricResponse queue : group.queues()) {
                if (queue.key().equals(key)) {
                    return queue;
                }
            }
        }
        return null;
    }

    private int messageCount(QueueMetricResponse queue) {
        return queue == null ? 0 : queue.messageCount();
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int safeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void registerQueueGauges(QueueTopic topic, String queueName, String queueRole, boolean dlq) {
        Gauge.builder("ircs.rabbit.queue.messages", () -> queueState(queueName).messageCount())
                .description("RabbitMQ queue message depth observed by ops-service")
                .tag("topic", topic.name())
                .tag("queue", queueName)
                .tag("queue_role", queueRole)
                .tag("dlq", String.valueOf(dlq))
                .register(meterRegistry);
        Gauge.builder("ircs.rabbit.queue.consumers", () -> queueState(queueName).consumerCount())
                .description("RabbitMQ queue consumer count observed by ops-service")
                .tag("topic", topic.name())
                .tag("queue", queueName)
                .tag("queue_role", queueRole)
                .tag("dlq", String.valueOf(dlq))
                .register(meterRegistry);
    }

    private void registerValkeyPipelineGauges(String taskType) {
        Gauge.builder("ircs.valkey.pipeline.pending", () -> pipelineCounts(taskType).pending())
                .description("Valkey pipeline pending task count observed by ops-service")
                .tag("task_type", taskType)
                .register(meterRegistry);
        Gauge.builder("ircs.valkey.pipeline.inflight", () -> pipelineCounts(taskType).inflight())
                .description("Valkey pipeline inflight task count observed by ops-service")
                .tag("task_type", taskType)
                .register(meterRegistry);
        Gauge.builder("ircs.valkey.pipeline.dlq", () -> pipelineCounts(taskType).dlq())
                .description("Valkey pipeline DLQ task count observed by ops-service")
                .tag("task_type", taskType)
                .register(meterRegistry);
    }

    private PipelineValkeyTaskCounts emptyPipelineCounts() {
        return new PipelineValkeyTaskCounts(0, 0, 0);
    }

    private record QueueState(int messageCount, int consumerCount) {
        static QueueState empty() {
            return new QueueState(0, 0);
        }
    }

    private enum PipelineMetricRole {
        PENDING,
        INFLIGHT,
        DLQ
    }
}
