package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class NormalizationRetryWatchdogTest {

    @Mock
    private RawVideoNormalizationRepository repository;

    @Mock
    private NormalizeVideoPublisher publisher;

    @Mock
    private NormalizationConfigValues configValues;

    @Mock
    private NormalizeVideoQueueDepthProbe queueDepthProbe;

    @Test
    void runOnceRepublishesDueRecoveryIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(configValues.watchdogBatchSize()).thenReturn(25);
        when(configValues.watchdogStalePendingSeconds()).thenReturn(3600L);
        when(repository.claimDueFailedNormalizationRecoveryIds(any(Instant.class), eq(25)))
                .thenReturn(List.of(first, second));
        NormalizationRetryWatchdog watchdog = watchdog();

        int republished = watchdog.runOnce();

        assertEquals(2, republished);
        verify(publisher).publish(first);
        verify(publisher).publish(second);
    }

    @Test
    void runOnceResetsStaleProcessingWhenQueueIsEmpty() {
        UUID failed = UUID.randomUUID();
        UUID processing = UUID.randomUUID();
        when(configValues.watchdogBatchSize()).thenReturn(25);
        when(configValues.watchdogStalePendingSeconds()).thenReturn(3600L);
        when(repository.claimDueFailedNormalizationRecoveryIds(any(Instant.class), eq(25)))
                .thenReturn(List.of(failed));
        when(queueDepthProbe.pendingMessages()).thenReturn(OptionalInt.of(0));
        when(repository.claimStaleActiveNormalizationRecoveryIds(any(Instant.class), eq(24)))
                .thenReturn(List.of(processing));
        NormalizationRetryWatchdog watchdog = watchdog();

        int republished = watchdog.runOnce();

        assertEquals(2, republished);
        verify(publisher).publish(processing);
    }

    @Test
    void runOnceSkipsStalePendingWhenQueueHasBacklogButStillRetriesDueFailed() {
        UUID failed = UUID.randomUUID();
        when(configValues.watchdogBatchSize()).thenReturn(25);
        when(configValues.watchdogStalePendingSeconds()).thenReturn(3600L);
        when(repository.claimDueFailedNormalizationRecoveryIds(any(Instant.class), eq(25)))
                .thenReturn(List.of(failed));
        when(queueDepthProbe.pendingMessages()).thenReturn(OptionalInt.of(3));
        NormalizationRetryWatchdog watchdog = watchdog();

        int republished = watchdog.runOnce();

        assertEquals(1, republished);
        verify(repository, never()).claimStaleActiveNormalizationRecoveryIds(any(), any(Integer.class));
        verify(publisher).publish(failed);
    }

    @Test
    void runOnceReleasesUnpublishedClaimsWhenPublisherFails() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("rabbit down");
        when(configValues.watchdogBatchSize()).thenReturn(25);
        when(configValues.watchdogStalePendingSeconds()).thenReturn(3600L);
        when(configValues.backoffBaseSeconds()).thenReturn(60L);
        when(repository.claimDueFailedNormalizationRecoveryIds(any(Instant.class), eq(25)))
                .thenReturn(List.of(first, second, third));
        doAnswer(invocation -> {
            UUID rawVideoId = invocation.getArgument(0);
            if (second.equals(rawVideoId)) {
                throw failure;
            }
            return null;
        }).when(publisher).publish(any(UUID.class));
        NormalizationRetryWatchdog watchdog = watchdog();

        assertThatThrownBy(watchdog::runOnce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rabbit down");

        verify(publisher).publish(first);
        verify(publisher).publish(second);
        verify(publisher, never()).publish(third);
        verify(repository).releaseRecoveryPublishFailures(eq(List.of(second, third)), any(Instant.class));
    }

    @Test
    void runOnceSkipsWhenBatchSizeIsDisabled() {
        when(configValues.watchdogBatchSize()).thenReturn(0);
        NormalizationRetryWatchdog watchdog = watchdog();

        int republished = watchdog.runOnce();

        assertEquals(0, republished);
        verify(repository, never()).claimDueFailedNormalizationRecoveryIds(any(), any(Integer.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    void watchdogBeanIsNotCreatedWhenGateIsMissing() {
        watchdogContextRunner().run(context -> assertThat(context).doesNotHaveBean(NormalizationRetryWatchdog.class));
    }

    @Test
    void watchdogBeanIsCreatedOnlyWhenGateIsEnabled() {
        watchdogContextRunner()
                .withPropertyValues("app.normalization.watchdog.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NormalizationRetryWatchdog.class));
    }

    private NormalizationRetryWatchdog watchdog() {
        return new NormalizationRetryWatchdog(repository, publisher, configValues, queueDepthProbe);
    }

    private ApplicationContextRunner watchdogContextRunner() {
        return new ApplicationContextRunner()
                .withBean(RawVideoNormalizationRepository.class, () -> repository)
                .withBean(NormalizeVideoPublisher.class, () -> publisher)
                .withBean(NormalizationConfigValues.class, () -> configValues)
                .withBean(NormalizeVideoQueueDepthProbe.class, () -> queueDepthProbe)
                .withUserConfiguration(NormalizationRetryWatchdog.class);
    }
}
