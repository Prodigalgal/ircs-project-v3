package com.prodigalgal.ircs.normalization;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NormalizeVideoMessageParser {

    public UUID parse(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        if (body.startsWith("\"") && body.endsWith("\"") && body.length() > 1) {
            body = body.substring(1, body.length() - 1);
        }
        if (!StringUtils.hasText(body)) {
            throw new AmqpRejectAndDontRequeueException("Normalize payload is empty");
        }
        try {
            return UUID.fromString(body);
        } catch (IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid normalize payload", ex);
        }
    }
}
