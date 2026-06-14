package com.prodigalgal.ircs.search.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SearchSyncConsumerTest {

    private final SearchSyncMessageParser messageParser = new SearchSyncMessageParser(new ObjectMapper());
    private final SearchSyncProcessor syncProcessor = org.mockito.Mockito.mock(SearchSyncProcessor.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);
    private final SearchSyncConsumer consumer = new SearchSyncConsumer(messageParser, syncProcessor, auditWriter);

    @Test
    void writesSucceededAuditForRawSearchSyncMessage() throws Exception {
        UUID entityId = UUID.randomUUID();
        SearchSyncMessage message = new SearchSyncMessage(entityId, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX);

        consumer.onRawMessage(message(message));

        verify(syncProcessor).process(org.mockito.ArgumentMatchers.argThat(actual ->
                entityId.equals(actual.getEntityId())
                        && actual.getEntityType() == SearchEntityType.RAW_VIDEO
                        && actual.getOperation() == SyncOperation.INDEX));
        WorkerJobAuditEvent event = captureAuditEvent();
        assertThat(event.jobType()).isEqualTo("queue-consumer");
        assertThat(event.jobName()).isEqualTo("search.sync.raw");
        assertThat(event.correlationId()).isEqualTo(entityId.toString());
        assertThat(event.status()).isEqualTo("succeeded");
        assertThat(event.error()).isNull();
    }

    @Test
    void writesFailedAuditAndRethrowsProcessorFailure() throws Exception {
        UUID entityId = UUID.randomUUID();
        SearchSyncMessage message = new SearchSyncMessage(entityId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.DELETE);
        RuntimeException failure = new RuntimeException("Elasticsearch rejected document");
        doThrow(failure).when(syncProcessor).process(org.mockito.ArgumentMatchers.any(SearchSyncMessage.class));

        assertThatThrownBy(() -> consumer.onUnifiedMessage(message(message)))
                .isSameAs(failure);

        WorkerJobAuditEvent event = captureAuditEvent();
        assertThat(event.jobName()).isEqualTo("search.sync.unified");
        assertThat(event.correlationId()).isEqualTo(entityId.toString());
        assertThat(event.status()).isEqualTo("failed");
        assertThat(event.error()).hasMessage("search sync processing failed");
        assertThat(event.error()).isNotSameAs(failure);
    }

    private Message message(SearchSyncMessage message) throws Exception {
        byte[] body = new ObjectMapper().writeValueAsBytes(message);
        return new Message(body, new MessageProperties());
    }

    private WorkerJobAuditEvent captureAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
