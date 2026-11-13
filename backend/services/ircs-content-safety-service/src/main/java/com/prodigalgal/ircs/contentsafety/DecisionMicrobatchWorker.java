package com.prodigalgal.ircs.contentsafety;

import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class DecisionMicrobatchWorker {
    private final DecisionQueueStore queue;
    private final DecisionQuotaStore quota;
    private final GoogleDecisionNsfwClient google;
    private final DecisionSettings settings;
    private final ContentSafetyProperties properties;
    private final MeterRegistry metrics;
    private final DistributionSummary batchSize;
    private final DistributionSummary quotaDelay;
    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong inflight = new AtomicLong();
    private final java.util.concurrent.atomic.AtomicReference<Double> oldestAge = new java.util.concurrent.atomic.AtomicReference<>(0d);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore slots;
    private long nextSnapshot;

    DecisionMicrobatchWorker(DecisionQueueStore queue, DecisionQuotaStore quota, GoogleDecisionNsfwClient google,
            DecisionSettings settings, ContentSafetyProperties properties, MeterRegistry metrics) {
        this.queue = queue; this.quota = quota; this.google = google; this.settings = settings;
        this.properties = properties; this.metrics = metrics;
        slots = new Semaphore(Math.max(1, settings.getConcurrentBatches()));
        metrics.gauge("ircs.decision.queue.queued", queued);
        metrics.gauge("ircs.decision.queue.inflight", inflight);
        io.micrometer.core.instrument.Gauge.builder("ircs.decision.queue.oldest.age.seconds", oldestAge, value -> value.get()).register(metrics);
        batchSize = metrics.summary("ircs.decision.batch.size");
        quotaDelay = metrics.summary("ircs.decision.quota.delay.seconds");
    }

    @Scheduled(fixedDelay = 50)
    void tick() {
        if (!properties.adult().model().enabled() || !DecisionSettings.PROVIDER.equalsIgnoreCase(properties.adult().model().provider())) { return; }
        if (System.currentTimeMillis() >= nextSnapshot) {
            try {
                var snapshot = queue.snapshot();
                queued.set(snapshot.queued()); inflight.set(snapshot.inflight()); oldestAge.set(snapshot.oldestAgeSeconds());
                nextSnapshot = System.currentTimeMillis()+1000;
            } catch (DecisionUnavailableException ex) {
                metrics.counter("ircs.decision.queue.errors", "reason", ex.reason()).increment(); return;
            }
        }
        if (!slots.tryAcquire()) { return; }
        boolean dispatched = false;
        DecisionQuotaStore.Reservation reservation = null;
        try {
            settings.validate();
            List<DecisionQueueStore.Job> jobs = new ArrayList<>(queue.ready());
            if (jobs.isEmpty()) { return; }
            List<DecisionQueueStore.Job> candidates = List.copyOf(jobs);
            jobs = new ArrayList<>(jobs.subList(0, Math.min(jobs.size(), settings.getBatchSize())));
            long estimate = estimate(jobs);
            if (estimate > settings.getMaxRequestBudget() && jobs.size() > 1) {
                int low = 1; int high = jobs.size();
                while (low < high) {
                    int middle = (low+high+1)/2;
                    if (estimate(jobs.subList(0, middle)) <= settings.getMaxRequestBudget()) { low = middle; }
                    else { high = middle-1; }
                }
                jobs = new ArrayList<>(jobs.subList(0, low)); estimate = estimate(jobs);
            }
            if (estimate > settings.getMaxRequestBudget()) {
                var rejected = queue.claim(jobs);
                rejected.jobs().forEach(job -> queue.complete(rejected, job, GoogleDecisionNsfwClient.unavailable("REQUEST_TOO_LARGE", 60)));
                return;
            }
            int alternate = 1;
            while (reservation == null) {
                try { reservation = quota.reserve(estimate); }
                catch (DecisionUnavailableException ex) {
                    if ("TOKEN_LIMITED".equals(ex.reason())) {
                        if (jobs.size() > 1) {
                            jobs = new ArrayList<>(jobs.subList(0, Math.max(1, jobs.size()/2)));
                            estimate = estimate(jobs); continue;
                        }
                        boolean selectedAlternate = false;
                        while (alternate < candidates.size()) {
                            var next = candidates.get(alternate++);
                            long nextEstimate = estimate(List.of(next));
                            if (nextEstimate <= settings.getMaxRequestBudget()) {
                                jobs = new ArrayList<>(List.of(next)); estimate = nextEstimate; selectedAlternate = true; break;
                            }
                        }
                        // Each alternate is tried once. An overlarge old job cannot block smaller work.
                        if (selectedAlternate) { continue; }
                    }
                    // Capacity waits never take ownership, provider credentials or a provider attempt.
                    queue.deferPending(candidates, ex.retryAfterSeconds(), ex.reason());
                    quotaDelay.record(ex.retryAfterSeconds()); return;
                }
            }
            var claim = queue.claim(jobs);
            if (claim.jobs().isEmpty()) { return; }
            DecisionQuotaStore.Reservation reserved = reservation;
            executor.execute(() -> dispatch(claim, reserved));
            dispatched = true;
        } catch (DecisionUnavailableException ex) {
            metrics.counter("ircs.decision.queue.errors", "reason", ex.reason()).increment();
        } catch (RuntimeException ex) {
            metrics.counter("ircs.decision.queue.errors", "reason", "INTERNAL_ERROR").increment();
            log.warn("Google decision queue dispatch failed; queued work remains recoverable");
        } finally {
            if (!dispatched) {
                // A claim with no task (for example shutdown rejection) is recovered after its lease.
                if (reservation != null) {
                    try { quota.cancelBeforeSend(reservation); }
                    catch (DecisionUnavailableException ex) { metrics.counter("ircs.decision.queue.errors", "reason", ex.reason()).increment(); }
                }
                slots.release();
            }
        }
    }

    private long estimate(List<DecisionQueueStore.Job> jobs) {
        return GoogleDecisionNsfwClient.estimateInputBudget(google.requestBody(jobs.stream().map(DecisionQueueStore.Job::item).toList()), jobs.size());
    }

    private void dispatch(DecisionQueueStore.Claim claim, DecisionQuotaStore.Reservation reservation) {
        try {
            if (!queue.markSent(claim)) { quota.cancelBeforeSend(reservation); return; }
            batchSize.record(claim.jobs().size());
            metrics.counter("ircs.decision.batches.dispatched").increment();
            var results = google.classifyReserved(claim.jobs().stream().map(DecisionQueueStore.Job::item).toList(), reservation);
            for (var job : claim.jobs()) {
                AdultAssessmentModelResult result = results.getOrDefault(job.item().id(), GoogleDecisionNsfwClient.unavailable("INVALID_RESPONSE", 60));
                String reason = String.valueOf(result.raw().getOrDefault("reason", "COMPLETE"));
                if (!result.available() && isCapacityWait(reason)) {
                    long retry = ((Number) result.raw().getOrDefault("retryAfterSeconds", 60L)).longValue();
                    queue.defer(claim, job, retry, reason); quotaDelay.record(retry);
                } else {
                    queue.complete(claim, job, result);
                    metrics.counter("ircs.decision.items", "outcome", reason).increment();
                }
            }
        } catch (RuntimeException ex) {
            // After dispatch the consumption may be unknown. Recovery fences this owner and retains its quota.
            metrics.counter("ircs.decision.queue.errors", "reason", "DISPATCH_OR_COMPLETION_ERROR").increment();
            log.warn("Google decision batch outcome unknown; retaining reservation for recovery");
        } finally { slots.release(); }
    }

    private static boolean isCapacityWait(String reason) {
        return switch (reason) {
            case "RATE_LIMITED", "TOKEN_LIMITED", "DAILY_LIMIT", "COOLDOWN", "QUOTA_UNAVAILABLE", "PROVIDER_RATE_LIMITED", "BATCH_DEFERRED" -> true;
            default -> false;
        };
    }

    @PreDestroy
    void close() { executor.close(); }
}
