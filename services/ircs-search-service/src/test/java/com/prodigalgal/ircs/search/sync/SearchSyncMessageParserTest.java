package com.prodigalgal.ircs.search.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SearchSyncMessageParserTest {

    private final SearchSyncMessageParser parser = new SearchSyncMessageParser(new ObjectMapper());

    @Test
    void parsesStructuredSearchSyncMessage() throws Exception {
        UUID id = UUID.randomUUID();
        SearchSyncMessage expected = new SearchSyncMessage(id, SearchEntityType.UNIFIED_VIDEO, SyncOperation.DELETE);
        byte[] body = new ObjectMapper().writeValueAsBytes(expected);

        SearchSyncMessage actual = parser.parse(new Message(body, new MessageProperties()), SearchEntityType.RAW_VIDEO);

        assertEquals(id, actual.getEntityId());
        assertEquals(SearchEntityType.UNIFIED_VIDEO, actual.getEntityType());
        assertEquals(SyncOperation.DELETE, actual.getOperation());
    }

    @Test
    void parsesLegacyUuidMessageAsIndexWithDefaultType() {
        UUID id = UUID.randomUUID();
        byte[] body = ("\"" + id + "\"").getBytes(StandardCharsets.UTF_8);

        SearchSyncMessage actual = parser.parse(new Message(body, new MessageProperties()), SearchEntityType.RAW_VIDEO);

        assertEquals(id, actual.getEntityId());
        assertEquals(SearchEntityType.RAW_VIDEO, actual.getEntityType());
        assertEquals(SyncOperation.INDEX, actual.getOperation());
    }

    @Test
    void rejectsInvalidPayloadWithoutRequeue() {
        byte[] body = "{\\\"entityId\\\":\\\"not-json-after-cli-escaping\\\"}".getBytes(StandardCharsets.UTF_8);

        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(new Message(body, new MessageProperties()), SearchEntityType.RAW_VIDEO));
    }

    @Test
    void rejectsStructuredPayloadWithMissingFieldsWithoutLegacyFallback() {
        byte[] body = "{\"entityId\":null,\"entityType\":\"RAW_VIDEO\",\"operation\":\"INDEX\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(new Message(body, new MessageProperties()), SearchEntityType.RAW_VIDEO));
    }
}
