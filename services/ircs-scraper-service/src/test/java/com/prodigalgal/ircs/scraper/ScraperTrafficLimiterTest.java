package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.common.lease.ClusterLease;
import com.prodigalgal.ircs.common.lock.DistributedLockBusinessType;
import com.prodigalgal.ircs.common.lock.DistributedLockManager;
import com.prodigalgal.ircs.common.lock.DistributedLockProfile;
import com.prodigalgal.ircs.common.lock.DistributedLockProfiles;
import com.prodigalgal.ircs.common.lock.TimeSliceReservation;
import com.prodigalgal.ircs.common.lock.TimeSliceReservationRequest;
import com.prodigalgal.ircs.common.lock.TokenBucketReservation;
import com.prodigalgal.ircs.common.lock.TokenBucketReservationRequest;
import com.prodigalgal.ircs.scraper.ScraperDtos.DataSourceRecord;
import com.prodigalgal.ircs.scraper.ScraperDtos.ManualScrapeConfigRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScraperTrafficLimiterTest {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void reservesDatasourceTimeSliceUsingFixedDelayAsGap() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager(TimeSliceReservation.reserved(
                "traffic:limit:DataSource:" + SOURCE_ID,
                Duration.ZERO,
                Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:01Z")));
        ScraperTrafficLimiter limiter = new ScraperTrafficLimiter(
                lockManager,
                true,
                1000,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));

        limiter.acquireDataSourceSlot(source(), config(750));

        assertThat(lockManager.lastTimeSliceRequest).isNotNull();
        assertThat(lockManager.lastTimeSliceRequest.key()).isEqualTo("traffic:limit:DataSource:" + SOURCE_ID);
        assertThat(lockManager.lastTimeSliceRequest.gap()).isEqualTo(Duration.ofMillis(750));
        assertThat(lockManager.lastTimeSliceRequest.maxWait()).isEqualTo(Duration.ofMinutes(2));
        assertThat(lockManager.lastTimeSliceRequest.ttl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void usesDefaultGapWhenRequestHasNoFixedDelay() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager(TimeSliceReservation.reserved(
                "traffic:limit:DataSource:" + SOURCE_ID,
                Duration.ZERO,
                Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:01Z")));
        ScraperTrafficLimiter limiter = new ScraperTrafficLimiter(
                lockManager,
                true,
                1250,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));

        limiter.acquireDataSourceSlot(source(), config(0));

        assertThat(lockManager.lastTimeSliceRequest.gap()).isEqualTo(Duration.ofMillis(1250));
    }

    @Test
    void rejectsWhenDatasourceQueueWaitExceedsMaxWait() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager(TimeSliceReservation.rejected(
                "traffic:limit:DataSource:" + SOURCE_ID,
                Duration.ofMinutes(3),
                Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-06-11T00:03:00Z")));
        ScraperTrafficLimiter limiter = new ScraperTrafficLimiter(
                lockManager,
                true,
                1000,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));

        assertThatThrownBy(() -> limiter.acquireDataSourceSlot(source(), config(1000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Data source traffic limit saturated");
    }

    private DataSourceRecord source() {
        return new DataSourceRecord(
                SOURCE_ID,
                "Fake",
                "https://provider.example.test",
                "/api.php",
                "{}",
                "/detail/{id}",
                "{}",
                "{}");
    }

    private ManualScrapeConfigRequest config(Integer fixedDelayMs) {
        return new ManualScrapeConfigRequest(
                "codex",
                1,
                1,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                fixedDelayMs,
                false,
                List.of());
    }

    private static class FakeDistributedLockManager implements DistributedLockManager {

        private final TimeSliceReservation reservation;
        private TimeSliceReservationRequest lastTimeSliceRequest;

        private FakeDistributedLockManager(TimeSliceReservation reservation) {
            this.reservation = reservation;
        }

        @Override
        public DistributedLockProfile profileFor(DistributedLockBusinessType businessType) {
            return DistributedLockProfiles.profileFor(businessType);
        }

        @Override
        public TimeSliceReservation reserveTimeSlice(TimeSliceReservationRequest request) {
            this.lastTimeSliceRequest = request;
            return reservation;
        }

        @Override
        public TokenBucketReservation reserveTokenBucket(TokenBucketReservationRequest request) {
            throw new UnsupportedOperationException("not used by scraper traffic limiter");
        }

        @Override
        public Optional<ClusterLease> tryAcquire(String name, String ownerId, Duration ttl) {
            return Optional.empty();
        }

        @Override
        public boolean renew(ClusterLease lease, Duration ttl) {
            return false;
        }

        @Override
        public boolean release(ClusterLease lease) {
            return false;
        }
    }
}
