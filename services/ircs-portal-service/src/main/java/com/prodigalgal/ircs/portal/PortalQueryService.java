package com.prodigalgal.ircs.portal;

import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalQueryService {

    private static final int MOVIE_RAIL_SIZE = 20;
    private static final int MOVIE_TREND_SIZE = 50;
    private static final List<String> CORE_CATEGORIES = List.of("电影", "电视剧");
    private static final List<SectionSpec> SECTION_SPECS = List.of(
            new SectionSpec("movies", "电影精选", "电影"),
            new SectionSpec("series", "热门剧集", "电视剧"),
            new SectionSpec("short-drama", "爽剧/微短剧", "短剧"),
            new SectionSpec("anime", "动漫番剧", "动漫"),
            new SectionSpec("variety", "综艺合集", "综艺"));

    private final JdbcPortalRepository portalRepository;
    private final PortalReadModelCache readModelCache;
    public PortalQueryService(JdbcPortalRepository portalRepository, PortalReadModelCache readModelCache) {
        this.portalRepository = portalRepository;
        this.readModelCache = readModelCache;
    }

    @Transactional(readOnly = true)
    public PortalMetadataResponse getMetadata(IrcsRequestPrincipal principal) {
        return readModelCache.metadata(principal, () -> getMetadataUncached(principal));
    }

    private PortalMetadataResponse getMetadataUncached(IrcsRequestPrincipal principal) {
        return new PortalMetadataResponse(
                safeList(portalRepository.findActiveCategories(principal)),
                safeList(portalRepository.findActiveGenres(principal)),
                safeList(portalRepository.findActiveAreas(principal)),
                safeList(portalRepository.findActiveLanguages(principal)),
                safeList(portalRepository.findActiveYears(principal)));
    }

    @Transactional(readOnly = true)
    public PortalHomeResponse getHome(IrcsRequestPrincipal principal) {
        return readModelCache.home(principal, () -> getHomeUncached(principal));
    }

    private PortalHomeResponse getHomeUncached(IrcsRequestPrincipal principal) {
        List<PortalMovieCard> spotlight = safeList(portalRepository.findSpotlight(principal, CORE_CATEGORIES, MOVIE_RAIL_SIZE));
        List<PortalMovieCard> trending = safeList(portalRepository.findTrending(principal, CORE_CATEGORIES, MOVIE_TREND_SIZE));
        List<PortalCategorySection> sections = new ArrayList<>();

        for (SectionSpec spec : SECTION_SPECS) {
            List<PortalMovieCard> movies = portalRepository.findCategorySection(principal, spec.categoryName(), MOVIE_RAIL_SIZE);
            if (movies != null && !movies.isEmpty()) {
                sections.add(new PortalCategorySection(spec.id(), spec.title(), movies));
            }
        }

        return new PortalHomeResponse(spotlight, trending, sections);
    }

    @Transactional(readOnly = true)
    public PageResponse<PortalMovieCard> explore(
            IrcsRequestPrincipal principal,
            int page,
            int size,
            String keyword,
            String type,
            String genre,
            String area,
            String year,
            String language,
            String sort) {
        return readModelCache.explore(
                principal,
                page,
                size,
                keyword,
                type,
                genre,
                area,
                year,
                language,
                sort,
                () -> portalRepository.findExplore(principal, page, size, keyword, type, genre, area, year, language, sort));
    }

    @Transactional(readOnly = true)
    public Optional<PortalMovieDetailResponse> getMovieDetail(IrcsRequestPrincipal principal, UUID id) {
        return readModelCache.detail(principal, id, () -> portalRepository.findMovieDetail(principal, id));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null || values.isEmpty() ? Collections.emptyList() : values;
    }

    private record SectionSpec(String id, String title, String categoryName) {
    }
}
