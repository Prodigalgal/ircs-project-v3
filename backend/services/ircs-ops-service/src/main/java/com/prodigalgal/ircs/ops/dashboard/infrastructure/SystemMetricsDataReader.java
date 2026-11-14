package com.prodigalgal.ircs.ops.dashboard.infrastructure;

import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueClient;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueSnapshot;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueues;
import com.prodigalgal.ircs.ops.queue.domain.QueueState;
import com.prodigalgal.ircs.ops.dashboard.dto.KeyValueMetricResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemMetricsDataReader {

    private final RabbitAdmin rabbitAdmin;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectProvider<RuntimeWorkQueue> runtimeWorkQueueProvider;
    private final ObjectProvider<AggregationOpsStatsClient> aggregationOpsStatsClientProvider;
    private final BrokerManagementQueueClient rabbitManagementQueueClient;
    private volatile CachedRabbitQueues cachedRabbitQueues;

    public QueueState queueState(String queueName) {
        BrokerManagementQueueSnapshot snapshot = nativeQueueSnapshot(queueName);
        if (snapshot != null) {
            return new QueueState(snapshot.messagesTotal(), snapshot.consumers());
        }
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

    private BrokerManagementQueueSnapshot nativeQueueSnapshot(String queueName) {
        Map<String, BrokerManagementQueueSnapshot> snapshots = nativeQueueSnapshots();
        return snapshots.get(queueName);
    }

    private Map<String, BrokerManagementQueueSnapshot> nativeQueueSnapshots() {
        long now = System.currentTimeMillis();
        CachedRabbitQueues cached = cachedRabbitQueues;
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.snapshots();
        }
        Optional<BrokerManagementQueues> queues = rabbitManagementQueueClient.fetchQueueSnapshots();
        Map<String, BrokerManagementQueueSnapshot> snapshots = queues
                .map(BrokerManagementQueues::byName)
                .orElse(Collections.emptyMap());
        cachedRabbitQueues = new CachedRabbitQueues(snapshots, now + Duration.ofSeconds(1).toMillis());
        return snapshots;
    }

    public KeyValueMetricResponse redisMetric() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Properties info = connection.serverCommands().info();
            if (info == null) {
                return null;
            }
            return new KeyValueMetricResponse(
                    parseLong(info.getProperty("used_memory")),
                    (int) parseLong(info.getProperty("connected_clients")),
                    parseLong(info.getProperty("instantaneous_ops_per_sec")),
                    info.getProperty("redis_version"));
        } catch (Exception ignored) {
            return null;
        }
    }

    public RuntimeWorkQueueCounts runtimeWorkCounts(String taskType) {
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

    public long runtimeWorkConsumerCount(String taskType) {
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

    public long runtimeWorkDlqConsumerCount(String taskType) {
        RuntimeWorkQueue queue = runtimeWorkQueueProvider.getIfAvailable();
        if (queue == null) {
            return 0L;
        }
        try {
            return queue.dlqConsumerCount(taskType);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public Map<String, Object> aggregationOpsStats() {
        AggregationOpsStatsClient client = aggregationOpsStatsClientProvider == null
                ? null
                : aggregationOpsStatsClientProvider.getIfAvailable();
        if (client == null) {
            return Map.of();
        }
        try {
            Map<String, Object> stats = client.currentStats();
            return stats == null ? Map.of() : stats;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private RuntimeWorkQueueCounts emptyRuntimeWorkCounts() {
        return new RuntimeWorkQueueCounts(0, 0, 0);
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
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

    private record CachedRabbitQueues(
            Map<String, BrokerManagementQueueSnapshot> snapshots,
            long expiresAtMillis
    ) {
    }
}
