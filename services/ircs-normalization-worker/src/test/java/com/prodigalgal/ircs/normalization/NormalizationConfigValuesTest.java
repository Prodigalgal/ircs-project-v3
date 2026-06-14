package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class NormalizationConfigValuesTest {

    private final SystemConfigRepository repository = org.mockito.Mockito.mock(SystemConfigRepository.class);

    @Test
    void usesV1DefaultsWhenNoRuntimeOrDbConfigExists() {
        NormalizationConfigValues values = values(new MockEnvironment());

        assertEquals(5, values.maxRetries());
        assertEquals(60L, values.backoffBaseSeconds());
        assertEquals(500, values.watchdogBatchSize());
        assertEquals(3600L, values.watchdogStalePendingSeconds());
    }

    @Test
    void dbCanonicalKeysOverrideV1Defaults() {
        when(repository.findValue("normalization.max-retries")).thenReturn(Optional.of("7"));
        when(repository.findValue("normalization.backoff.base-seconds")).thenReturn(Optional.of("90"));
        when(repository.findValue("normalization.batch-size")).thenReturn(Optional.of("33"));
        when(repository.findValue("normalization.batch-size.min")).thenReturn(Optional.of("10"));
        when(repository.findValue("normalization.batch-size.max")).thenReturn(Optional.of("40"));
        when(repository.findValue("normalization.timeout.pending-min")).thenReturn(Optional.of("15"));
        NormalizationConfigValues values = values(new MockEnvironment());

        assertEquals(7, values.maxRetries());
        assertEquals(90L, values.backoffBaseSeconds());
        assertEquals(33, values.watchdogBatchSize());
        assertEquals(900L, values.watchdogStalePendingSeconds());
    }

    @Test
    void dbBatchSizeIsClampedByAdaptiveMinAndMaxDefaults() {
        when(repository.findValue("normalization.batch-size")).thenReturn(Optional.of("33"));
        NormalizationConfigValues values = values(new MockEnvironment());

        assertEquals(100, values.watchdogBatchSize());
    }

    @Test
    void runtimeAliasesOverrideDbConfigAndKeepAliasUnits() {
        when(repository.findValue("normalization.max-retries")).thenReturn(Optional.of("7"));
        when(repository.findValue("normalization.backoff.base-seconds")).thenReturn(Optional.of("90"));
        when(repository.findValue("normalization.batch-size")).thenReturn(Optional.of("33"));
        when(repository.findValue("normalization.batch-size.min")).thenReturn(Optional.of("100"));
        when(repository.findValue("normalization.batch-size.max")).thenReturn(Optional.of("1000"));
        when(repository.findValue("normalization.timeout.pending-min")).thenReturn(Optional.of("15"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_NORMALIZATION_MAX_RETRIES", "3")
                .withProperty("APP_NORMALIZATION_BACKOFF_BASE_SECONDS", "12")
                .withProperty("APP_NORMALIZATION_WATCHDOG_BATCH_SIZE", "9")
                .withProperty("APP_NORMALIZATION_WATCHDOG_STALE_PENDING_SECONDS", "45");
        NormalizationConfigValues values = values(environment);

        assertEquals(3, values.maxRetries());
        assertEquals(12L, values.backoffBaseSeconds());
        assertEquals(9, values.watchdogBatchSize());
        assertEquals(45L, values.watchdogStalePendingSeconds());
    }

    @Test
    void runtimeCanonicalPendingTimeoutUsesV1MinutesUnit() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("NORMALIZATION_TIMEOUT_PENDING_MIN", "2");
        NormalizationConfigValues values = values(environment);

        assertEquals(120L, values.watchdogStalePendingSeconds());
    }

    @Test
    void invalidValuesFallBackWithoutThrowing() {
        when(repository.findValue("normalization.max-retries")).thenReturn(Optional.of("bad"));
        when(repository.findValue("normalization.backoff.base-seconds")).thenReturn(Optional.of("bad"));
        when(repository.findValue("normalization.batch-size")).thenReturn(Optional.of("bad"));
        when(repository.findValue("normalization.timeout.pending-min")).thenReturn(Optional.of("bad"));
        NormalizationConfigValues values = values(new MockEnvironment());

        assertEquals(5, values.maxRetries());
        assertEquals(60L, values.backoffBaseSeconds());
        assertEquals(500, values.watchdogBatchSize());
        assertEquals(3600L, values.watchdogStalePendingSeconds());
    }

    @Test
    void llmCleaningDefaultsStaySafeAndDisabled() {
        NormalizationConfigValues values = values(new MockEnvironment());

        assertEquals(false, values.llmCleaningEnabled());
        assertEquals("dry-run", values.llmCleaningMode());
        assertEquals(500, values.llmCleaningBatchSize());
        assertEquals("gemma-4-31b-it", values.llmModel());
        assertEquals("OPENAI", values.llmProvider());
    }

    @Test
    void llmRuntimeConfigOverridesDbSeedAndKeepsSecretRuntimeOnly() {
        when(repository.findValue("app.ai.llm.enabled")).thenReturn(Optional.of("false"));
        when(repository.findValue("app.ai.llm.cleaning.mode")).thenReturn(Optional.of("dry-run"));
        when(repository.findValue("app.ai.llm.cleaning.batch-size")).thenReturn(Optional.of("100"));
        when(repository.findValue("app.ai.llm.model")).thenReturn(Optional.of("db-model"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_AI_LLM_ENABLED", "true")
                .withProperty("APP_AI_LLM_CLEANING_MODE", "fake")
                .withProperty("APP_AI_LLM_CLEANING_BATCH_SIZE", "12")
                .withProperty("APP_AI_LLM_MODEL", "runtime-model")
                .withProperty("SPRING_AI_OPENAI_API_KEY", "sk-runtime")
                .withProperty("APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_TOKEN", "internal-token")
                .withProperty("APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_ID", "normalizer-a")
                .withProperty("APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_SCOPES", "credential:lease custom");
        NormalizationConfigValues values = values(environment);

        assertEquals(true, values.llmCleaningEnabled());
        assertEquals("fake", values.llmCleaningMode());
        assertEquals(12, values.llmCleaningBatchSize());
        assertEquals("runtime-model", values.llmModel());
        assertEquals("sk-runtime", values.llmRuntimeApiKey());
        assertEquals("internal-token", values.llmCredentialServiceToken());
        assertEquals("normalizer-a", values.llmCredentialServiceId());
        assertEquals("credential:lease custom", values.llmCredentialServiceScopes());
    }

    private NormalizationConfigValues values(MockEnvironment environment) {
        return new NormalizationConfigValues(environment, repository);
    }
}
