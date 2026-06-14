package com.prodigalgal.ircs.ops;

public record RabbitDlqMessageSample(
        String messageId,
        String correlationId,
        Integer retryCount,
        String disposition,
        String errorClass,
        String errorMessage,
        int bodyBytes,
        String bodyPreview
) {
}
