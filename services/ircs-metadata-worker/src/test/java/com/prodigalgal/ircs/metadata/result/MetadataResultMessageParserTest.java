package com.prodigalgal.ircs.metadata.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class MetadataResultMessageParserTest {

    private final MetadataResultMessageParser parser = new MetadataResultMessageParser(new ObjectMapper());

    @Test
    void parsesMetadataResultJson() {
        UUID id = UUID.randomUUID();
        String json = """
                {"videoId":"%s","providerType":"TMDB","success":true,"metadata":{"tmdbId":"123"}}
                """.formatted(id);

        EnrichmentResultMsg result = parser.parse(message(json));

        assertEquals(id, result.getVideoId());
        assertEquals("123", result.getMetadata().getTmdbId());
    }

    @Test
    void rejectsMissingVideoIdWithoutRequeue() {
        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(message("{\"success\":true}")));
    }

    @Test
    void rejectsInvalidJsonWithoutRequeue() {
        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> parser.parse(message("not-json")));
    }

    private Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
