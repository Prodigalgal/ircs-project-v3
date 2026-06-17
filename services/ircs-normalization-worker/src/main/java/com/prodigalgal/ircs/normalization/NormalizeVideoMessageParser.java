package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import com.prodigalgal.ircs.messaging.pipeline.PipelineCommandMessageCodec;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
public class NormalizeVideoMessageParser {

    private final PipelineCommandMessageCodec codec;

    public NormalizeVideoMessageParser(PipelineCommandMessageCodec codec) {
        this.codec = codec;
    }

    public PipelineCommand parse(Message message) {
        return codec.decode(message, PipelineStep.NORMALIZE_VIDEO);
    }
}
