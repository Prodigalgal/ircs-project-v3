package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.pipeline.PipelineLease;
import com.prodigalgal.ircs.common.pipeline.PipelineRedisState;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class NormalizeVideoConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NormalizeVideoMessageParser messageParser =
            new NormalizeVideoMessageParser(new PipelineCommandMessageCodec(objectMapper));
    private final RawVideoNormalizationService normalizationService = org.mockito.Mockito.mock(RawVideoNormalizationService.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);
    private final PipelineRedisState pipelineState = org.mockito.Mockito.mock(PipelineRedisState.class);
    private final NormalizeVideoConsumer consumer =
            new NormalizeVideoConsumer(
                    messageParser,
                    normalizationService,
                    auditWriter,
                    pipelineState,
                    "ircs-normalization-worker",
                    "worker-1");

    @Test
    void consumesPipelineCommandAndWritesSucceededAuditEvidence() throws Exception {
        UUID rawVideoId = UUID.randomUUID();
        PipelineLease lease = lease(rawVideoId);
        when(pipelineState.tryLease(
                        eq(PipelineStep.NORMALIZE_VIDEO),
                        eq(rawVideoId),
                        eq("hash-v1"),
                        eq("worker-1"),
                        eq(Duration.ofMinutes(10))))
                .thenReturn(Optional.of(lease));
        when(normalizationService.normalize(rawVideoId, "hash-v1"))
                .thenReturn(RawVideoNormalizationService.NormalizationResult.ready(rawVideoId));

        consumer.onMessage(message(PipelineCommand.normalize(rawVideoId, "hash-v1")));

        verify(normalizationService).normalize(rawVideoId, "hash-v1");
        verify(pipelineState).markComplete(
                eq(PipelineStep.NORMALIZE_VIDEO),
                eq(rawVideoId),
                eq("hash-v1"),
                eq(Duration.ofHours(6)));
        verify(pipelineState).release(lease);
        WorkerJobAuditEvent event = captureAuditEvent();
        assertThat(event.jobType()).isEqualTo("queue-consumer");
        assertThat(event.jobName()).isEqualTo("normalization.raw-video");
        assertThat(event.correlationId()).isEqualTo(rawVideoId.toString());
        assertThat(event.status()).isEqualTo("succeeded");
        assertThat(event.error()).isNull();
    }

    @Test
    void missingRawVideoIsRejectedAndWritesFailedAuditEvidence() throws Exception {
        UUID rawVideoId = UUID.randomUUID();
        PipelineLease lease = lease(rawVideoId);
        when(pipelineState.tryLease(any(), eq(rawVideoId), eq("hash-v1"), eq("worker-1"), any()))
                .thenReturn(Optional.of(lease));
        when(normalizationService.normalize(rawVideoId, "hash-v1"))
                .thenReturn(RawVideoNormalizationService.NormalizationResult.missing(rawVideoId));

        assertThatThrownBy(() -> consumer.onMessage(message(PipelineCommand.normalize(rawVideoId, "hash-v1"))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining(rawVideoId.toString());

        verify(normalizationService).normalize(rawVideoId, "hash-v1");
        verify(pipelineState).release(lease);
        WorkerJobAuditEvent event = captureAuditEvent();
        assertThat(event.jobType()).isEqualTo("queue-consumer");
        assertThat(event.jobName()).isEqualTo("normalization.raw-video");
        assertThat(event.correlationId()).isEqualTo(rawVideoId.toString());
        assertThat(event.status()).isEqualTo("failed");
        assertThat(event.error()).hasMessageContaining("Normalize raw video not found");
    }

    private Message message(PipelineCommand command) throws Exception {
        return new Message(objectMapper.writeValueAsString(command).getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private PipelineLease lease(UUID rawVideoId) {
        Instant now = Instant.parse("2026-06-17T00:00:00Z");
        return new PipelineLease(
                PipelineStep.NORMALIZE_VIDEO,
                rawVideoId,
                "hash-v1",
                "worker-1",
                "token",
                now,
                now.plus(Duration.ofMinutes(10)));
    }

    private WorkerJobAuditEvent captureAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
