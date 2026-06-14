package com.prodigalgal.ircs.search.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
class SearchOutboxRelayHealthIndicator implements HealthIndicator {

    private final Environment environment;
    private final ObjectProvider<SearchOutboxRelay> relayProvider;
    private final SearchSyncTaskOpsRepository statsRepository;

    @Override
    public Health health() {
        boolean enabled = enabled();
        SearchOutboxRelay relay = relayProvider.getIfAvailable();
        if (!enabled) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("relayBean", relay != null)
                    .build();
        }
        if (relay == null) {
            return Health.status(Status.OUT_OF_SERVICE)
                    .withDetail("enabled", true)
                    .withDetail("relayBean", false)
                    .withDetail("reason", "search_outbox_relay_enabled_but_bean_missing")
                    .build();
        }

        SearchSyncTaskStatsResponse stats = statsRepository.stats();
        SearchOutboxRelayState state = relay.state();
        Health.Builder builder;
        if (!stats.available()) {
            builder = Health.down()
                    .withDetail("reason", stats.unavailableReason());
        } else if (state.consecutiveFailures() > 0) {
            builder = Health.down()
                    .withDetail("reason", "search_outbox_relay_last_run_failed");
        } else {
            builder = Health.up();
        }
        return builder
                .withDetail("enabled", true)
                .withDetail("relayBean", true)
                .withDetail("workerId", state.workerId())
                .withDetail("running", state.running())
                .withDetail("lastRunAt", valueOrNever(state.lastRunAt()))
                .withDetail("lastSuccessAt", valueOrNever(state.lastSuccessAt()))
                .withDetail("lastFailureAt", valueOrNever(state.lastFailureAt()))
                .withDetail("lastPublished", state.lastPublished())
                .withDetail("lastRestored", state.lastRestored())
                .withDetail("consecutiveFailures", state.consecutiveFailures())
                .withDetail("lastError", StringUtils.hasText(state.lastError()) ? state.lastError() : "none")
                .withDetail("outbox", outboxDetails(stats))
                .build();
    }

    private Map<String, Object> outboxDetails(SearchSyncTaskStatsResponse stats) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("available", stats.available());
        details.put("total", stats.total());
        details.put("pending", stats.pending());
        details.put("processing", stats.processing());
        details.put("failed", stats.failed());
        details.put("duePending", stats.duePending());
        details.put("oldestPendingAt", valueOrNever(stats.oldestPendingAt()));
        details.put("oldestPendingAgeSeconds", ageSeconds(stats.oldestPendingAt()));
        details.put("nextDueAt", valueOrNever(stats.nextDueAt()));
        details.put("maxRetryCount", stats.maxRetryCount());
        return details;
    }

    private Long ageSeconds(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return Math.max(0L, Duration.between(timestamp, Instant.now()).toSeconds());
    }

    private Object valueOrNever(Instant timestamp) {
        return timestamp == null ? "never" : timestamp;
    }

    private boolean enabled() {
        String raw = firstNonBlank(
                environment.getProperty("app.search.outbox.relay.enabled"),
                environment.getProperty("APP_SEARCH_OUTBOX_RELAY_ENABLED"));
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "y", "on", "enabled" -> true;
            default -> false;
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
