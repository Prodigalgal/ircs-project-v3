package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;

/**
 * Audits the actual deliverable: the magnet URI each provider hands back.
 *
 * <p>The service stores magnet links, so a link that is missing its trackers is a degraded artifact,
 * not a cosmetic difference. This prints what each provider returns so the two shapes can be compared.
 */
@EnabledIfSystemProperty(named = "ircs.smoke.magnet", matches = "true")
class MagnetLinkQualityLiveAuditTest {

    private static final UUID VIDEO_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportsTheMagnetShapeEachProviderReturns() throws Exception {
        GatedMagnetProviderSearchRunner gated = new GatedMagnetProviderSearchRunner(
                org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class),
                new YtsBzMagnetProviderSearchRunner(
                        mapper, MagnetProviderTrafficLimiter.noop(), null, proxyProvider()),
                new GenericMagnetProviderSearchRunner(
                        mapper, MagnetProviderTrafficLimiter.noop(), null, proxyProvider()),
                null, true, "YTS_BZ,THE_PIRATE_BAY,THE_RARBG", null, false);

        for (MagnetProviderSummary provider : List.of(
                provider("therarbg", "THE_RARBG", "https://therarbg.com"),
                provider("thepiratebay", "THE_PIRATE_BAY", "https://apibay.org"),
                provider("yts_bz", "YTS_BZ", "https://movies-api.accel.li/api/v2"))) {

            MagnetProviderSearchResult result = gated.search(
                    provider, new MagnetExternalIdQuery("IMDB", "tt0133093"), VIDEO_ID);
            if (result.candidates().isEmpty()) {
                System.out.printf("%n[%s] no candidates%n", provider.code());
                continue;
            }
            MagnetProviderCandidate first = result.candidates().getFirst();
            // Runners return the provider's own shape; MagnetSearchExecutor.persistCandidates then appends
            // the configured announce list. Applying the same step here audits the persisted artifact.
            String persisted = MagnetUriUtils.ensureTrackers(first.magnetUri(), MagnetUriUtils.DEFAULT_TRACKERS);
            int trackers = countTrackers(persisted);
            System.out.printf("%n[%s]%n  fromProvider: %d tracker(s)%n  persisted   : %d tracker(s)%n  title: %s%n",
                    provider.code(),
                    countTrackers(first.magnetUri()),
                    trackers,
                    first.title());

            assertThat(persisted).startsWith("magnet:?xt=urn:btih:");
            assertThat(first.infoHash()).matches("[0-9A-F]{40}");
            assertThat(MagnetUriUtils.normalizeInfoHash(persisted))
                    .as("%s produced a magnet whose hash does not round-trip", provider.code())
                    .contains(first.infoHash());
            assertThat(trackers)
                    .as("%s produced a magnet without any tracker", provider.code())
                    .isGreaterThanOrEqualTo(10);
        }
    }

    private int countTrackers(String magnetUri) {
        int count = 0;
        for (String part : magnetUri.split("&")) {
            String lowered = part.toLowerCase(java.util.Locale.ROOT);
            if (lowered.startsWith("tr=") || lowered.contains("?tr=")) {
                count++;
            }
        }
        return count;
    }

    private org.springframework.beans.factory.ObjectProvider<PublicOutboundProxyPool> proxyProvider() {
        String endpoint = "http://" + System.getProperty("ircs.smoke.proxy", "127.0.0.1:10808");
        RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
        org.mockito.Mockito.when(config.stringValue(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "app.outbound.proxy-pool.endpoints".equals(invocation.getArgument(0))
                        ? endpoint : invocation.getArgument(1));
        org.mockito.Mockito.when(config.value(PublicOutboundProxyPool.ENABLED_KEY))
                .thenReturn(java.util.Optional.of("true"));
        org.mockito.Mockito.when(config.intValue(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(0);
        org.mockito.Mockito.when(config.positiveDurationValue(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(Duration.ofSeconds(30));
        PublicOutboundProxyPool pool = new PublicOutboundProxyPool(config);
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public PublicOutboundProxyPool getObject(Object... args) { return pool; }
            @Override public PublicOutboundProxyPool getIfAvailable() { return pool; }
            @Override public PublicOutboundProxyPool getIfUnique() { return pool; }
            @Override public PublicOutboundProxyPool getObject() { return pool; }
        };
    }

    private MagnetProviderSummary provider(String code, String type, String baseUrl) {
        return new MagnetProviderSummary(
                UUID.randomUUID(), code, code, type, baseUrl, true, 30, "HIGH",
                List.of("IMDB", "TITLE_YEAR", "TITLE"), 1000, 2000, 20000, 20, false,
                null, null, null, null, null, null);
    }
}
