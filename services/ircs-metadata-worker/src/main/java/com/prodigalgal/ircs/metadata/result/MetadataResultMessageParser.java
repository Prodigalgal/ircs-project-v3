package com.prodigalgal.ircs.metadata.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataResultMessageParser {

    private final ObjectMapper objectMapper;

    public EnrichmentResultMsg parse(Message message) {
        try {
            EnrichmentResultMsg result = objectMapper.readValue(message.getBody(), EnrichmentResultMsg.class);
            if (result.getVideoId() == null) {
                throw new AmqpRejectAndDontRequeueException("Metadata result missing videoId");
            }
            return result;
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid metadata result message payload", ex);
        }
    }
}
