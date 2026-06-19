package com.prodigalgal.ircs.common.work;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.audit.AuditReplicationWorkTypes;
import com.prodigalgal.ircs.common.normalization.LlmCleaningWorkTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.storage.StorageWorkTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkSubmissionTopologyTest {

    @Test
    void allRuntimeWorkTypesHaveSubmissionGates() {
        assertThat(runtimeKeys(SearchSyncWorkTypes.RAW))
                .containsExactly("app.search.sync.enabled", "app.search.work-queue.worker.enabled");
        assertThat(runtimeKeys(SearchSyncWorkTypes.UNIFIED))
                .containsExactly("app.search.sync.enabled", "app.search.work-queue.worker.enabled");
        assertThat(runtimeKeys(AggregationWorkTypes.RAW_VIDEO))
                .containsExactly("app.aggregation.work-queue.worker.enabled");
        assertThat(runtimeKeys(LlmCleaningWorkTypes.RAW_TERM))
                .containsExactly(
                        "app.ai.llm.enabled",
                        "app.normalization.llm-cleaning.work-queue.worker.enabled");
        assertThat(runtimeKeys(StorageWorkTypes.AVATAR_SYNC))
                .containsExactly("app.storage.r2.enabled", "app.storage.r2.work-queue.worker.enabled");
        assertThat(runtimeKeys(StorageWorkTypes.COVER_R2_SYNC))
                .containsExactly("app.storage.r2.enabled", "app.storage.r2.work-queue.worker.enabled");
        assertThat(runtimeKeys(AuditReplicationWorkTypes.ES_REPLICATION))
                .containsExactly("app.search.audit-es-replication.worker.enabled");
    }

    @Test
    void allPipelineWorkTypesHaveSubmissionGates() {
        assertThat(pipelineKeys(PipelineTaskTypes.NORMALIZE_VIDEO))
                .containsExactly("app.normalization.valkey-worker.enabled");
        assertThat(pipelineKeys(PipelineTaskTypes.ENRICH_METADATA))
                .containsExactly("app.metadata.enabled", "app.metadata.valkey-dispatcher.enabled");
        assertThat(pipelineKeys(PipelineTaskTypes.METADATA_PROVIDER))
                .containsExactly("app.metadata.enabled", "app.metadata.valkey-provider-worker.enabled");
    }

    private static List<String> runtimeKeys(String taskType) {
        return SystemConfigWorkSubmissionGate.runtimeFlags(taskType).stream()
                .map(SystemConfigWorkSubmissionGate.ConfigFlag::key)
                .toList();
    }

    private static List<String> pipelineKeys(String taskType) {
        return SystemConfigWorkSubmissionGate.pipelineFlags(taskType).stream()
                .map(SystemConfigWorkSubmissionGate.ConfigFlag::key)
                .toList();
    }
}
