package com.prodigalgal.ircs.task;

public record TaskItemLogResponse(
        String timestamp,
        String level,
        String sourceVid,
        String message
) {
}
