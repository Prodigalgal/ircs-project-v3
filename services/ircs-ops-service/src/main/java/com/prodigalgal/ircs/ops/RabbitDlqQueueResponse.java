package com.prodigalgal.ircs.ops;

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
