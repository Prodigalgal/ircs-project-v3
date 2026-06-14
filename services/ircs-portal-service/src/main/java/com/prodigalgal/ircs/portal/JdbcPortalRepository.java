package com.prodigalgal.ircs.portal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class JdbcPortalRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final String CARD_SELECT = """
            SELECT uv.id,
                   uv.title,
                   uv.alias_title,
                   uv.season,
                   uv.subtitle,
                   uv.score,
                   uv.year AS release_year,
                   sc.name AS category_name,
                   CASE
                       WHEN uv.description IS NULL THEN NULL
                       WHEN length(uv.description) > 150 THEN substring(uv.description FROM 1 FOR 150)
                       ELSE uv.description
                   END AS description,
                   uv.total_episodes,
                   uv.duration,
                   uv.remarks,
                   uv.last_trend_at,
                   ci.storage_type AS cover_storage_type,
                   ci.original_url AS cover_original_url,
                   ci.storage_path AS cover_storage_path,
                   sd.domain_value AS cover_source_domain
              FROM unified_videos uv
              LEFT JOIN standard_category sc ON uv.category_id = sc.id
              LEFT JOIN cover_images ci ON uv.cover_image_id = ci.id
              LEFT JOIN source_domains sd ON ci.source_domain_id = sd.id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PortalCoverImageUrlResolver coverImageUrlResolver;
    private final ObjectMapper objectMapper;

    public List<CategoryItem> findActiveCategories(IrcsRequestPrincipal principal) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendScopeFilters(where, params, principal);
        return jdbcTemplate.query(
                """
                SELECT sc.name, sc.slug
                  FROM unified_videos uv
                  JOIN standard_category sc ON uv.category_id = sc.id
                """ + where + """
                 GROUP BY sc.id, sc.name, sc.slug
                 ORDER BY count(uv.id) DESC, sc.name ASC
                """,
                params,
                (rs, rowNum) -> new CategoryItem(rs.getString("name"), rs.getString("slug")));
    }

    public List<String> findActiveGenres(IrcsRequestPrincipal principal) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendScopeFilters(where, params, principal);
        return jdbcTemplate.query("""
                SELECT sg.name
                  FROM unified_videos uv
                  LEFT JOIN standard_category sc ON uv.category_id = sc.id
                  JOIN unified_video_genres uvg ON uv.id = uvg.unified_video_id
                  JOIN standard_genre sg ON uvg.genre_id = sg.id
                """ + where + """
                 GROUP BY sg.name
                 ORDER BY count(uv.id) DESC, sg.name ASC
                """, params, (rs, rowNum) -> rs.getString(1));
    }

    public List<String> findActiveAreas(IrcsRequestPrincipal principal) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendScopeFilters(where, params, principal);
        return jdbcTemplate.query("""
                SELECT sa.name
                  FROM unified_videos uv
                  LEFT JOIN standard_category sc ON uv.category_id = sc.id
                  JOIN unified_video_standard_areas uvsa ON uv.id = uvsa.unified_video_id
                  JOIN standard_areas sa ON uvsa.standard_area_id = sa.id
                """ + where + """
                 GROUP BY sa.name
                 ORDER BY count(uv.id) DESC, sa.name ASC
                """, params, (rs, rowNum) -> rs.getString(1));
    }

    public List<String> findActiveLanguages(IrcsRequestPrincipal principal) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendScopeFilters(where, params, principal);
        return jdbcTemplate.query("""
                SELECT sl.name
                  FROM unified_videos uv
                  LEFT JOIN standard_category sc ON uv.category_id = sc.id
                  JOIN unified_video_standard_languages uvsl ON uv.id = uvsl.unified_video_id
                  JOIN standard_languages sl ON uvsl.standard_language_id = sl.id
                """ + where + """
                 GROUP BY sl.name
                 ORDER BY count(uv.id) DESC, sl.name ASC
                """, params, (rs, rowNum) -> rs.getString(1));
    }

    public List<String> findActiveYears(IrcsRequestPrincipal principal) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("""
                 WHERE uv.year IS NOT NULL
                   AND trim(uv.year) <> ''
                """);
        appendScopeFilters(where, params, principal);
        return jdbcTemplate.query("""
                SELECT uv.year
                  FROM unified_videos uv
                  LEFT JOIN standard_category sc ON uv.category_id = sc.id
                """ + where + """
                 GROUP BY uv.year
                 ORDER BY uv.year DESC
                """, params, (rs, rowNum) -> rs.getString(1));
    }

    public List<PortalMovieCard> findSpotlight(IrcsRequestPrincipal principal, List<String> categoryNames, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("categoryNames", categoryNames);
        params.put("limit", limit);
        StringBuilder where = new StringBuilder(" WHERE sc.name IN (:categoryNames)");
        appendScopeFilters(where, params, principal);
        return enrichCards(jdbcTemplate.query(
                CARD_SELECT + where + """
                 ORDER BY uv.published_at DESC NULLS LAST,
                          uv.updated_at DESC NULLS LAST,
                          uv.id ASC
                 LIMIT :limit
                """,
                params,
                cardMapper()));
    }

    public List<PortalMovieCard> findTrending(IrcsRequestPrincipal principal, List<String> categoryNames, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("categoryNames", categoryNames);
        params.put("limit", limit);
        StringBuilder where = new StringBuilder(" WHERE sc.name IN (:categoryNames)");
        appendScopeFilters(where, params, principal);
        return enrichCards(jdbcTemplate.query(
                CARD_SELECT + where + """
                 ORDER BY uv.last_trend_at DESC NULLS LAST,
                          uv.score DESC NULLS LAST,
                          uv.updated_at DESC NULLS LAST,
                          uv.id ASC
                 LIMIT :limit
                """,
                params,
                cardMapper()));
    }

    public List<PortalMovieCard> findCategorySection(IrcsRequestPrincipal principal, String categoryName, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("categoryName", categoryName);
        params.put("limit", limit);
        StringBuilder where = new StringBuilder(" WHERE sc.name = :categoryName");
        appendScopeFilters(where, params, principal);
        return enrichCards(jdbcTemplate.query(
                CARD_SELECT + where + """
                 ORDER BY uv.published_at DESC NULLS LAST,
                          uv.updated_at DESC NULLS LAST,
                          uv.id ASC
                 LIMIT :limit
                """,
                params,
                cardMapper()));
    }

    public PageResponse<PortalMovieCard> findExplore(
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
        Map<String, Object> params = new HashMap<>();
        String where = exploreWhere(params, principal, keyword, type, genre, area, year, language);
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM unified_videos uv LEFT JOIN standard_category sc ON uv.category_id = sc.id " + where,
                params,
                Long.class);

        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<PortalMovieCard> content = enrichCards(jdbcTemplate.query(
                CARD_SELECT + where + orderBy(sort) + " LIMIT :limit OFFSET :offset",
                params,
                cardMapper()));
        return PageResponse.of(content, total == null ? 0 : total, page, size);
    }

    public Optional<PortalMovieDetailResponse> findMovieDetail(IrcsRequestPrincipal principal, UUID id) {
        DetailBase base;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            StringBuilder where = new StringBuilder(" WHERE uv.id = :id");
            appendScopeFilters(where, params, principal);
            base = jdbcTemplate.queryForObject(
                    """
                    SELECT uv.id,
                           uv.title,
                           uv.alias_title,
                           uv.description,
                           uv.score,
                           uv.year AS release_year,
                           uv.total_episodes,
                           uv.remarks,
                           uv.duration,
                           uv.douban_id,
                           uv.tmdb_id,
                           uv.imdb_id,
                           uv.last_trend_at,
                           sc.name AS category_name,
                           ci.storage_type AS cover_storage_type,
                           ci.original_url AS cover_original_url,
                           ci.storage_path AS cover_storage_path,
                           sd.domain_value AS cover_source_domain
                      FROM unified_videos uv
                      LEFT JOIN standard_category sc ON uv.category_id = sc.id
                      LEFT JOIN cover_images ci ON uv.cover_image_id = ci.id
                      LEFT JOIN source_domains sd ON ci.source_domain_id = sd.id
                    """ + where,
                    params,
                    (rs, rowNum) -> new DetailBase(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            rs.getString("alias_title"),
                            rs.getString("description"),
                            getBigDecimal(rs, "score"),
                            rs.getString("release_year"),
                            rs.getString("total_episodes"),
                            rs.getString("remarks"),
                            rs.getString("duration"),
                            coverImageUrlResolver.resolve(
                                    rs.getString("cover_storage_type"),
                                    rs.getString("cover_original_url"),
                                    rs.getString("cover_storage_path"),
                                    rs.getString("cover_source_domain")),
                            rs.getString("category_name"),
                            rs.getString("douban_id"),
                            rs.getString("tmdb_id"),
                            rs.getString("imdb_id"),
                            getInstant(rs, "last_trend_at")));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }

        return Optional.of(new PortalMovieDetailResponse(
                base.id(),
                base.title(),
                base.aliasTitle(),
                null,
                base.description(),
                base.rating(),
                base.releaseYear(),
                base.totalEpisodes(),
                base.remarks(),
                base.duration(),
                base.posterUrl(),
                String.join(", ", findNames("""
                        SELECT sa.name
                          FROM unified_video_standard_areas uvsa
                          JOIN standard_areas sa ON uvsa.standard_area_id = sa.id
                         WHERE uvsa.unified_video_id = :id
                         ORDER BY sa.name ASC
                        """, id)),
                String.join(", ", findNames("""
                        SELECT sl.name
                          FROM unified_video_standard_languages uvsl
                          JOIN standard_languages sl ON uvsl.standard_language_id = sl.id
                         WHERE uvsl.unified_video_id = :id
                         ORDER BY sl.name ASC
                        """, id)),
                base.categoryName(),
                base.doubanId(),
                base.tmdbId(),
                base.imdbId(),
                base.lastTrendAt(),
                findNames("""
                        SELECT sg.name
                          FROM unified_video_genres uvg
                          JOIN standard_genre sg ON uvg.genre_id = sg.id
                         WHERE uvg.unified_video_id = :id
                         ORDER BY sg.name ASC
                        """, id),
                findCast(id),
                findSources(id),
                findMagnets(id),
                findTags(id),
                List.of()));
    }

    private String exploreWhere(
            Map<String, Object> params,
            IrcsRequestPrincipal principal,
            String keyword,
            String type,
            String genre,
            String area,
            String year,
            String language) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (StringUtils.hasText(keyword)) {
            params.put("keyword", "%" + keyword.toLowerCase() + "%");
            where.append("""
                     AND (
                         lower(uv.title) LIKE :keyword
                         OR lower(coalesce(uv.alias_title, '')) LIKE :keyword
                         OR lower(coalesce(uv.description, '')) LIKE :keyword
                     )
                    """);
        }
        addExactFilter(where, params, "type", type, " AND (sc.slug = :type OR sc.name = :type)");
        addExactFilter(where, params, "year", year, " AND uv.year = :year");
        addExistsFilter(where, params, "genre", genre, """
                 AND EXISTS (
                     SELECT 1
                       FROM unified_video_genres uvg
                       JOIN standard_genre sg ON uvg.genre_id = sg.id
                      WHERE uvg.unified_video_id = uv.id
                        AND sg.name = :genre
                 )
                """);
        addExistsFilter(where, params, "area", area, """
                 AND EXISTS (
                     SELECT 1
                       FROM unified_video_standard_areas uvsa
                       JOIN standard_areas sa ON uvsa.standard_area_id = sa.id
                      WHERE uvsa.unified_video_id = uv.id
                        AND sa.name = :area
                 )
                """);
        addExistsFilter(where, params, "language", language, """
                 AND EXISTS (
                     SELECT 1
                       FROM unified_video_standard_languages uvsl
                       JOIN standard_languages sl ON uvsl.standard_language_id = sl.id
                      WHERE uvsl.unified_video_id = uv.id
                        AND sl.name = :language
                 )
                """);
        appendScopeFilters(where, params, principal);
        return where.toString();
    }

    private void appendScopeFilters(StringBuilder where, Map<String, Object> params, IrcsRequestPrincipal principal) {
        IrcsRequestPrincipal effective = principal == null ? IrcsRequestPrincipal.publicPrincipal() : principal;
        if (!effective.hasUnrestrictedVisibility()) {
            params.put("contentVisibility", effective.contentVisibility());
            where.append(" AND coalesce(uv.content_visibility, '")
                    .append(IrcsPermissions.VISIBILITY_PUBLIC)
                    .append("') IN (:contentVisibility)");
        }
        if (!effective.hasUnrestrictedCategories()) {
            params.put("dataCategories", effective.dataCategories());
            where.append("""
                     AND (
                         cast(uv.category_id as varchar) IN (:dataCategories)
                         OR sc.name IN (:dataCategories)
                         OR sc.slug IN (:dataCategories)
                     )
                    """);
        }
        if (!effective.hasUnrestrictedGenres()) {
            params.put("dataGenres", effective.dataGenres());
            where.append("""
                     AND EXISTS (
                         SELECT 1
                           FROM unified_video_genres scope_uvg
                           JOIN standard_genre scope_sg ON scope_uvg.genre_id = scope_sg.id
                          WHERE scope_uvg.unified_video_id = uv.id
                            AND (
                                cast(scope_sg.id as varchar) IN (:dataGenres)
                                OR scope_sg.name IN (:dataGenres)
                            )
                     )
                    """);
        }
        if (!effective.hasUnrestrictedTags()) {
            params.put("dataTags", effective.dataTags());
            where.append("""
                     AND EXISTS (
                         SELECT 1
                           FROM unified_video_tags scope_uvt
                          WHERE scope_uvt.unified_video_id = uv.id
                            AND scope_uvt.tag IN (:dataTags)
                     )
                    """);
        }
    }

    private void addExactFilter(StringBuilder where, Map<String, Object> params, String key, String value, String clause) {
        if (StringUtils.hasText(value)) {
            params.put(key, value.trim());
            where.append(clause);
        }
    }

    private void addExistsFilter(StringBuilder where, Map<String, Object> params, String key, String value, String clause) {
        addExactFilter(where, params, key, value, clause);
    }

    private String orderBy(String sort) {
        String normalized = StringUtils.hasText(sort) ? sort.trim().toLowerCase() : "";
        return switch (normalized) {
            case "rating", "score" -> """
                     ORDER BY uv.score DESC NULLS LAST,
                              uv.updated_at DESC NULLS LAST,
                              uv.id ASC
                    """;
            case "trend", "trending", "hot" -> """
                     ORDER BY uv.last_trend_at DESC NULLS LAST,
                              uv.score DESC NULLS LAST,
                              uv.updated_at DESC NULLS LAST,
                              uv.id ASC
                    """;
            case "latest", "new", "published" -> """
                     ORDER BY uv.published_at DESC NULLS LAST,
                              uv.updated_at DESC NULLS LAST,
                              uv.id ASC
                    """;
            case "year" -> """
                     ORDER BY uv.year DESC NULLS LAST,
                              uv.updated_at DESC NULLS LAST,
                              uv.id ASC
                    """;
            default -> """
                     ORDER BY uv.published_at DESC NULLS LAST,
                              uv.updated_at DESC NULLS LAST,
                              uv.id ASC
                    """;
        };
    }

    private List<String> findNames(String sql, UUID id) {
        return jdbcTemplate.query(sql, Map.of("id", id), (rs, rowNum) -> rs.getString(1));
    }

    private List<PortalMovieDetailResponse.CastMember> findCast(UUID id) {
        List<PortalMovieDetailResponse.CastMember> cast = new ArrayList<>();
        cast.addAll(jdbcTemplate.query(
                """
                SELECT d.id, d.name
                  FROM unified_video_directors uvd
                  JOIN directors d ON uvd.director_id = d.id
                 WHERE uvd.unified_video_id = :id
                 ORDER BY d.name ASC
                """,
                Map.of("id", id),
                (rs, rowNum) -> new PortalMovieDetailResponse.CastMember(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("name"),
                        "导演")));
        cast.addAll(jdbcTemplate.query(
                """
                SELECT a.id, a.name
                  FROM unified_video_actors uva
                  JOIN actors a ON uva.actor_id = a.id
                 WHERE uva.unified_video_id = :id
                 ORDER BY a.name ASC
                """,
                Map.of("id", id),
                (rs, rowNum) -> new PortalMovieDetailResponse.CastMember(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("name"),
                        "演员")));
        return cast;
    }

    private List<PortalMovieDetailResponse.VideoSource> findSources(UUID id) {
        Map<UUID, SourceAccumulator> sources = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT rv.id AS source_id,
                       coalesce(ds.name, rds.name, '未知来源') AS source_name,
                       pl.name AS playlist_name,
                       ep.id AS episode_id,
                       ep.name AS episode_name,
                       ep.url AS episode_url,
                       sd.domain_value AS episode_domain
                  FROM raw_video_unified_video rvu
                  JOIN raw_videos rv ON rvu.raw_video_id = rv.id
                  LEFT JOIN data_sources ds ON rv.data_source_id = ds.id
                  LEFT JOIN raw_category rc ON rv.data_source_category_id = rc.id
                  LEFT JOIN data_sources rds ON rc.data_source_id = rds.id
                  LEFT JOIN playlists pl ON pl.video_id = rv.id
                  LEFT JOIN episodes ep ON ep.playlist_id = pl.id
                  LEFT JOIN source_domains sd ON ep.source_domain_id = sd.id
                 WHERE rvu.unified_video_id = :id
                 ORDER BY source_name ASC, pl.name ASC, ep.name ASC, ep.id ASC
                """,
                Map.of("id", id),
                rs -> {
                    UUID sourceId = rs.getObject("source_id", UUID.class);
                    UUID episodeId = rs.getObject("episode_id", UUID.class);
                    if (sourceId == null || episodeId == null) {
                        return;
                    }
                    String sourceName = fallback(rs.getString("source_name"), "未知来源");
                    SourceAccumulator source = sources.computeIfAbsent(
                            sourceId,
                            ignored -> new SourceAccumulator(sourceId.toString(), sourceName));
                    source.episodes().add(new PortalMovieDetailResponse.Episode(
                            episodeId.toString(),
                            formatEpisodeTitle(rs.getString("playlist_name"), rs.getString("episode_name")),
                            resolveEpisodeUrl(rs.getString("episode_domain"), rs.getString("episode_url"))));
                });
        return sources.values().stream()
                .filter(source -> !source.episodes().isEmpty())
                .map(source -> new PortalMovieDetailResponse.VideoSource(
                        source.id(),
                        source.name(),
                        source.episodes()))
                .toList();
    }

    private List<PortalMovieDetailResponse.MagnetLink> findMagnets(UUID id) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       title,
                       magnet_uri,
                       size_label,
                       published_at,
                       quality,
                       resolution,
                       seeders,
                       provider_code,
                       tags::text AS tags_json
                  FROM magnet_links
                 WHERE unified_video_id = :id
                   AND status = 'APPROVED'
                 ORDER BY seeders DESC NULLS LAST,
                          published_at DESC NULLS LAST,
                          id ASC
                """,
                Map.of("id", id),
                (rs, rowNum) -> new PortalMovieDetailResponse.MagnetLink(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("magnet_uri"),
                        rs.getString("size_label"),
                        getInstant(rs, "published_at"),
                        rs.getString("quality"),
                        rs.getString("resolution"),
                        getInteger(rs, "seeders"),
                        rs.getString("provider_code"),
                        parseTags(rs.getString("tags_json"))));
    }

    private List<String> findTags(UUID id) {
        return findNames("""
                SELECT tag
                  FROM unified_video_tags
                 WHERE unified_video_id = :id
                 ORDER BY tag ASC
                """, id);
    }

    private List<PortalMovieCard> enrichCards(List<PortalMovieCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = cards.stream()
                .map(PortalMovieCard::id)
                .toList();
        Map<UUID, List<String>> genresByVideo = findNamesByVideoId("""
                SELECT uvg.unified_video_id, sg.name
                  FROM unified_video_genres uvg
                  JOIN standard_genre sg ON uvg.genre_id = sg.id
                 WHERE uvg.unified_video_id IN (:ids)
                 ORDER BY sg.name ASC
                """, ids);
        Map<UUID, List<String>> areasByVideo = findNamesByVideoId("""
                SELECT uvsa.unified_video_id, sa.name
                  FROM unified_video_standard_areas uvsa
                  JOIN standard_areas sa ON uvsa.standard_area_id = sa.id
                 WHERE uvsa.unified_video_id IN (:ids)
                 ORDER BY sa.name ASC
                """, ids);

        return cards.stream()
                .map(card -> withMetadata(
                        card,
                        limitGenres(genresByVideo.getOrDefault(card.id(), List.of())),
                        String.join(", ", areasByVideo.getOrDefault(card.id(), List.of()))))
                .toList();
    }

    private List<String> limitGenres(List<String> genres) {
        if (genres.size() <= 3) {
            return genres;
        }
        return genres.subList(0, 3);
    }

    private Map<UUID, List<String>> findNamesByVideoId(String sql, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> namesByVideo = new LinkedHashMap<>();
        jdbcTemplate.query(sql, Map.of("ids", ids), rs -> {
            UUID videoId = rs.getObject(1, UUID.class);
            String name = rs.getString(2);
            if (name != null && !name.isBlank()) {
                namesByVideo.computeIfAbsent(videoId, ignored -> new ArrayList<>()).add(name);
            }
        });
        return namesByVideo;
    }

    private PortalMovieCard withMetadata(PortalMovieCard card, List<String> genres, String area) {
        return new PortalMovieCard(
                card.id(),
                card.title(),
                card.aliasTitle(),
                card.season(),
                card.subtitle(),
                card.posterUrl(),
                card.rating(),
                card.releaseYear(),
                card.categoryName(),
                card.totalEpisodes(),
                card.duration(),
                card.remarks(),
                area,
                card.description(),
                genres,
                card.lastTrendAt());
    }

    private List<String> parseTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, STRING_LIST);
            return tags == null ? List.of() : tags.stream()
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String resolveEpisodeUrl(String domain, String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        if (!StringUtils.hasText(domain)) {
            return url;
        }
        String safeDomain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        String safePath = url.startsWith("/") ? url.substring(1) : url;
        return safeDomain + "/" + safePath;
    }

    private String formatEpisodeTitle(String playlistName, String episodeName) {
        String safeEpisodeName = fallback(episodeName, "未命名剧集");
        if (!StringUtils.hasText(playlistName) || "默认".equals(playlistName)) {
            return safeEpisodeName;
        }
        return "[" + playlistName + "] " + safeEpisodeName;
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private RowMapper<PortalMovieCard> cardMapper() {
        return (rs, rowNum) -> new PortalMovieCard(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                getInteger(rs, "season"),
                rs.getString("subtitle"),
                coverImageUrlResolver.resolve(
                        rs.getString("cover_storage_type"),
                        rs.getString("cover_original_url"),
                        rs.getString("cover_storage_path"),
                        rs.getString("cover_source_domain")),
                getBigDecimal(rs, "score"),
                rs.getString("release_year"),
                rs.getString("category_name"),
                rs.getString("total_episodes"),
                rs.getString("duration"),
                rs.getString("remarks"),
                null,
                rs.getString("description"),
                List.of(),
                getInstant(rs, "last_trend_at"));
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal getBigDecimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record DetailBase(
            UUID id,
            String title,
            String aliasTitle,
            String description,
            BigDecimal rating,
            String releaseYear,
            String totalEpisodes,
            String remarks,
            String duration,
            String posterUrl,
            String categoryName,
            String doubanId,
            String tmdbId,
            String imdbId,
            Instant lastTrendAt) {
    }

    private record SourceAccumulator(
            String id,
            String name,
            List<PortalMovieDetailResponse.Episode> episodes) {

        SourceAccumulator(String id, String name) {
            this(id, name, new ArrayList<>());
        }
    }
}
