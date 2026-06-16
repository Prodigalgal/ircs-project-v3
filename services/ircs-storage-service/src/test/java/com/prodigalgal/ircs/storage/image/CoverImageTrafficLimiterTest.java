package com.prodigalgal.ircs.storage.image;

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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoverImageTrafficLimiterTest {

    @Test
    void reservesGlobalAndDomainSlotsByEgressIdentity() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager();
        CoverImageTrafficLimiter limiter = new CoverImageTrafficLimiter(
                lockManager,
                true,
                "203.0.113.10",
                500,
                1500,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));

        limiter.acquire(URI.create("https://Images.Example.Test/cover.png"));

        assertThat(lockManager.timeSliceRequests).hasSize(2);
        assertThat(lockManager.timeSliceRequests.getFirst().key())
                .isEqualTo("traffic:limit:Global:ImageDownload:Ip:203.0.113.10");
        assertThat(lockManager.timeSliceRequests.getFirst().gap()).isEqualTo(Duration.ofMillis(500));
        assertThat(lockManager.timeSliceRequests.get(1).key())
                .isEqualTo("traffic:limit:Domain:Ip:203.0.113.10:images.example.test");
        assertThat(lockManager.timeSliceRequests.get(1).gap()).isEqualTo(Duration.ofMillis(1500));
        assertThat(lockManager.timeSliceRequests.get(1).maxWait()).isEqualTo(Duration.ofMinutes(2));
        assertThat(lockManager.timeSliceRequests.get(1).ttl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void rejectsWhenDomainWaitExceedsMaxWait() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager(
                TimeSliceReservation.reserved(
                        "traffic:limit:Global:ImageDownload:Ip:203.0.113.10",
                        Duration.ZERO,
                        Instant.parse("2026-06-11T00:00:00Z"),
                        Instant.parse("2026-06-11T00:00:01Z")),
                TimeSliceReservation.rejected(
                        "traffic:limit:Domain:Ip:203.0.113.10:images.example.test",
                        Duration.ofMinutes(3),
                        Instant.parse("2026-06-11T00:00:00Z"),
                        Instant.parse("2026-06-11T00:03:00Z")));
        CoverImageTrafficLimiter limiter = new CoverImageTrafficLimiter(
                lockManager,
                true,
                "203.0.113.10",
                500,
                1500,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));

        assertThatThrownBy(() -> limiter.acquire(URI.create("https://images.example.test/cover.png")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Image download traffic limit saturated");
    }

    private static class FakeDistributedLockManager implements DistributedLockManager {

        private final java.util.Queue<TimeSliceReservation> reservations = new java.util.ArrayDeque<>();
        private final List<TimeSliceReservationRequest> timeSliceRequests = new java.util.ArrayList<>();

        private FakeDistributedLockManager(TimeSliceReservation... reservations) {
            this.reservations.addAll(List.of(reservations));
        }

        @Override
        public DistributedLockProfile profileFor(DistributedLockBusinessType businessType) {
            return DistributedLockProfiles.profileFor(businessType);
        }

        @Override
        public TimeSliceReservation reserveTimeSlice(TimeSliceReservationRequest request) {
            this.timeSliceRequests.add(request);
            TimeSliceReservation reservation = reservations.poll();
            if (reservation == null) {
                return TimeSliceReservation.reserved(
                        request.key(),
                        Duration.ZERO,
                        Instant.parse("2026-06-11T00:00:00Z"),
                        Instant.parse("2026-06-11T00:00:01Z"));
            }
            return reservation;
        }

        @Override
        public TokenBucketReservation reserveTokenBucket(TokenBucketReservationRequest request) {
            throw new UnsupportedOperationException("not used by cover image traffic limiter");
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
