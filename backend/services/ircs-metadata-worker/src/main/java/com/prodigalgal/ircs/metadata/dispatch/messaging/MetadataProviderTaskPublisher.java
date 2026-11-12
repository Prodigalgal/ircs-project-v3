package com.prodigalgal.ircs.metadata.dispatch.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.ircs.common.pipeline.PipelineRuntimeWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataProviderTaskPublisher {

    private final RuntimeWorkQueue workQueue;
    private final ObjectMapper objectMapper;

    public void publish(ProviderType provider, MetadataSearchContext context) {
        try {
            String payload = objectMapper.writeValueAsString(payloadNode(provider, context));
            workQueue.submitAfterCommit(new RuntimeWorkItemRequest(
                    PipelineRuntimeWorkTypes.METADATA_PROVIDER,
                    PipelineRuntimeWorkTypes.providerTaskId(
                            provider.name(),
                            context.getVideoId(),
                            context.getPipelineVersion()),
                    context.getVideoId().toString(),
                    PipelineRuntimeWorkTypes.normalizeVersion(context.getPipelineVersion()),
                    payload));
        } catch (JacksonException ex) {
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
