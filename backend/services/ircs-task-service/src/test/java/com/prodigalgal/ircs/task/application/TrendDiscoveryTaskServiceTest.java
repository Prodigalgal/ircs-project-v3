package com.prodigalgal.ircs.task.application;





import com.prodigalgal.ircs.task.infrastructure.DataSourceSeedCandidate;
import com.prodigalgal.ircs.task.infrastructure.JdbcCollectionTaskRepository;
import com.prodigalgal.ircs.task.infrastructure.TaskDistributedLockRunner;
import com.prodigalgal.ircs.task.dto.TaskCreateRequest;
import com.prodigalgal.ircs.task.domain.CatalogDiscoveryCandidate;
import com.prodigalgal.ircs.task.domain.TaskRuntimeState;
import com.prodigalgal.ircs.task.config.CatalogDiscoveryConfigValues;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class TrendDiscoveryTaskServiceTest {

    private final JdbcCollectionTaskRepository taskRepository = org.mockito.Mockito.mock(JdbcCollectionTaskRepository.class);
    private final TaskCommandService taskCommandService = org.mockito.Mockito.mock(TaskCommandService.class);
    private final CatalogDiscoveryConfigValues config = org.mockito.Mockito.mock(CatalogDiscoveryConfigValues.class);
    private final TrendDiscoveryTaskService service = service();

    @Test
    void createsAndQueuesDiscoveryTaskForEachDatasourceAndKeyword() {
        UUID dataSourceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findDefaultTaskSeedDataSources())
                .thenReturn(List.of(new DataSourceSeedCandidate(dataSourceId, "Codex Source")));
        when(taskRepository.findTaskIdByName(any())).thenReturn(Optional.empty());
        when(taskCommandService.create(any())).thenReturn(taskId);
        when(taskCommandService.startInternal(taskId, false)).thenReturn(true);

        var response = service.schedule(new TrendDiscoveryScheduleRequest(
                List.of("Codex Trend", "Codex Trend", " "),
                1,
                1,
                0,
                false), "corr-trend");

        assertThat(response.requestedKeywords()).isEqualTo(1);
        assertThat(response.dataSourceCount()).isEqualTo(1);
        assertThat(response.createdTasks()).isEqualTo(1);
        assertThat(response.queuedTasks()).isEqualTo(1);
        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().getFirst().status()).isEqualTo("QUEUED");

        ArgumentCaptor<TaskCreateRequest> captor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskCommandService).create(captor.capture());
        TaskCreateRequest request = captor.getValue();
        assertThat(request.dataSourceId()).isEqualTo(dataSourceId);
        assertThat(request.filterKeywords()).isEqualTo("Codex Trend");
        assertThat(request.startPage()).isEqualTo(1);
        assertThat(request.endPage()).isEqualTo(1);
    }

    @Test
    void reusesExistingTaskAndReportsSkippedWhenAlreadyActive() {
        UUID dataSourceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findDefaultTaskSeedDataSources())
                .thenReturn(List.of(new DataSourceSeedCandidate(dataSourceId, "Codex Source")));
        when(taskRepository.findTaskIdByName(any())).thenReturn(Optional.of(taskId));
        when(taskCommandService.startInternal(taskId, false)).thenReturn(false);

        var response = service.schedule(new TrendDiscoveryScheduleRequest(
                List.of("Codex Trend"),
                null,
                null,
                null,
                false), "corr-trend");

        assertThat(response.createdTasks()).isZero();
        assertThat(response.reusedTasks()).isEqualTo(1);
        assertThat(response.queuedTasks()).isZero();
        assertThat(response.tasks().getFirst().status()).isEqualTo("SKIPPED_ACTIVE_OR_DISABLED");
    }

    @Test
    void limitsDiscoveryDataSourcesByRequestAndConfig() {
        UUID firstDataSourceId = UUID.randomUUID();
        UUID secondDataSourceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "maxDataSources", 1);
        when(taskRepository.findDefaultTaskSeedDataSources())
                .thenReturn(List.of(
                        new DataSourceSeedCandidate(firstDataSourceId, "First Source"),
                        new DataSourceSeedCandidate(secondDataSourceId, "Second Source")));
        when(taskRepository.findTaskIdByName(any())).thenReturn(Optional.empty());
        when(taskCommandService.create(any())).thenReturn(taskId);
        when(taskCommandService.startInternal(taskId, false)).thenReturn(true);

        var response = service.schedule(new TrendDiscoveryScheduleRequest(
                List.of("Codex Trend"),
                1,
                1,
                0,
                false,
                2), "corr-trend");

        assertThat(response.requestedKeywords()).isEqualTo(1);
        assertThat(response.dataSourceCount()).isEqualTo(1);
        assertThat(response.createdTasks()).isEqualTo(1);
        assertThat(response.tasks()).hasSize(1);
        assertThat(response.tasks().getFirst().dataSourceId()).isEqualTo(firstDataSourceId);

        ArgumentCaptor<TaskCreateRequest> captor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskCommandService).create(captor.capture());
        assertThat(captor.getValue().dataSourceId()).isEqualTo(firstDataSourceId);
    }

    @Test
    void skipsKeywordDiscoveryForConfiguredListOnlySources() {
        UUID unsupportedId = UUID.randomUUID();
        UUID supportedId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "keywordDisabledDataSources", " 旺旺资源,天涯 ");
        when(taskRepository.findDefaultTaskSeedDataSources()).thenReturn(List.of(
                new DataSourceSeedCandidate(unsupportedId, "旺旺资源"),
                new DataSourceSeedCandidate(UUID.randomUUID(), "天涯"),
                new DataSourceSeedCandidate(supportedId, "Other Source")));
        when(taskRepository.findTaskIdByName(any())).thenReturn(Optional.empty());
        when(taskCommandService.create(any())).thenReturn(taskId);
        when(taskCommandService.startInternal(taskId, false)).thenReturn(true);

        var response = service.schedule(new TrendDiscoveryScheduleRequest(
                List.of("Codex Trend"), 1, 1, 0, false), "corr-trend");

        assertThat(response.dataSourceCount()).isEqualTo(1);
        assertThat(response.tasks()).extracting(task -> task.dataSourceId()).containsExactly(supportedId);
        ArgumentCaptor<TaskCreateRequest> captor = ArgumentCaptor.forClass(TaskCreateRequest.class);
        verify(taskCommandService).create(captor.capture());
        assertThat(captor.getValue().dataSourceId()).isEqualTo(supportedId);
    }

    private TrendDiscoveryTaskService service() {
        TrendDiscoveryTaskService result = new TrendDiscoveryTaskService(
                taskRepository,
                taskCommandService,
                TaskDistributedLockRunner.local(),
                new tools.jackson.databind.ObjectMapper(),
                config);
        ReflectionTestUtils.setField(result, "enabled", true);
        ReflectionTestUtils.setField(result, "maxKeywords", 50);
        ReflectionTestUtils.setField(result, "maxDataSources", 0);
        ReflectionTestUtils.setField(result, "keywordDisabledDataSources", "");
        return result;
    }

    @Test
    void catalogDiscoverySeparatesIdenticalTitlesByWorkAndSeasonWithoutPromotingExpectedIdsToEvidence() {
        UUID sourceId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CatalogDiscoveryCandidate first = new CatalogDiscoveryCandidate(workId, 1, 5, "Same Title", 1, UUID.randomUUID());
        CatalogDiscoveryCandidate second = new CatalogDiscoveryCandidate(workId, 2, 5, "Same Title", 1, UUID.randomUUID());
        when(config.sourceIds()).thenReturn(List.of(sourceId));
        when(taskRepository.findCatalogDiscoveryDataSources(first.requestKey(), 3, List.of(sourceId)))
                .thenReturn(List.of(new DataSourceSeedCandidate(sourceId, "Source")));
        when(taskRepository.findCatalogDiscoveryDataSources(second.requestKey(), 3, List.of(sourceId)))
                .thenReturn(List.of(new DataSourceSeedCandidate(sourceId, "Source")));
        when(taskRepository.findTaskIdByName(any())).thenReturn(Optional.empty());
        when(taskCommandService.create(any())).thenReturn(taskId);
        when(taskCommandService.startCatalog(org.mockito.ArgumentMatchers.eq(taskId),
                org.mockito.ArgumentMatchers.eq(sourceId), any())).thenReturn(true);
        AtomicInteger checks = new AtomicInteger();

        service.scheduleCatalog(first, 50, checks::incrementAndGet);
        service.scheduleCatalog(second, 50, checks::incrementAndGet);

        ArgumentCaptor<TaskCreateRequest> tasks = ArgumentCaptor.forClass(TaskCreateRequest.class);
        org.mockito.Mockito.verify(taskCommandService, org.mockito.Mockito.times(2)).create(tasks.capture());
        assertThat(tasks.getAllValues().get(0).name()).isNotEqualTo(tasks.getAllValues().get(1).name());
        for (TaskCreateRequest request : tasks.getAllValues()) {
            assertThat(request.headers()).contains("ircsCatalogDiscovery", workId.toString())
                    .doesNotContain("tmdbId", "doubanId", "imdbId", "rawMetadata");
            assertThat(request.filterKeywords()).isEqualTo("Same Title");
        }
        assertThat(checks.get()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void catalogDiscoveryRecoversAnAlreadyActiveTaskButDoesNotAcceptADisabledTask() {
        UUID sourceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CatalogDiscoveryCandidate candidate = new CatalogDiscoveryCandidate(
                UUID.randomUUID(), 0, 0, "Example", 2, UUID.randomUUID());
        when(config.sourceIds()).thenReturn(List.of());
        when(taskRepository.findCatalogDiscoveryDataSources(candidate.requestKey(), 1, List.of()))
                .thenReturn(List.of(new DataSourceSeedCandidate(sourceId, "Source")));
        when(taskRepository.findTaskIdByName(any())).thenReturn(Optional.of(taskId));
        when(taskRepository.findRuntimeState(taskId))
                .thenReturn(Optional.of(new TaskRuntimeState(taskId, sourceId, "RUNNING", true, 1, 1, UUID.randomUUID())))
                .thenReturn(Optional.of(new TaskRuntimeState(taskId, sourceId, "RUNNING", false, 1, 1, UUID.randomUUID())));

        assertThat(service.scheduleCatalog(candidate, 1, () -> {}).tasks().getFirst().status()).isEqualTo("ACTIVE");
        assertThat(service.scheduleCatalog(candidate, 1, () -> {}).tasks().getFirst().status())
                .isEqualTo("SKIPPED_ACTIVE_OR_DISABLED");
    }
}
