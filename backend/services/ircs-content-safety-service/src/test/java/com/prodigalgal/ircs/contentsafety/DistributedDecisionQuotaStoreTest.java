package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DistributedDecisionQuotaStoreTest {
    @Test
    void pacificDaysUseCalendarMidnightAcrossBothDstTransitions() {
        Instant spring = Instant.parse("2026-03-08T12:00:00Z");
        Instant autumn = Instant.parse("2026-11-01T12:00:00Z");
        assertThat(Duration.between(DistributedDecisionQuotaStore.dayStart(spring), DistributedDecisionQuotaStore.nextDay(spring)))
                .isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(DistributedDecisionQuotaStore.dayStart(autumn), DistributedDecisionQuotaStore.nextDay(autumn)))
                .isEqualTo(Duration.ofHours(25));
    }

    @Test
    void pacificRolloverDoesNotFollowUtcOrBeijingMidnight() {
        assertThat(DistributedDecisionQuotaStore.dayStart(Instant.parse("2026-11-13T07:59:59Z")))
                .isEqualTo(Instant.parse("2026-11-12T08:00:00Z"));
        assertThat(DistributedDecisionQuotaStore.dayStart(Instant.parse("2026-11-13T08:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-13T08:00:00Z"));
    }

    @Test
    void storeUnavailabilityFailsClosed() {
        var redis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        var store = new DistributedDecisionQuotaStore(redis, java.time.Clock.systemUTC(), DistributedDecisionQuotaStore.PREFIX);
        assertThatThrownBy(() -> store.reserve(100)).isInstanceOf(DecisionUnavailableException.class).hasMessage("QUOTA_UNAVAILABLE");
        assertThatThrownBy(() -> store.reserve(50001)).hasMessage("REQUEST_TOO_LARGE");
    }
}
