package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GatedMagnetProviderSearchRunnerTest {

    private final UUID unifiedVideoId = UUID.randomUUID();
    private final MagnetExternalIdQuery imdbQuery = new MagnetExternalIdQuery("IMDB", "tt1234567");

    @Test
    void defaultsToFixtureAndDoesNotCallRealRunner() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        org.mockito.Mockito.when(fixtureRunner.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("fixture"));
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                false,
                "YTS_BZ");

        MagnetProviderSearchResult result = runner.search(ytsProvider(), imdbQuery, unifiedVideoId);

        org.mockito.Mockito.verify(fixtureRunner).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals("fixture", result.requestUrl());
    }

    @Test
    void usesYtsBzRunnerOnlyWhenGateAndAllowlistMatch() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        org.mockito.Mockito.when(realRunner.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("real"));
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                true,
                "YTS_BZ");

        MagnetProviderSearchResult result = runner.search(ytsProvider(), imdbQuery, unifiedVideoId);

        org.mockito.Mockito.verify(fixtureRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals("real", result.requestUrl());
    }

    @Test
    void keepsFixtureWhenAllowlistDoesNotMatch() {
        FixtureMagnetProviderSearchRunner fixtureRunner = org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class);
        YtsBzMagnetProviderSearchRunner realRunner = org.mockito.Mockito.mock(YtsBzMagnetProviderSearchRunner.class);
        org.mockito.Mockito.when(fixtureRunner.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("fixture"));
        GatedMagnetProviderSearchRunner runner = new GatedMagnetProviderSearchRunner(
                fixtureRunner,
                realRunner,
                true,
                "THE_PIRATE_BAY");

        MagnetProviderSearchResult result = runner.search(ytsProvider(), imdbQuery, unifiedVideoId);

        org.mockito.Mockito.verify(fixtureRunner).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(realRunner, org.mockito.Mockito.never()).search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals("fixture", result.requestUrl());
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

}
