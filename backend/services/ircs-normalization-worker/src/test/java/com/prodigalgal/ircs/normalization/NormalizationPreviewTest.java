package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class NormalizationPreviewTest {
    private final RawVideoNormalizationRepository repository = mock(RawVideoNormalizationRepository.class);
    private final RawSearchSyncPublisher search = mock(RawSearchSyncPublisher.class);
    private final MetadataEnrichPublisher metadata = mock(MetadataEnrichPublisher.class);
    private final NormalizeVideoPublisher normalize = mock(NormalizeVideoPublisher.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final RawVideoNormalizationService service = new RawVideoNormalizationService(repository,
            new RawVideoTextNormalizer(mapper, new DescriptionMetadataExtractor(), new RawRelationAliasPolicy()),
            search, metadata, normalize, mapper, mock(NormalizationConfigValues.class));

    @Test
    void previewsUseTheRealNormalizerWithoutAnyPersistenceOrQueueOperation() {
        UUID id = UUID.randomUUID();
        String raw = "{\"title\":\"资源记录(2)\",\"actorNames\":[\"罗予彤＆孙乾钊\"]}";
        when(repository.findById(id)).thenReturn(Optional.of(new RawVideoRecord(id, "READY", 0, raw, "[]",
                "资源记录(2)", null, null, null, null, "2026", null, null, null, null, null, null,
                null, null, null, null, null, "source-hash", "other", null)));
        NormalizationPreviewResult preview = service.preview(List.of(id)).getFirst();
        assertEquals("PREVIEW_ONLY", preview.status());
        assertEquals("source-hash", preview.dataHash());
        assertEquals(64, preview.rawMetadataSha256().length());
        assertEquals(java.util.Set.of("罗予彤", "孙乾钊"), preview.patch().actorValues());
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(search, metadata, normalize);
    }

    @Test
    void rejectsUnboundedOrDuplicateInputsBeforeReadingAndRequiresTheExistingInternalScope() {
        assertThrows(ResponseStatusException.class, () -> service.preview(List.of()));
        UUID id = UUID.randomUUID();
        assertThrows(ResponseStatusException.class, () -> service.preview(List.of(id, id)));
        assertThrows(ResponseStatusException.class, () -> service.preview(java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> UUID.randomUUID()).toList()));
        NormalizationInternalAccessPolicy policy = new NormalizationInternalAccessPolicy();
        ReflectionTestUtils.setField(policy, "configuredToken", "test-only-token");
        ReflectionTestUtils.setField(policy, "requiredScope", "normalization:maintenance");
        NormalizationMaintenanceInternalController controller = new NormalizationMaintenanceInternalController(service, policy);
        assertThrows(ResponseStatusException.class, () -> controller.preview(
                new NormalizationMaintenanceInternalController.PreviewRequest(List.of(id)), "auditor", "wrong", "normalization:maintenance"));
        assertThrows(ResponseStatusException.class, () -> controller.preview(
                new NormalizationMaintenanceInternalController.PreviewRequest(List.of(id)), "auditor", "test-only-token", "wrong-scope"));
        verifyNoInteractions(repository, search, metadata, normalize);
    }
}
