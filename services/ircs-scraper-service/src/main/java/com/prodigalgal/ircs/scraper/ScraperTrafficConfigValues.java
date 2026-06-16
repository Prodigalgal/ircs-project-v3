package com.prodigalgal.ircs.scraper;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ScraperTrafficConfigValues {

    static final String GLOBAL_MIN_DELAY_KEY = "global.traffic.min-delay-ms";
    static final String GLOBAL_MAX_DELAY_KEY = "global.traffic.max-delay-ms";
    static final String GLOBAL_SAFETY_FLOOR_KEY = "global.traffic.safety-floor-ms";
    static final String GLOBAL_MAX_WAIT_KEY = "global.traffic.max-wait-ms";

    static final int DEFAULT_GLOBAL_MIN_DELAY_MS = 3000;
    static final int DEFAULT_GLOBAL_MAX_DELAY_MS = 5000;
    static final int DEFAULT_GLOBAL_SAFETY_FLOOR_MS = 3000;
    static final int DEFAULT_GLOBAL_MAX_WAIT_MS = 120000;

    private final Environment environment;
    private final SystemConfigRepository repository;

    ScraperTrafficConfigValues(Environment environment, SystemConfigRepository repository) {
        this.environment = environment;
        this.repository = repository;
    }

    Duration globalDelay() {
        int min = globalMinDelayMs();
        int max = Math.max(min, intValue(DEFAULT_GLOBAL_MAX_DELAY_MS, GLOBAL_MAX_DELAY_KEY));
        if (max == min) {
            return Duration.ofMillis(min);
        }
        return Duration.ofMillis(java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    Duration safetyFloor() {
        return Duration.ofMillis(Math.max(0, intValue(DEFAULT_GLOBAL_SAFETY_FLOOR_MS, GLOBAL_SAFETY_FLOOR_KEY)));
    }

    Duration maxWait(Duration fallback) {
        int configured = Math.max(0, intValue(DEFAULT_GLOBAL_MAX_WAIT_MS, GLOBAL_MAX_WAIT_KEY));
        if (configured > 0) {
            return Duration.ofMillis(configured);
        }
        return fallback == null || fallback.isNegative() ? Duration.ZERO : fallback;
    }

    private int globalMinDelayMs() {
        return Math.max(0, intValue(DEFAULT_GLOBAL_MIN_DELAY_MS, GLOBAL_MIN_DELAY_KEY));
    }

    private int intValue(int defaultValue, String key, String... aliases) {
        String raw = value(key, aliases);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String value(String key, String... aliases) {
        String[] runtimeKeys = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(key),
                        java.util.Arrays.stream(aliases))
                .toArray(String[]::new);
        return RuntimeInjectedConfig.find(environment, runtimeKeys)
                .or(() -> repository.findValue(key))
                .orElse(null);
    }
}
