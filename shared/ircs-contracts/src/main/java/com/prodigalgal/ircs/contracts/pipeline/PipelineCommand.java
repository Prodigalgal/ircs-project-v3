package com.prodigalgal.ircs.contracts.pipeline;

import java.util.UUID;

public record PipelineCommand(
        UUID rawVideoId,
        PipelineStep step,
        String pipelineVersion,
        long createdAtEpochMilli
) {
    public PipelineCommand {
        if (rawVideoId == null) {
            throw new IllegalArgumentException("rawVideoId is required");
        }
        if (step == null) {
            throw new IllegalArgumentException("step is required");
        }
        pipelineVersion = normalize(pipelineVersion);
        if (createdAtEpochMilli <= 0) {
            createdAtEpochMilli = System.currentTimeMillis();
        }
    }

    public static PipelineCommand normalize(UUID rawVideoId, String pipelineVersion) {
        return new PipelineCommand(rawVideoId, PipelineStep.NORMALIZE_VIDEO, pipelineVersion, System.currentTimeMillis());
    }

    public static PipelineCommand enrich(UUID rawVideoId, String pipelineVersion) {
        return new PipelineCommand(rawVideoId, PipelineStep.ENRICH_METADATA, pipelineVersion, System.currentTimeMillis());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
