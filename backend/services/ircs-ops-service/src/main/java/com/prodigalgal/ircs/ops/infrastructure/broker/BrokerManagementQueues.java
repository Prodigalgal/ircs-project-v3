package com.prodigalgal.ircs.ops.infrastructure.broker;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record BrokerManagementQueues(
        JsonNode raw,
        List<BrokerManagementQueueSnapshot> snapshots
) {
    public BrokerManagementQueues {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    }

    public Map<String, BrokerManagementQueueSnapshot> byName() {
        return snapshots.stream()
                .collect(Collectors.toUnmodifiableMap(
                        BrokerManagementQueueSnapshot::name,
                        Function.identity(),
                        (left, right) -> right));
    }
}
