package com.prodigalgal.ircs.metadata.dispatch;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetadataProviderTaskPublisherTest {

    @Test
    void publishesProviderFetchAsValkeyTasks() {
        PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
        MetadataProviderTaskPublisher publisher = new MetadataProviderTaskPublisher(pipelineQueue, new ObjectMapper());
        UUID videoId = UUID.randomUUID();
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(videoId)
                .pipelineVersion("hash-v1")
                .title("test")
                .build();

        publisher.publish(ProviderType.DOUBAN, context);
        publisher.publish(ProviderType.TMDB, context);
        publisher.publish(ProviderType.ROTTEN_TOMATOES, context);

        verify(pipelineQueue).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("DOUBAN"),
                eq(videoId),
                eq("hash-v1"),
                contains("\"provider\":\"DOUBAN\""),
                eq(Duration.ZERO));
        verify(pipelineQueue).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("TMDB"),
                eq(videoId),
                eq("hash-v1"),
                contains("\"provider\":\"TMDB\""),
                eq(Duration.ZERO));
        verify(pipelineQueue).submitAfterCommit(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                contains("ROTTEN_TOMATOES"),
                eq(videoId),
                eq("hash-v1"),
                contains("\"provider\":\"ROTTEN_TOMATOES\""),
                eq(Duration.ZERO));
    }
}
