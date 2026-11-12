package com.prodigalgal.ircs.ops.queue.dlq.broker;

import java.util.List;

public record DeadLetterQueueResponse(
        String topic,
        String displayName,
        String queueName,
        String sourceQueueName,
        String exchange,
        String routingKey,
        int messagesReady,
        int messagesUnacknowledged,
        int messagesTotal,
        int consumers,
        boolean actionSupported,
        List<DeadLetterMessageSample> samples
) {
}
