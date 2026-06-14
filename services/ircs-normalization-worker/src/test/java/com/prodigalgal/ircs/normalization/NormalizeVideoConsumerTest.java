package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
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

class NormalizeVideoConsumerTest {

    private final NormalizeVideoMessageParser messageParser = new NormalizeVideoMessageParser();
    private final RawVideoNormalizationService normalizationService = org.mockito.Mockito.mock(RawVideoNormalizationService.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);
    private final NormalizeVideoConsumer consumer =
            new NormalizeVideoConsumer(messageParser, normalizationService, auditWriter);

    @Test
    void consumesUuidPayloadAndWritesSucceededAuditEvidence() {
        UUID rawVideoId = UUID.randomUUID();
        when(normalizationService.normalize(rawVideoId))
                .thenReturn(RawVideoNormalizationService.NormalizationResult.ready(rawVideoId));

        consumer.onMessage(message(rawVideoId));

        verify(normalizationService).normalize(rawVideoId);
        WorkerJobAuditEvent event = captureAuditEvent();
        assertThat(event.jobType()).isEqualTo("queue-consumer");
        assertThat(event.jobName()).isEqualTo("normalization.raw-video");
        assertThat(event.correlationId()).isEqualTo(rawVideoId.toString());
        assertThat(event.status()).isEqualTo("succeeded");
        assertThat(event.error()).isNull();
    }

    private Message message(UUID rawVideoId) {
        return new Message(rawVideoId.toString().getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private WorkerJobAuditEvent captureAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
