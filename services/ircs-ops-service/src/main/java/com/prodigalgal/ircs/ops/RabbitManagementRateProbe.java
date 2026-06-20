package com.prodigalgal.ircs.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
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
import java.util.Objects;
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
    private final RuntimeConfigService runtimeConfig;
    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private volatile HttpClient httpClient;
    private volatile Duration httpClientConnectTimeout;
    private volatile Instant nextSampleAt;
    private volatile boolean lastSampleSuccessful;
    private volatile Instant lastSampleAt;
    private volatile String lastError;

    @Value("${spring.rabbitmq.host:localhost}")
    private String fallbackRabbitHost;

    @Value("${spring.rabbitmq.username:guest}")
    private String fallbackUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String fallbackPassword;

    RabbitManagementRateProbe(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            RuntimeConfigService runtimeConfig) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.runtimeConfig = runtimeConfig;
    }

    RabbitManagementRateProbe(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this(objectMapper, redisTemplate, null);
    }

    @Scheduled(initialDelayString = "1000", fixedDelayString = "1000")
    public void sampleScheduled() {
        Instant now = Instant.now();
        Instant scheduledAt = nextSampleAt;
        if (scheduledAt == null) {
            nextSampleAt = now.plus(initialDelay());
            return;
        }
        if (now.isBefore(scheduledAt)) {
            return;
        }
        nextSampleAt = now.plus(fixedDelay());
        sampleOnce();
    }

    int sampleOnce() {
        if (!enabled()) {
            lastSampleSuccessful = false;
            lastError = "disabled";
            return 0;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(queueEndpoint())
                    .timeout(requestTimeout())
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
        return sampledAt.isAfter(Instant.now().minus(requestTimeout().multipliedBy(12)));
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
        long bucketStart = RateMetricKeys.bucketStartMillis(now, rateBucketSize());
        String normalizedPrefix = RateMetricKeys.normalizeKeyPrefix(rateMetricsKeyPrefix());
        String bucketKey = RateMetricKeys.bucketKey(normalizedPrefix, bucketStart);
        String bucketIndexKey = RateMetricKeys.bucketIndexKey(normalizedPrefix);
        redisTemplate.opsForHash().increment(bucketKey, metricKey, delta);
        redisTemplate.expire(bucketKey, Duration.ofSeconds(RateMetricKeys.ttlSeconds(
                rateBucketTtl(),
                RateMetricKeys.DEFAULT_BUCKET_TTL)));
        redisTemplate.opsForZSet().add(bucketIndexKey, Long.toString(bucketStart), bucketStart);
        long retentionMillis = RateMetricKeys.retentionMillis(
                rateBucketIndexRetention(),
                RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION);
        redisTemplate.opsForZSet().removeRangeByScore(
                bucketIndexKey,
                Double.NEGATIVE_INFINITY,
                bucketStart - retentionMillis);
        redisTemplate.expire(bucketIndexKey, Duration.ofMillis(retentionMillis));
    }

    private URI queueEndpoint() {
        String baseUrl = baseUrl();
        String root = StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : "http://" + rabbitHost() + ":" + DEFAULT_BASE_URL_PORT;
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return URI.create(root + "/api/queues/" + encodeVhost());
    }

    private String encodeVhost() {
        String raw = StringUtils.hasText(vhost()) ? vhost().trim() : "/";
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String basicAuth() {
        String raw = (StringUtils.hasText(username()) ? username() : "guest") + ":"
                + (password() == null ? "" : password());
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HttpClient client() {
        Duration desiredConnectTimeout = connectTimeout();
        HttpClient existing = httpClient;
        if (existing != null && Objects.equals(httpClientConnectTimeout, desiredConnectTimeout)) {
            return existing;
        }
        synchronized (this) {
            if (httpClient == null || !Objects.equals(httpClientConnectTimeout, desiredConnectTimeout)) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(desiredConnectTimeout)
                        .build();
                httpClientConnectTimeout = desiredConnectTimeout;
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

    private boolean enabled() {
        return runtimeConfig == null
                || runtimeConfig.booleanValue("app.ops.rabbit-management.enabled", true);
    }

    private String baseUrl() {
        return runtimeConfig == null
                ? ""
                : runtimeConfig.stringValue("app.ops.rabbit-management.base-url", "");
    }

    private String rabbitHost() {
        return runtimeConfig == null
                ? fallbackRabbitHost
                : runtimeConfig.stringValue("spring.rabbitmq.host", fallbackRabbitHost);
    }

    private String username() {
        return runtimeConfig == null
                ? fallbackUsername
                : runtimeConfig.stringValue("spring.rabbitmq.username", fallbackUsername);
    }

    private String password() {
        return runtimeConfig == null
                ? fallbackPassword
                : runtimeConfig.stringValue("spring.rabbitmq.password", fallbackPassword);
    }

    private String vhost() {
        return runtimeConfig == null
                ? "/"
                : runtimeConfig.stringValue("app.ops.rabbit-management.vhost", "/");
    }

    private Duration connectTimeout() {
        return runtimeConfig == null
                ? Duration.ofSeconds(3)
                : runtimeConfig.positiveDurationValue("app.ops.rabbit-management.connect-timeout", Duration.ofSeconds(3));
    }

    private Duration requestTimeout() {
        return runtimeConfig == null
                ? Duration.ofSeconds(5)
                : runtimeConfig.positiveDurationValue("app.ops.rabbit-management.request-timeout", Duration.ofSeconds(5));
    }

    private String rateMetricsKeyPrefix() {
        return runtimeConfig == null
                ? RateMetricKeys.DEFAULT_KEY_PREFIX
                : runtimeConfig.stringValue("app.rate-metrics.key-prefix", RateMetricKeys.DEFAULT_KEY_PREFIX);
    }

    private Duration rateBucketSize() {
        return runtimeConfig == null
                ? RateMetricKeys.DEFAULT_BUCKET_SIZE
                : runtimeConfig.positiveDurationValue("app.rate-metrics.bucket-size", RateMetricKeys.DEFAULT_BUCKET_SIZE);
    }

    private Duration rateBucketTtl() {
        return runtimeConfig == null
                ? RateMetricKeys.DEFAULT_BUCKET_TTL
                : runtimeConfig.positiveDurationValue("app.rate-metrics.bucket-ttl", RateMetricKeys.DEFAULT_BUCKET_TTL);
    }

    private Duration rateBucketIndexRetention() {
        return runtimeConfig == null
                ? RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION
                : runtimeConfig.positiveDurationValue(
                        "app.rate-metrics.bucket-index-retention",
                        RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION);
    }

    private Duration initialDelay() {
        long delayMs = runtimeConfig == null
                ? 10_000L
                : Math.max(0L, runtimeConfig.longValue("app.ops.rabbit-management.initial-delay-ms", 10_000L));
        return Duration.ofMillis(delayMs);
    }

    private Duration fixedDelay() {
        long delayMs = runtimeConfig == null
                ? 10_000L
                : Math.max(1_000L, runtimeConfig.longValue("app.ops.rabbit-management.fixed-delay-ms", 10_000L));
        return Duration.ofMillis(delayMs);
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
