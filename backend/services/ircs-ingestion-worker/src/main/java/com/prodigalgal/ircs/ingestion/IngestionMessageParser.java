package com.prodigalgal.ircs.ingestion;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class IngestionMessageParser {

    private final ObjectMapper objectMapper;

    public IngestionItem parse(Message message) {
        try {
            IngestionItem item = objectMapper.readValue(message.getBody(), IngestionItem.class);
            validate(item);
            return item;
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (tools.jackson.core.JacksonException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid ingestion payload", ex);
        }
    }

    private void validate(IngestionItem item) {
        if (item == null || item.video() == null) {
            throw new AmqpRejectAndDontRequeueException("Ingestion payload missing video");
        }
        String sourceVid = item.video().sourceVid();
        if (!StringUtils.hasText(sourceVid) || sourceVid.codePointCount(0, sourceVid.length()) > 255) {
            throw new AmqpRejectAndDontRequeueException("Ingestion payload has an invalid upstream record key");
        }
        if (!StringUtils.hasText(item.video().sourceHash())) {
            throw new AmqpRejectAndDontRequeueException("Ingestion payload missing sourceHash");
        }
        String dataHash = item.video().dataHash();
        if (!StringUtils.hasText(dataHash) || dataHash.length() > 64) {
            throw new AmqpRejectAndDontRequeueException("Ingestion payload has an invalid dataHash");
        }
        if (!StringUtils.hasText(item.video().title())) {
            throw new AmqpRejectAndDontRequeueException("Ingestion payload missing title");
        }
    }
}
