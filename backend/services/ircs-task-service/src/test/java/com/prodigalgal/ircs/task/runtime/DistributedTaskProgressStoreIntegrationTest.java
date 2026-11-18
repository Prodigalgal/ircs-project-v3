package com.prodigalgal.ircs.task.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.contracts.task.TaskDetailDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageDiscoveredMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageFailedMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "[0-9]+")
class DistributedTaskProgressStoreIntegrationTest {

    private final UUID master = UUID.randomUUID();
    private final UUID page = UUID.randomUUID();
    private final Instant time = Instant.now();
    private LettuceConnectionFactory factory;
    private StringRedisTemplate keyValue;
    private DistributedTaskProgressStore progress;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        factory.afterPropertiesSet();
        keyValue = new StringRedisTemplate(factory);
        progress = new DistributedTaskProgressStore(keyValue, 1000, "ircs:test:page-progress:" + master,
                Duration.ofSeconds(10), Duration.ofMinutes(30), Duration.ofMinutes(31));
        assertThat(progress.trySchedulePage(master, page, 1, time)).isEqualTo(1);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) factory.destroy();
    }

    @Test
    void discoveryFinishesAPageWithPreviouslyDeliveredDetailsExactlyOnce() {
        TaskDetailDoneMessage success = detail(true);
        assertThat(progress.recordDetailDone(success)).isEqualTo(1);
        assertThat(progress.recordDetailDone(success)).isZero();
        assertThat(progress.recordDetailDone(detail(false))).isEqualTo(1);
        assertThat(progress.pageProgress(master, page)).isEmpty();

        assertThat(progress.recordPageDiscovered(discovery(2))).isEqualTo(1);
        assertThat(progress.recordPageDiscovered(discovery(0))).isZero();

        assertThat(progress.pageProgress(master, page)).get().isEqualTo(new PageProgressState(1, 5, "COMPLETED_WITH_ERRORS"));
        assertThat(keyValue.opsForHash().entries(TaskHotKeys.pageState(page)))
                .containsEntry("detailScheduled", "2").containsEntry("detailCompleted", "2")
                .containsEntry("detailSucceeded", "1").containsEntry("detailFailed", "1");
        assertThat(keyValue.opsForHash().entries(TaskHotKeys.masterState(master)))
                .containsEntry("pageDiscovered", "1").containsEntry("pageCompleted", "1")
                .containsEntry("detailScheduled", "2").containsEntry("detailCompleted", "2");
    }

    @Test
    void repeatedDiscoveryAndLateFailureCannotEraseInProgressOrCompletedDetails() {
        assertThat(progress.recordPageDiscovered(discovery(2))).isEqualTo(1);
        assertThat(progress.recordDetailDone(detail(true))).isEqualTo(1);
        assertThat(progress.recordPageDiscovered(discovery(2))).isZero();
        assertThat(progress.recordPageFailed(new TaskPageFailedMessage(master, page, 1, "late", "test", time)))
                .isZero();

        assertThat(progress.recordDetailDone(detail(true))).isEqualTo(1);
        assertThat(progress.recordDetailDone(detail(false))).isZero();

        assertThat(progress.pageProgress(master, page)).get().isEqualTo(new PageProgressState(1, 5, "COMPLETED"));
        assertThat(keyValue.opsForHash().entries(TaskHotKeys.masterState(master)))
                .containsEntry("pageCompleted", "1").containsEntry("pageSucceeded", "1")
                .containsEntry("detailCompleted", "2").containsEntry("detailSucceeded", "2");
    }

    private TaskPageDiscoveredMessage discovery(long scheduled) {
        return new TaskPageDiscoveredMessage(master, page, 1, scheduled, 5, 20, "test", time);
    }

    private TaskDetailDoneMessage detail(boolean success) {
        return new TaskDetailDoneMessage(master, page, UUID.randomUUID(), "fixture", success,
                success ? null : "source failure", "test", time);
    }
}
