package com.prodigalgal.ircs.common.pipeline;

public record PipelineValkeyTaskCounts(
        long pending,
        long inflight,
        long dlq
) {
    public long activeBacklog() {
        return pending + inflight;
    }
}
