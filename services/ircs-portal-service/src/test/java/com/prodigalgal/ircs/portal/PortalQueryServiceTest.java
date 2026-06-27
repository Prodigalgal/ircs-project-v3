package com.prodigalgal.ircs.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.cache.CacheRegistry;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortalQueryServiceTest {

    private final JdbcPortalRepository portalRepository = org.mockito.Mockito.mock(JdbcPortalRepository.class);
    private final PortalQueryService service = new PortalQueryService(portalRepository, PortalReadModelCache.disabled());
    private final IrcsRequestPrincipal principal = IrcsRequestPrincipal.publicPrincipal();

    @Test
    void metadataReturnsEmptyListsForMissingData() {
        when(portalRepository.findActiveCategories(principal)).thenReturn(List.of(new CategoryItem("体育", "sports")));
        when(portalRepository.findActiveGenres(principal)).thenReturn(List.of());
        when(portalRepository.findActiveAreas(principal)).thenReturn(null);
        when(portalRepository.findActiveLanguages(principal)).thenReturn(List.of("国语"));
        when(portalRepository.findActiveYears(principal)).thenReturn(null);

        PortalMetadataResponse metadata = service.getMetadata(principal);

        assertTrue(metadata.categories().isEmpty());
        assertTrue(metadata.genres().isEmpty());
        assertTrue(metadata.areas().isEmpty());
        assertEquals(List.of("国语"), metadata.languages());
        assertTrue(metadata.years().isEmpty());
    }

    @Test
    void anonymousMetadataUsesReadModelCache() {
        PortalQueryService cachedService = new PortalQueryService(portalRepository, localCache());
        when(portalRepository.findActiveCategories(principal)).thenReturn(List.of(new CategoryItem("movies", "电影")));
        when(portalRepository.findActiveGenres(principal)).thenReturn(List.of("剧情"));
        when(portalRepository.findActiveAreas(principal)).thenReturn(List.of("中国大陆"));
        when(portalRepository.findActiveLanguages(principal)).thenReturn(List.of("国语"));
        when(portalRepository.findActiveYears(principal)).thenReturn(List.of("2026"));

        PortalMetadataResponse first = cachedService.getMetadata(principal);
        PortalMetadataResponse second = cachedService.getMetadata(principal);

        assertEquals(first, second);
        verify(portalRepository, times(1)).findActiveCategories(principal);
        verify(portalRepository, times(1)).findActiveGenres(principal);
        verify(portalRepository, times(1)).findActiveAreas(principal);
        verify(portalRepository, times(1)).findActiveLanguages(principal);
        verify(portalRepository, times(1)).findActiveYears(principal);
    }

    @Test
    void authenticatedMetadataBypassesPublicReadModelCache() {
        PortalQueryService cachedService = new PortalQueryService(portalRepository, localCache());
        IrcsRequestPrincipal member = memberPrincipal();
        when(portalRepository.findActiveCategories(member)).thenReturn(List.of(new CategoryItem("movies", "电影")));
        when(portalRepository.findActiveGenres(member)).thenReturn(List.of("剧情"));
        when(portalRepository.findActiveAreas(member)).thenReturn(List.of("中国大陆"));
        when(portalRepository.findActiveLanguages(member)).thenReturn(List.of("国语"));
        when(portalRepository.findActiveYears(member)).thenReturn(List.of("2026"));

        cachedService.getMetadata(member);
        cachedService.getMetadata(member);

        verify(portalRepository, times(2)).findActiveCategories(member);
        verify(portalRepository, times(2)).findActiveGenres(member);
        verify(portalRepository, times(2)).findActiveAreas(member);
        verify(portalRepository, times(2)).findActiveLanguages(member);
        verify(portalRepository, times(2)).findActiveYears(member);
    }

    @Test
    void homeReturnsArraysAndOnlyNonEmptySections() {
        PortalMovieCard card = sampleCard();
        when(portalRepository.findSpotlight(principal, 20)).thenReturn(null);
        when(portalRepository.findTrending(principal, 50)).thenReturn(List.of(card));
        when(portalRepository.findActiveCategories(principal)).thenReturn(List.of(
                new CategoryItem("短剧", "short-drama"),
                new CategoryItem("纪录片", "documentary")));
        when(portalRepository.findCategorySection(principal, "short-drama", 20)).thenReturn(List.of());
        when(portalRepository.findCategorySection(principal, "documentary", 20)).thenReturn(List.of(card));

        PortalHomeResponse home = service.getHome(principal);

        assertTrue(home.spotlight().isEmpty());
        assertEquals(List.of(card), home.trending());
        assertEquals(1, home.sections().size());
        assertEquals("documentary", home.sections().getFirst().id());
        assertEquals("纪录片场", home.sections().getFirst().title());
        assertEquals(List.of(card), home.sections().getFirst().movies());
        verify(portalRepository).findCategorySection(principal, "documentary", 20);
    }

    @Test
    void homeOrdersActiveCategoriesAndDropsUnsupportedLegacySlugs() {
        PortalMovieCard card = sampleCard();
        when(portalRepository.findSpotlight(principal, 20)).thenReturn(List.of(card));
        when(portalRepository.findTrending(principal, 50)).thenReturn(List.of(card));
        when(portalRepository.findActiveCategories(principal)).thenReturn(List.of(
                new CategoryItem("纪录片", "documentary"),
                new CategoryItem("短剧", "short-drama"),
                new CategoryItem("实验影像", "experimental")));
        when(portalRepository.findCategorySection(principal, "short-drama", 20)).thenReturn(List.of(card));
        when(portalRepository.findCategorySection(principal, "documentary", 20)).thenReturn(List.of(card));

        PortalHomeResponse home = service.getHome(principal);

        assertEquals(List.of(card), home.spotlight());
        assertEquals(List.of(card), home.trending());
        assertEquals(List.of("short-drama", "documentary"),
                home.sections().stream().map(PortalCategorySection::id).toList());
        verify(portalRepository, times(0)).findCategorySection(principal, "experimental", 20);
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
        PortalQueryService cachedService = new PortalQueryService(portalRepository, localCache());
        UUID movieId = UUID.randomUUID();
        PortalMovieDetailResponse detail = sampleDetail(movieId);
        when(portalRepository.findMovieDetail(principal, movieId)).thenReturn(Optional.of(detail));

        assertEquals(Optional.of(detail), cachedService.getMovieDetail(principal, movieId));
        assertEquals(Optional.of(detail), cachedService.getMovieDetail(principal, movieId));

        verify(portalRepository, times(1)).findMovieDetail(principal, movieId);
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

    private PortalReadModelCache localCache() {
        return new PortalReadModelCache(
                JsonMapper.builder().findAndAddModules().build(),
                new CacheRegistry(),
                null,
                null,
                true,
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5));
    }

    private IrcsRequestPrincipal memberPrincipal() {
        return new IrcsRequestPrincipal(
                "member-1",
                IrcsPermissions.ROLE_MEMBER,
                IrcsPermissions.defaultPermissions(IrcsPermissions.ROLE_MEMBER),
                IrcsPermissions.defaultScopes(IrcsPermissions.ROLE_MEMBER),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                IrcsPermissions.defaultContentVisibility(IrcsPermissions.ROLE_MEMBER));
    }
}
