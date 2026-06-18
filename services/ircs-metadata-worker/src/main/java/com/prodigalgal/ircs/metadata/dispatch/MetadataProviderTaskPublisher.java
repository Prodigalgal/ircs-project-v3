package com.prodigalgal.ircs.metadata.dispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataProviderTaskPublisher {

    private final PipelineValkeyQueue pipelineQueue;
    private final ObjectMapper objectMapper;

    public void publish(ProviderType provider, MetadataSearchContext context) {
        try {
            String payload = objectMapper.writeValueAsString(payloadNode(provider, context));
            pipelineQueue.submitAfterCommit(
                    PipelineTaskTypes.METADATA_PROVIDER,
                    PipelineValkeyQueue.providerTaskId(provider.name(), context.getVideoId(), context.getPipelineVersion()),
                    context.getVideoId(),
                    context.getPipelineVersion(),
                    payload,
                    java.time.Duration.ZERO);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize metadata search context", ex);
        }
    }

    private ObjectNode payloadNode(ProviderType provider, MetadataSearchContext context) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("provider", provider.name());
        JsonNode contextNode = objectMapper.valueToTree(context);
        if (contextNode instanceof ObjectNode objectNode) {
            objectNode.remove("fullTitle");
        }
        root.set("context", contextNode);
        return root;
    }

    public record MetadataProviderTaskPayload(ProviderType provider, MetadataSearchContext context) {
    }
}
