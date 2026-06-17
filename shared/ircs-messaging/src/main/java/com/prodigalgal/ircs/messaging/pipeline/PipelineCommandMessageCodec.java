package com.prodigalgal.ircs.messaging.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PipelineCommandMessageCodec {

    private final ObjectMapper objectMapper;

    public PipelineCommandMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(PipelineCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize pipeline command", ex);
        }
    }

    public PipelineCommand decode(Message message, PipelineStep expectedStep) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        if (!StringUtils.hasText(body)) {
            throw new AmqpRejectAndDontRequeueException("Pipeline command payload is empty");
        }
        try {
            PipelineCommand command = objectMapper.readValue(body, PipelineCommand.class);
            if (command.step() != expectedStep) {
                throw new AmqpRejectAndDontRequeueException(
                        "Unexpected pipeline step: expected=" + expectedStep + ", actual=" + command.step());
            }
            return command;
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid pipeline command payload", ex);
        }
    }
}
