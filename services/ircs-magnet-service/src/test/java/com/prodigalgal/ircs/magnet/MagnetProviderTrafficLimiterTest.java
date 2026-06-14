package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.lease.ClusterLease;
import com.prodigalgal.ircs.common.lock.DistributedLockBusinessType;
import com.prodigalgal.ircs.common.lock.DistributedLockManager;
import com.prodigalgal.ircs.common.lock.DistributedLockProfile;
import com.prodigalgal.ircs.common.lock.DistributedLockProfiles;
import com.prodigalgal.ircs.common.lock.TimeSliceReservation;
import com.prodigalgal.ircs.common.lock.TimeSliceReservationRequest;
import com.prodigalgal.ircs.common.lock.TokenBucketReservation;
import com.prodigalgal.ircs.common.lock.TokenBucketReservationRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MagnetProviderTrafficLimiterTest {

    private static final UUID PROVIDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void reservesProviderTimeSliceUsingProviderMinDelay() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager(TimeSliceReservation.reserved(
                "traffic:limit:Provider:" + PROVIDER_ID,
                Duration.ZERO,
                Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:01Z")));
        MagnetProviderTrafficLimiter limiter = new MagnetProviderTrafficLimiter(
                lockManager,
                true,
                1000,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));

        limiter.acquireProviderSlot(provider(1500));

        assertThat(lockManager.lastTimeSliceRequest).isNotNull();
        assertThat(lockManager.lastTimeSliceRequest.key()).isEqualTo("traffic:limit:Provider:" + PROVIDER_ID);
        assertThat(lockManager.lastTimeSliceRequest.gap()).isEqualTo(Duration.ofMillis(1500));
        assertThat(lockManager.lastTimeSliceRequest.maxWait()).isEqualTo(Duration.ofMinutes(2));
        assertThat(lockManager.lastTimeSliceRequest.ttl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void ytsRunnerStopsBeforeHttpWhenProviderTrafficIsSaturated() {
        FakeDistributedLockManager lockManager = new FakeDistributedLockManager(TimeSliceReservation.rejected(
                "traffic:limit:Provider:" + PROVIDER_ID,
                Duration.ofMinutes(3),
                Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-06-11T00:03:00Z")));
        MagnetProviderTrafficLimiter limiter = new MagnetProviderTrafficLimiter(
                lockManager,
                true,
                1000,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10));
        FakeHttpClient httpClient = new FakeHttpClient();
        YtsBzMagnetProviderSearchRunner runner = new YtsBzMagnetProviderSearchRunner(
                new ObjectMapper(),
                limiter,
                httpClientProvider(httpClient));

        MagnetProviderRunnerException error = assertThrows(
                MagnetProviderRunnerException.class,
                () -> runner.search(provider(1000), new MagnetExternalIdQuery("IMDB", "tt1234567"), UUID.randomUUID()));

        assertThat(error.failureType()).isEqualTo(MagnetProviderTrafficLimiter.FAILURE_TYPE);
        assertThat(httpClient.requested).isFalse();
    }

    private MagnetProviderSummary provider(Integer minDelayMs) {
        return new MagnetProviderSummary(
                PROVIDER_ID,
                "yts_bz",
                "YTS.BZ",
                "YTS_BZ",
                "https://movies-api.accel.li/api/v2",
                true,
                10,
                "HIGH",
                List.of("IMDB"),
                minDelayMs,
                3000,
                10000,
                20,
                true,
                "test",
                null,
                null,
                null,
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<YtsBzMagnetProviderSearchRunner.YtsBzHttpClient> httpClientProvider(
            YtsBzMagnetProviderSearchRunner.YtsBzHttpClient httpClient) {
        ObjectProvider<YtsBzMagnetProviderSearchRunner.YtsBzHttpClient> provider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfUnique()).thenReturn(httpClient);
        return provider;
    }

    private static class FakeHttpClient implements YtsBzMagnetProviderSearchRunner.YtsBzHttpClient {

        private boolean requested;

        @Override
        public YtsBzMagnetProviderSearchRunner.YtsBzHttpResponse get(URI uri, Duration timeout)
                throws IOException {
            requested = true;
            return new YtsBzMagnetProviderSearchRunner.YtsBzHttpResponse(200, "{}");
        }
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
            throw new UnsupportedOperationException("not used by magnet provider limiter");
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
