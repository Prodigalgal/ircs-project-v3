package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the request shape for the JSON API added alongside the existing HTML providers, confirmed
 * against the live site: TheRarBg answers {@code /api/v1/search/?imdb=} for an exact id and
 * {@code ?q=} for free text, and the path needs its trailing slash because the un-slashed form
 * redirects.
 */
class GenericMagnetRequestUrlBuilderTest {

    private final GenericMagnetRequestUrlBuilder builder = new GenericMagnetRequestUrlBuilder();

    @Test
    void theRarbgUsesTheExactIdParameterForAnImdbQuery() {
        MagnetProviderSummary provider = provider("THE_RARBG", "https://therarbg.com");

        assertThat(builder.build(provider, new MagnetExternalIdQuery("IMDB", "tt0133093")))
                .isEqualTo("https://therarbg.com/api/v1/search/?imdb=tt0133093");
    }

    @Test
    void theRarbgFallsBackToFreeTextForATitleQuery() {
        MagnetProviderSummary provider = provider("THE_RARBG", "https://therarbg.com");

        assertThat(builder.build(provider, new MagnetExternalIdQuery("TITLE", "The Matrix")))
                .isEqualTo("https://therarbg.com/api/v1/search/?q=The%20Matrix");
    }

    @Test
    void theRarbgTrimsTrailingSlashes() {
        MagnetProviderSummary provider = provider("THE_RARBG", "https://therarbg.com/");

        assertThat(builder.build(provider, new MagnetExternalIdQuery("IMDB", "tt0133093")))
                .isEqualTo("https://therarbg.com/api/v1/search/?imdb=tt0133093");
    }

    @Test
    void apibayKeepsItsExistingQueryShapeForThePirateBay() {
        MagnetProviderSummary provider = provider("APIBAY", "https://apibay.org");

        assertThat(builder.build(provider, new MagnetExternalIdQuery("IMDB", "tt0133093")))
                .isEqualTo("https://apibay.org/q.php?q=tt0133093&cat=0");
    }

    @Test
    void aConfiguredTemplateStillWinsOverTheProviderType() {
        MagnetProviderSummary provider = provider("THE_RARBG", "https://therarbg.com/api/v1/search/?q={query}");

        assertThat(builder.build(provider, new MagnetExternalIdQuery("TITLE", "matrix")))
                .isEqualTo("https://therarbg.com/api/v1/search/?q=matrix");
    }

    private MagnetProviderSummary provider(String type, String baseUrl) {
        return new MagnetProviderSummary(
                java.util.UUID.randomUUID(), "code", "name", type, baseUrl, true, 10, "HIGH",
                java.util.List.of("IMDB"), 1000, 2000, 10000, 20, false, null, null, null, null, null, null);
    }
}
