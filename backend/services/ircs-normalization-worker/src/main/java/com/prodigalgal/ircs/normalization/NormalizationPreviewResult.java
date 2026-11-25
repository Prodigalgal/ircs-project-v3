package com.prodigalgal.ircs.normalization;

import java.util.UUID;

/** Read-only output. A preview never changes pipeline state or authorizes a write. */
public record NormalizationPreviewResult(
        UUID rawVideoId, String dataHash, String rawMetadataSha256, String status, RawVideoPatch patch) {}
