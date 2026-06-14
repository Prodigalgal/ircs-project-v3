package com.prodigalgal.ircs.ops;

import java.util.List;

public record RabbitDlqActionResponse(
        String queueName,
        String action,
        int requested,
        int affected,
        RabbitDlqQueueResponse after,
        List<RabbitDlqMessageSample> samples
) {
}
