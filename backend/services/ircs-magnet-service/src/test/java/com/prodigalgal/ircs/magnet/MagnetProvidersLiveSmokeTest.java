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
 * Live check that each newly wired magnet source answers a real query through the real runner.
 *
 * <p>Disabled by default; run with {@code -Dircs.smoke.magnet=true -Dircs.smoke.proxy=host:port}.
 * A unit test cannot prove the upstream contract still holds, and these sites change theirs without
 * notice, so each provider is exercised against the live endpoint it is configured with.
 */
@EnabledIfSystemProperty(named = "ircs.smoke.magnet", matches = "true")
class MagnetProvidersLiveSmokeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final UUID VIDEO_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void thePirateBayServesResultsThroughApiBay() throws Exception {
        var provider = provider("thepiratebay", "THE_PIRATE_BAY", "https://apibay.org", List.of("IMDB", "TITLE"));

        assertNonEmpty("THE_PIRATE_BAY", provider, new MagnetExternalIdQuery("IMDB", "tt0133093"));
    }

    @Test
    void ytsServesResultsThroughItsApi() throws Exception {
        var provider = provider("yts_bz", "YTS_BZ", "https://movies-api.accel.li/api/v2", List.of("IMDB"));

        assertNonEmpty("YTS_BZ", provider, new MagnetExternalIdQuery("IMDB", "tt0133093"));
    }

    @Test
    void theRarbgServesResultsThroughItsJsonApi() throws Exception {
        var provider = provider("therarbg", "THE_RARBG", "https://therarbg.com", List.of("IMDB", "TITLE"));

        assertNonEmpty("THE_RARBG", provider, new MagnetExternalIdQuery("IMDB", "tt0133093"));
    }

    private void assertNonEmpty(String label, MagnetProviderSummary provider, MagnetExternalIdQuery query)
            throws Exception {
        MagnetProviderSearchResult result = new GatedMagnetProviderSearchRunner(
                org.mockito.Mockito.mock(FixtureMagnetProviderSearchRunner.class),
                new YtsBzMagnetProviderSearchRunner(
                        mapper, MagnetProviderTrafficLimiter.noop(), null, proxyProvider()),
                new GenericMagnetProviderSearchRunner(
                        mapper, MagnetProviderTrafficLimiter.noop(), null, proxyProvider()),
                null, true, label, null, false)
                .search(provider, query, VIDEO_ID);

        System.out.printf("[%s] %s -> %d candidates, first=%s%n", label, result.requestUrl(),
                result.candidates().size(),
                result.candidates().isEmpty() ? "-" : result.candidates().getFirst().title());
        assertThat(result.candidates())
                .as("%s returned no candidates from %s", label, result.requestUrl())
                .isNotEmpty();
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.infoHash()).matches("[0-9A-F]{40}");
            assertThat(candidate.magnetUri()).startsWith("magnet:?xt=urn:btih:");
        });
    }

    private org.springframework.beans.factory.ObjectProvider<PublicOutboundProxyPool> proxyProvider() {
        String endpoint = "http://" + System.getProperty("ircs.smoke.proxy", "127.0.0.1:10808");
        RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
        org.mockito.Mockito.when(config.stringValue(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "app.outbound.proxy-pool.endpoints".equals(invocation.getArgument(0))
                        ? endpoint
                        : invocation.getArgument(1));
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
            @Override
            public PublicOutboundProxyPool getObject(Object... args) {
                return pool;
            }

            @Override
            public PublicOutboundProxyPool getIfAvailable() {
                return pool;
            }

            @Override
            public PublicOutboundProxyPool getIfUnique() {
                return pool;
            }

            @Override
            public PublicOutboundProxyPool getObject() {
                return pool;
            }
        };
    }

    private MagnetProviderSummary provider(String code, String type, String baseUrl, List<String> ids) {
        return new MagnetProviderSummary(
                UUID.randomUUID(), code, code, type, baseUrl, true, 30, "HIGH", ids,
                1000, 2000, 20000, 20, false, null, null, null, null, null, null);
    }
}
