package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MagnetTrackerConfigValuesTest {

    private final RuntimeConfigService runtimeConfig = mock(RuntimeConfigService.class);

    @BeforeEach
    void enablePublicTrackers() {
        when(runtimeConfig.booleanValue(MagnetTrackerConfigValues.APPEND_ENABLED_KEY, true)).thenReturn(true);
    }

    @Test
    void disablingAppendDoesNotReadOrUseTheTrackerList() {
        when(runtimeConfig.booleanValue(MagnetTrackerConfigValues.APPEND_ENABLED_KEY, true)).thenReturn(false);

        assertThat(new MagnetTrackerConfigValues(single(runtimeConfig)).trackers()).isEmpty();
        verify(runtimeConfig, never()).stringValue(eq(MagnetTrackerConfigValues.TRACKERS_KEY), anyString());
    }

    @Test
    void anUnsetValueFallsBackToTheBuiltInList() {
        when(runtimeConfig.stringValue(eq(MagnetTrackerConfigValues.TRACKERS_KEY), anyString())).thenReturn("");

        assertThat(new MagnetTrackerConfigValues(single(runtimeConfig)).trackers())
                .isEqualTo(MagnetUriUtils.DEFAULT_TRACKERS);
    }

    @Test
    void aConfiguredListReplacesTheBuiltInOne() {
        when(runtimeConfig.stringValue(eq(MagnetTrackerConfigValues.TRACKERS_KEY), anyString()))
                .thenReturn("udp://a.example:1337/announce,https://b.example/announce");

        assertThat(new MagnetTrackerConfigValues(single(runtimeConfig)).trackers())
                .containsExactly("udp://a.example:1337/announce", "https://b.example/announce");
    }

    @Test
    void newlinesAndSpacesAreAcceptedAsSeparators() {
        assertThat(MagnetTrackerConfigValues.parse(
                "  udp://a.example:1/announce \n udp://b.example:2/announce\r\nudp://c.example:3/announce "))
                .containsExactly(
                        "udp://a.example:1/announce",
                        "udp://b.example:2/announce",
                        "udp://c.example:3/announce");
    }

    @Test
    void entriesWithoutAnAnnounceSchemeOrHostAreDropped() {
        assertThat(MagnetTrackerConfigValues.parse(
                "not-a-tracker,ftp://bad.example/announce,udp://,http://,udp://good.example:1/announce"))
                .containsExactly("udp://good.example:1/announce");
    }

    @Test
    void duplicatesCollapseAndTheFirstOccurrenceWins() {
        assertThat(MagnetTrackerConfigValues.parse(
                "udp://a.example:1/announce,udp://a.example:1/announce,udp://b.example:2/announce"))
                .containsExactly("udp://a.example:1/announce", "udp://b.example:2/announce");
    }

    @Test
    void wssIsAcceptedBecauseSomeTrackersOnlyServeWebsockets() {
        assertThat(MagnetTrackerConfigValues.parse("wss://tracker.example/announce"))
                .containsExactly("wss://tracker.example/announce");
    }

    @Test
    void theListIsBoundedSoAMisconfiguredValueCannotBlowUpEveryMagnet() {
        StringBuilder raw = new StringBuilder();
        for (int index = 0; index < 60; index++) {
            raw.append("udp://t").append(index).append(".example:1/announce,");
        }

        assertThat(MagnetTrackerConfigValues.parse(raw.toString())).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void aValueThatParsesToNothingIsHonouredAsDoNotAppend() {
        when(runtimeConfig.stringValue(eq(MagnetTrackerConfigValues.TRACKERS_KEY), anyString()))
                .thenReturn("placeholder-with-no-scheme");

        assertThat(new MagnetTrackerConfigValues(single(runtimeConfig)).trackers()).isEmpty();
    }

    @Test
    void withoutARuntimeConfigServiceTheBuiltInListIsUsed() {
        assertThat(new MagnetTrackerConfigValues(null).trackers()).isEqualTo(MagnetUriUtils.DEFAULT_TRACKERS);
    }

    private static org.springframework.beans.factory.ObjectProvider<RuntimeConfigService> single(
            RuntimeConfigService service) {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public RuntimeConfigService getObject(Object... args) {
                return service;
            }

            @Override
            public RuntimeConfigService getIfAvailable() {
                return service;
            }

            @Override
            public RuntimeConfigService getIfUnique() {
                return service;
            }

            @Override
            public RuntimeConfigService getObject() {
                return service;
            }
        };
    }

    @Test
    void theBuiltInListContainsOnlyAnnounceUrls() {
        List<String> defaults = MagnetUriUtils.DEFAULT_TRACKERS;

        assertThat(defaults).isNotEmpty().allSatisfy(tracker -> {
            assertThat(tracker).matches("(?i)^(?:https?|udp|wss?)://[^/]+:\\d+/announce$|(?i)^(?:https?)://[^/]+/announce$");
            assertThat(java.net.URI.create(tracker).getHost()).isNotBlank();
        });
    }
}
