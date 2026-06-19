package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.normalization.LlmCleaningWorkGate;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class LlmCleaningWorkQueueSubmitGate implements LlmCleaningWorkGate {

    static final String ENABLED_KEY = "app.normalization.llm-cleaning.work-queue.worker.enabled";
    private static final long ENABLED_CACHE_TTL_NANOS = Duration.ofSeconds(30).toNanos();

    private final SystemConfigRepository repository;
    private final boolean enabledByDeployment;
    private volatile Boolean cachedEnabled;
    private volatile long cachedEnabledExpiresAtNanos;

    LlmCleaningWorkQueueSubmitGate(
            SystemConfigRepository repository,
            @Value("${app.normalization.llm-cleaning.work-queue.worker.enabled:false}") boolean enabledByDeployment) {
        this.repository = repository;
        this.enabledByDeployment = enabledByDeployment;
    }

    @Override
    public boolean canSubmit() {
        long now = System.nanoTime();
        Boolean value = cachedEnabled;
        if (value != null && now < cachedEnabledExpiresAtNanos) {
            return value;
        }
        synchronized (this) {
            value = cachedEnabled;
            if (value != null && now < cachedEnabledExpiresAtNanos) {
                return value;
            }
            boolean refreshed = enabledByDeployment;
            try {
                repository.evict(ENABLED_KEY);
                refreshed = repository.findValue(ENABLED_KEY)
                        .map(raw -> parseBoolean(raw, enabledByDeployment))
                        .orElse(enabledByDeployment);
            } catch (RuntimeException ex) {
                log.warn("Unable to read LLM cleaning submit gate config: {}", ex.getMessage());
            }
            cachedEnabled = refreshed;
            cachedEnabledExpiresAtNanos = now + ENABLED_CACHE_TTL_NANOS;
            return refreshed;
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "y", "on", "enabled" -> true;
            case "false", "0", "no", "n", "off", "disabled" -> false;
            default -> defaultValue;
        };
    }
}
