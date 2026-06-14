package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class NormalizeVideoMessageParserTest {

    private final NormalizeVideoMessageParser parser = new NormalizeVideoMessageParser();

    @Test
    void parsesLegacyUuidPayload() {
        UUID id = UUID.randomUUID();

        UUID actual = parser.parse(message(id.toString()));

        assertEquals(id, actual);
    }

    @Test
    void parsesQuotedUuidPayload() {
        UUID id = UUID.randomUUID();

        UUID actual = parser.parse(message("\"" + id + "\""));

        assertEquals(id, actual);
    }

    @Test
    void rejectsInvalidPayloadWithoutRequeue() {
        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(message("not-a-uuid")));
    }

    private Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
