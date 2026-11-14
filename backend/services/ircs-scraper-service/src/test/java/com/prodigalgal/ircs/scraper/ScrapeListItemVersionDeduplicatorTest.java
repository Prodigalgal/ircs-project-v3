package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.scraper.ScraperDtos.ListItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScrapeListItemVersionDeduplicatorTest {

    @Test
    void keepsEntriesWithoutUpdateTimeBecauseTheirVersionCannotBeCompared() {
        ScrapeListItemVersionDeduplicator deduplicator = new ScrapeListItemVersionDeduplicator();
        ListItem first = new ListItem("v-no-time", null, Map.of());
        ListItem second = new ListItem("v-no-time", null, Map.of());

        assertThat(deduplicator.keepUnseenVersions(List.of(first, second))).containsExactly(first, second);
    }

    @Test
    void evictsOldestVersionAtCapacityAndDoesNotRetainOversizedKeys() {
        ScrapeListItemVersionDeduplicator deduplicator = new ScrapeListItemVersionDeduplicator(2);
        ListItem first = new ListItem("v-first", "2026-06-13 10:00:00", Map.of());
        ListItem second = new ListItem("v-second", "2026-06-13 10:00:00", Map.of());
        ListItem third = new ListItem("v-third", "2026-06-13 10:00:00", Map.of());
        ListItem oversized = new ListItem(
                "v".repeat(ScrapeListItemVersionDeduplicator.MAX_SOURCE_VID_LENGTH + 1),
                "2026-06-13 10:00:00",
                Map.of());
        ListItem oversizedTimestamp = new ListItem(
                "v-long-time",
                "t".repeat(ScrapeListItemVersionDeduplicator.MAX_UPDATE_TIME_LENGTH + 1),
                Map.of());

        assertThat(deduplicator.keepUnseenVersions(List.of(first, second, oversized, oversizedTimestamp)))
                .containsExactly(first, second, oversized, oversizedTimestamp);
        assertThat(deduplicator.keepUnseenVersions(List.of(oversized, oversizedTimestamp)))
                .containsExactly(oversized, oversizedTimestamp);
        assertThat(deduplicator.keepUnseenVersions(List.of(third))).containsExactly(third);
        assertThat(deduplicator.keepUnseenVersions(List.of(first))).containsExactly(first);
        assertThat(deduplicator.keepUnseenVersions(List.of(first))).isEmpty();
    }

    @Test
    void separateSourceScopesDoNotShareSeenVersions() {
        ListItem item = new ListItem("same-source-vid", "2026-06-13 10:00:00", Map.of());
        ScrapeListItemVersionDeduplicator firstSource = new ScrapeListItemVersionDeduplicator();
        ScrapeListItemVersionDeduplicator secondSource = new ScrapeListItemVersionDeduplicator();

        assertThat(firstSource.keepUnseenVersions(List.of(item))).containsExactly(item);
        assertThat(secondSource.keepUnseenVersions(List.of(item))).containsExactly(item);
    }
}
