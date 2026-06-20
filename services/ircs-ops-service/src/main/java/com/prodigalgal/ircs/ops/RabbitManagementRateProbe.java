package com.prodigalgal.ircs.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
class RabbitManagementRateProbe {

    private static final String DEFAULT_BASE_URL_PORT = "15672";
    private static final Map<String, QueueBinding> QUEUE_BINDINGS = queueBindings();

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private volatile HttpClient httpClient;
    private volatile boolean lastSampleSuccessful;
    private volatile Instant lastSampleAt;
    private volatile String lastError;

    @Value("${app.ops.rabbit-management.enabled:true}")
    private boolean enabled;

    @Value("${app.ops.rabbit-management.base-url:}")
    private String baseUrl;

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    @Value("${app.ops.rabbit-management.vhost:/}")
    private String vhost;

    @Value("${app.ops.rabbit-management.connect-timeout:PT3S}")
    private Duration connectTimeout;

    @Value("${app.ops.rabbit-management.request-timeout:PT5S}")
    private Duration requestTimeout;

    @Value("${app.rate-metrics.key-prefix:ircs:metrics:rate}")
    private String rateMetricsKeyPrefix;

    @Value("${app.rate-metrics.bucket-size:PT10S}")
    private Duration rateBucketSize;

    @Value("${app.rate-metrics.bucket-ttl:PT30M}")
    private Duration rateBucketTtl;

    @Value("${app.rate-metrics.bucket-index-retention:PT31M}")
    private Duration rateBucketIndexRetention;

    RabbitManagementRateProbe(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(
            initialDelayString = "${app.ops.rabbit-management.initial-delay-ms:10000}",
            fixedDelayString = "${app.ops.rabbit-management.fixed-delay-ms:10000}")
    public void sampleScheduled() {
        sampleOnce();
    }

    int sampleOnce() {
        if (!enabled) {
            lastSampleSuccessful = false;
            lastError = "disabled";
            return 0;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(queueEndpoint())
                    .timeout(positiveOr(requestTimeout, Duration.ofSeconds(5)))
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + basicAuth())
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Rabbit management returned HTTP " + response.statusCode());
            }
            int recorded = recordCounters(objectMapper.readTree(response.body()));
            lastSampleAt = Instant.now();
            lastSampleSuccessful = true;
            lastError = null;
            return recorded;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            markFailed(ex);
            return 0;
        } catch (Exception ex) {
            markFailed(ex);
            return 0;
        }
    }

    boolean rateAvailable() {
        Instant sampledAt = lastSampleAt;
        if (!lastSampleSuccessful || sampledAt == null) {
            return false;
        }
        return sampledAt.isAfter(Instant.now().minus(positiveOr(requestTimeout, Duration.ofSeconds(5)).multipliedBy(12)));
    }

    String lastError() {
        return lastError;
    }

    static String metricKey(QueueTopic topic, RabbitQueueRole role, RabbitRateAction action) {
        return "rabbit.%s.%s.%s".formatted(
                topic.name().toLowerCase(java.util.Locale.ROOT),
                role.metricPart(),
                action.metricPart());
    }

    int recordCounters(JsonNode root) {
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("Rabbit management queues response is not an array");
        }
        int recorded = 0;
        for (JsonNode node : root) {
            String queueName = node.path("name").asText("");
            QueueBinding binding = QUEUE_BINDINGS.get(queueName);
            if (binding == null) {
                continue;
            }
            JsonNode stats = node.path("message_stats");
            recorded += recordDelta(binding, RabbitRateAction.PUBLISHED, stats.path("publish").asLong(0L));
            recorded += recordDelta(binding, RabbitRateAction.DELIVERED, stats.path("deliver_get").asLong(0L));
            recorded += recordDelta(binding, RabbitRateAction.ACKED, stats.path("ack").asLong(0L));
            recorded += recordDelta(binding, RabbitRateAction.REDELIVERED, stats.path("redeliver").asLong(0L));
        }
        return recorded;
    }

    private int recordDelta(QueueBinding binding, RabbitRateAction action, long value) {
        String counterKey = binding.queueName() + ":" + action.metricPart();
        Long previous = counters.put(counterKey, value);
        if (previous == null || value < previous) {
            return 0;
        }
        long delta = value - previous;
        if (delta <= 0L) {
            return 0;
        }
        recordRate(metricKey(binding.topic(), binding.role(), action), delta);
        return 1;
    }

    private void recordRate(String metricKey, long delta) {
        long now = System.currentTimeMillis();
        long bucketStart = RateMetricKeys.bucketStartMillis(now, rateBucketSize);
        String normalizedPrefix = RateMetricKeys.normalizeKeyPrefix(rateMetricsKeyPrefix);
        String bucketKey = RateMetricKeys.bucketKey(normalizedPrefix, bucketStart);
        String bucketIndexKey = RateMetricKeys.bucketIndexKey(normalizedPrefix);
        redisTemplate.opsForHash().increment(bucketKey, metricKey, delta);
        redisTemplate.expire(bucketKey, Duration.ofSeconds(RateMetricKeys.ttlSeconds(
                rateBucketTtl,
                RateMetricKeys.DEFAULT_BUCKET_TTL)));
        redisTemplate.opsForZSet().add(bucketIndexKey, Long.toString(bucketStart), bucketStart);
        long retentionMillis = RateMetricKeys.retentionMillis(
                rateBucketIndexRetention,
                RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION);
        redisTemplate.opsForZSet().removeRangeByScore(
                bucketIndexKey,
                Double.NEGATIVE_INFINITY,
                bucketStart - retentionMillis);
        redisTemplate.expire(bucketIndexKey, Duration.ofMillis(retentionMillis));
    }

    private URI queueEndpoint() {
        String root = StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : "http://" + rabbitHost + ":" + DEFAULT_BASE_URL_PORT;
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return URI.create(root + "/api/queues/" + encodeVhost());
    }

    private String encodeVhost() {
        String raw = StringUtils.hasText(vhost) ? vhost.trim() : "/";
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String basicAuth() {
        String raw = (StringUtils.hasText(username) ? username : "guest") + ":"
                + (password == null ? "" : password);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HttpClient client() {
        HttpClient existing = httpClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(positiveOr(connectTimeout, Duration.ofSeconds(3)))
                        .build();
            }
            return httpClient;
        }
    }

    private void markFailed(Exception ex) {
        lastSampleSuccessful = false;
        lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        log.debug("Rabbit management rate probe failed: {}", lastError);
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static Map<String, QueueBinding> queueBindings() {
        Map<String, QueueBinding> bindings = new HashMap<>();
        for (QueueTopic topic : QueueTopic.values()) {
            bindings.put(topic.queueName(), new QueueBinding(topic.queueName(), topic, RabbitQueueRole.MAIN));
            bindings.put(topic.retryName(), new QueueBinding(topic.retryName(), topic, RabbitQueueRole.RETRY));
            bindings.put(topic.dlqName(), new QueueBinding(topic.dlqName(), topic, RabbitQueueRole.DLQ));
        }
        return Map.copyOf(bindings);
    }

    private record QueueBinding(String queueName, QueueTopic topic, RabbitQueueRole role) {
    }

    enum RabbitQueueRole {
        MAIN("main"),
        RETRY("retry"),
        DLQ("dlq");

        private final String metricPart;

        RabbitQueueRole(String metricPart) {
            this.metricPart = metricPart;
        }

        String metricPart() {
            return metricPart;
        }
    }

    enum RabbitRateAction {
        PUBLISHED("published"),
        DELIVERED("delivered"),
        ACKED("acked"),
        REDELIVERED("redelivered");

        private final String metricPart;

        RabbitRateAction(String metricPart) {
            this.metricPart = metricPart;
        }

        String metricPart() {
            return metricPart;
        }
    }
}
