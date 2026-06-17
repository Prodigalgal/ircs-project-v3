package com.prodigalgal.ircs.metadata.dispatch;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.pipeline.PipelineLease;
import com.prodigalgal.ircs.common.pipeline.PipelineRedisState;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class MetadataDispatchConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetadataEnrichMessageParser messageParser =
            new MetadataEnrichMessageParser(new PipelineCommandMessageCodec(objectMapper));
    private final MetadataDispatchService dispatchService = org.mockito.Mockito.mock(MetadataDispatchService.class);
    private final PipelineRedisState pipelineState = org.mockito.Mockito.mock(PipelineRedisState.class);
    private final MetadataDispatchConsumer consumer =
            new MetadataDispatchConsumer(messageParser, dispatchService, pipelineState, "ircs-metadata-worker", "worker-1");

    @Test
    void dispatchesPipelineCommandAndReleasesLease() throws Exception {
        UUID rawVideoId = UUID.randomUUID();
        PipelineLease lease = lease(rawVideoId);
        when(pipelineState.tryLease(
                        eq(PipelineStep.ENRICH_METADATA),
                        eq(rawVideoId),
                        eq("hash-v1"),
                        eq("worker-1"),
                        eq(Duration.ofMinutes(10))))
                .thenReturn(Optional.of(lease));
        when(dispatchService.dispatch(rawVideoId, "hash-v1"))
                .thenReturn(new MetadataDispatchResult(rawVideoId, true, "DISPATCHED", List.of(ProviderType.TMDB)));

        consumer.onMessage(message(PipelineCommand.enrich(rawVideoId, "hash-v1")));

        verify(dispatchService).dispatch(rawVideoId, "hash-v1");
        verify(pipelineState).markComplete(
                eq(PipelineStep.ENRICH_METADATA),
                eq(rawVideoId),
                eq("hash-v1"),
                eq(Duration.ofHours(6)));
        verify(pipelineState).release(lease);
    }

    private Message message(PipelineCommand command) throws Exception {
        return new Message(objectMapper.writeValueAsString(command).getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private PipelineLease lease(UUID rawVideoId) {
        Instant now = Instant.parse("2026-06-17T00:00:00Z");
        return new PipelineLease(
                PipelineStep.ENRICH_METADATA,
                rawVideoId,
                "hash-v1",
                "worker-1",
                "token",
                now,
                now.plus(Duration.ofMinutes(10)));
    }
}
