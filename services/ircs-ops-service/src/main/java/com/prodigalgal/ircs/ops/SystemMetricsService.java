package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTaskCounts;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.audit.AuditReplicationWorkTypes;
import com.prodigalgal.ircs.common.normalization.LlmCleaningWorkTypes;
import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.storage.StorageWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemMetricsService {

    private final RabbitAdmin rabbitAdmin;
    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<PipelineValkeyQueue> pipelineValkeyQueueProvider;
    private final ObjectProvider<RuntimeWorkQueue> runtimeWorkQueueProvider;
    private final Map<String, BacklogSample> previousBacklogSamples = new ConcurrentHashMap<>();

    @Value("${app.pipeline.valkey.key-prefix:ircs:pipeline:v2}")
    private String pipelineKeyPrefix;

    @Value("${app.runtime-work-queue.key-prefix:ircs:work:v1}")
    private String runtimeWorkKeyPrefix;

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
        runtimeWorkDescriptors().forEach(descriptor -> registerRuntimeWorkGauges(descriptor.taskType()));
    }

    public SystemMetricsResponse currentMetrics() {
        Runtime runtime = Runtime.getRuntime();
        File disk = new File(".");
        ValkeyThroughputSnapshot throughputSnapshot = valkeyThroughputSnapshot();
        List<QueueGroupResponse> queueGroups = queueGroups(throughputSnapshot);
        QueueMetricResponse ingest = findQueue(queueGroups, QueueTopic.INGEST_VIDEO.name());
        QueueMetricResponse image = findQueue(queueGroups, QueueTopic.DOWNLOAD_IMAGE.name());
        PipelineValkeyTaskCounts normalize = pipelineCounts(PipelineTaskTypes.NORMALIZE_VIDEO);
        PipelineValkeyTaskCounts enrich = pipelineCounts(PipelineTaskTypes.ENRICH_METADATA);

        return new SystemMetricsResponse(
                throughput(ingest),
                throughputSnapshot.pipelineCompleted(PipelineTaskTypes.NORMALIZE_VIDEO),
                throughputSnapshot.pipelineCompleted(PipelineTaskTypes.ENRICH_METADATA)
                        + throughputSnapshot.pipelineCompleted(PipelineTaskTypes.METADATA_PROVIDER),
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
        previousBacklogSamples.clear();
    }

    private List<QueueGroupResponse> queueGroups(ValkeyThroughputSnapshot throughputSnapshot) {
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
        List<QueueMetricResponse> pipelineQueues = valkeyPipelineQueues(throughputSnapshot);
        if (!pipelineQueues.isEmpty()) {
            normalGroups.put("Valkey Pipeline", pipelineQueues);
        }
        List<QueueMetricResponse> runtimeWorkQueues = valkeyRuntimeWorkQueues(throughputSnapshot);
        if (!runtimeWorkQueues.isEmpty()) {
            normalGroups.put("Valkey Runtime Work", runtimeWorkQueues);
        }

        List<QueueGroupResponse> groups = new ArrayList<>();
        normalGroups.forEach((title, queues) -> groups.add(new QueueGroupResponse(title, false, queues)));
        List<QueueMetricResponse> valkeyDlqs = valkeyPipelineDlqs(throughputSnapshot);
        valkeyDlqs.addAll(valkeyRuntimeWorkDlqs(throughputSnapshot));
        if (!valkeyDlqs.isEmpty()) {
            dlqs.addAll(valkeyDlqs);
        }
        groups.add(new QueueGroupResponse("死信队列", true, dlqs));
        return groups;
    }

    private List<QueueMetricResponse> valkeyPipelineQueues(ValkeyThroughputSnapshot throughputSnapshot) {
        List<QueueMetricResponse> queues = new ArrayList<>();
        addValkeyQueueMetrics(queues, "Normalize Pending", "VALKEY_NORMALIZE_PENDING", "#0284c7",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.PENDING, throughputSnapshot);
        addValkeyQueueMetrics(queues, "Normalize Inflight", "VALKEY_NORMALIZE_INFLIGHT", "#0ea5e9",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.INFLIGHT, throughputSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Dispatch Pending", "VALKEY_ENRICH_PENDING", "#7c3aed",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.PENDING, throughputSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Dispatch Inflight", "VALKEY_ENRICH_INFLIGHT", "#8b5cf6",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.INFLIGHT, throughputSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Provider Pending", "VALKEY_PROVIDER_PENDING", "#059669",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.PENDING, throughputSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Provider Inflight", "VALKEY_PROVIDER_INFLIGHT", "#10b981",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.INFLIGHT, throughputSnapshot);
        return queues;
    }

    private List<QueueMetricResponse> valkeyPipelineDlqs(ValkeyThroughputSnapshot throughputSnapshot) {
        List<QueueMetricResponse> dlqs = new ArrayList<>();
        addValkeyQueueMetrics(dlqs, "Normalize Valkey DLQ", "VALKEY_NORMALIZE_DLQ", "#dc2626",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.DLQ, throughputSnapshot);
        addValkeyQueueMetrics(dlqs, "Metadata Dispatch Valkey DLQ", "VALKEY_ENRICH_DLQ", "#dc2626",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.DLQ, throughputSnapshot);
        addValkeyQueueMetrics(dlqs, "Metadata Provider Valkey DLQ", "VALKEY_PROVIDER_DLQ", "#dc2626",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.DLQ, throughputSnapshot);
        return dlqs;
    }

    private List<QueueMetricResponse> valkeyRuntimeWorkQueues(ValkeyThroughputSnapshot throughputSnapshot) {
        List<QueueMetricResponse> queues = new ArrayList<>();
        runtimeWorkDescriptors().forEach(descriptor -> {
            addRuntimeWorkQueueMetrics(queues, descriptor.label() + " Pending",
                    "RUNTIME_" + descriptor.key() + "_PENDING", descriptor.color(), descriptor.taskType(),
                    RuntimeWorkMetricRole.PENDING, throughputSnapshot);
            addRuntimeWorkQueueMetrics(queues, descriptor.label() + " Inflight",
                    "RUNTIME_" + descriptor.key() + "_INFLIGHT", descriptor.inflightColor(), descriptor.taskType(),
                    RuntimeWorkMetricRole.INFLIGHT, throughputSnapshot);
        });
        return queues;
    }

    private List<QueueMetricResponse> valkeyRuntimeWorkDlqs(ValkeyThroughputSnapshot throughputSnapshot) {
        List<QueueMetricResponse> dlqs = new ArrayList<>();
        runtimeWorkDescriptors().forEach(descriptor -> addRuntimeWorkQueueMetrics(dlqs, descriptor.label() + " Runtime DLQ",
                "RUNTIME_" + descriptor.key() + "_DLQ", "#dc2626", descriptor.taskType(),
                RuntimeWorkMetricRole.DLQ, throughputSnapshot));
        return dlqs;
    }

    private void addValkeyQueueMetrics(
            List<QueueMetricResponse> target,
            String name,
            String key,
            String color,
            String taskType,
            PipelineMetricRole role,
            ValkeyThroughputSnapshot throughputSnapshot) {
        PipelineValkeyTaskCounts counts = pipelineCounts(taskType);
        long current = switch (role) {
            case PENDING -> counts.pending();
            case INFLIGHT -> counts.inflight();
            case DLQ -> counts.dlq();
        };
        long throughput = pipelineThroughput(throughputSnapshot, taskType, role);
        target.add(new QueueMetricResponse(
                name,
                key,
                color,
                safeInt(current),
                role == PipelineMetricRole.DLQ ? 0 : safeInt(pipelineConsumerCount(taskType)),
                throughput));
    }

    private void addRuntimeWorkQueueMetrics(
            List<QueueMetricResponse> target,
            String name,
            String key,
            String color,
            String taskType,
            RuntimeWorkMetricRole role,
            ValkeyThroughputSnapshot throughputSnapshot) {
        RuntimeWorkQueueCounts counts = runtimeWorkCounts(taskType);
        long current = switch (role) {
            case PENDING -> counts.pending();
            case INFLIGHT -> counts.inflight();
            case DLQ -> counts.dlq();
        };
        long throughput = runtimeWorkThroughput(throughputSnapshot, taskType, role);
        target.add(new QueueMetricResponse(
                name,
                key,
                color,
                safeInt(current),
                role == RuntimeWorkMetricRole.DLQ ? 0 : safeInt(runtimeWorkConsumerCount(taskType)),
                throughput));
    }

    private QueueMetricResponse queueMetric(String name, String key, String color, String queueName) {
        QueueState state = queueState(queueName);
        long current = state.messageCount();
        long throughput = drainThroughputPerMinute(queueName, current);
        return new QueueMetricResponse(
                name,
                key,
                color,
                state.messageCount(),
                state.consumerCount(),
                throughput);
    }

    private long pipelineThroughput(
            ValkeyThroughputSnapshot throughputSnapshot,
            String taskType,
            PipelineMetricRole role) {
        return switch (role) {
            case PENDING -> throughputSnapshot.pipelineClaimed(taskType);
            case INFLIGHT -> throughputSnapshot.pipelineCompleted(taskType);
            case DLQ -> throughputSnapshot.pipelineFailed(taskType);
        };
    }

    private long runtimeWorkThroughput(
            ValkeyThroughputSnapshot throughputSnapshot,
            String taskType,
            RuntimeWorkMetricRole role) {
        return switch (role) {
            case PENDING -> throughputSnapshot.runtimeClaimed(taskType);
            case INFLIGHT -> throughputSnapshot.runtimeCompleted(taskType);
            case DLQ -> throughputSnapshot.runtimeFailed(taskType);
        };
    }

    private long drainThroughputPerMinute(String queueName, long current) {
        long now = System.currentTimeMillis();
        BacklogSample previous = previousBacklogSamples.put(queueName, new BacklogSample(current, now));
        if (previous == null || previous.sampledAtMillis() >= now) {
            return 0L;
        }
        long drained = Math.max(0L, previous.count() - current);
        if (drained == 0L) {
            return 0L;
        }
        long elapsedMillis = now - previous.sampledAtMillis();
        return Math.max(1L, Math.round((drained * 60_000.0) / elapsedMillis));
    }

    private ValkeyThroughputSnapshot valkeyThroughputSnapshot() {
        long now = System.currentTimeMillis();
        long start = now - 60_000L;
        Map<String, EventCounts> pipeline = streamEventCounts(pipelineEventStreamKey(), start, now);
        Map<String, EventCounts> runtimeWork = streamEventCounts(runtimeWorkEventStreamKey(), start, now);
        return new ValkeyThroughputSnapshot(pipeline, runtimeWork);
    }

    private Map<String, EventCounts> streamEventCounts(String streamKey, long startMillis, long endMillis) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                    streamKey,
                    Range.closed(startMillis + "-0", endMillis + "-999999"),
                    Limit.limit().count(20_000));
            if (records == null || records.isEmpty()) {
                return Map.of();
            }
            Map<String, EventCounts> counts = new LinkedHashMap<>();
            for (MapRecord<String, Object, Object> record : records) {
                String taskType = valueAsString(record.getValue().get("taskType"));
                String event = valueAsString(record.getValue().get("event"));
                if (taskType == null || event == null) {
                    continue;
                }
                counts.compute(taskType, (ignored, current) ->
                        (current == null ? EventCounts.empty() : current).with(event));
            }
            return counts;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private String pipelineEventStreamKey() {
        return normalizeKeyPrefix(pipelineKeyPrefix, "ircs:pipeline:v2") + ":events";
    }

    private String runtimeWorkEventStreamKey() {
        return normalizeKeyPrefix(runtimeWorkKeyPrefix, "ircs:work:v1") + ":events";
    }

    private String normalizeKeyPrefix(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.endsWith(":") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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

    private RuntimeWorkQueueCounts runtimeWorkCounts(String taskType) {
        RuntimeWorkQueue queue = runtimeWorkQueueProvider.getIfAvailable();
        if (queue == null) {
            return emptyRuntimeWorkCounts();
        }
        try {
            RuntimeWorkQueueCounts counts = queue.counts(taskType);
            return counts == null ? emptyRuntimeWorkCounts() : counts;
        } catch (Exception ignored) {
            return emptyRuntimeWorkCounts();
        }
    }

    private long pipelineConsumerCount(String taskType) {
        PipelineValkeyQueue queue = pipelineValkeyQueueProvider.getIfAvailable();
        if (queue == null) {
            return 0L;
        }
        try {
            return queue.consumerCount(taskType);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long runtimeWorkConsumerCount(String taskType) {
        RuntimeWorkQueue queue = runtimeWorkQueueProvider.getIfAvailable();
        if (queue == null) {
            return 0L;
        }
        try {
            return queue.consumerCount(taskType);
        } catch (Exception ignored) {
            return 0L;
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

    private long throughput(QueueMetricResponse queue) {
        return queue == null ? 0 : queue.throughput();
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private void registerRuntimeWorkGauges(String taskType) {
        Gauge.builder("ircs.valkey.runtime_work.pending", () -> runtimeWorkCounts(taskType).pending())
                .description("Valkey runtime work pending task count observed by ops-service")
                .tag("task_type", taskType)
                .register(meterRegistry);
        Gauge.builder("ircs.valkey.runtime_work.inflight", () -> runtimeWorkCounts(taskType).inflight())
                .description("Valkey runtime work inflight task count observed by ops-service")
                .tag("task_type", taskType)
                .register(meterRegistry);
        Gauge.builder("ircs.valkey.runtime_work.dlq", () -> runtimeWorkCounts(taskType).dlq())
                .description("Valkey runtime work DLQ task count observed by ops-service")
                .tag("task_type", taskType)
                .register(meterRegistry);
    }

    private PipelineValkeyTaskCounts emptyPipelineCounts() {
        return new PipelineValkeyTaskCounts(0, 0, 0);
    }

    private RuntimeWorkQueueCounts emptyRuntimeWorkCounts() {
        return new RuntimeWorkQueueCounts(0, 0, 0);
    }

    private List<RuntimeWorkDescriptor> runtimeWorkDescriptors() {
        return List.of(
                new RuntimeWorkDescriptor("SEARCH_RAW", "Search Raw", SearchSyncWorkTypes.RAW, "#2563eb", "#60a5fa"),
                new RuntimeWorkDescriptor("SEARCH_UNIFIED", "Search Unified", SearchSyncWorkTypes.UNIFIED, "#1d4ed8", "#3b82f6"),
                new RuntimeWorkDescriptor("AGGREGATION", "Aggregation", AggregationWorkTypes.RAW_VIDEO, "#c2410c", "#fb923c"),
                new RuntimeWorkDescriptor("LLM_CLEANING", "LLM Cleaning", LlmCleaningWorkTypes.RAW_TERM, "#9333ea", "#c084fc"),
                new RuntimeWorkDescriptor("STORAGE_AVATAR", "Avatar R2", StorageWorkTypes.AVATAR_SYNC, "#0891b2", "#22d3ee"),
                new RuntimeWorkDescriptor("STORAGE_COVER", "Cover R2", StorageWorkTypes.COVER_R2_SYNC, "#0f766e", "#2dd4bf"),
                new RuntimeWorkDescriptor("AUDIT_ES", "Audit ES", AuditReplicationWorkTypes.ES_REPLICATION, "#4b5563", "#9ca3af"));
    }

    private record QueueState(int messageCount, int consumerCount) {
        static QueueState empty() {
            return new QueueState(0, 0);
        }
    }

    private record BacklogSample(long count, long sampledAtMillis) {
    }

    private record EventCounts(long claimed, long completed, long failed) {
        static EventCounts empty() {
            return new EventCounts(0, 0, 0);
        }

        EventCounts with(String event) {
            return switch (event) {
                case "claimed" -> new EventCounts(claimed + 1, completed, failed);
                case "completed" -> new EventCounts(claimed, completed + 1, failed);
                case "failed" -> new EventCounts(claimed, completed, failed + 1);
                default -> this;
            };
        }
    }

    private record ValkeyThroughputSnapshot(
            Map<String, EventCounts> pipeline,
            Map<String, EventCounts> runtimeWork) {

        long pipelineClaimed(String taskType) {
            return counts(pipeline, taskType).claimed();
        }

        long pipelineCompleted(String taskType) {
            return counts(pipeline, taskType).completed();
        }

        long pipelineFailed(String taskType) {
            return counts(pipeline, taskType).failed();
        }

        long runtimeClaimed(String taskType) {
            return counts(runtimeWork, taskType).claimed();
        }

        long runtimeCompleted(String taskType) {
            return counts(runtimeWork, taskType).completed();
        }

        long runtimeFailed(String taskType) {
            return counts(runtimeWork, taskType).failed();
        }

        private EventCounts counts(Map<String, EventCounts> source, String taskType) {
            return source.getOrDefault(taskType, EventCounts.empty());
        }
    }

    private enum PipelineMetricRole {
        PENDING,
        INFLIGHT,
        DLQ
    }

    private enum RuntimeWorkMetricRole {
        PENDING,
        INFLIGHT,
        DLQ
    }

    private record RuntimeWorkDescriptor(
            String key,
            String label,
            String taskType,
            String color,
            String inflightColor) {
    }
}
