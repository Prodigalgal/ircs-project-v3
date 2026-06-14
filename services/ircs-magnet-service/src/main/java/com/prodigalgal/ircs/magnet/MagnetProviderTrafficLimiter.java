package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.lock.DistributedLockBusinessType;
import com.prodigalgal.ircs.common.lock.DistributedLockManager;
import com.prodigalgal.ircs.common.lock.DistributedLockProfile;
import com.prodigalgal.ircs.common.lock.TimeSliceReservation;
import com.prodigalgal.ircs.common.lock.TimeSliceReservationRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class MagnetProviderTrafficLimiter {

    static final String FAILURE_TYPE = "MAGNET_PROVIDER_TRAFFIC_SATURATED";

    private final DistributedLockManager lockManager;
    private final boolean enabled;
    private final Duration defaultGap;
    private final Duration maxWait;
    private final Duration ttl;

    MagnetProviderTrafficLimiter(
            DistributedLockManager lockManager,
            @Value("${app.magnet.traffic.enabled:true}") boolean enabled,
            @Value("${app.magnet.traffic.default-gap-ms:1000}") long defaultGapMs,
            @Value("${app.magnet.traffic.max-wait:PT2M}") Duration maxWait,
            @Value("${app.magnet.traffic.ttl:PT10M}") Duration ttl) {
        this.lockManager = lockManager;
        this.enabled = enabled;
        this.defaultGap = defaultGapMs > 0 ? Duration.ofMillis(defaultGapMs) : Duration.ofSeconds(1);
        this.maxWait = maxWait == null || maxWait.isNegative() ? Duration.ZERO : maxWait;
        this.ttl = ttl == null || !ttl.isPositive() ? Duration.ofMinutes(10) : ttl;
    }

    static MagnetProviderTrafficLimiter noop() {
        return new MagnetProviderTrafficLimiter(null, false, 0, Duration.ZERO, Duration.ZERO);
    }

    void acquireProviderSlot(MagnetProviderSummary provider) {
        if (!enabled || lockManager == null) {
            return;
        }
        String keyPart = keyPart(provider);
        if (!StringUtils.hasText(keyPart)) {
            return;
        }
        DistributedLockProfile profile = lockManager.profileFor(DistributedLockBusinessType.PROVIDER_FETCH);
        TimeSliceReservation reservation = lockManager.reserveTimeSlice(new TimeSliceReservationRequest(
                profile.keyPrefix() + keyPart,
                gap(provider),
                maxWait,
                ttl));
        if (reservation.rejected()) {
            throw new MagnetProviderRunnerException(FAILURE_TYPE, null, null);
        }
        sleepIfNeeded(reservation.waitTime());
    }

    private String keyPart(MagnetProviderSummary provider) {
        if (provider == null) {
            return null;
        }
        if (provider.id() != null) {
            return provider.id().toString();
        }
        if (StringUtils.hasText(provider.code())) {
            return provider.code().trim();
        }
        return provider.providerType();
    }

    private Duration gap(MagnetProviderSummary provider) {
        if (provider != null && provider.minDelayMs() != null && provider.minDelayMs() > 0) {
            return Duration.ofMillis(provider.minDelayMs());
        }
        return defaultGap;
    }

    private void sleepIfNeeded(Duration waitTime) {
        if (waitTime == null || !waitTime.isPositive()) {
            return;
        }
        try {
            Thread.sleep(waitTime.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MagnetProviderRunnerException(FAILURE_TYPE, null, null);
        }
    }
}
