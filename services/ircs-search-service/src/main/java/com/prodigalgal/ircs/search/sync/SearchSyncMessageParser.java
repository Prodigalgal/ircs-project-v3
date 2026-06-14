package com.prodigalgal.ircs.search.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchSyncMessageParser {

    private final ObjectMapper objectMapper;

    public SearchSyncMessage parse(Message amqpMessage, SearchEntityType defaultType) {
        try {
            SearchSyncMessage message = objectMapper.readValue(amqpMessage.getBody(), SearchSyncMessage.class);
            validate(message);
            return message;
        } catch (IOException ignored) {
            return parseLegacyUuid(amqpMessage, defaultType);
        }
    }

    private void validate(SearchSyncMessage message) {
        if (message == null
                || message.getEntityId() == null
                || message.getEntityType() == null
                || message.getOperation() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid structured search sync message payload");
        }
    }

    private SearchSyncMessage parseLegacyUuid(Message amqpMessage, SearchEntityType defaultType) {
        String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8)
                .replace("\"", "")
                .trim();
        try {
            return new SearchSyncMessage(UUID.fromString(body), defaultType, SyncOperation.INDEX);
        } catch (IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid search sync message payload", ex);
        }
    }
}
