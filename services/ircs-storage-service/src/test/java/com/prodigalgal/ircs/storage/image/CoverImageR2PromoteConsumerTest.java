package com.prodigalgal.ircs.storage.image;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class CoverImageR2PromoteConsumerTest {

    private final StorageUuidMessageParser messageParser = new StorageUuidMessageParser();
    private final CoverImageR2PromoteService promoteService = org.mockito.Mockito.mock(CoverImageR2PromoteService.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);
    private final CoverImageR2PromoteConsumer consumer =
            new CoverImageR2PromoteConsumer(messageParser, promoteService, auditWriter);

    @Test
    void consumesUuidPayload() {
        UUID imageId = UUID.randomUUID();
        Message message = new Message(imageId.toString().getBytes(StandardCharsets.UTF_8), new MessageProperties());
        when(promoteService.promote(imageId))
                .thenReturn(CoverImageR2PromoteService.CoverImageR2PromoteResult.scheduledResult());

        consumer.onMessage(message);

        verify(promoteService).promote(imageId);
        WorkerJobAuditEvent event = captureAuditEvent();
        org.assertj.core.api.Assertions.assertThat(event.jobName()).isEqualTo("storage.cover-r2-promote");
        org.assertj.core.api.Assertions.assertThat(event.correlationId()).isEqualTo(imageId.toString());
        org.assertj.core.api.Assertions.assertThat(event.status()).isEqualTo("succeeded");
    }

    @Test
    void recordsSkippedAuditWhenPromoteIsNotScheduled() {
        UUID imageId = UUID.randomUUID();
        Message message = new Message(imageId.toString().getBytes(StandardCharsets.UTF_8), new MessageProperties());
        when(promoteService.promote(imageId))
                .thenReturn(CoverImageR2PromoteService.CoverImageR2PromoteResult.skipped("r2-disabled"));

        consumer.onMessage(message);

        WorkerJobAuditEvent event = captureAuditEvent();
        org.assertj.core.api.Assertions.assertThat(event.status()).isEqualTo("skipped");
        org.assertj.core.api.Assertions.assertThat(event.error().getMessage()).isEqualTo("r2-disabled");
    }

    private WorkerJobAuditEvent captureAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
