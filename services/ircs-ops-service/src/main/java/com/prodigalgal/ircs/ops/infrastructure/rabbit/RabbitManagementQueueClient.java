package com.prodigalgal.ircs.ops.infrastructure.rabbit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitManagementQueueClient {

    private static final String DEFAULT_BASE_URL_PORT = "15672";

    private final ObjectMapper objectMapper;
    private final RuntimeConfigService runtimeConfig;
    private volatile HttpClient httpClient;
    private volatile Duration httpClientConnectTimeout;
    private volatile String lastError;

    @Value("${spring.rabbitmq.host:localhost}")
    private String fallbackRabbitHost;

    @Value("${spring.rabbitmq.username:guest}")
    private String fallbackUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String fallbackPassword;

    public Optional<RabbitManagementQueues> fetchQueues() {
        if (!enabled()) {
            lastError = "disabled";
            return Optional.empty();
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
            JsonNode root = objectMapper.readTree(response.body());
            RabbitManagementQueues queues = new RabbitManagementQueues(root, snapshots(root));
            lastError = null;
            return Optional.of(queues);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            markFailed(ex);
            return Optional.empty();
        } catch (Exception ex) {
            markFailed(ex);
            return Optional.empty();
        }
    }

    public String lastError() {
        return lastError;
    }

    List<RabbitManagementQueueSnapshot> snapshots(JsonNode root) {
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("Rabbit management queues response is not an array");
        }
        List<RabbitManagementQueueSnapshot> snapshots = new ArrayList<>();
        for (JsonNode node : root) {
            String name = node.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            long ready = longValue(node, "messages_ready", 0L);
            long unacknowledged = longValue(node, "messages_unacknowledged", 0L);
            long total = longValue(node, "messages", ready + unacknowledged);
            snapshots.add(new RabbitManagementQueueSnapshot(
                    name,
                    safeInt(ready),
                    safeInt(unacknowledged),
                    safeInt(total),
                    safeInt(longValue(node, "consumers", 0L))));
        }
        return List.copyOf(snapshots);
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
        lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        log.debug("Rabbit management queue snapshot failed: {}", lastError);
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

    private long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asLong(fallback);
    }

    private int safeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
