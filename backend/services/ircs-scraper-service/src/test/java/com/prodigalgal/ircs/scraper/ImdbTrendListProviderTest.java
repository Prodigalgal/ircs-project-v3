package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ImdbTrendListProviderTest {

    @Test
    void unconfiguredOfficialRankAccessIsVisibleWithoutPretendingRatingsAreTrends() {
        ScraperTrendConfigValues config = Mockito.mock(ScraperTrendConfigValues.class);
        when(config.imdbEnabled()).thenReturn(true);

        var boards = new ImdbTrendListProvider(config).fetchBoards();

        assertThat(boards).hasSize(2).allSatisfy(board -> {
            assertThat(board.status()).isEqualTo("UNAVAILABLE");
            assertThat(board.errorCode()).isEqualTo("OFFICIAL_TREND_ACCESS_NOT_CONFIGURED");
            assertThat(board.items()).isEmpty();
        });
    }
}
