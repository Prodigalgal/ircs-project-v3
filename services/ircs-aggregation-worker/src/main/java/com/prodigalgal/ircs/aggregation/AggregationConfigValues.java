package com.prodigalgal.ircs.aggregation;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class AggregationConfigValues {

    static final String BATCH_SIZE_KEY = "aggregation.batch-size";
    static final String BATCH_SIZE_ALIAS = "app.aggregation.batch-size";
    static final int DEFAULT_BATCH_SIZE = 200;

    private final Environment environment;
    private final SystemConfigRepository repository;

    AggregationConfigValues(Environment environment, SystemConfigRepository repository) {
        this.environment = environment;
        this.repository = repository;
    }

    int batchSize() {
        return intValue(DEFAULT_BATCH_SIZE, BATCH_SIZE_KEY, BATCH_SIZE_ALIAS);
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
                        Arrays.stream(aliases))
                .toArray(String[]::new);
        return RuntimeInjectedConfig.find(environment, runtimeKeys)
                .or(() -> repository.findValue(key))
                .orElse(null);
    }
}
