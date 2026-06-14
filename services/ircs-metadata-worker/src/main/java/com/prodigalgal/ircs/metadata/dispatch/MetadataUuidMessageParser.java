package com.prodigalgal.ircs.metadata.dispatch;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
public class MetadataUuidMessageParser {

    public UUID parse(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8)
                .replace("\"", "")
                .trim();
        try {
            return UUID.fromString(body);
        } catch (IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid metadata UUID message payload", ex);
        }
    }
}
