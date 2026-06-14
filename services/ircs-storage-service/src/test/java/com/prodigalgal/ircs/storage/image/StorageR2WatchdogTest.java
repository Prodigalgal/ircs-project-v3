package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.lease.UnavailableClusterLeaseService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class StorageR2WatchdogTest {

    @Mock
    private AvatarSyncMemberRepository memberRepository;

    @Mock
    private CoverImageAdminRepository coverImageRepository;

    @Mock
    private StorageCommandPublisher commandPublisher;

    @Mock
    private CoverImageR2SyncService coverImageR2SyncService;

    @Mock
    private R2ObjectStorage r2ObjectStorage;

    @Mock
    private SystemConfigRepository configRepository;

    @Mock
    private WorkerJobAuditWriter auditWriter;

    @Test
    void skipsWhenR2Inactive() {
        when(r2ObjectStorage.isActive()).thenReturn(false);

        StorageR2Watchdog.WatchdogRunResult result = newWatchdog().runOnce();

        assertTrue(result.skipped());
        assertEquals(0, result.avatarRepublished());
        verify(memberRepository, never()).claimLocalAvatarMemberIds("/media", 25, "local-test", Duration.ofMinutes(10));
        verify(coverImageRepository, never()).claimDueLocalR2UploadIds(25);
        WorkerJobAuditEvent event = captureAuditEvent();
        assertEquals("storage.r2-watchdog", event.jobName());
        assertEquals("r2-inactive", event.correlationId());
        assertEquals("skipped", event.status());
    }

    @Test
    void republishesLocalAvatarMembersAndSyncsDueCoverImages() {
        UUID memberId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(configRepository.findValue("app.storage.public-path")).thenReturn(Optional.of("/assets"));
        when(r2ObjectStorage.isActive()).thenReturn(true);
        when(memberRepository.claimLocalAvatarMemberIds("/assets", 25, "local-test", Duration.ofMinutes(10)))
                .thenReturn(List.of(memberId));
        when(coverImageRepository.claimDueLocalR2UploadIds(25)).thenReturn(List.of(imageId));
        when(coverImageR2SyncService.sync(imageId))
                .thenReturn(CoverImageR2SyncService.CoverImageR2SyncResult.synced("covers/a.png"));

        StorageR2Watchdog.WatchdogRunResult result = newWatchdog().runOnce();

        assertFalse(result.skipped());
        assertEquals(1, result.avatarRepublished());
        assertEquals(1, result.coverSynced());
        assertEquals(0, result.coverFailed());
        verify(commandPublisher).publishAvatarSync(memberId);
        verify(coverImageR2SyncService).sync(imageId);
        WorkerJobAuditEvent event = captureAuditEvent();
        assertEquals("storage.r2-watchdog", event.jobName());
        assertEquals("execute", event.correlationId());
        assertEquals("succeeded", event.status());
    }

    @Test
    void countsCoverFailureAndContinuesBatch() {
        UUID failed = UUID.randomUUID();
        UUID synced = UUID.randomUUID();
        when(configRepository.findValue("app.storage.public-path")).thenReturn(Optional.empty());
        when(r2ObjectStorage.isActive()).thenReturn(true);
        when(memberRepository.claimLocalAvatarMemberIds("/media", 25, "local-test", Duration.ofMinutes(10)))
                .thenReturn(List.of());
        when(coverImageRepository.claimDueLocalR2UploadIds(25)).thenReturn(List.of(failed, synced));
        when(coverImageR2SyncService.sync(failed))
                .thenReturn(CoverImageR2SyncService.CoverImageR2SyncResult.failed("r2 upload failed"));
        when(coverImageR2SyncService.sync(synced))
                .thenReturn(CoverImageR2SyncService.CoverImageR2SyncResult.synced("covers/b.png"));

        StorageR2Watchdog.WatchdogRunResult result = newWatchdog().runOnce();

        assertEquals(1, result.coverSynced());
        assertEquals(1, result.coverFailed());
        verify(coverImageR2SyncService).sync(failed);
        verify(coverImageR2SyncService).sync(synced);
    }

    @Test
    void watchdogBeanIsNotCreatedWhenGateIsMissing() {
        watchdogContextRunner().run(context -> assertThat(context).doesNotHaveBean(StorageR2Watchdog.class));
    }

    @Test
    void watchdogBeanIsCreatedOnlyWhenGateIsEnabled() {
        watchdogContextRunner()
                .withPropertyValues("app.storage.r2.watchdog.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(StorageR2Watchdog.class));
    }

    private StorageR2Watchdog newWatchdog() {
        return new StorageR2Watchdog(
                memberRepository,
                coverImageRepository,
                commandPublisher,
                coverImageR2SyncService,
                r2ObjectStorage,
                new StorageConfigValues(new MockEnvironment(), configRepository),
                auditWriter,
                "ircs-storage-service",
                "local-test",
                "PT10M",
                25);
    }

    private ApplicationContextRunner watchdogContextRunner() {
        return new ApplicationContextRunner()
                .withBean(AvatarSyncMemberRepository.class, () -> memberRepository)
                .withBean(CoverImageAdminRepository.class, () -> coverImageRepository)
                .withBean(StorageCommandPublisher.class, () -> commandPublisher)
                .withBean(CoverImageR2SyncService.class, () -> coverImageR2SyncService)
                .withBean(R2ObjectStorage.class, () -> r2ObjectStorage)
                .withBean(StorageConfigValues.class, () -> new StorageConfigValues(new MockEnvironment(), configRepository))
                .withBean(WorkerJobAuditWriter.class, () -> auditWriter)
                .withBean(UnavailableClusterLeaseService.class, () -> new UnavailableClusterLeaseService("test lock manager"))
                .withUserConfiguration(StorageR2Watchdog.class);
    }

    private WorkerJobAuditEvent captureAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
