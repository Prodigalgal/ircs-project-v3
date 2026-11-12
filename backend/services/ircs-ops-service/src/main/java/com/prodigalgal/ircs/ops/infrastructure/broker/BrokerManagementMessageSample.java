package com.prodigalgal.ircs.ops.infrastructure.broker;

public record BrokerManagementMessageSample(
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
