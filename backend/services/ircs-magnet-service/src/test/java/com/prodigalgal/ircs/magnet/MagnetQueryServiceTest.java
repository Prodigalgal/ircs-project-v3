package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.cache.CacheRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class MagnetQueryServiceTest {

    private final JdbcMagnetRepository magnetRepository = org.mockito.Mockito.mock(JdbcMagnetRepository.class);
    private final MagnetSearchExecutor searchExecutor = org.mockito.Mockito.mock(MagnetSearchExecutor.class);
    private final MagnetTrackerConfigValues trackerConfig = mock(MagnetTrackerConfigValues.class);
    private final MagnetQueryService service = new MagnetQueryService(
            magnetRepository,
            MagnetReadModelCache.disabled(),
            null,
            searchExecutor,
            trackerConfig);

    @Test
    void listProvidersCachesReadModel() {
        MagnetProviderSummary provider = provider(UUID.randomUUID());
        when(magnetRepository.listProviders()).thenReturn(List.of(provider));
        MagnetQueryService cachedService = cachedService();

        assertEquals(List.of(provider), cachedService.listProviders());
        assertEquals(List.of(provider), cachedService.listProviders());

        verify(magnetRepository, times(1)).listProviders();
    }

    @Test
    void createProviderEvictsCachedProviderReadModel() {
        MagnetProviderSummary before = provider(UUID.randomUUID());
        MagnetProviderSummary created = provider(UUID.randomUUID());
        when(magnetRepository.listProviders()).thenReturn(List.of(before), List.of(before, created));
        when(magnetRepository.createProvider(any())).thenReturn(created);
        MagnetQueryService cachedService = cachedService();

        assertEquals(List.of(before), cachedService.listProviders());
        assertSame(created, cachedService.createProvider(providerRequest(null)));
        assertEquals(List.of(before, created), cachedService.listProviders());

        verify(magnetRepository, times(2)).listProviders();
    }

    @Test
    void approvedLinksReadCurrentTrackersWithoutReloadingSourceFromDatabase() {
        UUID unifiedVideoId = UUID.randomUUID();
        String sourceTracker = "udp://source.example:1337/announce";
        String previousTracker = "udp://old.example:1337/announce";
        String nextTracker = "udp://new.example:1337/announce";
        MagnetLinkSummary link = approvedLink(unifiedVideoId);
        link = link.withMagnetUri(MagnetUriUtils.ensureTrackers(link.magnetUri(), List.of(sourceTracker)));
        when(magnetRepository.findApprovedLinks(unifiedVideoId)).thenReturn(List.of(link));
        when(trackerConfig.trackers()).thenReturn(List.of(previousTracker), List.of(nextTracker), List.of());
        MagnetQueryService cachedService = cachedService();

        MagnetLinkSummary initial = cachedService.findApprovedLinks(unifiedVideoId).getFirst();
        assertEquals(List.of(sourceTracker, previousTracker), MagnetUriUtils.trackersOf(initial.magnetUri()));
        assertEquals(2, initial.trackerCount());
        MagnetLinkSummary updated = cachedService.findApprovedLinks(unifiedVideoId).getFirst();
        assertEquals(List.of(sourceTracker, nextTracker), MagnetUriUtils.trackersOf(updated.magnetUri()));
        assertEquals(List.of(link), cachedService.findApprovedLinks(unifiedVideoId));

        verify(magnetRepository, times(1)).findApprovedLinks(unifiedVideoId);
    }

    @Test
    void approvingALinkInvalidatesCachedDeliveryAfterCommit() {
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetLinkSummary link = approvedLink(unifiedVideoId);
        MagnetReadModelCache cache = spy(enabledCache());
        MagnetQueryService cachedService = new MagnetQueryService(
                magnetRepository, cache, null, searchExecutor, trackerConfig);
        when(magnetRepository.existsUnifiedVideo(unifiedVideoId)).thenReturn(true);
        when(magnetRepository.findApprovedLinks(unifiedVideoId)).thenReturn(List.of(), List.of(link));
        when(magnetRepository.updateLinkStatus(unifiedVideoId, link.id(), "APPROVED"))
                .thenReturn(java.util.Optional.of(link));

        assertEquals(List.of(), cachedService.findApprovedLinks(unifiedVideoId));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertSame(link, cachedService.updateLinkStatus(
                    unifiedVideoId, link.id(), new MagnetLinkStatusRequest("APPROVED")));
            verify(cache, never()).evictApprovedLinks(unifiedVideoId);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals(List.of(link), cachedService.findApprovedLinks(unifiedVideoId));
        verify(cache).evictApprovedLinks(unifiedVideoId);
    }

    @Test
    void rejectsCreateProviderWithBodyId() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createProvider(providerRequest(UUID.randomUUID())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rejectsUpdateProviderIdMismatch() {
        UUID pathId = UUID.randomUUID();
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateProvider(pathId, providerRequest(UUID.randomUUID())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(magnetRepository, never()).updateProvider(any(), any());
    }

    @Test
    void allowsUpdateProviderWithoutBodyIdLikeV1() {
        UUID pathId = UUID.randomUUID();
        MagnetProviderSummary provider = provider(pathId);
        when(magnetRepository.updateProvider(eq(pathId), any()))
                .thenReturn(java.util.Optional.of(provider));

        assertSame(provider, service.updateProvider(pathId, providerRequest(null)));
    }

    private MagnetProviderRequest providerRequest(UUID id) {
        return new MagnetProviderRequest(
                id, "codex_provider", "Codex Provider", "CODEX_PROVIDER", "https://example.invalid/api", true,
                10, "HIGH", List.of("IMDB"), 1000, 3000, 10000, 20, true, "仅用于 dev smoke。",
                null, null, null, null, null);
    }

    private MagnetProviderSummary provider(UUID id) {
        return new MagnetProviderSummary(
                id, "codex_provider", "Codex Provider", "CODEX_PROVIDER", "https://example.invalid/api", true,
                10, "HIGH", List.of("IMDB"), 1000, 3000, 10000, 20, true, "仅用于 dev smoke。",
                null, null, null, Instant.parse("2026-06-07T00:00:00Z"), Instant.parse("2026-06-07T00:00:00Z"));
    }

    private MagnetQueryService cachedService() {
        return new MagnetQueryService(magnetRepository, enabledCache(), null, searchExecutor, trackerConfig);
    }

    private MagnetReadModelCache enabledCache() {
        return new MagnetReadModelCache(
                JsonMapper.builder().findAndAddModules().build(), new CacheRegistry(), null, null, true,
                Duration.ofMinutes(5), Duration.ofSeconds(60));
    }

    private MagnetLinkSummary approvedLink(UUID unifiedVideoId) {
        return new MagnetLinkSummary(
                UUID.randomUUID(), unifiedVideoId, "codex_provider", "abcdef1234567890abcdef1234567890abcdef12",
                "magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890abcdef12", 0, "Codex Fixture", 1024L, "1 KB",
                Instant.parse("2026-06-07T00:00:00Z"), 10, 1, "WEB", "1080p", "IMDB", "tt1234567", 100,
                "APPROVED", "fixture://magnet-provider/codex_provider", List.of("dev-safe"),
                Instant.parse("2026-06-07T00:00:00Z"), Instant.parse("2026-06-07T00:00:01Z"));
    }
}
