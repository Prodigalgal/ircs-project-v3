package com.prodigalgal.ircs.normalization;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class NormalizationConfigValues {

    static final String MAX_RETRIES_KEY = "normalization.max-retries";
    static final String MAX_RETRIES_ALIAS = "app.normalization.max-retries";
    static final String BACKOFF_BASE_SECONDS_KEY = "normalization.backoff.base-seconds";
    static final String BACKOFF_BASE_SECONDS_ALIAS = "app.normalization.backoff-base-seconds";
    static final String BATCH_SIZE_KEY = "normalization.batch-size";
    static final String BATCH_SIZE_MIN_KEY = "normalization.batch-size.min";
    static final String BATCH_SIZE_MAX_KEY = "normalization.batch-size.max";
    static final String WATCHDOG_BATCH_SIZE_ALIAS = "app.normalization.watchdog.batch-size";
    static final String PENDING_TIMEOUT_MIN_KEY = "normalization.timeout.pending-min";
    static final String WATCHDOG_STALE_PENDING_SECONDS_ALIAS = "app.normalization.watchdog.stale-pending-seconds";
    static final String LLM_ENABLED_KEY = "app.ai.llm.enabled";
    static final String LLM_CLEANING_MODE_KEY = "app.ai.llm.cleaning.mode";
    static final String LLM_CLEANING_BATCH_SIZE_KEY = "app.ai.llm.cleaning.batch-size";
    static final String LLM_MODEL_KEY = "app.ai.llm.model";
    static final String LLM_CREDENTIAL_SERVICE_BASE_URL_KEY = "app.normalization.llm.credential-service.base-url";
    static final String LLM_CREDENTIAL_SERVICE_BASE_URL_ALIAS = "app.credential-service.base-url";
    static final String LLM_CREDENTIAL_SERVICE_TOKEN_KEY = "app.normalization.llm.credential-service.token";
    static final String LLM_CREDENTIAL_SERVICE_ID_KEY = "app.normalization.llm.credential-service.service-id";
    static final String LLM_CREDENTIAL_SERVICE_SCOPES_KEY = "app.normalization.llm.credential-service.scopes";
    static final String LLM_REQUEST_TIMEOUT_SECONDS_KEY = "app.ai.llm.request-timeout-seconds";
    static final String LLM_API_KEY_KEY = "spring.ai.openai.api-key";
    static final String LLM_BASE_URL_KEY = "spring.ai.openai.base-url";
    static final String LLM_PROVIDER_KEY = "app.ai.llm.provider";
    static final int DEFAULT_MAX_RETRIES = 5;
    static final long DEFAULT_BACKOFF_BASE_SECONDS = 60L;
    static final int DEFAULT_BATCH_SIZE = 500;
    static final int DEFAULT_BATCH_SIZE_MIN = 100;
    static final int DEFAULT_BATCH_SIZE_MAX = 1000;
    static final long DEFAULT_PENDING_TIMEOUT_MIN = 60L;
    static final String DEFAULT_LLM_CLEANING_MODE = "dry-run";
    static final String DEFAULT_LLM_MODEL = "gemma-4-31b-it";
    static final String DEFAULT_LLM_PROVIDER = "OPENAI";
    static final String DEFAULT_SERVICE_ID = "normalization-worker";
    static final String DEFAULT_CREDENTIAL_SERVICE_SCOPES = "credential:lease";
    static final String DEFAULT_OPENAI_BASE_URL = "https://ai.mnnu.eu.org/v1";
    static final long DEFAULT_LLM_REQUEST_TIMEOUT_SECONDS = 30L;

    private final Environment environment;
    private final SystemConfigRepository repository;

    NormalizationConfigValues(Environment environment, SystemConfigRepository repository) {
        this.environment = environment;
        this.repository = repository;
    }

    int maxRetries() {
        return intValue(DEFAULT_MAX_RETRIES, MAX_RETRIES_KEY, MAX_RETRIES_ALIAS);
    }

    long backoffBaseSeconds() {
        return longValue(DEFAULT_BACKOFF_BASE_SECONDS, BACKOFF_BASE_SECONDS_KEY, BACKOFF_BASE_SECONDS_ALIAS);
    }

    int watchdogBatchSize() {
        String runtimeBatchSize = valueFromRuntime(BATCH_SIZE_KEY, WATCHDOG_BATCH_SIZE_ALIAS);
        if (StringUtils.hasText(runtimeBatchSize)) {
            return Math.max(1, parseInt(runtimeBatchSize, DEFAULT_BATCH_SIZE));
        }
        int batchSize = dbIntValue(DEFAULT_BATCH_SIZE, BATCH_SIZE_KEY);
        int min = intValue(DEFAULT_BATCH_SIZE_MIN, BATCH_SIZE_MIN_KEY);
        int max = intValue(DEFAULT_BATCH_SIZE_MAX, BATCH_SIZE_MAX_KEY);
        if (max < min) {
            return Math.max(1, batchSize);
        }
        return Math.max(1, Math.min(max, Math.max(min, batchSize)));
    }

    long watchdogStalePendingSeconds() {
        String runtimeSeconds = valueFromRuntime(WATCHDOG_STALE_PENDING_SECONDS_ALIAS);
        if (StringUtils.hasText(runtimeSeconds)) {
            return Math.max(1L, parseLong(runtimeSeconds, DEFAULT_PENDING_TIMEOUT_MIN * 60L));
        }
        String runtimeMinutes = valueFromRuntime(PENDING_TIMEOUT_MIN_KEY);
        if (StringUtils.hasText(runtimeMinutes)) {
            return Math.max(1L, parseLong(runtimeMinutes, DEFAULT_PENDING_TIMEOUT_MIN)) * 60L;
        }
        long minutes = repository.findValue(PENDING_TIMEOUT_MIN_KEY)
                .map(raw -> parseLong(raw, DEFAULT_PENDING_TIMEOUT_MIN))
                .orElse(DEFAULT_PENDING_TIMEOUT_MIN);
        return Math.max(1L, minutes) * 60L;
    }

    boolean llmCleaningEnabled() {
        return booleanValue(false, LLM_ENABLED_KEY, "app.normalization.llm-cleaning.enabled");
    }

    String llmCleaningMode() {
        String mode = value(LLM_CLEANING_MODE_KEY, "app.normalization.llm-cleaning.mode");
        return StringUtils.hasText(mode) ? mode.trim().toLowerCase(Locale.ROOT) : DEFAULT_LLM_CLEANING_MODE;
    }

    int llmCleaningBatchSize() {
        return intValue(DEFAULT_BATCH_SIZE, LLM_CLEANING_BATCH_SIZE_KEY, "app.normalization.llm-cleaning.batch-size");
    }

    String llmModel() {
        String model = value(LLM_MODEL_KEY, "spring.ai.openai.chat.options.model");
        return StringUtils.hasText(model) ? model.trim() : DEFAULT_LLM_MODEL;
    }

    String llmProvider() {
        String provider = value(LLM_PROVIDER_KEY, "app.normalization.llm.provider");
        return StringUtils.hasText(provider) ? provider.trim().toUpperCase(Locale.ROOT) : DEFAULT_LLM_PROVIDER;
    }

    String llmRuntimeApiKey() {
        return valueFromRuntime(LLM_API_KEY_KEY);
    }

    String llmRuntimeBaseUrl() {
        String baseUrl = valueFromRuntime(LLM_BASE_URL_KEY);
        return StringUtils.hasText(baseUrl) ? baseUrl.trim() : DEFAULT_OPENAI_BASE_URL;
    }

    String llmCredentialServiceBaseUrl() {
        return value(LLM_CREDENTIAL_SERVICE_BASE_URL_KEY, LLM_CREDENTIAL_SERVICE_BASE_URL_ALIAS);
    }

    String llmCredentialServiceToken() {
        return valueFromRuntime(LLM_CREDENTIAL_SERVICE_TOKEN_KEY, "app.credential-service.token");
    }

    String llmCredentialServiceId() {
        String value = valueFromRuntime(
                LLM_CREDENTIAL_SERVICE_ID_KEY,
                "APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_ID",
                "app.credential-service.service-id");
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_SERVICE_ID;
    }

    String llmCredentialServiceScopes() {
        String value = valueFromRuntime(
                LLM_CREDENTIAL_SERVICE_SCOPES_KEY,
                "APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_SCOPES",
                "app.credential-service.scopes");
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_CREDENTIAL_SERVICE_SCOPES;
    }

    long llmRequestTimeoutSeconds() {
        return Math.max(1L, longValue(
                DEFAULT_LLM_REQUEST_TIMEOUT_SECONDS,
                LLM_REQUEST_TIMEOUT_SECONDS_KEY,
                "app.normalization.llm.request-timeout-seconds"));
    }

    private boolean booleanValue(boolean defaultValue, String key, String... aliases) {
        String raw = value(key, aliases);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on", "enabled" -> true;
            case "false", "0", "no", "n", "off", "disabled" -> false;
            default -> defaultValue;
        };
    }

    private int intValue(int defaultValue, String key, String... aliases) {
        String raw = value(key, aliases);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        return parseInt(raw, defaultValue);
    }

    private int dbIntValue(int defaultValue, String key) {
        return repository.findValue(key)
                .map(raw -> parseInt(raw, defaultValue))
                .orElse(defaultValue);
    }

    private long longValue(long defaultValue, String key, String... aliases) {
        String raw = value(key, aliases);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
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

    private String valueFromRuntime(String key) {
        return RuntimeInjectedConfig.find(environment, key).orElse(null);
    }

    private String valueFromRuntime(String key, String... aliases) {
        String[] runtimeKeys = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(key),
                        Arrays.stream(aliases))
                .toArray(String[]::new);
        return RuntimeInjectedConfig.find(environment, runtimeKeys).orElse(null);
    }

    private long parseLong(String raw, long defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private int parseInt(String raw, int defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
