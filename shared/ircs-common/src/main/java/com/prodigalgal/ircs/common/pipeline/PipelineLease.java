package com.prodigalgal.ircs.common.pipeline;

import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import java.time.Instant;
import java.util.UUID;

public record PipelineLease(
        PipelineStep step,
        UUID rawVideoId,
        String pipelineVersion,
        String ownerId,
        String token,
        Instant acquiredAt,
        Instant expiresAt
) {
}
