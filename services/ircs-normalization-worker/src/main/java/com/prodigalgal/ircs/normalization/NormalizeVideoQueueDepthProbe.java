package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class NormalizeVideoQueueDepthProbe {

    private final RabbitTemplate rabbitTemplate;

    OptionalInt pendingMessages() {
        try {
            Integer count = rabbitTemplate.execute(channel ->
                    channel.queueDeclarePassive(QueueTopic.Names.PROCESS_Q_NORM).getMessageCount());
            return count == null ? OptionalInt.empty() : OptionalInt.of(Math.max(0, count));
        } catch (RuntimeException ex) {
            log.warn("Normalization queue depth probe skipped: errorClass={}", ex.getClass().getSimpleName());
            return OptionalInt.empty();
        }
    }
}
