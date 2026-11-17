package com.prodigalgal.ircs.task.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.task.application.CatalogDiscoveryService;
import com.prodigalgal.ircs.task.config.CatalogDiscoveryConfigValues;
import com.prodigalgal.ircs.task.infrastructure.TaskDistributedLockRunner;
import org.junit.jupiter.api.Test;

class CatalogDiscoverySchedulerTest {

    private final CatalogDiscoveryService service = mock(CatalogDiscoveryService.class);
    private final CatalogDiscoveryConfigValues config = mock(CatalogDiscoveryConfigValues.class);
    private final TaskDistributedLockRunner lock = mock(TaskDistributedLockRunner.class);
    private final CatalogDiscoveryScheduler scheduler = new CatalogDiscoveryScheduler(service, config, lock);

    @Test
    void disabledRuntimeSettingDoesNotAcquireOrDispatch() {
        scheduler.dispatchOnce();
        verifyNoInteractions(service, lock);
        scheduler.shutdown();
    }

    @Test
    void dispatchesOnlyInsideTheExistingClusterLease() {
        when(config.enabled()).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        }).when(lock).runExclusive(eq(CatalogDiscoveryScheduler.LOCK_NAME), any());

        scheduler.dispatchOnce();

        verify(service).dispatchDue();
        scheduler.shutdown();
    }

    @Test
    void contendedClusterLeaseDoesNotRunTheDispatcher() {
        when(config.enabled()).thenReturn(true);

        scheduler.dispatchOnce();

        verifyNoInteractions(service);
        scheduler.shutdown();
    }
}
