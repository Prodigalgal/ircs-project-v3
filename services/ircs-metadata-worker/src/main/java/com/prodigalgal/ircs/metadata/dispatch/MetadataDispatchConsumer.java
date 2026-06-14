package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.metadata.dispatcher", name = "enabled", havingValue = "true")
public class MetadataDispatchConsumer {

    private final MetadataUuidMessageParser messageParser;
    private final MetadataDispatchService dispatchService;

    @RabbitListener(queues = QueueTopic.Names.PROCESS_Q_ENRICH)
    public void onMessage(Message message) {
        UUID videoId = messageParser.parse(message);
        log.info("Processing metadata dispatch task: {}", videoId);
        dispatchService.dispatch(videoId);
    }
}
