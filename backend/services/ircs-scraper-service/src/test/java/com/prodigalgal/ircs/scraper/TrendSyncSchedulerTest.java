package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrendSyncSchedulerTest {
    @Test
    void firstRunIsDueWithoutInventingHistoricalSamples() {
        assertThat(TrendSyncScheduler.due(null, Instant.parse("2026-11-16T13:15:00Z"),
                "0 */30 * * * *", "Asia/Shanghai")).isTrue();
    }

    @Test
    void dueUsesPersistedLastAttemptAndHonorsRuntimeCronAndTimezone() {
        Instant last = Instant.parse("2026-11-16T13:00:01Z");
        assertThat(TrendSyncScheduler.due(last, last.plusSeconds(600), "0 */30 * * * *", "Asia/Shanghai")).isFalse();
        assertThat(TrendSyncScheduler.due(last, last.plusSeconds(1800), "0 */30 * * * *", "Asia/Shanghai")).isTrue();
        assertThat(TrendSyncScheduler.due(last, Instant.parse("2026-11-16T18:59:59Z"), "0 0 3 * * ?", "Asia/Shanghai")).isFalse();
        assertThat(TrendSyncScheduler.due(last, Instant.parse("2026-11-16T19:00:00Z"), "0 0 3 * * ?", "Asia/Shanghai")).isTrue();
    }

    @Test
    void invalidScheduleFailsExplicitlyInsteadOfStartingContinuousFetches() {
        assertThatThrownBy(() -> TrendSyncScheduler.due(Instant.EPOCH, Instant.now(), "bad", "Asia/Shanghai"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrendSyncScheduler.due(Instant.EPOCH, Instant.now(), "0 */30 * * * *", "bad/zone"))
                .isInstanceOf(java.time.DateTimeException.class);
    }
}
