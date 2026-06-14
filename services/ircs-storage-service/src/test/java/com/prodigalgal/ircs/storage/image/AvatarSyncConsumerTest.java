package com.prodigalgal.ircs.storage.image;

import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class AvatarSyncConsumerTest {

    private final StorageUuidMessageParser messageParser = new StorageUuidMessageParser();
    private final AvatarSyncService avatarSyncService = org.mockito.Mockito.mock(AvatarSyncService.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);
    private final AvatarSyncConsumer consumer = new AvatarSyncConsumer(messageParser, avatarSyncService, auditWriter);

    @Test
    void consumesUuidPayload() {
        UUID memberId = UUID.randomUUID();
        Message message = new Message(memberId.toString().getBytes(StandardCharsets.UTF_8), new MessageProperties());

        consumer.onMessage(message);

        verify(avatarSyncService).sync(memberId);
        WorkerJobAuditEvent event = captureAuditEvent();
        org.assertj.core.api.Assertions.assertThat(event.jobName()).isEqualTo("storage.avatar-sync");
        org.assertj.core.api.Assertions.assertThat(event.correlationId()).isEqualTo(memberId.toString());
        org.assertj.core.api.Assertions.assertThat(event.status()).isEqualTo("succeeded");
    }

    private WorkerJobAuditEvent captureAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
