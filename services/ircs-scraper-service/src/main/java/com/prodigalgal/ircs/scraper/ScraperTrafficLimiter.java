package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.lock.DistributedLockBusinessType;
import com.prodigalgal.ircs.common.lock.DistributedLockManager;
import com.prodigalgal.ircs.common.lock.DistributedLockProfile;
import com.prodigalgal.ircs.common.lock.TimeSliceReservation;
import com.prodigalgal.ircs.common.lock.TimeSliceReservationRequest;
import com.prodigalgal.ircs.scraper.ScraperDtos.DataSourceRecord;
import com.prodigalgal.ircs.scraper.ScraperDtos.ManualScrapeConfigRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ScraperTrafficLimiter {

    private final DistributedLockManager lockManager;
    private final boolean enabled;
    private final Duration defaultGap;
    private final Duration maxWait;
    private final Duration ttl;

    ScraperTrafficLimiter(
            DistributedLockManager lockManager,
            @Value("${app.scraper.traffic.enabled:true}") boolean enabled,
            @Value("${app.scraper.traffic.default-gap-ms:1000}") long defaultGapMs,
            @Value("${app.scraper.traffic.max-wait:PT2M}") Duration maxWait,
            @Value("${app.scraper.traffic.ttl:PT10M}") Duration ttl) {
        this.lockManager = lockManager;
        this.enabled = enabled;
        this.defaultGap = positiveOr(defaultGapMs, Duration.ofSeconds(1));
        this.maxWait = maxWait == null || maxWait.isNegative() ? Duration.ZERO : maxWait;
        this.ttl = ttl == null || !ttl.isPositive() ? Duration.ofMinutes(10) : ttl;
    }

    static ScraperTrafficLimiter noop() {
        return new ScraperTrafficLimiter(null, false, 0, Duration.ZERO, Duration.ZERO);
    }

    void acquireDataSourceSlot(DataSourceRecord source, ManualScrapeConfigRequest config) {
        if (!enabled || lockManager == null || source == null || source.id() == null) {
            return;
        }
        DistributedLockProfile profile = lockManager.profileFor(DistributedLockBusinessType.DATA_SOURCE_SCRAPE);
        TimeSliceReservation reservation = lockManager.reserveTimeSlice(new TimeSliceReservationRequest(
                key(profile, source.id()),
                gap(config),
                maxWait,
                ttl));
        if (reservation.rejected()) {
            throw new IllegalStateException("Data source traffic limit saturated: " + source.name()
                    + ", wait=" + reservation.waitTime().toMillis() + "ms");
        }
        sleepIfNeeded(reservation.waitTime(), source);
    }

    private String key(DistributedLockProfile profile, UUID sourceId) {
        return profile.keyPrefix() + sourceId;
    }

    private Duration gap(ManualScrapeConfigRequest config) {
        if (config != null && config.fixedDelayMs() != null && config.fixedDelayMs() > 0) {
            return Duration.ofMillis(config.fixedDelayMs());
        }
        return defaultGap;
    }

    private void sleepIfNeeded(Duration waitTime, DataSourceRecord source) {
        if (waitTime == null || !waitTime.isPositive()) {
            return;
        }
        try {
            Thread.sleep(waitTime.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for data source traffic limit: "
                    + source.name(), ex);
        }
    }

    private static Duration positiveOr(long millis, Duration fallback) {
        return millis > 0 ? Duration.ofMillis(millis) : fallback;
    }
}
