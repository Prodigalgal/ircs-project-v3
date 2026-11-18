package com.prodigalgal.ircs.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.cache.CacheRegistry;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PortalQueryServiceTest {

    private final JdbcPortalRepository portalRepository = mock(JdbcPortalRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PortalQueryService service =
            new PortalQueryService(portalRepository, PortalReadModelCache.disabled(), transactionManager);
    private final IrcsRequestPrincipal principal = IrcsRequestPrincipal.publicPrincipal();

    @BeforeEach
    void setUpTransactions() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void metadataReturnsEmptyListsForMissingData() {
        when(portalRepository.findActiveCategories(principal)).thenReturn(List.of(new CategoryItem("体育", "sports")));
        when(portalRepository.findActiveGenres(principal)).thenReturn(List.of());
        when(portalRepository.findActiveAreas(principal)).thenReturn(null);
        when(portalRepository.findActiveLanguages(principal)).thenReturn(List.of("国语"));
        when(portalRepository.findActiveYears(principal)).thenReturn(null);

        PortalMetadataResponse metadata = service.getMetadata(principal);

        assertEquals(List.of(
                        "movie",
                        "series",
                        "short-drama",
                        "anime",
                        "variety",
                        "documentary",
                        "sports",
                        "news",
                        "education",
                        "music",
                        "other"),
                metadata.categories().stream().map(CategoryItem::slug).toList());
        assertTrue(metadata.genres().isEmpty());
        assertTrue(metadata.areas().isEmpty());
        assertEquals(List.of("国语"), metadata.languages());
        assertTrue(metadata.years().isEmpty());
    }

    @Test
    void anonymousMetadataUsesReadModelCache() {
        PortalQueryService cachedService = serviceWithCache();
        when(portalRepository.findActiveGenres(principal)).thenReturn(List.of("剧情"));
        when(portalRepository.findActiveAreas(principal)).thenReturn(List.of("中国大陆"));
        when(portalRepository.findActiveLanguages(principal)).thenReturn(List.of("国语"));
        when(portalRepository.findActiveYears(principal)).thenReturn(List.of("2026"));

        PortalMetadataResponse first = cachedService.getMetadata(principal);
        PortalMetadataResponse second = cachedService.getMetadata(principal);

        assertEquals(first, second);
        verify(portalRepository, times(1)).findActiveGenres(principal);
        verify(portalRepository, times(1)).findActiveAreas(principal);
        verify(portalRepository, times(1)).findActiveLanguages(principal);
        verify(portalRepository, times(1)).findActiveYears(principal);
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).getTransaction(argThat(TransactionDefinition::isReadOnly));
    }

    @Test
    void authenticatedMetadataBypassesPublicReadModelCache() {
        PortalQueryService cachedService = serviceWithCache();
        IrcsRequestPrincipal member = memberPrincipal();
        when(portalRepository.findActiveGenres(member)).thenReturn(List.of("剧情"));
        when(portalRepository.findActiveAreas(member)).thenReturn(List.of("中国大陆"));
        when(portalRepository.findActiveLanguages(member)).thenReturn(List.of("国语"));
        when(portalRepository.findActiveYears(member)).thenReturn(List.of("2026"));

        cachedService.getMetadata(member);
        cachedService.getMetadata(member);

        verify(portalRepository, times(2)).findActiveGenres(member);
        verify(portalRepository, times(2)).findActiveAreas(member);
        verify(portalRepository, times(2)).findActiveLanguages(member);
        verify(portalRepository, times(2)).findActiveYears(member);
    }

    @Test
    void homeReturnsArraysAndAuthorizedCategorySections() {
        PortalMovieCard card = sampleCard();
        when(portalRepository.findSpotlight(principal, 20)).thenReturn(null);
        when(portalRepository.findTrending(principal, 50)).thenReturn(List.of(card));
        when(portalRepository.findCategorySections(principal, publicCategorySlugs(), 20))
                .thenReturn(Map.of("documentary", List.of(card)));

        PortalHomeResponse home = service.getHome(principal);

        assertTrue(home.spotlight().isEmpty());
        assertEquals(List.of(card), home.trending());
        assertEquals(11, home.sections().size());
        assertEquals("movie", home.sections().getFirst().id());
        assertEquals(List.of(), home.sections().getFirst().movies());
        assertEquals("documentary", home.sections().get(5).id());
        assertEquals("纪录片场", home.sections().get(5).title());
        assertEquals(List.of(card), home.sections().get(5).movies());
        verify(portalRepository).findCategorySections(principal, publicCategorySlugs(), 20);
    }

    @Test
    void homePassesThroughRealUpdatesSourceFailuresAndResourceGaps() {
        Instant observed = Instant.parse("2026-11-16T12:00:00Z");
        PortalMovieCard recent = sampleCard().withRanking(new PortalRankingInfo(List.of("TMDB"), observed, false,
                LocalDate.of(2026, 11, 16), "EPISODE_AIR", 2, 12, false, null, BigDecimal.ZERO));
        PortalTrendSourceState failure = new PortalTrendSourceState("Douban", "chart", "movie", "chart",
                "FAILED", "PROVIDER_UNAVAILABLE", observed, observed.minus(Duration.ofHours(8)), 20, true);
        when(portalRepository.findRecentlyUpdated(principal, 20)).thenReturn(List.of(recent));
        when(portalRepository.findTrendSourceStates()).thenReturn(List.of(failure));

        PortalHomeResponse home = service.getHome(principal);

        assertEquals(List.of(recent), home.recentlyUpdated());
        assertEquals(List.of(failure), home.trendSources());
        assertFalse(home.recentlyUpdated().getFirst().ranking().resourceAvailable());
    }

    @Test
    void anonymousHomeCacheKeepsRankingsAndSourceTimestampsWithoutRepeatingDatabaseReads() {
        PortalQueryService cachedService = serviceWithCache();
        Instant observed = Instant.parse("2026-11-16T12:00:00Z");
        PortalMovieCard card = sampleCard().withRanking(new PortalRankingInfo(List.of("TMDB", "Douban"), observed,
                false, LocalDate.of(2026, 11, 16), "EPISODE_AIR", 2, 12, true, 10, BigDecimal.valueOf(0.1)));
        PortalTrendSourceState source = new PortalTrendSourceState("TMDB", "trending-day", "tv", "day",
                "SUCCESS", null, observed, observed, 20, false);
        when(portalRepository.findTrending(principal, 50)).thenReturn(List.of(card));
        when(portalRepository.findRecentlyUpdated(principal, 20)).thenReturn(List.of(card));
        when(portalRepository.findTrendSourceStates()).thenReturn(List.of(source));

        PortalHomeResponse first = cachedService.getHome(principal);
        PortalHomeResponse cached = cachedService.getHome(principal);

        assertEquals(first, cached);
        assertEquals(card.ranking(), cached.recentlyUpdated().getFirst().ranking());
        assertEquals(observed, cached.trendSources().getFirst().lastSuccessAt());
        verify(portalRepository, times(1)).findTrending(principal, 50);
        verify(portalRepository, times(1)).findRecentlyUpdated(principal, 20);
        verify(portalRepository, times(1)).findTrendSourceStates();
    }

    @Test
    void legacyHomeContractDefaultsNewArraysAndLeavesLegacyCardRankingUnknown() throws Exception {
        PortalHomeResponse legacy = new PortalHomeResponse(List.of(), List.of(sampleCard()), List.of());
        assertTrue(legacy.recentlyUpdated().isEmpty());
        assertTrue(legacy.trendSources().isEmpty());
        assertNull(legacy.trending().getFirst().ranking());

        PortalHomeResponse previousCachedPayload = JsonMapper.builder().findAndAddModules().build().readValue(
                "{\"spotlight\":[],\"trending\":[],\"sections\":[]}", PortalHomeResponse.class);
        assertTrue(previousCachedPayload.recentlyUpdated().isEmpty());
        assertTrue(previousCachedPayload.trendSources().isEmpty());
    }

    @Test
    void homeOrdersActiveCategoriesAndDropsUnsupportedLegacySlugs() {
        PortalMovieCard card = sampleCard();
        when(portalRepository.findSpotlight(principal, 20)).thenReturn(List.of(card));
        when(portalRepository.findTrending(principal, 50)).thenReturn(List.of(card));
        when(portalRepository.findCategorySections(principal, publicCategorySlugs(), 20))
                .thenReturn(Map.of(
                        "short-drama", List.of(card),
                        "documentary", List.of(card)));

        PortalHomeResponse home = service.getHome(principal);

        assertEquals(List.of(card), home.spotlight());
        assertEquals(List.of(card), home.trending());
        assertEquals(List.of(
                        "movie",
                        "series",
                        "short-drama",
                        "anime",
                        "variety",
                        "documentary",
                        "sports",
                        "news",
                        "education",
                        "music",
                        "other"),
                home.sections().stream().map(PortalCategorySection::id).toList());
        verify(portalRepository).findCategorySections(principal, publicCategorySlugs(), 20);
    }

    @Test
    void adultCategoryIsVisibleOnlyWhenPrincipalAllowsIt() {
        IrcsRequestPrincipal adultPrincipal = adultPrincipal();
        when(portalRepository.findActiveGenres(adultPrincipal)).thenReturn(List.of());
        when(portalRepository.findActiveAreas(adultPrincipal)).thenReturn(List.of());
        when(portalRepository.findActiveLanguages(adultPrincipal)).thenReturn(List.of());
        when(portalRepository.findActiveYears(adultPrincipal)).thenReturn(List.of());

        PortalMetadataResponse metadata = service.getMetadata(adultPrincipal);

        assertEquals(12, metadata.categories().size());
        assertEquals("adult", metadata.categories().get(10).slug());
    }

    @Test
    void exploreDelegatesToRepository() {
        PortalMovieCard card = sampleCard();
        PageResponse<PortalMovieCard> page = PageResponse.of(List.of(card), 1, 0, 20);
        when(portalRepository.findExplore(principal, 0, 20, "codex", "movies", "剧情", "中国大陆", "2026", "国语", "rating"))
                .thenReturn(page);

        PageResponse<PortalMovieCard> response =
                service.explore(principal, 0, 20, "codex", "movies", "剧情", "中国大陆", "2026", "国语", "rating");

        assertEquals(page, response);
        verify(portalRepository).findExplore(principal, 0, 20, "codex", "movies", "剧情", "中国大陆", "2026", "国语", "rating");
    }

    @Test
    void anonymousExploreWithKeywordUsesReadModelCache() {
        PortalQueryService cachedService = serviceWithCache();
        PortalMovieCard card = sampleCard();
        PageResponse<PortalMovieCard> page = PageResponse.of(List.of(card), 1, 0, 20);
        when(portalRepository.findExplore(principal, 0, 20, "爱", null, null, null, null, null, null))
                .thenReturn(page);

        assertEquals(page, cachedService.explore(principal, 0, 20, "爱", null, null, null, null, null, null));
        assertEquals(page, cachedService.explore(principal, 0, 20, "爱", null, null, null, null, null, null));

        verify(portalRepository, times(1)).findExplore(principal, 0, 20, "爱", null, null, null, null, null, null);
    }

    @Test
    void sitemapMoviesAlwaysUsesPublicPrincipal() {
        PortalSitemapMovie movie = new PortalSitemapMovie(
                UUID.randomUUID(),
                "Codex Series",
                "https://example.invalid/covers/a.webp",
                null);
        PageResponse<PortalSitemapMovie> page = PageResponse.of(List.of(movie), 1, 0, 1000);
        when(portalRepository.findSitemapMovies(principal, 0, 1000)).thenReturn(page);

        PageResponse<PortalSitemapMovie> response = service.sitemapMovies(0, 1000);

        assertEquals(page, response);
        verify(portalRepository).findSitemapMovies(principal, 0, 1000);
    }

    @Test
    void movieDetailDelegatesToRepository() {
        UUID movieId = UUID.randomUUID();
        PortalMovieDetailResponse detail = sampleDetail(movieId);
        when(portalRepository.findMovieDetail(principal, movieId)).thenReturn(Optional.of(detail));

        Optional<PortalMovieDetailResponse> response = service.getMovieDetail(principal, movieId);

        assertEquals(Optional.of(detail), response);
        verify(portalRepository).findMovieDetail(principal, movieId);
    }

    @Test
    void anonymousMovieDetailUsesReadModelCache() {
        PortalQueryService cachedService = serviceWithCache();
        UUID movieId = UUID.randomUUID();
        PortalMovieDetailResponse detail = sampleDetail(movieId);
        when(portalRepository.findMovieDetail(principal, movieId)).thenReturn(Optional.of(detail));

        assertEquals(Optional.of(detail), cachedService.getMovieDetail(principal, movieId));
        assertEquals(Optional.of(detail), cachedService.getMovieDetail(principal, movieId));

        verify(portalRepository, times(1)).findMovieDetail(principal, movieId);
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).getTransaction(argThat(TransactionDefinition::isReadOnly));
    }

    @Test
    void newlyPublishedDetailBecomesVisibleAfterMissingRequest() {
        PortalQueryService cachedService = serviceWithCache();
        UUID movieId = UUID.randomUUID();
        PortalMovieDetailResponse detail = sampleDetail(movieId);
        when(portalRepository.findMovieDetail(principal, movieId))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(detail));

        assertTrue(cachedService.getMovieDetail(principal, movieId).isEmpty());
        assertEquals(Optional.of(detail), cachedService.getMovieDetail(principal, movieId));
        assertEquals(Optional.of(detail), cachedService.getMovieDetail(principal, movieId));

        verify(portalRepository, times(2)).findMovieDetail(principal, movieId);
    }

    private PortalMovieCard sampleCard() {
        return new PortalMovieCard(
                UUID.randomUUID(),
                "Codex Series",
                null,
                null,
                null,
                null,
                BigDecimal.valueOf(7.5),
                "2026",
                "电视剧",
                null,
                null,
                null,
                "",
                null,
                List.of(),
                null);
    }

    private PortalMovieDetailResponse sampleDetail(UUID movieId) {
        return new PortalMovieDetailResponse(
                movieId,
                "Codex Series",
                null,
                null,
                "简介",
                BigDecimal.valueOf(7.5),
                "2026",
                null,
                null,
                null,
                null,
                "",
                "",
                "电视剧",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private List<String> publicCategorySlugs() {
        return List.of(
                "movie",
                "series",
                "short-drama",
                "anime",
                "variety",
                "documentary",
                "sports",
                "news",
                "education",
                "music",
                "other");
    }

    private PortalQueryService serviceWithCache() {
        PortalReadModelCache cache = new PortalReadModelCache(
                JsonMapper.builder().findAndAddModules().build(),
                new CacheRegistry(),
                null,
                null,
                true,
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5));
        return new PortalQueryService(portalRepository, cache, transactionManager);
    }

    private IrcsRequestPrincipal memberPrincipal() {
        return new IrcsRequestPrincipal(
                "member-1",
                IrcsPermissions.ROLE_MEMBER,
                IrcsPermissions.defaultPermissions(IrcsPermissions.ROLE_MEMBER),
                IrcsPermissions.defaultScopes(IrcsPermissions.ROLE_MEMBER),
                IrcsPermissions.defaultCategoryScope(IrcsPermissions.ROLE_MEMBER),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                IrcsPermissions.defaultContentVisibility(IrcsPermissions.ROLE_MEMBER));
    }

    private IrcsRequestPrincipal adultPrincipal() {
        return new IrcsRequestPrincipal(
                "member-adult",
                IrcsPermissions.ROLE_MEMBER,
                IrcsPermissions.defaultPermissions(IrcsPermissions.ROLE_MEMBER),
                IrcsPermissions.defaultScopes(IrcsPermissions.ROLE_MEMBER),
                IrcsPermissions.memberCategoryScope(true),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                IrcsPermissions.defaultContentVisibility(IrcsPermissions.ROLE_MEMBER));
    }
}
