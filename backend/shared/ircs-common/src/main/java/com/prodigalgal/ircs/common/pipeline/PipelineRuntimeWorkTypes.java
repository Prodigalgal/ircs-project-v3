package com.prodigalgal.ircs.common.pipeline;

import java.util.UUID;
import org.springframework.util.StringUtils;

public final class PipelineRuntimeWorkTypes {

    public static final String NORMALIZE_VIDEO = "pipeline.normalize-video";
    public static final String ENRICH_METADATA = "pipeline.enrich-metadata";
    public static final String METADATA_PROVIDER = "pipeline.metadata-provider";

    private static final String EMPTY_VERSION = "_";

    private PipelineRuntimeWorkTypes() {
    }

    public static String normalizeTaskId(UUID sourceRecordId, String pipelineVersion) {
        return taskId(NORMALIZE_VIDEO, sourceRecordId, pipelineVersion);
    }

    public static String enrichTaskId(UUID sourceRecordId, String pipelineVersion) {
        return taskId(ENRICH_METADATA, sourceRecordId, pipelineVersion);
    }

    public static String providerTaskId(String providerType, UUID sourceRecordId, String pipelineVersion) {
        if (!StringUtils.hasText(providerType)) {
            throw new IllegalArgumentException("providerType is required");
        }
        if (sourceRecordId == null) {
            throw new IllegalArgumentException("sourceRecordId is required");
        }
        return METADATA_PROVIDER + ":" + providerType.trim() + ":" + sourceRecordId + ":" + normalizeVersion(pipelineVersion);
    }

    private static String taskId(String taskType, UUID sourceRecordId, String pipelineVersion) {
        if (sourceRecordId == null) {
            throw new IllegalArgumentException("sourceRecordId is required");
        }
        return taskType + ":" + sourceRecordId + ":" + normalizeVersion(pipelineVersion);
    }

    public static String normalizeVersion(String pipelineVersion) {
        return StringUtils.hasText(pipelineVersion) ? pipelineVersion.trim() : EMPTY_VERSION;
    }
}
