package com.prodigalgal.ircs.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.contracts.task.TaskMasterSnapshot;
import com.prodigalgal.ircs.contracts.task.TaskPageMessage;
import com.prodigalgal.ircs.contracts.task.TaskScrapeOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class TaskQueueDispatchService {

    private final JdbcCollectionTaskRepository taskRepository;
    private final TaskMasterSnapshotService snapshotService;
    private final TaskProgressRedisService progressService;
    private final TaskQueuePublisher queuePublisher;
    private final TaskLogService taskLogService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int initialPageWindow;
    private final int maxPagesPerRun;

    TaskQueueDispatchService(
            JdbcCollectionTaskRepository taskRepository,
            TaskMasterSnapshotService snapshotService,
            TaskProgressRedisService progressService,
            TaskQueuePublisher queuePublisher,
            TaskLogService taskLogService,
            ObjectMapper objectMapper,
            @Value("${app.task.queue.enabled:true}") boolean enabled,
            @Value("${app.task.queue.initial-page-window:200}") int initialPageWindow,
            @Value("${app.task.queue.max-pages-per-run:0}") int maxPagesPerRun) {
        this.taskRepository = taskRepository;
        this.snapshotService = snapshotService;
        this.progressService = progressService;
        this.queuePublisher = queuePublisher;
        this.taskLogService = taskLogService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.initialPageWindow = Math.max(1, initialPageWindow);
        this.maxPagesPerRun = Math.max(0, maxPagesPerRun);
    }

    void dispatchQueuedMaster(UUID taskId, boolean resume) {
        if (!enabled) {
            failMaster(taskId, "Task Rabbit queue is disabled; destructive V3 task mode has no local runner fallback.");
            return;
        }
        TaskExecutionPlan plan = taskRepository.findExecutionPlan(taskId).orElse(null);
        if (plan == null) {
            log.warn("Task master dispatch skipped: execution plan missing, masterTaskId={}", taskId);
            return;
        }
        Instant now = Instant.now();
        List<Integer> pages = initialPages(plan);
        TaskScrapeOptions options = scrapeOptions(plan);
        TaskMasterSnapshot queuedSnapshot = snapshot(plan, resume, pages.size(), "QUEUED", null, now);
        try {
            snapshotService.put(queuedSnapshot);
            progressService.recordScheduledPages(plan.id(), pages);
            for (int pageNumber : pages) {
                queuePublisher.publishPage(new TaskPageMessage(
                        plan.id(),
                        IrcsUuidGenerators.nextId(),
                        plan.dataSourceId(),
                        pageNumber,
                        resume,
                        0,
                        options,
                        correlationId(plan.id()),
                        now));
            }
            append(plan.id(), "INFO", "Queued " + pages.size() + " page task(s) into RabbitMQ");
        } catch (RuntimeException ex) {
            String reason = "Task queue dispatch failed: " + safeMessage(ex);
            failMaster(plan.id(), reason);
            snapshotService.put(snapshot(plan, resume, pages.size(), "FAILED", reason, Instant.now()));
            throw ex;
        }
    }

    void dispatchNextPageIfNeeded(UUID masterTaskId, int completedPage, Integer observedTotalPages, String correlationId) {
        if (!enabled || masterTaskId == null || completedPage < 1) {
            return;
        }
        TaskExecutionPlan plan = taskRepository.findExecutionPlan(masterTaskId).orElse(null);
        if (plan == null) {
            log.warn("Task page expansion skipped: execution plan missing, masterTaskId={}", masterTaskId);
            return;
        }
        int nextPage = completedPage + initialPageWindow;
        Integer finalPage = finalPage(plan, observedTotalPages);
        if (finalPage == null || nextPage > finalPage) {
            return;
        }
        Instant now = Instant.now();
        long scheduledPage = progressService.trySchedulePage(masterTaskId, nextPage, now);
        if (scheduledPage <= 0) {
            return;
        }
        TaskScrapeOptions options = scrapeOptions(plan);
        queuePublisher.publishPage(new TaskPageMessage(
                plan.id(),
                IrcsUuidGenerators.nextId(),
                plan.dataSourceId(),
                (int) scheduledPage,
                false,
                0,
                options,
                correlationId(plan.id(), correlationId),
                now));
        append(plan.id(), "INFO", "Queued page task " + scheduledPage + " into RabbitMQ");
    }

    private List<Integer> initialPages(TaskExecutionPlan plan) {
        int start = effectiveStartPage(plan.currentPage(), plan.startPage());
        Integer endPage = effectiveEndPage(plan.endPage());
        int end = endPage == null ? start : Math.max(start, endPage);
        if (maxPagesPerRun > 0) {
            end = Math.min(end, start + maxPagesPerRun - 1);
        }
        int cappedEnd = Math.min(end, start + initialPageWindow - 1);
        List<Integer> pages = new ArrayList<>();
        for (int page = start; page <= cappedEnd; page++) {
            pages.add(page);
        }
        return pages;
    }

    private Integer finalPage(TaskExecutionPlan plan, Integer observedTotalPages) {
        int start = effectiveStartPage(plan.currentPage(), plan.startPage());
        Integer configuredEnd = effectiveEndPage(plan.endPage());
        Integer observedEnd = observedTotalPages != null && observedTotalPages > 0 ? observedTotalPages : null;
        Integer finalPage = configuredEnd == null ? observedEnd : configuredEnd;
        if (finalPage == null) {
            return null;
        }
        finalPage = Math.max(start, finalPage);
        if (maxPagesPerRun > 0) {
            finalPage = Math.min(finalPage, start + maxPagesPerRun - 1);
        }
        return finalPage;
    }

    private TaskMasterSnapshot snapshot(
            TaskExecutionPlan plan,
            boolean resume,
            long pageScheduled,
            String status,
            String lastError,
            Instant now) {
        return new TaskMasterSnapshot(
                plan.id(),
                plan.dataSourceId(),
                plan.name(),
                status,
                resume,
                effectiveStartPage(plan.currentPage(), plan.startPage()),
                effectiveEndPage(plan.endPage()),
                pageScheduled,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                lastError,
                correlationId(plan.id()),
                now,
                now);
    }

    private TaskScrapeOptions scrapeOptions(TaskExecutionPlan plan) {
        return new TaskScrapeOptions(
                blankToNull(plan.filterKeywords()),
                plan.userAgent(),
                Boolean.TRUE.equals(plan.enableRandomUa()),
                Boolean.TRUE.equals(plan.useCustomProxy()),
                plan.proxyType(),
                plan.proxyHost(),
                plan.proxyPort(),
                plan.proxyUsername(),
                plan.proxyPassword(),
                httpHeaders(plan.headers()),
                plan.fixedDelayMs(),
                false);
    }

    private String httpHeaders(String headers) {
        if (headers == null || headers.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(headers);
            JsonNode httpHeaders = root.path("httpHeaders");
            if (httpHeaders.isObject()) {
                return objectMapper.writeValueAsString(httpHeaders);
            }
        } catch (Exception ignored) {
            return headers;
        }
        return headers;
    }

    private void failMaster(UUID taskId, String reason) {
        taskRepository.fail(taskId, reason);
        append(taskId, "ERROR", reason);
    }

    private void append(UUID taskId, String level, String message) {
        taskLogService.appendLog(taskId, new TaskItemLogResponse(
                Instant.now().toString(),
                level,
                "SYSTEM",
                message));
    }

    private int effectiveStartPage(Integer currentPage, Integer startPage) {
        if (currentPage != null && currentPage > 0) {
            return currentPage;
        }
        return startPage != null && startPage > 0 ? startPage : 1;
    }

    private Integer effectiveEndPage(Integer endPage) {
        return endPage != null && endPage > 0 ? endPage : null;
    }

    private static String correlationId(UUID taskId) {
        return taskId == null ? null : taskId.toString();
    }

    private static String correlationId(UUID taskId, String existing) {
        return existing == null || existing.isBlank() ? correlationId(taskId) : existing;
    }

    private static String blankToNull(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword;
    }

    private static String safeMessage(Throwable ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex == null ? "unknown" : ex.getClass().getSimpleName();
        }
        String message = ex.getMessage().replaceAll("\\s+", " ").trim();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }
}
