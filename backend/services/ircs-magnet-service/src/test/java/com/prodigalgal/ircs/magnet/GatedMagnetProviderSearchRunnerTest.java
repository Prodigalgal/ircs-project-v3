package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.ObjectProvider;

class GatedMagnetProviderSearchRunnerTest {

    private final UUID unifiedVideoId = UUID.randomUUID();
    private final MagnetExternalIdQuery imdbQuery = new MagnetExternalIdQuery("IMDB", "tt1234567");

    @Test
    void disabledRealProviderFailsWithoutSilentlyUsingFixture() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        GenericMagnetProviderSearchRunner genericRunner = org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class);
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                genericRunner,
                emptyRuntimeConfigProvider(),
                false,
                "YTS_BZ",
                environment(false),
                false);

        MagnetProviderRunnerException exception = org.junit.jupiter.api.Assertions.assertThrows(
                MagnetProviderRunnerException.class,
                () -> runner.search(ytsProvider(), imdbQuery, unifiedVideoId));

        assertEquals("MAGNET_PROVIDER_DISABLED", exception.failureType());
        org.mockito.Mockito.verify(fixtureRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(genericRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesYtsBzRunnerOnlyWhenGateAndAllowlistMatch() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        GenericMagnetProviderSearchRunner genericRunner = org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class);
        org.mockito.Mockito.when(realRunner.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("real"));
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                genericRunner,
                emptyRuntimeConfigProvider(),
                true,
                "YTS_BZ",
                environment(false),
                false);

        MagnetProviderSearchResult result = runner.search(ytsProvider(), imdbQuery, unifiedVideoId);

        org.mockito.Mockito.verify(fixtureRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(genericRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals("real", result.requestUrl());
    }

    @Test
    void usesGenericRunnerForAllowlistedNonYtsProvider() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        GenericMagnetProviderSearchRunner genericRunner = org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class);
        org.mockito.Mockito.when(genericRunner.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("generic"));
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                genericRunner,
                emptyRuntimeConfigProvider(),
                true,
                "THE_PIRATE_BAY",
                environment(false),
                false);

        MagnetProviderSearchResult result = runner.search(genericProvider(), imdbQuery, unifiedVideoId);

        org.mockito.Mockito.verify(fixtureRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(genericRunner).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals("generic", result.requestUrl());
    }

    @Test
    void rejectsProviderWhenAllowlistDoesNotMatch() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        GenericMagnetProviderSearchRunner genericRunner = org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class);
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                genericRunner,
                emptyRuntimeConfigProvider(),
                true,
                "THE_PIRATE_BAY",
                environment(false),
                false);

        MagnetProviderRunnerException exception = org.junit.jupiter.api.Assertions.assertThrows(
                MagnetProviderRunnerException.class,
                () -> runner.search(ytsProvider(), imdbQuery, unifiedVideoId));

        assertEquals("MAGNET_PROVIDER_UNSUPPORTED", exception.failureType());
        org.mockito.Mockito.verify(fixtureRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(genericRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesFixtureOnlyWhenExplicitlyEnabledInAllowedProfile() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class),
                org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class),
                emptyRuntimeConfigProvider(),
                false,
                "",
                environment(true),
                true);
        org.mockito.Mockito.when(fixtureRunner.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("fixture"));

        MagnetProviderSearchResult result = runner.search(fixtureProvider(), imdbQuery, unifiedVideoId);

        org.mockito.Mockito.verify(fixtureRunner).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals("fixture", result.requestUrl());
    }

    @Test
    void rejectsFixtureWhenProfileIsNotAllowedEvenIfEnabled() {
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class),
                org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class),
                org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class),
                emptyRuntimeConfigProvider(),
                false,
                "",
                environment(false),
                true);

        MagnetProviderRunnerException exception = org.junit.jupiter.api.Assertions.assertThrows(
                MagnetProviderRunnerException.class,
                () -> runner.search(fixtureProvider(), imdbQuery, unifiedVideoId));

        assertEquals("MAGNET_FIXTURE_DISABLED", exception.failureType());
    }

    @Test
    void rejectsUnknownProviderEvenWhenItsCodeIsAllowlisted() {
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class),
                org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class),
                org.mockito.Mockito.mock(GenericMagnetProviderSearchRunner.class),
                emptyRuntimeConfigProvider(),
                true,
                "UNKNOWN_PROVIDER",
                environment(false),
                false);

        MagnetProviderRunnerException exception = org.junit.jupiter.api.Assertions.assertThrows(
                MagnetProviderRunnerException.class,
                () -> runner.search(unknownProvider(), imdbQuery, unifiedVideoId));

        assertEquals("MAGNET_PROVIDER_UNSUPPORTED", exception.failureType());
    }

    private MagnetProviderSearchResult result(String requestUrl) {
        return new MagnetProviderSearchResult(
                requestUrl,
                200,
                List.of(new MagnetProviderCandidate(
                        "ABCDEF1234567890ABCDEF1234567890ABCDEF12",
                        "magnet:?xt=urn:btih:ABCDEF1234567890ABCDEF1234567890ABCDEF12",
                        "Codex Fixture",
                        1024L,
                        "1 KB",
                        Instant.parse("2026-06-08T00:00:00Z"),
                        1,
                        0,
                        "WEB",
                        "1080p",
                        imdbQuery.type(),
                        imdbQuery.value(),
                        100,
                        "fixture://provider",
                        List.of("dev-safe"),
                        Map.of("mode", requestUrl))));
    }

    private MagnetProviderSummary ytsProvider() {
        return new MagnetProviderSummary(
                UUID.randomUUID(),
                "yts_bz",
                "YTS.BZ",
                "YTS_BZ",
                "https://movies-api.accel.li/api/v2",
                true,
                10,
                "HIGH",
                List.of("IMDB"),
                1000,
                3000,
                10000,
                20,
                true,
                "仅用于测试。",
                null,
                null,
                null,
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z"));
    }

    private MagnetProviderSummary genericProvider() {
        return new MagnetProviderSummary(
                UUID.randomUUID(),
                "thepiratebay",
                "The Pirate Bay",
                "THE_PIRATE_BAY",
                "https://apibay.org",
                true,
                20,
                "HIGH",
                List.of("IMDB", "TITLE_YEAR", "TITLE"),
                1000,
                3000,
                10000,
                20,
                true,
                "仅用于测试。",
                null,
                null,
                null,
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z"));
    }

    private MagnetProviderSummary fixtureProvider() {
        return new MagnetProviderSummary(
                UUID.randomUUID(), "fixture", "Fixture", "FIXTURE", "fixture://provider", true, 10, "LOW",
                List.of("IMDB"), 0, 0, 1000, 20, false, "test fixture", null, null, null,
                Instant.parse("2026-06-08T00:00:00Z"), Instant.parse("2026-06-08T00:00:00Z"));
    }

    private MagnetProviderSummary unknownProvider() {
        return new MagnetProviderSummary(
                UUID.randomUUID(), "unknown_provider", "Unknown", "UNKNOWN_PROVIDER", "https://example.invalid",
                true, 10, "HIGH", List.of("IMDB"), 0, 0, 1000, 20, false, "test", null, null, null,
                Instant.parse("2026-06-08T00:00:00Z"), Instant.parse("2026-06-08T00:00:00Z"));
    }

    private Environment environment(boolean allowedProfile) {
        Environment environment = org.mockito.Mockito.mock(Environment.class);
        org.mockito.Mockito.when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.any(Profiles.class)))
                .thenReturn(allowedProfile);
        return environment;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<com.prodigalgal.ircs.common.config.RuntimeConfigService> emptyRuntimeConfigProvider() {
        ObjectProvider<com.prodigalgal.ircs.common.config.RuntimeConfigService> provider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
