package com.prodigalgal.ircs.ops.queue.dlq.broker;

import java.util.List;

public record DeadLetterActionResponse(
        String queueName,
        String action,
        int requested,
        int affected,
        DeadLetterQueueResponse after,
        List<DeadLetterMessageSample> samples
) {
}
