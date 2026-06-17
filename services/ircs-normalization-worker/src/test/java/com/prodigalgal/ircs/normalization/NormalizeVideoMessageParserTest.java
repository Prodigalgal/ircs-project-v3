package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class NormalizeVideoMessageParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NormalizeVideoMessageParser parser =
            new NormalizeVideoMessageParser(new PipelineCommandMessageCodec(objectMapper));

    @Test
    void parsesNormalizePipelineCommand() throws Exception {
        UUID id = UUID.randomUUID();

        PipelineCommand actual = parser.parse(message(objectMapper.writeValueAsString(
                PipelineCommand.normalize(id, "hash-v1"))));

        assertEquals(id, actual.rawVideoId());
        assertEquals("hash-v1", actual.pipelineVersion());
    }

    @Test
    void rejectsUnexpectedStepWithoutRequeue() throws Exception {
        UUID id = UUID.randomUUID();

        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(message(objectMapper.writeValueAsString(
                        PipelineCommand.enrich(id, "hash-v1")))));
    }

    @Test
    void rejectsNonJsonUuidPayloadWithoutRequeue() {
        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(message(UUID.randomUUID().toString())));
    }

    private Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
