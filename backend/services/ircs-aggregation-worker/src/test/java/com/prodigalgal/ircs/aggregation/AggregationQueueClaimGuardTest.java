package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AggregationQueueClaimGuardTest {
    @Test
    void renewsTailClaimsAndStopsRenewingCompletedClaims() {
        AggregationWorkDeferrer deferrer = mock(AggregationWorkDeferrer.class);
        when(deferrer.renew(any(), any())).thenReturn(true);
        RuntimeWorkItem first = item();
        RuntimeWorkItem tail = item();
        Duration visibility = Duration.ofMinutes(1);

        try (AggregationQueueClaimGuard guard = new AggregationQueueClaimGuard(
                deferrer, List.of(first, tail), visibility, () -> {})) {
            assertThat(AggregationQueueClaimGuard.finishCurrent(first, () -> true)).isTrue();
            guard.renewNow();
            AggregationQueueClaimGuard.checkCurrentOwnership();
            verify(deferrer).renew(first, visibility);
            verify(deferrer, times(2)).renew(tail, visibility);
        }
        AggregationQueueClaimGuard.checkCurrentOwnership();
        assertThat(AggregationQueueClaimGuard.ownsCurrent(tail)).isFalse();
    }

    @Test
    void renewalLossStopsWritesAndNeverCallsTerminalTransition() {
        AggregationWorkDeferrer deferrer = mock(AggregationWorkDeferrer.class);
        when(deferrer.renew(any(), any())).thenReturn(true, false);
        RuntimeWorkItem item = item();
        AtomicBoolean transitionCalled = new AtomicBoolean();

        try (AggregationQueueClaimGuard guard = new AggregationQueueClaimGuard(
                deferrer, List.of(item), Duration.ofMinutes(1), () -> {})) {
            guard.renewNow();
            assertThatThrownBy(AggregationQueueClaimGuard::checkCurrentOwnership)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("ownership lost");
            assertThat(AggregationQueueClaimGuard.finishCurrent(item, () -> {
                transitionCalled.set(true);
                return true;
            })).isFalse();
            assertThat(transitionCalled).isFalse();
        }
    }

    @Test
    void backgroundRenewerRunsWhileOwnerIsBlockedAndStopsOnClose() throws Exception {
        AggregationWorkDeferrer deferrer = mock(AggregationWorkDeferrer.class);
        CountDownLatch backgroundRenewal = new CountDownLatch(1);
        Thread owner = Thread.currentThread();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean renewedAfterClose = new AtomicBoolean();
        when(deferrer.renew(any(), any())).thenAnswer(invocation -> {
            if (Thread.currentThread() != owner) { backgroundRenewal.countDown(); }
            if (closed.get()) { renewedAfterClose.set(true); }
            return true;
        });
        RuntimeWorkItem item = item();
        try (AggregationQueueClaimGuard guard = new AggregationQueueClaimGuard(
                deferrer, List.of(item), Duration.ofSeconds(1), () -> {})) {
            assertThat(backgroundRenewal.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(AggregationQueueClaimGuard.ownsCurrent(item)).isTrue();
        }
        closed.set(true);
        assertThat(renewedAfterClose).isFalse();
    }

    @Test
    void transactionOwnershipChecksNeverWaitForBlockedRedisRenewalAndUsePublishedDeadline() throws Exception {
        AggregationWorkDeferrer deferrer = mock(AggregationWorkDeferrer.class);
        CountDownLatch renewalBlocked = new CountDownLatch(1);
        CountDownLatch releaseRenewal = new CountDownLatch(1);
        AtomicLong nowNanos = new AtomicLong(1);
        Thread owner = Thread.currentThread();
        when(deferrer.renew(any(), any())).thenAnswer(invocation -> {
            if (Thread.currentThread() != owner) {
                renewalBlocked.countDown();
                try {
                    releaseRenewal.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        });
        Duration visibility = Duration.ofMillis(90);
        RuntimeWorkItem item = item();
        AggregationQueueClaimGuard guard = new AggregationQueueClaimGuard(
                deferrer, List.of(item), visibility, () -> {}, nowNanos::get);
        try {
            assertThat(renewalBlocked.await(5, TimeUnit.SECONDS)).isTrue();
            // assertTimeout executes on this owner thread, preserving the actual ThreadLocal guard.
            assertTimeout(Duration.ofMillis(250), () -> {
                AggregationQueueClaimGuard.checkCurrentOwnership();
                assertThat(AggregationQueueClaimGuard.ownsCurrent(item)).isTrue();
            });
            nowNanos.addAndGet(visibility.toNanos());
            assertTimeout(Duration.ofMillis(250), () -> {
                assertThatThrownBy(AggregationQueueClaimGuard::checkCurrentOwnership)
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("ownership lost");
                assertThat(AggregationQueueClaimGuard.ownsCurrent(item)).isFalse();
            });
        } finally {
            releaseRenewal.countDown();
            guard.close();
        }
    }

    private RuntimeWorkItem item() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return new RuntimeWorkItem(AggregationWorkTypes.RAW_VIDEO, AggregationWorkTypes.taskId(id),
                UUID.randomUUID().toString(), id.toString(), "v1", "{}", "PROCESSING", 1,
                now, now, now, now.plusSeconds(60), "worker", null);
    }
}
