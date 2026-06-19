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
import com.prodigalgal.ircs.common.work.SystemConfigWorkSubmissionGate;
import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
    private final SystemConfigRepository systemConfigRepository;

    @Value("${app.rate-metrics.key-prefix:ircs:metrics:rate}")
    private String rateMetricsKeyPrefix;

    @Value("${app.rate-metrics.bucket-size:PT10S}")
    private Duration rateBucketSize = RateMetricKeys.DEFAULT_BUCKET_SIZE;

    @Value("${app.ops.metrics.instant-window:PT60S}")
    private Duration instantRateWindow = Duration.ofSeconds(60);

    @Value("${app.ops.metrics.stable-window:PT5M}")
    private Duration stableRateWindow = Duration.ofMinutes(5);

    @Value("${app.ops.metrics.ewma-alpha:0.25}")
    private double ewmaAlpha = 0.25d;

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
        RateMetricSnapshot rateSnapshot = rateMetricSnapshot();
        List<QueueGroupResponse> queueGroups = queueGroups(rateSnapshot);
        QueueMetricResponse ingest = findQueue(queueGroups, QueueTopic.INGEST_VIDEO.name());
        QueueMetricResponse image = findQueue(queueGroups, QueueTopic.DOWNLOAD_IMAGE.name());
        PipelineValkeyTaskCounts normalize = pipelineCounts(PipelineTaskTypes.NORMALIZE_VIDEO);
        PipelineValkeyTaskCounts enrich = pipelineCounts(PipelineTaskTypes.ENRICH_METADATA);

        return new SystemMetricsResponse(
                rateSnapshot.metric(RateMetricKeys.COLLECTION_PAGE_DISCOVERED, "PT 发现速率"),
                rateSnapshot.metric(RateMetricKeys.COLLECTION_DETAIL_COMPLETED, "DT 采集速率"),
                rateSnapshot.metric(RateMetricKeys.pipelineMetric(
                        PipelineTaskTypes.NORMALIZE_VIDEO,
                        RateMetricKeys.ACTION_COMPLETED), "清洗速率"),
                rateSnapshot.metric("metadata.combined", "元数据速率",
                        RateMetricKeys.pipelineMetric(PipelineTaskTypes.ENRICH_METADATA, RateMetricKeys.ACTION_COMPLETED),
                        RateMetricKeys.pipelineMetric(PipelineTaskTypes.METADATA_PROVIDER, RateMetricKeys.ACTION_COMPLETED)),
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

    public void refreshRateMetrics() {
        // Rate metrics are pre-aggregated in Valkey buckets; there is no local window to reset.
    }

    private List<QueueGroupResponse> queueGroups(RateMetricSnapshot rateSnapshot) {
        Map<String, List<QueueMetricResponse>> normalGroups = new LinkedHashMap<>();
        List<QueueMetricResponse> dlqs = new ArrayList<>();

        for (QueueTopic topic : QueueTopic.values()) {
            QueueTopicDescriptor descriptor = QueueTopicDescriptor.from(topic);
            QueueMetricResponse normal = queueMetric(
                    descriptor.displayName(),
                    topic.name(),
                    descriptor.color(),
                    topic.queueName(),
                    true,
                    rateSnapshot);
            normalGroups.computeIfAbsent(descriptor.group(), ignored -> new ArrayList<>()).add(normal);

            dlqs.add(queueMetric(
                    descriptor.displayName() + " DLQ",
                    "DLQ_" + topic.name(),
                    "#dc2626",
                    topic.dlqName(),
                    false,
                    rateSnapshot));
        }
        List<QueueMetricResponse> pipelineQueues = valkeyPipelineQueues(rateSnapshot);
        if (!pipelineQueues.isEmpty()) {
            normalGroups.put("Valkey Pipeline", pipelineQueues);
        }
        List<QueueMetricResponse> runtimeWorkQueues = valkeyRuntimeWorkQueues(rateSnapshot);
        if (!runtimeWorkQueues.isEmpty()) {
            normalGroups.put("Valkey Runtime Work", runtimeWorkQueues);
        }

        List<QueueGroupResponse> groups = new ArrayList<>();
        normalGroups.forEach((title, queues) -> groups.add(new QueueGroupResponse(title, false, queues)));
        List<QueueMetricResponse> valkeyDlqs = valkeyPipelineDlqs(rateSnapshot);
        valkeyDlqs.addAll(valkeyRuntimeWorkDlqs(rateSnapshot));
        if (!valkeyDlqs.isEmpty()) {
            dlqs.addAll(valkeyDlqs);
        }
        groups.add(new QueueGroupResponse("死信队列", true, dlqs));
        return groups;
    }

    private List<QueueMetricResponse> valkeyPipelineQueues(RateMetricSnapshot rateSnapshot) {
        List<QueueMetricResponse> queues = new ArrayList<>();
        addValkeyQueueMetrics(queues, "Normalize Pending", "VALKEY_NORMALIZE_PENDING", "#0284c7",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.PENDING, rateSnapshot);
        addValkeyQueueMetrics(queues, "Normalize Inflight", "VALKEY_NORMALIZE_INFLIGHT", "#0ea5e9",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.INFLIGHT, rateSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Dispatch Pending", "VALKEY_ENRICH_PENDING", "#7c3aed",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.PENDING, rateSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Dispatch Inflight", "VALKEY_ENRICH_INFLIGHT", "#8b5cf6",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.INFLIGHT, rateSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Provider Pending", "VALKEY_PROVIDER_PENDING", "#059669",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.PENDING, rateSnapshot);
        addValkeyQueueMetrics(queues, "Metadata Provider Inflight", "VALKEY_PROVIDER_INFLIGHT", "#10b981",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.INFLIGHT, rateSnapshot);
        return queues;
    }

    private List<QueueMetricResponse> valkeyPipelineDlqs(RateMetricSnapshot rateSnapshot) {
        List<QueueMetricResponse> dlqs = new ArrayList<>();
        addValkeyQueueMetrics(dlqs, "Normalize Valkey DLQ", "VALKEY_NORMALIZE_DLQ", "#dc2626",
                PipelineTaskTypes.NORMALIZE_VIDEO, PipelineMetricRole.DLQ, rateSnapshot);
        addValkeyQueueMetrics(dlqs, "Metadata Dispatch Valkey DLQ", "VALKEY_ENRICH_DLQ", "#dc2626",
                PipelineTaskTypes.ENRICH_METADATA, PipelineMetricRole.DLQ, rateSnapshot);
        addValkeyQueueMetrics(dlqs, "Metadata Provider Valkey DLQ", "VALKEY_PROVIDER_DLQ", "#dc2626",
                PipelineTaskTypes.METADATA_PROVIDER, PipelineMetricRole.DLQ, rateSnapshot);
        return dlqs;
    }

    private List<QueueMetricResponse> valkeyRuntimeWorkQueues(RateMetricSnapshot rateSnapshot) {
        List<QueueMetricResponse> queues = new ArrayList<>();
        runtimeWorkDescriptors().forEach(descriptor -> {
            addRuntimeWorkQueueMetrics(queues, descriptor.label() + " Pending",
                    "RUNTIME_" + descriptor.key() + "_PENDING", descriptor.color(), descriptor.taskType(),
                    RuntimeWorkMetricRole.PENDING, rateSnapshot);
            addRuntimeWorkQueueMetrics(queues, descriptor.label() + " Inflight",
                    "RUNTIME_" + descriptor.key() + "_INFLIGHT", descriptor.inflightColor(), descriptor.taskType(),
                    RuntimeWorkMetricRole.INFLIGHT, rateSnapshot);
        });
        return queues;
    }

    private List<QueueMetricResponse> valkeyRuntimeWorkDlqs(RateMetricSnapshot rateSnapshot) {
        List<QueueMetricResponse> dlqs = new ArrayList<>();
        runtimeWorkDescriptors().forEach(descriptor -> addRuntimeWorkQueueMetrics(dlqs, descriptor.label() + " Runtime DLQ",
                "RUNTIME_" + descriptor.key() + "_DLQ", "#dc2626", descriptor.taskType(),
                RuntimeWorkMetricRole.DLQ, rateSnapshot));
        return dlqs;
    }

    private void addValkeyQueueMetrics(
            List<QueueMetricResponse> target,
            String name,
            String key,
            String color,
            String taskType,
            PipelineMetricRole role,
            RateMetricSnapshot rateSnapshot) {
        PipelineValkeyTaskCounts counts = pipelineCounts(taskType);
        long current = switch (role) {
            case PENDING -> counts.pending();
            case INFLIGHT -> counts.inflight();
            case DLQ -> counts.dlq();
        };
        RateDescriptor rateDescriptor = pipelineRate(taskType, role);
        int consumers = role == PipelineMetricRole.DLQ ? 0 : safeInt(pipelineConsumerCount(taskType));
        target.add(new QueueMetricResponse(
                name,
                key,
                color,
                safeInt(current),
                consumers,
                role == PipelineMetricRole.DLQ
                        ? null
                        : blockedReason(current, consumers, pipelineWorkPolicy(taskType)),
                rateDescriptor.kind(),
                rateSnapshot.metric(rateDescriptor.metricKey(), rateDescriptor.label())));
    }

    private void addRuntimeWorkQueueMetrics(
            List<QueueMetricResponse> target,
            String name,
            String key,
            String color,
            String taskType,
            RuntimeWorkMetricRole role,
            RateMetricSnapshot rateSnapshot) {
        RuntimeWorkQueueCounts counts = runtimeWorkCounts(taskType);
        long current = switch (role) {
            case PENDING -> counts.pending();
            case INFLIGHT -> counts.inflight();
            case DLQ -> counts.dlq();
        };
        RateDescriptor rateDescriptor = runtimeRate(taskType, role);
        int consumers = role == RuntimeWorkMetricRole.DLQ ? 0 : safeInt(runtimeWorkConsumerCount(taskType));
        target.add(new QueueMetricResponse(
                name,
                key,
                color,
                safeInt(current),
                consumers,
                role == RuntimeWorkMetricRole.DLQ ? null : blockedReason(current, consumers, runtimeWorkPolicy(taskType)),
                rateDescriptor.kind(),
                rateSnapshot.metric(rateDescriptor.metricKey(), rateDescriptor.label())));
    }

    private QueueMetricResponse queueMetric(
            String name,
            String key,
            String color,
            String queueName,
            boolean checkConsumers,
            RateMetricSnapshot rateSnapshot) {
        QueueState state = queueState(queueName);
        return new QueueMetricResponse(
                name,
                key,
                color,
                state.messageCount(),
                state.consumerCount(),
                checkConsumers
                        ? blockedReason(state.messageCount(), state.consumerCount(), QueueConsumerPolicy.enabled())
                        : null,
                RateKind.NONE,
                rateSnapshot.empty(key, name));
    }

    private RateMetricSnapshot rateMetricSnapshot() {
        long now = System.currentTimeMillis();
        long bucketMillis = RateMetricKeys.bucketSizeMillis(rateBucketSize);
        long instantWindowMillis = durationMillis(instantRateWindow, Duration.ofSeconds(60));
        long stableWindowMillis = Math.max(
                instantWindowMillis,
                durationMillis(stableRateWindow, Duration.ofMinutes(5)));
        long start = Math.floorDiv(now - stableWindowMillis, bucketMillis) * bucketMillis;
        long end = Math.floorDiv(now, bucketMillis) * bucketMillis;
        Set<String> indexedBuckets = indexedRateBuckets(start, end);
        List<RateMetricWindowCalculator.RateBucketPoint> points = new ArrayList<>();

        for (long bucketStart = start; bucketStart <= end; bucketStart += bucketMillis) {
            String bucketStartValue = Long.toString(bucketStart);
            boolean shouldRead = indexedBuckets == null
                    || indexedBuckets.contains(bucketStartValue);
            points.add(new RateMetricWindowCalculator.RateBucketPoint(
                    bucketStart,
                    shouldRead ? rateBucketCounts(bucketStart) : Map.of()));
        }

        RateMetricWindowCalculator calculator = new RateMetricWindowCalculator(
                bucketMillis,
                instantWindowMillis,
                stableWindowMillis,
                ewmaAlpha);
        return new RateMetricSnapshot(calculator, points, now);
    }

    private Set<String> indexedRateBuckets(long start, long end) {
        try {
            Set<String> buckets = redisTemplate.opsForZSet().rangeByScore(rateBucketIndexKey(), start, end);
            return buckets == null ? Set.of() : buckets;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Long> rateBucketCounts(long bucketStart) {
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(
                    RateMetricKeys.bucketKey(rateMetricsKeyPrefix, bucketStart));
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, Long> counts = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                long count = parseLong(String.valueOf(value));
                if (key != null && count > 0L) {
                    counts.put(String.valueOf(key), count);
                }
            });
            return counts;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private String rateBucketIndexKey() {
        return RateMetricKeys.bucketIndexKey(rateMetricsKeyPrefix);
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

    private String blockedReason(long messageCount, int consumerCount, QueueConsumerPolicy policy) {
        if (messageCount <= 0L || consumerCount > 0) {
            return null;
        }
        if (policy == null || !policy.hasConsumer()) {
            return "NO_CONSUMER_IMPL";
        }
        for (SystemConfigWorkSubmissionGate.ConfigFlag flag : policy.enabledFlags()) {
            if (!configEnabled(flag.key(), flag.defaultEnabled())) {
                return "DISABLED_BY_CONFIG";
            }
        }
        return "CONSUMER_STALE";
    }

    private QueueConsumerPolicy pipelineWorkPolicy(String taskType) {
        return SystemConfigWorkSubmissionGate.pipelineConsumerFlags(taskType)
                .map(QueueConsumerPolicy::enabled)
                .orElseGet(QueueConsumerPolicy::missing);
    }

    private QueueConsumerPolicy runtimeWorkPolicy(String taskType) {
        return SystemConfigWorkSubmissionGate.runtimeConsumerFlags(taskType)
                .map(QueueConsumerPolicy::enabled)
                .orElseGet(QueueConsumerPolicy::missing);
    }

    private boolean configEnabled(String key, boolean defaultValue) {
        try {
            return systemConfigRepository.findValue(key)
                    .map(value -> parseBoolean(value, defaultValue))
                    .orElse(defaultValue);
        } catch (RuntimeException ignored) {
            return defaultValue;
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

    private RateDescriptor pipelineRate(String taskType, PipelineMetricRole role) {
        return switch (role) {
            case PENDING -> new RateDescriptor(
                    RateKind.CLAIM,
                    RateMetricKeys.pipelineMetric(taskType, RateMetricKeys.ACTION_CLAIMED),
                    "领取速率");
            case INFLIGHT -> new RateDescriptor(
                    RateKind.COMPLETE,
                    RateMetricKeys.pipelineMetric(taskType, RateMetricKeys.ACTION_COMPLETED),
                    "完成速率");
            case DLQ -> new RateDescriptor(
                    RateKind.FAIL,
                    RateMetricKeys.pipelineMetric(taskType, RateMetricKeys.ACTION_FAILED),
                    "失败速率");
        };
    }

    private RateDescriptor runtimeRate(String taskType, RuntimeWorkMetricRole role) {
        return switch (role) {
            case PENDING -> new RateDescriptor(
                    RateKind.CLAIM,
                    RateMetricKeys.runtimeMetric(taskType, RateMetricKeys.ACTION_CLAIMED),
                    "领取速率");
            case INFLIGHT -> new RateDescriptor(
                    RateKind.COMPLETE,
                    RateMetricKeys.runtimeMetric(taskType, RateMetricKeys.ACTION_COMPLETED),
                    "完成速率");
            case DLQ -> new RateDescriptor(
                    RateKind.FAIL,
                    RateMetricKeys.runtimeMetric(taskType, RateMetricKeys.ACTION_FAILED),
                    "失败速率");
        };
    }

    private long durationMillis(Duration value, Duration fallback) {
        Duration effective = value == null || value.isZero() || value.isNegative() ? fallback : value;
        return Math.max(RateMetricKeys.DEFAULT_BUCKET_SIZE.toMillis(), effective.toMillis());
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

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "on", "enabled" -> true;
            case "false", "0", "no", "off", "disabled" -> false;
            default -> defaultValue;
        };
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

    private record RateDescriptor(RateKind kind, String metricKey, String label) {
    }

    private record QueueConsumerPolicy(
            boolean hasConsumer,
            List<SystemConfigWorkSubmissionGate.ConfigFlag> enabledFlags) {

        static QueueConsumerPolicy enabled() {
            return new QueueConsumerPolicy(true, List.of());
        }

        static QueueConsumerPolicy enabled(List<SystemConfigWorkSubmissionGate.ConfigFlag> enabledFlags) {
            return new QueueConsumerPolicy(true, enabledFlags == null ? List.of() : enabledFlags);
        }

        static QueueConsumerPolicy missing() {
            return new QueueConsumerPolicy(false, List.of());
        }
    }

    private record RateMetricSnapshot(
            RateMetricWindowCalculator calculator,
            List<RateMetricWindowCalculator.RateBucketPoint> buckets,
            long nowMillis) {

        RateMetricResponse metric(String metricKey, String label, String... sourceMetricKeys) {
            return calculator.calculate(metricKey, label, buckets, nowMillis, sourceMetricKeys);
        }

        RateMetricResponse empty(String metricKey, String label) {
            return calculator.empty(metricKey, label);
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
