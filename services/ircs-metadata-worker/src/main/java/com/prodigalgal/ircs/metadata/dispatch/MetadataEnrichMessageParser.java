package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
public class MetadataEnrichMessageParser {

    private final PipelineCommandMessageCodec codec;

    public MetadataEnrichMessageParser(PipelineCommandMessageCodec codec) {
        this.codec = codec;
    }

    public PipelineCommand parse(Message message) {
        return codec.decode(message, PipelineStep.ENRICH_METADATA);
    }
}
