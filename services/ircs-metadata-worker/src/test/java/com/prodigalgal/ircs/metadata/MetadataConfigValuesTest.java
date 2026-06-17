package com.prodigalgal.ircs.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MetadataConfigValuesTest {

    private final SystemConfigRepository repository = org.mockito.Mockito.mock(SystemConfigRepository.class);

    @Test
    void usesV1DefaultsWhenNoRuntimeOrDbConfigExists() {
        MetadataConfigValues values = values(new MockEnvironment());

        assertTrue(values.metadataEnabled());
        assertTrue(values.tmdbEnabled());
        assertTrue(values.doubanEnabled());
        assertTrue(values.rottenTomatoesEnabled());
        assertEquals(Duration.ofMinutes(120), values.pendingTtl());
        assertEquals(100, values.watchdogBatchSize());
        assertEquals(300L, values.watchdogStalePendingSeconds());
    }

    @Test
    void dbCanonicalKeysOverrideV1Defaults() {
        when(repository.findValue(MetadataConfigValues.METADATA_ENABLED_KEY)).thenReturn(Optional.of("false"));
        when(repository.findValue(MetadataConfigValues.TMDB_ENABLED_KEY)).thenReturn(Optional.of("false"));
        when(repository.findValue(MetadataConfigValues.DOUBAN_ENABLED_KEY)).thenReturn(Optional.of("false"));
        when(repository.findValue(MetadataConfigValues.RT_ENABLED_KEY)).thenReturn(Optional.of("false"));
        when(repository.findValue(MetadataConfigValues.PENDING_RESCUE_MIN_KEY)).thenReturn(Optional.of("45"));

        MetadataConfigValues values = values(new MockEnvironment());

        assertFalse(values.metadataEnabled());
        assertFalse(values.tmdbEnabled());
        assertFalse(values.doubanEnabled());
        assertFalse(values.rottenTomatoesEnabled());
        assertEquals(Duration.ofMinutes(45), values.pendingTtl());
    }

    @Test
    void runtimeAliasesOverrideDbCanonicalKeysForDevSafety() {
        when(repository.findValue(MetadataConfigValues.TMDB_ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(repository.findValue(MetadataConfigValues.DOUBAN_ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(repository.findValue(MetadataConfigValues.RT_ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(repository.findValue(MetadataConfigValues.PENDING_RESCUE_MIN_KEY)).thenReturn(Optional.of("120"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_METADATA_TMDB_ENABLED", "false")
                .withProperty("APP_METADATA_DOUBAN_ENABLED", "false")
                .withProperty("APP_METADATA_RT_ENABLED", "false")
                .withProperty("APP_METADATA_PENDING_TTL", "15m")
                .withProperty("APP_METADATA_WATCHDOG_BATCH_SIZE", "25")
                .withProperty("APP_METADATA_WATCHDOG_STALE_PENDING_SECONDS", "PT10M");

        MetadataConfigValues values = values(environment);

        assertFalse(values.tmdbEnabled());
        assertFalse(values.doubanEnabled());
        assertFalse(values.rottenTomatoesEnabled());
        assertEquals(Duration.ofMinutes(15), values.pendingTtl());
        assertEquals(25, values.watchdogBatchSize());
        assertEquals(600L, values.watchdogStalePendingSeconds());
    }

    @Test
    void providerTypeSwitchUsesSameDynamicValues() {
        when(repository.findValue(MetadataConfigValues.TMDB_ENABLED_KEY)).thenReturn(Optional.of("false"));
        MetadataConfigValues values = values(new MockEnvironment());

        assertFalse(values.isProviderEnabled(ProviderType.TMDB));
        assertTrue(values.isProviderEnabled(ProviderType.DOUBAN));
    }

    @Test
    void listenerStartupFlagDoesNotOverrideMetadataGlobalSwitch() {
        when(repository.findValue(MetadataConfigValues.METADATA_ENABLED_KEY)).thenReturn(Optional.of("false"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_METADATA_LISTENER_ENABLED", "true");

        MetadataConfigValues values = values(environment);

        assertFalse(values.metadataEnabled());
    }

    @Test
    void invalidValuesFallBackToV1Defaults() {
        when(repository.findValue(MetadataConfigValues.TMDB_ENABLED_KEY)).thenReturn(Optional.of("bad"));
        when(repository.findValue(MetadataConfigValues.PENDING_RESCUE_MIN_KEY)).thenReturn(Optional.of("bad"));

        MetadataConfigValues values = values(new MockEnvironment());

        assertTrue(values.tmdbEnabled());
        assertEquals(Duration.ofMinutes(120), values.pendingTtl());
    }

    private MetadataConfigValues values(MockEnvironment environment) {
        return new MetadataConfigValues(environment, repository);
    }
}
