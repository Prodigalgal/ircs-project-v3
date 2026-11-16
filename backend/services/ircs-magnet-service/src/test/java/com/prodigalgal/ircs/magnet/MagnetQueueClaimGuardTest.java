package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MagnetQueueClaimGuardTest {

    @Test
    void renewsEveryClaimAndStopsRenewingFinishedItems() {
        MagnetWorkLeaseStore store = mock(MagnetWorkLeaseStore.class);
        when(store.renew(any(), any())).thenReturn(true);
        RuntimeWorkItem first = item();
        RuntimeWorkItem tail = item();
        Duration visibility = Duration.ofMinutes(1);

        try (MagnetQueueClaimGuard guard = new MagnetQueueClaimGuard(store, List.of(first, tail), visibility, () -> {})) {
            assertTrue(MagnetQueueClaimGuard.finishCurrent(first, () -> true));
            guard.renewNow();
            MagnetQueueClaimGuard.checkCurrentOwnership();
            verify(store, times(1)).renew(first, visibility);
            verify(store, times(2)).renew(tail, visibility);
        }
        assertFalse(MagnetQueueClaimGuard.ownsCurrent(tail));
    }

    @Test
    void renewalLossStopsOwnershipChecksAndTerminalWrites() {
        MagnetWorkLeaseStore store = mock(MagnetWorkLeaseStore.class);
        when(store.renew(any(), any())).thenReturn(true, false);
        RuntimeWorkItem item = item();
        AtomicBoolean transitionCalled = new AtomicBoolean();

        try (MagnetQueueClaimGuard guard = new MagnetQueueClaimGuard(store, List.of(item), Duration.ofMinutes(1), () -> {})) {
            guard.renewNow();
            assertThrows(IllegalStateException.class, MagnetQueueClaimGuard::checkCurrentOwnership);
            assertFalse(MagnetQueueClaimGuard.finishCurrent(item, () -> {
                transitionCalled.set(true);
                return true;
            }));
            assertFalse(transitionCalled.get());
        }
    }

    @Test
    void renewerContinuesWhileOwnerIsBlockedAndStopsOnClose() throws Exception {
        MagnetWorkLeaseStore store = mock(MagnetWorkLeaseStore.class);
        CountDownLatch backgroundRenewal = new CountDownLatch(1);
        Thread owner = Thread.currentThread();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean renewedAfterClose = new AtomicBoolean();
        when(store.renew(any(), any())).thenAnswer(invocation -> {
            if (Thread.currentThread() != owner) {
                backgroundRenewal.countDown();
            }
            if (closed.get()) {
                renewedAfterClose.set(true);
            }
            return true;
        });

        try (MagnetQueueClaimGuard ignored = new MagnetQueueClaimGuard(
                store, List.of(item()), Duration.ofMillis(120), () -> {})) {
            assertTrue(backgroundRenewal.await(5, TimeUnit.SECONDS));
        }
        closed.set(true);
        assertFalse(renewedAfterClose.get());
    }

    private RuntimeWorkItem item() {
        Instant now = Instant.now();
        return new RuntimeWorkItem(
                MagnetWorkTypes.SEARCH,
                MagnetWorkTypes.searchTaskId(UUID.randomUUID()),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "v1",
                "{}",
                "PROCESSING",
                1,
                now,
                now,
                now,
                now.plusSeconds(60),
                "worker-1",
                null);
    }
}
