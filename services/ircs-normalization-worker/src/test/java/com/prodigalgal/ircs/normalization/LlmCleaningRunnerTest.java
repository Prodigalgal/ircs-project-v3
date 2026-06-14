package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LlmCleaningRunnerTest {

    private final LlmCleaningService cleaningService = org.mockito.Mockito.mock(LlmCleaningService.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);

    @Test
    void runnerBeanIsNotCreatedWhenGateIsMissing() {
        runnerContext().run(context -> assertThat(context).doesNotHaveBean(LlmCleaningRunner.class));
    }

    @Test
    void runnerBeanIsCreatedOnlyWhenGateIsEnabled() {
        runnerContext()
                .withPropertyValues("app.normalization.llm-cleaning.runner.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LlmCleaningRunner.class));
    }

    @Test
    void runScheduledRecordsSkippedAuditForDryRunKinds() {
        when(cleaningService.cleanBatch(any()))
                .thenAnswer(invocation -> LlmCleaningService.LlmCleaningResult.skipped(
                        invocation.getArgument(0),
                        "DRY_RUN_SKIPPED"));
        LlmCleaningRunner runner = new LlmCleaningRunner(cleaningService, auditWriter);

        runner.runScheduled();

        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter, org.mockito.Mockito.times(LlmCleaningKind.values().length)).record(captor.capture());
        List<WorkerJobAuditEvent> events = captor.getAllValues();
        assertThat(events).allSatisfy(event -> {
            assertThat(event.jobType()).isEqualTo("scheduler");
            assertThat(event.jobName()).startsWith("normalization.llm-cleaning.");
            assertThat(event.correlationId()).isEqualTo("DRY_RUN_SKIPPED");
            assertThat(event.status()).isEqualTo("skipped");
            assertThat(event.error()).isNotNull();
        });
    }

    private ApplicationContextRunner runnerContext() {
        return new ApplicationContextRunner()
                .withBean(LlmCleaningService.class, () -> cleaningService)
                .withBean(WorkerJobAuditWriter.class, () -> auditWriter)
                .withUserConfiguration(LlmCleaningRunner.class);
    }
}
