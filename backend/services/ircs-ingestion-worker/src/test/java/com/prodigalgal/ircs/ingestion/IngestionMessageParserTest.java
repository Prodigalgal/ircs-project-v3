package com.prodigalgal.ircs.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class IngestionMessageParserTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .build();
    private final IngestionMessageParser parser = new IngestionMessageParser(objectMapper);

    @Test
    void parsesValidIngestionPayload() throws Exception {
        String json = """
                {
                  "video": {
                    "sourceVid": "codex-source-1",
                    "sourceHash": "codex-source-hash",
                    "dataHash": "codex-data-hash",
                    "title": "Codex Smoke",
                    "publishedAt": "2026-06-03",
                    "playlists": []
                  },
                  "forceIngest": false
                }
                """;

        IngestionItem item = parser.parse(message(json));

        assertEquals("codex-source-hash", item.video().sourceHash());
        assertEquals("Codex Smoke", item.video().title());
    }

    @Test
    void rejectsPayloadWithoutSourceHash() {
        String json = """
                {
                  "video": {
                    "sourceVid": "codex-source-1",
                    "title": "Codex Smoke"
                  },
                  "forceIngest": false
                }
                """;

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> parser.parse(message(json)));
    }

    private Message message(String json) {
        return new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties());
    }
}
