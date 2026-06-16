package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.lock.DistributedLockBusinessType;
import com.prodigalgal.ircs.common.lock.DistributedLockManager;
import com.prodigalgal.ircs.common.lock.DistributedLockProfile;
import com.prodigalgal.ircs.common.lock.TimeSliceReservation;
import com.prodigalgal.ircs.common.lock.TimeSliceReservationRequest;
import com.prodigalgal.ircs.common.outbound.EgressIdentities;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class CoverImageTrafficLimiter {

    private static final String GLOBAL_KEY_PREFIX = "traffic:limit:Global:ImageDownload:Ip:";

    private final DistributedLockManager lockManager;
    private final boolean enabled;
    private final String egressIdentity;
    private final Duration globalGap;
    private final Duration domainGap;
    private final Duration maxWait;
    private final Duration ttl;

    CoverImageTrafficLimiter(
            DistributedLockManager lockManager,
            @Value("${app.storage.image.traffic.enabled:true}") boolean enabled,
            @Value("${app.storage.image.traffic.egress-id:${APP_STORAGE_TRAFFIC_EGRESS_ID:${IRCS_TRAFFIC_EGRESS_ID:${HOST_IP:${HOSTNAME:unknown}}}}}") String egressIdentity,
            @Value("${app.storage.image.traffic.global-gap-ms:500}") long globalGapMs,
            @Value("${app.storage.image.traffic.domain-gap-ms:1000}") long domainGapMs,
            @Value("${app.storage.image.traffic.max-wait:PT2M}") Duration maxWait,
            @Value("${app.storage.image.traffic.ttl:PT10M}") Duration ttl) {
        this.lockManager = lockManager;
        this.enabled = enabled;
        this.egressIdentity = EgressIdentities.sanitize(egressIdentity);
        this.globalGap = positiveOr(globalGapMs, Duration.ofMillis(500));
        this.domainGap = positiveOr(domainGapMs, Duration.ofSeconds(1));
        this.maxWait = maxWait == null || maxWait.isNegative() ? Duration.ZERO : maxWait;
        this.ttl = ttl == null || !ttl.isPositive() ? Duration.ofMinutes(10) : ttl;
    }

    static CoverImageTrafficLimiter noop() {
        return new CoverImageTrafficLimiter(null, false, "unknown", 0, 0, Duration.ZERO, Duration.ZERO);
    }

    void acquire(URI uri) {
        if (!enabled || lockManager == null) {
            return;
        }
        reserve(GLOBAL_KEY_PREFIX + egressIdentity, globalGap, "image download");
        String host = uri == null ? null : uri.getHost();
        if (!StringUtils.hasText(host)) {
            return;
        }
        DistributedLockProfile profile = lockManager.profileFor(DistributedLockBusinessType.DOMAIN_FETCH);
        reserve(
                profile.keyPrefix() + "Ip:" + egressIdentity + ":" + EgressIdentities.sanitize(host.toLowerCase(Locale.ROOT)),
                domainGap,
                host);
    }

    private void reserve(String key, Duration gap, String label) {
        TimeSliceReservation reservation = lockManager.reserveTimeSlice(new TimeSliceReservationRequest(
                key,
                gap,
                maxWait,
                ttl));
        if (reservation.rejected()) {
            throw new IllegalStateException("Image download traffic limit saturated: " + label);
        }
        sleepIfNeeded(reservation.waitTime(), label);
    }

    private void sleepIfNeeded(Duration waitTime, String label) {
        if (waitTime == null || !waitTime.isPositive()) {
            return;
        }
        try {
            Thread.sleep(waitTime.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for image download traffic limit: " + label, ex);
        }
    }

    private static Duration positiveOr(long millis, Duration fallback) {
        return millis > 0 ? Duration.ofMillis(millis) : fallback;
    }
}
