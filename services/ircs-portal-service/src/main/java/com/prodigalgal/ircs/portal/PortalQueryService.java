package com.prodigalgal.ircs.portal;

import com.prodigalgal.ircs.common.normalization.StandardContentCategoryClassifier;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalQueryService {

    private static final int MOVIE_RAIL_SIZE = 20;
    private static final int MOVIE_TREND_SIZE = 50;
    private static final List<String> CATEGORY_DISPLAY_ORDER = List.of(
            "movie",
            "series",
            "short-drama",
            "anime",
            "variety",
            "documentary");
    private static final Map<String, String> SECTION_TITLES = Map.of(
            "movie", "电影精选",
            "series", "热门剧集",
            "short-drama", "爽剧/微短剧",
            "anime", "动漫番剧",
            "variety", "综艺合集",
            "documentary", "纪录片场");
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "movie", "电影",
            "series", "剧集",
            "short-drama", "短剧",
            "anime", "动漫",
            "variety", "综艺",
            "documentary", "纪录片");

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
                stableCategories(),
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
        List<PortalMovieCard> spotlight = safeList(portalRepository.findSpotlight(principal, MOVIE_RAIL_SIZE));
        List<PortalMovieCard> trending = safeList(portalRepository.findTrending(principal, MOVIE_TREND_SIZE));
        List<PortalCategorySection> sections = new ArrayList<>();

        for (CategoryItem category : stableCategories()) {
            List<PortalMovieCard> movies = portalRepository.findCategorySection(principal, category.slug(), MOVIE_RAIL_SIZE);
            if (movies != null && !movies.isEmpty()) {
                sections.add(new PortalCategorySection(category.slug(), sectionTitle(category), movies));
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

    private List<CategoryItem> stableCategories() {
        return CATEGORY_DISPLAY_ORDER.stream()
                .filter(StandardContentCategoryClassifier::isAllowedCode)
                .map(slug -> new CategoryItem(CATEGORY_NAMES.getOrDefault(slug, slug), slug))
                .toList();
    }

    private String sectionTitle(CategoryItem category) {
        String title = SECTION_TITLES.get(category.slug());
        return title == null || title.isBlank() ? category.name() : title;
    }
}
