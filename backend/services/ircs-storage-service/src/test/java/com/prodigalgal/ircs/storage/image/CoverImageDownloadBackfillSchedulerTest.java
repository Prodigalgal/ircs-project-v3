package com.prodigalgal.ircs.storage.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.lock.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CoverImageDownloadBackfillSchedulerTest {

    @Test
    void busyClusterLeaseSkipsBackfillEvenWhenDeploymentEnablesIt() {
        CoverImageAdminService service = org.mockito.Mockito.mock(CoverImageAdminService.class);
        DistributedLockService locks = org.mockito.Mockito.mock(DistributedLockService.class);
        CoverImageDownloadBackfillScheduler scheduler = new CoverImageDownloadBackfillScheduler(service, locks);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        scheduler.runOnce();
        verify(service, never()).enqueueDownloadBackfill(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void acquiredClusterLeaseRunsTheConfiguredBackfillBatch() {
        CoverImageAdminService service = org.mockito.Mockito.mock(CoverImageAdminService.class);
        DistributedLockService locks = org.mockito.Mockito.mock(DistributedLockService.class);
        CoverImageDownloadBackfillScheduler scheduler = new CoverImageDownloadBackfillScheduler(service, locks);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 25);
        when(locks.runWithLock(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(3)).run();
            return true;
        });
        scheduler.runOnce();
        verify(service).enqueueDownloadBackfill(25);
    }
}
