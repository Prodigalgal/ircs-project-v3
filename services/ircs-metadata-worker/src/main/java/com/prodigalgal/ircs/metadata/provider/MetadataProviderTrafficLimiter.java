package com.prodigalgal.ircs.metadata.provider;

import com.prodigalgal.ircs.common.lock.DistributedLockBusinessType;
import com.prodigalgal.ircs.common.lock.DistributedLockManager;
import com.prodigalgal.ircs.common.lock.DistributedLockProfile;
import com.prodigalgal.ircs.common.lock.TimeSliceReservation;
import com.prodigalgal.ircs.common.lock.TimeSliceReservationRequest;
import com.prodigalgal.ircs.common.outbound.EgressIdentities;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MetadataProviderTrafficLimiter {

    private final DistributedLockManager lockManager;
    private final boolean enabled;
    private final String egressIdentity;
    private final Duration defaultGap;
    private final Duration maxWait;
    private final Duration ttl;

    MetadataProviderTrafficLimiter(
            DistributedLockManager lockManager,
            @Value("${app.metadata.public-traffic.enabled:true}") boolean enabled,
            @Value("${app.metadata.public-traffic.egress-id:${APP_METADATA_TRAFFIC_EGRESS_ID:${IRCS_TRAFFIC_EGRESS_ID:${HOST_IP:${HOSTNAME:unknown}}}}}") String egressIdentity,
            @Value("${app.metadata.public-traffic.default-gap-ms:1000}") long defaultGapMs,
            @Value("${app.metadata.public-traffic.max-wait:PT2M}") Duration maxWait,
            @Value("${app.metadata.public-traffic.ttl:PT10M}") Duration ttl) {
        this.lockManager = lockManager;
        this.enabled = enabled;
        this.egressIdentity = EgressIdentities.sanitize(egressIdentity);
        this.defaultGap = defaultGapMs > 0 ? Duration.ofMillis(defaultGapMs) : Duration.ofSeconds(1);
        this.maxWait = maxWait == null || maxWait.isNegative() ? Duration.ZERO : maxWait;
        this.ttl = ttl == null || !ttl.isPositive() ? Duration.ofMinutes(10) : ttl;
    }

    public static MetadataProviderTrafficLimiter noop() {
        return new MetadataProviderTrafficLimiter(null, false, "unknown", 0, Duration.ZERO, Duration.ZERO);
    }

    public void acquireProviderSlot(String providerCode) {
        if (!enabled || lockManager == null || !StringUtils.hasText(providerCode)) {
            return;
        }
        DistributedLockProfile profile = lockManager.profileFor(DistributedLockBusinessType.PROVIDER_FETCH);
        String key = profile.keyPrefix() + "Ip:" + egressIdentity + ":" + EgressIdentities.sanitize(providerCode);
        TimeSliceReservation reservation = lockManager.reserveTimeSlice(new TimeSliceReservationRequest(
                key,
                defaultGap,
                maxWait,
                ttl));
        if (reservation.rejected()) {
            throw new MetadataProviderRetryableException(
                    "PROVIDER_TRAFFIC_SATURATED",
                    "Metadata provider traffic saturated: " + providerCode);
        }
        sleepIfNeeded(reservation.waitTime(), providerCode);
    }

    private void sleepIfNeeded(Duration waitTime, String providerCode) {
        if (waitTime == null || !waitTime.isPositive()) {
            return;
        }
        try {
            Thread.sleep(waitTime.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MetadataProviderRetryableException(
                    "PROVIDER_TRAFFIC_INTERRUPTED",
                    "Interrupted while waiting for metadata provider traffic limit: " + providerCode,
                    ex);
        }
    }
}
