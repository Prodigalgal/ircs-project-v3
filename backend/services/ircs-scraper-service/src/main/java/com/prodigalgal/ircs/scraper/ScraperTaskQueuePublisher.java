package com.prodigalgal.ircs.scraper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.contracts.task.TaskDetailDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskDetailMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageDiscoveredMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageFailedMessage;
import lombok.RequiredArgsConstructor;
import com.prodigalgal.ircs.messaging.MessagePublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ScraperTaskQueuePublisher {

    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    void publishDetail(TaskDetailMessage message) {
        publish(QueueTopic.TASK_DETAIL, message);
    }

    void publishPageDiscovered(TaskPageDiscoveredMessage message) {
        publish(QueueTopic.TASK_PAGE_DISCOVERED, message);
    }

    void publishPageFailed(TaskPageFailedMessage message) {
        publish(QueueTopic.TASK_PAGE_FAILED, message);
    }

    void publishDetailDone(TaskDetailDoneMessage message) {
        publish(QueueTopic.TASK_DETAIL_DONE, message);
    }

    private void publish(QueueTopic topic, Object payload) {
        try {
            messagePublisher.publish(
                    topic.exchange(),
                    topic.routingKey(),
                    objectMapper.writeValueAsString(payload));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize scraper task queue payload: " + topic.name(), ex);
        }
    }
}
