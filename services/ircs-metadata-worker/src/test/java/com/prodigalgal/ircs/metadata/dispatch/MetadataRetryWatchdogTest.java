package com.prodigalgal.ircs.metadata.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.metadata.MetadataConfigValues;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class MetadataRetryWatchdogTest {

    @Mock
    private RawVideoEnrichmentRepository repository;

    @Mock
    private MetadataEnrichPublisher publisher;

    @Mock
    private MetadataConfigValues configValues;

    @Test
    void runOnceRepublishesDueEnrichmentIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PipelineCommand firstCommand = PipelineCommand.enrich(first, "hash-1");
        PipelineCommand secondCommand = PipelineCommand.enrich(second, "hash-2");
        when(configValues.watchdogBatchSize()).thenReturn(50);
        when(configValues.watchdogStalePendingSeconds()).thenReturn(300L);
        when(repository.claimDueEnrichmentRecoveryCommands(any(Instant.class), eq(50)))
                .thenReturn(List.of(firstCommand, secondCommand));
        MetadataRetryWatchdog watchdog = watchdog();

        int republished = watchdog.runOnce();

        assertEquals(2, republished);
        verify(publisher).publish(firstCommand);
        verify(publisher).publish(secondCommand);
    }

    @Test
    void runOnceReleasesUnpublishedClaimsWhenPublisherFails() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        PipelineCommand firstCommand = PipelineCommand.enrich(first, "hash-1");
        PipelineCommand secondCommand = PipelineCommand.enrich(second, "hash-2");
        PipelineCommand thirdCommand = PipelineCommand.enrich(third, "hash-3");
        RuntimeException failure = new IllegalStateException("rabbit down");
        when(configValues.watchdogBatchSize()).thenReturn(50);
        when(configValues.watchdogStalePendingSeconds()).thenReturn(300L);
        when(repository.claimDueEnrichmentRecoveryCommands(any(Instant.class), eq(50)))
                .thenReturn(List.of(firstCommand, secondCommand, thirdCommand));
        doAnswer(invocation -> {
            PipelineCommand command = invocation.getArgument(0);
            if (second.equals(command.rawVideoId())) {
                throw failure;
            }
            return null;
        }).when(publisher).publish(any(PipelineCommand.class));
        MetadataRetryWatchdog watchdog = watchdog();

        assertThatThrownBy(watchdog::runOnce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rabbit down");

        verify(publisher).publish(firstCommand);
        verify(publisher).publish(secondCommand);
        verify(publisher, never()).publish(thirdCommand);
        verify(repository).releaseRecoveryPublishFailures(List.of(second, third));
    }

    @Test
    void runOnceSkipsWhenBatchSizeIsDisabled() {
        when(configValues.watchdogBatchSize()).thenReturn(0);
        MetadataRetryWatchdog watchdog = watchdog();

        int republished = watchdog.runOnce();

        assertEquals(0, republished);
        verify(repository, never()).claimDueEnrichmentRecoveryCommands(any(), any(Integer.class));
        verify(publisher, never()).publish(any(PipelineCommand.class));
    }

    @Test
    void watchdogBeanIsNotCreatedWhenGateIsMissing() {
        watchdogContextRunner().run(context -> assertThat(context).doesNotHaveBean(MetadataRetryWatchdog.class));
    }

    @Test
    void watchdogBeanIsCreatedOnlyWhenGateIsEnabled() {
        watchdogContextRunner()
                .withPropertyValues("app.metadata.watchdog.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MetadataRetryWatchdog.class));
    }

    private MetadataRetryWatchdog watchdog() {
        return new MetadataRetryWatchdog(repository, publisher, configValues);
    }

    private ApplicationContextRunner watchdogContextRunner() {
        return new ApplicationContextRunner()
                .withBean(RawVideoEnrichmentRepository.class, () -> repository)
                .withBean(MetadataEnrichPublisher.class, () -> publisher)
                .withBean(MetadataConfigValues.class, () -> configValues)
                .withUserConfiguration(MetadataRetryWatchdog.class);
    }
}
