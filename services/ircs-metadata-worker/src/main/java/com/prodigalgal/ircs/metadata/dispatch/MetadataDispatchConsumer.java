package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.common.pipeline.PipelineLease;
import com.prodigalgal.ircs.common.pipeline.PipelineRedisState;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.metadata.dispatcher", name = "enabled", havingValue = "true")
public class MetadataDispatchConsumer {

    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final Duration COMPLETE_HINT_TTL = Duration.ofHours(6);

    private final MetadataEnrichMessageParser messageParser;
    private final MetadataDispatchService dispatchService;
    private final PipelineRedisState pipelineState;
    private final String workerId;

    public MetadataDispatchConsumer(
            MetadataEnrichMessageParser messageParser,
            MetadataDispatchService dispatchService,
            PipelineRedisState pipelineState,
            @Value("${spring.application.name:ircs-metadata-worker}") String applicationName,
            @Value("${app.metadata.pipeline.worker-id:${APP_METADATA_PIPELINE_WORKER_ID:}}")
                    String configuredWorkerId) {
        this.messageParser = messageParser;
        this.dispatchService = dispatchService;
        this.pipelineState = pipelineState;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
    }

    @RabbitListener(queues = QueueTopic.Names.PROCESS_Q_ENRICH)
    public void onMessage(Message message) {
        PipelineLease lease = null;
        PipelineCommand command = messageParser.parse(message);
        UUID videoId = command.rawVideoId();
        try {
            Optional<PipelineLease> maybeLease = pipelineState.tryLease(
                    PipelineStep.ENRICH_METADATA,
                    videoId,
                    command.pipelineVersion(),
                    workerId,
                    LEASE_TTL);
            if (maybeLease.isEmpty()) {
                log.info("Skipping metadata dispatch because lease is held: rawVideoId={}", videoId);
                return;
            }
            lease = maybeLease.get();

            log.info(
                    "Processing metadata dispatch task: rawVideoId={}, pipelineVersion={}",
                    videoId,
                    command.pipelineVersion());
            MetadataDispatchResult result = dispatchService.dispatch(videoId, command.pipelineVersion());
            if (isTerminal(result.status())) {
                pipelineState.markComplete(
                        PipelineStep.ENRICH_METADATA,
                        videoId,
                        command.pipelineVersion(),
                        COMPLETE_HINT_TTL);
            }
        } finally {
            releaseLease(lease, videoId);
        }
    }

    private void releaseLease(PipelineLease lease, UUID videoId) {
        if (lease == null) {
            return;
        }
        try {
            if (!pipelineState.release(lease)) {
                log.debug("Metadata dispatch consumer lease was not released by owner: rawVideoId={}", videoId);
            }
        } catch (RuntimeException ex) {
            log.warn("Metadata dispatch consumer lease release failed: rawVideoId={}, error={}", videoId, ex.getMessage());
        }
    }

    private boolean isTerminal(String status) {
        return "DISPATCHED".equals(status)
                || "SUCCESS".equals(status)
                || "STALE".equals(status)
                || "PERMANENT_FAILURE".equals(status);
    }
}
