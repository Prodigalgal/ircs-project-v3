package com.prodigalgal.ircs.ops.queue.dlq.rabbit;

public record RabbitDlqQueueResponse(
        String topic,
        String displayName,
        String queueName,
        String sourceQueueName,
        String exchange,
        String routingKey,
        int messagesReady,
        int consumers
) {
}
