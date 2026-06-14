package com.prodigalgal.ircs.metadata.result;

import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.metadata.listener", name = "enabled", havingValue = "true")
public class MetadataResultConsumer {

    private final MetadataResultMessageParser messageParser;
    private final MetadataResultCollectorService collectorService;

    @RabbitListener(queues = QueueTopic.Names.PROCESS_Q_RESULT)
    public void onMessage(Message message) {
        EnrichmentResultMsg result = messageParser.parse(message);
        log.info("Processing metadata result: videoId={}, provider={}, success={}",
                result.getVideoId(), result.getProviderType(), result.isSuccess());
        collectorService.process(result);
    }
}
