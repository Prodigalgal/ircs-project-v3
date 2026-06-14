package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(value = "app.normalization.llm-cleaning.runner.enabled", havingValue = "true")
class LlmCleaningRunner {

    private final LlmCleaningService cleaningService;
    private final WorkerJobAuditWriter auditWriter;
    LlmCleaningRunner(
            LlmCleaningService cleaningService,
            WorkerJobAuditWriter auditWriter) {
        this.cleaningService = cleaningService;
        this.auditWriter = auditWriter;
    }

    @Scheduled(
            initialDelayString = "${app.normalization.llm-cleaning.runner.initial-delay-ms:90000}",
            fixedDelayString = "${app.normalization.llm-cleaning.runner.fixed-delay-ms:3600000}")
    void runScheduled() {
        for (LlmCleaningKind kind : LlmCleaningKind.values()) {
            runKind(kind);
        }
    }

    private void runKind(LlmCleaningKind kind) {
        Instant startedAt = Instant.now();
        try {
            LlmCleaningService.LlmCleaningResult result = cleaningService.cleanBatch(kind);
            log.info(
                    "LLM cleaning {} result status={}, reason={}, candidates={}, mapped={}, noise={}",
                    kind,
                    result.status(),
                    result.reason(),
                    result.candidates(),
                    result.mapped(),
                    result.noise());
            recordResult(startedAt, kind, result);
        } catch (RuntimeException ex) {
            recordFailed(startedAt, kind, ex);
            throw ex;
        }
    }

    private void recordResult(Instant startedAt, LlmCleaningKind kind, LlmCleaningService.LlmCleaningResult result) {
        if (result != null && "SKIPPED".equals(result.status())) {
            recordAudit(new WorkerJobAuditEvent(
                    "scheduler",
                    jobName(kind),
                    result.reason(),
                    "skipped",
                    elapsedSince(startedAt),
                    new LlmCleaningAuditException(result.reason())));
            return;
        }
        if (result != null && "SUCCESS".equals(result.status())) {
            recordAudit(WorkerJobAuditEvent.succeeded(
                    "scheduler",
                    jobName(kind),
                    result.reason(),
                    elapsedSince(startedAt)));
            return;
        }
        recordAudit(WorkerJobAuditEvent.failed(
                "scheduler",
                jobName(kind),
                result == null ? null : result.reason(),
                elapsedSince(startedAt),
                new LlmCleaningAuditException("unexpected result status")));
    }

    private void recordFailed(Instant startedAt, LlmCleaningKind kind, RuntimeException error) {
        recordAudit(WorkerJobAuditEvent.failed(
                "scheduler",
                jobName(kind),
                null,
                elapsedSince(startedAt),
                error));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("LLM cleaning runner audit write failed: {}", ex.getMessage());
        }
    }

    private static String jobName(LlmCleaningKind kind) {
        String name = kind == null ? "unknown" : kind.name().toLowerCase(Locale.ROOT);
        return "normalization.llm-cleaning." + name;
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static class LlmCleaningAuditException extends RuntimeException {
        LlmCleaningAuditException(String message) {
            super(message);
        }
    }
}
