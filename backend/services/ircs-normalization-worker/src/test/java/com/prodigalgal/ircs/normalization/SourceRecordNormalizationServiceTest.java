package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.contracts.normalization.NormalizationMaintenanceRunResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourceRecordNormalizationServiceTest {

    @Mock
    private SourceRecordNormalizationRepository repository;

    @Mock
    private RawSearchSyncPublisher searchSyncPublisher;

    @Mock
    private MetadataEnrichPublisher metadataEnrichPublisher;

    @Mock
    private AggregationWorkPublisher aggregationWorkPublisher;

    @Mock
    private NormalizeVideoPublisher normalizeVideoPublisher;

    @Mock
    private NormalizationConfigValues configValues;

    private SourceRecordNormalizationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new SourceRecordNormalizationService(
                repository,
                new SourceRecordTextNormalizer(
                        objectMapper,
                        new DescriptionMetadataExtractor(),
                        new RawRelationAliasPolicy()),
                searchSyncPublisher,
                metadataEnrichPublisher,
                aggregationWorkPublisher,
                normalizeVideoPublisher,
                objectMapper,
                configValues);
    }

    @Test
    void normalizesSourceRecordAndPublishesSearchSync() {
        UUID id = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new SourceRecordRecord(
                id,
                "PENDING",
                0,
                """
                {
                  "title": "Codex Title 2026 1080p",
                  "year": "2026",
                  "season": 2,
                  "subtitle": "黄金行为",
                  "score": "9.1",
                  "genreNames": ["动作", "科幻"],
                  "language": "国语/英语",
                  "area": "中国大陆, 美国",
                  "actorNames": ["演员甲", "演员乙"],
                  "directorNames": "导演甲/导演乙",
                  "rawTypeId": "movie",
                  "rawTypeName": "电影"
                }
                """,
                "[]",
                "Old",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                dataSourceId,
                "hash-v1")));
        when(repository.markReady(eq(id), any()))
                .thenReturn(new SourceRecordNormalizationRepository.MarkReadyResult());

        SourceRecordNormalizationService.NormalizationResult result = service.normalize(id, "hash-v1");

        assertEquals("READY", result.status());
        ArgumentCaptor<SourceRecordPatch> patch = ArgumentCaptor.forClass(SourceRecordPatch.class);
        verify(repository).markReady(eq(id), patch.capture());
        assertEquals("Codex Title", patch.getValue().title());
        assertEquals("2026", patch.getValue().year());
        assertNull(patch.getValue().season());
        assertEquals("黄金行为", patch.getValue().subtitle());
        assertEquals(Set.of("动作", "科幻"), patch.getValue().rawGenreValues());
        assertEquals(Set.of("国语", "英语"), patch.getValue().rawLanguageValues());
        assertEquals(Set.of("中国大陆", "美国"), patch.getValue().rawAreaValues());
        assertEquals(Set.of("演员甲", "演员乙"), patch.getValue().actorValues());
        assertEquals(Set.of("导演甲", "导演乙"), patch.getValue().directorValues());
        assertEquals(dataSourceId, patch.getValue().rawCategoryDataSourceId());
        assertEquals("movie", patch.getValue().rawCategorySourceCode());
        assertEquals("电影", patch.getValue().rawCategorySourceName());
        verify(searchSyncPublisher).publishIndex(id);
        verify(metadataEnrichPublisher).publish(id, "hash-v1");
        verify(aggregationWorkPublisher).enqueue(id, "hash-v1", "normalization-ready");
    }

    @Test
    void emptyRawMetadataMarksFailedAndDoesNotPublishSearchSync() {
        UUID id = UUID.randomUUID();
        when(configValues.maxRetries()).thenReturn(5);
        when(configValues.backoffBaseSeconds()).thenReturn(60L);
        when(repository.findById(id)).thenReturn(Optional.of(new SourceRecordRecord(
                id,
                "PENDING",
                0,
                "",
                "[]",
                "Title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "hash-v1")));

        SourceRecordNormalizationService.NormalizationResult result = service.normalize(id, "hash-v1");

        assertEquals("FAILED", result.status());
        verify(repository).markFailure(eq(id), eq("FAILED"), eq(1), any());
        verify(searchSyncPublisher, never()).publishIndex(any());
        verify(metadataEnrichPublisher, never()).publish(any(UUID.class), any());
    }

    @Test
    void invalidRawMetadataMarksFailedAndDoesNotPublishSearchSync() {
        UUID id = UUID.randomUUID();
        when(configValues.maxRetries()).thenReturn(5);
        when(configValues.backoffBaseSeconds()).thenReturn(60L);
        when(repository.findById(id)).thenReturn(Optional.of(new SourceRecordRecord(
                id,
                "PENDING",
                0,
                "{invalid",
                "[]",
                "Title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "hash-v1")));

        SourceRecordNormalizationService.NormalizationResult result = service.normalize(id, "hash-v1");

        assertEquals("FAILED", result.status());
        verify(repository).markFailure(eq(id), eq("FAILED"), eq(1), any());
        verify(searchSyncPublisher, never()).publishIndex(any());
        verify(metadataEnrichPublisher, never()).publish(any(UUID.class), any());
    }

    @Test
    void v1DefaultFifthFailureBecomesPermanent() {
        UUID id = UUID.randomUUID();
        when(configValues.maxRetries()).thenReturn(5);
        when(repository.findById(id)).thenReturn(Optional.of(new SourceRecordRecord(
                id,
                "PENDING",
                4,
                "",
                "[]",
                "Title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "hash-v1")));

        SourceRecordNormalizationService.NormalizationResult result = service.normalize(id, "hash-v1");

        assertEquals("PERMANENT_FAILURE", result.status());
        verify(repository).markFailure(eq(id), eq("PERMANENT_FAILURE"), eq(5), eq(null));
        verify(configValues).maxRetries();
    }

    @Test
    void resetAllNormalizationPendingSamplesCountsAndMarksPending() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.sampleSourceRecordIds(3)).thenReturn(List.of(first, second));
        when(repository.countSourceRecords()).thenReturn(12L);
        when(repository.resetAllNormalizationPending()).thenReturn(9);

        NormalizationMaintenanceRunResponse result = service.resetAllNormalizationPending(3);

        assertEquals("sanitize", result.taskName());
        assertEquals(12L, result.sourceRecordCount());
        assertEquals(9L, result.changedRows());
        assertEquals(0L, result.enqueuedRows());
        assertEquals(List.of(first, second), result.sampleSourceRecordIds());
        verify(repository).sampleSourceRecordIds(3);
        verify(repository).countSourceRecords();
        verify(repository).resetAllNormalizationPending();
    }

    @Test
    void resetAllNormalizationPendingCanEnqueueAllSourceRecordsByCursor() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.sampleSourceRecordIds(1)).thenReturn(List.of(first));
        when(repository.countSourceRecords()).thenReturn(2L);
        when(repository.resetAllNormalizationPending()).thenReturn(2);
        when(repository.findNormalizationQueueItems(null, 1))
                .thenReturn(List.of(new SourceRecordNormalizationRepository.SourceRecordQueueItem(first, "hash-1")));
        when(repository.findNormalizationQueueItems(first, 1))
                .thenReturn(List.of(new SourceRecordNormalizationRepository.SourceRecordQueueItem(second, "hash-2")));
        when(repository.findNormalizationQueueItems(second, 1)).thenReturn(List.of());

        NormalizationMaintenanceRunResponse result = service.resetAllNormalizationPending(1, true, 1);

        assertEquals(2L, result.enqueuedRows());
        verify(normalizeVideoPublisher).publishNow(first, "hash-1");
        verify(normalizeVideoPublisher).publishNow(second, "hash-2");
    }
}
