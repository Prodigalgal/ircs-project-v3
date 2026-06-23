package com.prodigalgal.ircs.ops.infrastructure.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RabbitManagementQueueClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitManagementQueueClient client = new RabbitManagementQueueClient(objectMapper, null);

    @Test
    void parsesNativeRabbitQueueCountersWithReadyUnackedAndTotal() throws Exception {
        List<RabbitManagementQueueSnapshot> snapshots = client.snapshots(objectMapper.readTree("""
                [
                  {
                    "name": "q.task.detail.dlq",
                    "messages": 12,
                    "messages_ready": 7,
                    "messages_unacknowledged": 5,
                    "consumers": 1
                  },
                  {
                    "name": "q.empty.dlq",
                    "messages_ready": 3,
                    "messages_unacknowledged": 2,
                    "consumers": 0
                  }
                ]
                """));

        assertThat(snapshots)
                .extracting(RabbitManagementQueueSnapshot::name)
                .containsExactly("q.task.detail.dlq", "q.empty.dlq");
        assertThat(snapshots.get(0).messagesReady()).isEqualTo(7);
        assertThat(snapshots.get(0).messagesUnacknowledged()).isEqualTo(5);
        assertThat(snapshots.get(0).messagesTotal()).isEqualTo(12);
        assertThat(snapshots.get(1).messagesTotal()).isEqualTo(5);
    }
}
