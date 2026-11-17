package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ScraperTrendConfigValuesTest {

    @Test
    void defaultsLimitPublicSamplesAndEpisodeRequestsAndAllowInitialCatalogApply() {
        var config = new ScraperTrendConfigValues(new MockEnvironment());

        assertThat(config.maxProviderItems()).isEqualTo(100);
        assertThat(config.tmdbEpisodeDetailBudget()).isEqualTo(10);
        assertThat(config.contentOwnerRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void legacyUnlimitedSamplingUsesBoundedDefaultAndExplicitBudgetsAreClamped() {
        var environment = new MockEnvironment()
                .withProperty("app.scraper.trend-sync.max-provider-items", "0")
                .withProperty("app.scraper.trend-sync.tmdb-episode-detail-budget", "9000");
        var config = new ScraperTrendConfigValues(environment);

        assertThat(config.maxProviderItems()).isEqualTo(100);
        assertThat(config.tmdbEpisodeDetailBudget()).isEqualTo(30);
    }
}
