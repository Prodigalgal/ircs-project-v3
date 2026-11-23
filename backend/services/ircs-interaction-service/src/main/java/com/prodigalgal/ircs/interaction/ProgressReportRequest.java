package com.prodigalgal.ircs.interaction;

import java.util.UUID;

public record ProgressReportRequest(
        UUID catalogEntryId,
        UUID videoId,
        UUID episodeId,
        String episodeName,
        int progress,
        int duration) {
}

