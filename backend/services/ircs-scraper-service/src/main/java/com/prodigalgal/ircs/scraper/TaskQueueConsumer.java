package com.prodigalgal.ircs.scraper;

import tools.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.concurrent.VirtualThreadExecutors;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.contracts.task.TaskDetailDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskDetailMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageFailedMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageMessage;
import com.prodigalgal.ircs.messaging.TaskFailureClassifier;
import com.prodigalgal.ircs.messaging.TaskHttpStatusException;
import com.prodigalgal.ircs.messaging.TaskRetryPublisher;
import com.prodigalgal.ircs.messaging.TaskSourceTerminalException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class TaskQueueConsumer {

    private static final String JOB_TYPE_QUEUE_CONSUMER = "queue-consumer";
    private static final String JOB_NAME_PAGE = "scraper.task-page";
    private static final String JOB_NAME_DETAIL = "scraper.task-detail";

    private final ObjectMapper objectMapper;
    private final TaskQueueScraperService scraperService;
    private final ScraperTaskQueuePublisher queuePublisher;
    private final TaskRetryPublisher retryPublisher;
    private final TaskFailureClassifier failureClassifier;
    private final WorkerJobAuditWriter auditWriter;
    private final int maxRetries;
    private final Duration processingTimeout;
    private final ExecutorService executionExecutor =
            VirtualThreadExecutors.newPerTaskExecutor("scraper-task-consumer-");

    TaskQueueConsumer(
            ObjectMapper objectMapper,
            TaskQueueScraperService scraperService,
            ScraperTaskQueuePublisher queuePublisher,
            TaskRetryPublisher retryPublisher,
            TaskFailureClassifier failureClassifier,
            WorkerJobAuditWriter auditWriter,
            @Value("${app.scraper.task-queue.retry.max-retries:3}") int maxRetries,
            @Value("${app.scraper.task-queue.processing-timeout:PT5M}") Duration processingTimeout) {
        this.objectMapper = objectMapper;
        this.scraperService = scraperService;
        this.queuePublisher = queuePublisher;
        this.retryPublisher = retryPublisher;
        this.failureClassifier = failureClassifier;
        this.auditWriter = auditWriter;
        this.maxRetries = Math.max(0, maxRetries);
        this.processingTimeout = processingTimeout == null || !processingTimeout.isPositive()
                ? Duration.ofMinutes(5) : processingTimeout;
    }

    @RabbitListener(
            queues = QueueTopic.Names.TASK_Q_PAGE,
            autoStartup = "${app.scraper.task-queue.listener-enabled:false}",
            ackMode = "MANUAL")
    void consumePage(
            Message rawMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        withManualAck(channel, deliveryTag, () -> handlePage(rawMessage));
    }

    private void handlePage(Message rawMessage) {
        Instant startedAt = Instant.now();
        TaskPageMessage message;
        try {
            message = read(rawMessage, TaskPageMessage.class);
        } catch (RuntimeException ex) {
            deadLetter(QueueTopic.TASK_PAGE, rawMessage, JOB_NAME_PAGE, null, startedAt,
                    new ScraperTaskFailure("Invalid task page queue payload"));
            return;
        }
        try {
            executeWithTimeout(() -> scraperService.processPage(message));
        } catch (RuntimeException ex) {
            retryOrDeadLetter(
                    QueueTopic.TASK_PAGE,
                    rawMessage,
                    JOB_NAME_PAGE,
                    message.correlationId(),
                    startedAt,
                    ex,
                    reason -> publishPageFailed(message, reason));
        }
    }

    @RabbitListener(
            queues = QueueTopic.Names.TASK_Q_DETAIL,
            autoStartup = "${app.scraper.task-queue.listener-enabled:false}",
            ackMode = "MANUAL")
    void consumeDetail(
            Message rawMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        withManualAck(channel, deliveryTag, () -> handleDetail(rawMessage));
    }

    private void handleDetail(Message rawMessage) {
        Instant startedAt = Instant.now();
        TaskDetailMessage message;
        try {
            message = read(rawMessage, TaskDetailMessage.class);
        } catch (RuntimeException ex) {
            deadLetter(QueueTopic.TASK_DETAIL, rawMessage, JOB_NAME_DETAIL, null, startedAt,
                    new ScraperTaskFailure("Invalid task detail queue payload"));
            return;
        }
        try {
            executeWithTimeout(() -> scraperService.processDetail(message));
        } catch (RuntimeException ex) {
            retryOrDeadLetter(
                    QueueTopic.TASK_DETAIL,
                    rawMessage,
                    JOB_NAME_DETAIL,
                    message.correlationId(),
                    startedAt,
                    ex,
                    reason -> publishDetailFailed(message, reason));
        }
    }

    private void executeWithTimeout(Runnable action) {
        var future = executionExecutor.submit(() -> ScraperTaskDeadline.run(processingTimeout, action));
        try {
            future.get(processingTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalStateException("Scraper task exceeded " + processingTimeout, ex);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scraper task consumer interrupted", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (ex.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Scraper task execution failed", ex.getCause());
        }
    }

    @PreDestroy
    void shutdown() {
        executionExecutor.shutdownNow();
    }

    private void withManualAck(Channel channel, long deliveryTag, Runnable handler) {
        try {
            handler.run();
            acknowledge(channel, deliveryTag);
        } catch (RuntimeException ex) {
            reject(channel, deliveryTag, ex);
        }
    }

    private void acknowledge(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ex) {
            throw new IllegalStateException("Rabbit manual ack failed", ex);
        }
    }

    private void reject(Channel channel, long deliveryTag, RuntimeException error) {
        try {
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException ex) {
            error.addSuppressed(ex);
        }
        throw error;
    }

    private <T> T read(Message rawMessage, Class<T> type) {
        T result = objectMapper.readValue(rawMessage.getBody(), type);
        if (result == null) {
            throw new IllegalArgumentException("Missing task queue payload");
        }
        return result;
    }

    private void retryOrDeadLetter(
            QueueTopic topic,
            Message rawMessage,
            String jobName,
            String correlationId,
            Instant startedAt,
            RuntimeException error,
            Consumer<String> notifyFailure) {
        RuntimeException sourceError = sourceFailure(error);
        RuntimeException classified = sourceError == null ? error : sourceError;
        ScraperTaskFailure failure = ScraperTaskFailure.summarize(classified);
        if (!(classified instanceof TaskSourceTerminalException) && failureClassifier.retryable(classified)
                && retryPublisher.retryCount(rawMessage) < maxRetries) {
            int retryCount = retryPublisher.publishRetry(topic, rawMessage, failure);
            recordOutcome(jobName, correlationId, startedAt, "retrying", new ScraperTaskFailure(
                    "Retry scheduled " + retryCount + "/" + maxRetries + ": " + failure.getMessage()));
            return;
        }
        notifyFailure.accept(failure.getMessage());
        if (sourceError != null) {
            recordOutcome(jobName, correlationId, startedAt, "failed", failure);
        } else {
            deadLetter(topic, rawMessage, jobName, correlationId, startedAt, failure);
        }
    }

    private void deadLetter(
            QueueTopic topic,
            Message rawMessage,
            String jobName,
            String correlationId,
            Instant startedAt,
            ScraperTaskFailure failure) {
        retryPublisher.publishDlq(topic, rawMessage, failure);
        recordOutcome(jobName, correlationId, startedAt, "dead-lettered", failure);
    }

    private RuntimeException sourceFailure(RuntimeException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TaskHttpStatusException status) {
                return status;
            }
            if (cause instanceof TaskSourceTerminalException terminal) {
                return terminal;
            }
        }
        return null;
    }

    private void publishPageFailed(TaskPageMessage message, String reason) {
        queuePublisher.publishPageFailed(new TaskPageFailedMessage(
                message.masterTaskId(),
                message.pageTaskId(),
                message.pageNumber(),
                reason,
                message.correlationId(),
                Instant.now()));
    }

    private void publishDetailFailed(TaskDetailMessage message, String reason) {
        queuePublisher.publishDetailDone(new TaskDetailDoneMessage(
                message.masterTaskId(),
                message.pageTaskId(),
                message.detailTaskId(),
                message.sourceVid(),
                false,
                reason,
                message.correlationId(),
                Instant.now()));
    }

    private void recordOutcome(String jobName, String correlationId, Instant startedAt, String outcome,
            ScraperTaskFailure failure) {
        try {
            auditWriter.record(new WorkerJobAuditEvent(
                    JOB_TYPE_QUEUE_CONSUMER, jobName, correlationId, outcome,
                    Duration.between(startedAt, Instant.now()), failure));
        } catch (RuntimeException ex) {
            log.warn("Task queue consumer audit write failed: {}", ex.getClass().getSimpleName());
        }
    }
}
