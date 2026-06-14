package com.prodigalgal.ircs.content.video;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final SearchSyncOutboxWriter searchSyncOutboxWriter;

    public void publishRawSearch(UUID rawVideoId, SyncOperation operation) {
        searchSyncOutboxWriter.enqueue(rawVideoId, SearchEntityType.RAW_VIDEO, operation);
    }

    public void publishUnifiedSearch(UUID unifiedVideoId, SyncOperation operation) {
        searchSyncOutboxWriter.enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, operation);
    }

    public void publishNormalize(UUID rawVideoId) {
        QueueTopic topic = QueueTopic.NORMALIZE_VIDEO;
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), rawVideoId.toString());
    }

    public void publishEnrich(UUID rawVideoId) {
        QueueTopic topic = QueueTopic.ENRICH_METADATA;
        rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), rawVideoId.toString());
    }
}
