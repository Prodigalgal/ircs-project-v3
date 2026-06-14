package com.prodigalgal.ircs.metadata.result;

import java.util.UUID;

public record MetadataResultProcessingResult(UUID videoId, boolean videoFound, String enrichmentStatus) {
}
