package com.prodigalgal.ircs.ops.infrastructure.broker;

public record BrokerManagementQueueSnapshot(
        String name,
        int messagesReady,
        int messagesUnacknowledged,
        int messagesTotal,
        int consumers
) {
    public static BrokerManagementQueueSnapshot empty(String name) {
        return new BrokerManagementQueueSnapshot(name, 0, 0, 0, 0);
    }
}
