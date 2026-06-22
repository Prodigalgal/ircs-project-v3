package com.prodigalgal.ircs.content.video.infrastructure;


import com.prodigalgal.ircs.content.video.api.ContentApiException;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.normalizeContentVisibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.content.config.ContentCoverImageUrlResolver;
import com.prodigalgal.ircs.content.video.infrastructure.JdbcVideoAdminRepository.RawVideoSnapshot;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.NamedRef;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.RawAreaRef;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.RawGenreRef;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.RawLanguageRef;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.RawVideoCardResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.RawVideoDetailResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.StandardAreaRef;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.UnifiedVideoCardResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.UnifiedVideoDetailResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
class VideoAdminQueryRepository {

    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ContentCoverImageUrlResolver coverImageUrlResolver;

    Page<RawVideoCardResponse> findRawVideos(
            Pageable pageable,
            String title,
            UUID categoryId,
            String enrichmentStatus,
            String normalizationStatus,
            String year,
            String area,
            BigDecimal minScore,
            Boolean isMissingSlug,
            UUID dataSourceId,
            String sourceCategoryName,
            String genre,
            String language) {
        Pageable safe = sanitize(pageable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safe.getPageSize())
                .addValue("offset", safe.getOffset());
        String where = rawWhere(params, title, categoryId, enrichmentStatus, normalizationStatus, year, area,
                minScore, isMissingSlug, dataSourceId, sourceCategoryName, genre, language);
        Long total = jdbcTemplate.queryForObject(rawCountSql(where), params, Long.class);
        List<RawVideoCardResponse> content = jdbcTemplate.query(
                rawCardSql(where) + orderBy(safe, "rv"),
                params,
                this::mapRawCard);
        return new PageImpl<>(content, safe, total == null ? 0 : total);
    }

    Optional<RawVideoDetailResponse> findRawDetail(UUID id) {
        try {
            RawVideoDetailBase base = jdbcTemplate.queryForObject(
                    """
                    select rv.*, rc.category_id, sc.name as category_name, rc.source_name,
                           ds.name as data_source_name, coalesce(rv.data_source_id, rc.data_source_id) as resolved_data_source_id,
                           ci.original_url as cover_original_url, ci.storage_path as cover_storage_path,
                           ci.storage_type as cover_storage_type, ci.status as cover_status,
                           sd.domain_value as cover_domain,
                           (
                               select rvu.unified_video_id
                               from raw_video_unified_video rvu
                               where rvu.raw_video_id = rv.id
                               limit 1
                           ) as unified_video_id
                    from raw_videos rv
                    left join raw_category rc on rv.data_source_category_id = rc.id
                    left join standard_category sc on rc.category_id = sc.id
                    left join data_sources ds on coalesce(rv.data_source_id, rc.data_source_id) = ds.id
                    left join cover_images ci on rv.cover_image_id = ci.id
                    left join source_domains sd on ci.source_domain_id = sd.id
                    where rv.id = :id
                    """,
                    new MapSqlParameterSource("id", id),
                    this::mapRawDetailBase);
            return Optional.of(new RawVideoDetailResponse(
                    base.id(),
                    base.sourceVid(),
                    base.title(),
                    base.aliasTitle(),
                    base.season(),
                    base.subtitle(),
                    base.description(),
                    resolveCoverUrl(
                            base.coverStorageType(),
                            base.coverOriginalUrl(),
                            base.coverStoragePath(),
                            base.coverDomain()),
                    base.coverOriginalUrl(),
                    base.coverStoragePath(),
                    base.coverStorageType(),
                    base.coverStatus(),
                    base.year(),
                    findRawLanguageNames(id),
                    base.rawLanguageStr(),
                    base.remarks(),
                    base.score(),
                    base.publishedAt(),
                    base.totalEpisodes(),
                    base.duration(),
                    base.doubanId(),
                    base.tmdbId(),
                    base.imdbId(),
                    base.rottenTomatoesId(),
                    base.categoryId(),
                    base.categoryName(),
                    base.dataSourceCategoryId(),
                    base.sourceCategoryName(),
                    base.dataSourceName(),
                    base.dataSourceId(),
                    null,
                    base.unifiedVideoId(),
                    base.enrichmentStatus(),
                    base.normalizationStatus(),
                    base.createdAt(),
                    base.updatedAt(),
                    readStringSet(base.lockedFieldsJson()),
                    base.rawMetadata(),
                    findNamedRefs("video_actors", "video_id", "actor_id", "actors", id),
                    findNamedRefs("video_directors", "video_id", "director_id", "directors", id),
                    findRawGenres(id),
                    findRawLanguages(id),
                    findRawAreas(id),
                    Set.of()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    RawVideoSnapshot rawSnapshot(UUID id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select rv.id, rv.title, rv.alias_title, rv.year, rv.season, rv.data_hash,
                           (
                               select rvu.unified_video_id
                               from raw_video_unified_video rvu
                               where rvu.raw_video_id = rv.id
                               limit 1
                           ) as unified_video_id
                    from raw_videos rv
                    where rv.id = :id
                    """,
                    new MapSqlParameterSource("id", id),
                    (rs, rowNum) -> new RawVideoSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            rs.getString("alias_title"),
                            rs.getString("year"),
                            (Integer) rs.getObject("season"),
                            rs.getString("data_hash"),
                            rs.getObject("unified_video_id", UUID.class)));
        } catch (EmptyResultDataAccessException ex) {
            throw new ContentApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Raw video not found: " + id);
        }
    }

    Page<UnifiedVideoCardResponse> findUnifiedVideos(
            Pageable pageable,
            String title,
            UUID categoryId,
            String year,
            String area,
            BigDecimal minScore,
            Boolean hasDoubanId,
            Boolean hasTmdbId,
            String contentVisibility) {
        Pageable safe = sanitize(pageable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safe.getPageSize())
                .addValue("offset", safe.getOffset());
        String where = unifiedWhere(
                params,
                title,
                categoryId,
                year,
                area,
                minScore,
                hasDoubanId,
                hasTmdbId,
                contentVisibility);
        Long total = jdbcTemplate.queryForObject(unifiedCountSql(where), params, Long.class);
        List<UnifiedVideoCardResponse> content = jdbcTemplate.query(
                unifiedCardSql(where) + orderBy(safe, "uv"),
                params,
                this::mapUnifiedCard);
        return new PageImpl<>(content, safe, total == null ? 0 : total);
    }

    Optional<UnifiedVideoDetailResponse> findUnifiedDetail(UUID id) {
        try {
            UnifiedVideoDetailBase base = jdbcTemplate.queryForObject(
                    """
                    select uv.*, sc.name as category_name,
                           ci.original_url as cover_original_url, ci.storage_path as cover_storage_path,
                           ci.storage_type as cover_storage_type, sd.domain_value as cover_domain
                    from unified_videos uv
                    left join standard_category sc on uv.category_id = sc.id
                    left join cover_images ci on uv.cover_image_id = ci.id
                    left join source_domains sd on ci.source_domain_id = sd.id
                    where uv.id = :id
                    """,
                    new MapSqlParameterSource("id", id),
                    this::mapUnifiedDetailBase);
            return Optional.of(new UnifiedVideoDetailResponse(
                    base.id(),
                    base.title(),
                    base.aliasTitle(),
                    resolveCoverUrl(
                            base.coverStorageType(),
                            base.coverOriginalUrl(),
                            base.coverStoragePath(),
                            base.coverDomain()),
                    base.description(),
                    base.year(),
                    findStandardAreas(id),
                    findUnifiedNames("unified_video_standard_languages", "standard_language_id", "standard_languages", id),
                    base.score(),
                    base.publishedAt(),
                    base.totalEpisodes(),
                    base.duration(),
                    base.remarks(),
                    base.season(),
                    base.subtitle(),
                    base.doubanId(),
                    base.tmdbId(),
                    base.imdbId(),
                    base.rottenTomatoesId(),
                    base.lastTrendAt(),
                    base.categoryId(),
                    base.categoryName(),
                    base.contentVisibility(),
                    findUnifiedNames("unified_video_actors", "actor_id", "actors", id),
                    findUnifiedNames("unified_video_directors", "director_id", "directors", id),
                    findUnifiedNames("unified_video_genres", "genre_id", "standard_genre", id),
                    findUnifiedTags(id),
                    readStringSet(base.lockedFieldsJson()),
                    findRawCardsForUnified(id),
                    base.createdAt(),
                    base.updatedAt()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private String rawWhere(
            MapSqlParameterSource params,
            String title,
            UUID categoryId,
            String enrichmentStatus,
            String normalizationStatus,
            String year,
            String area,
            BigDecimal minScore,
            Boolean isMissingSlug,
            UUID dataSourceId,
            String sourceCategoryName,
            String genre,
            String language) {
        List<String> clauses = new ArrayList<>();
        if (StringUtils.hasText(title)) {
            clauses.add("lower(rv.title) like lower(:title)");
            params.addValue("title", "%" + title.trim() + "%");
        }
        if (categoryId != null) {
            clauses.add("rc.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        if (StringUtils.hasText(enrichmentStatus)) {
            clauses.add("rv.enrichment_status = :enrichmentStatus");
            params.addValue("enrichmentStatus", enrichmentStatus);
        }
        if (StringUtils.hasText(normalizationStatus)) {
            clauses.add("rv.normalization_status = :normalizationStatus");
            params.addValue("normalizationStatus", normalizationStatus);
        }
        if (StringUtils.hasText(year)) {
            clauses.add("rv.year = :year");
            params.addValue("year", year.trim());
        }
        if (minScore != null) {
            clauses.add("rv.score >= :minScore");
            params.addValue("minScore", minScore);
        }
        if (dataSourceId != null) {
            clauses.add("coalesce(rv.data_source_id, rc.data_source_id) = :dataSourceId");
            params.addValue("dataSourceId", dataSourceId);
        }
        if (StringUtils.hasText(sourceCategoryName)) {
            clauses.add("lower(rc.source_name) like lower(:sourceCategoryName)");
            params.addValue("sourceCategoryName", "%" + sourceCategoryName.trim() + "%");
        }
        if (StringUtils.hasText(area)) {
            clauses.add("""
                    exists (
                        select 1 from video_raw_areas vra
                        join raw_areas ra on vra.raw_area_id = ra.id
                        where vra.video_id = rv.id and lower(ra.source_value) like lower(:area)
                    )
                    """);
            params.addValue("area", "%" + area.trim() + "%");
        }
        if (StringUtils.hasText(genre)) {
            clauses.add("""
                    exists (
                        select 1 from video_raw_genres vrg
                        join raw_genres rg on vrg.raw_genre_id = rg.id
                        where vrg.video_id = rv.id and lower(rg.source_value) like lower(:genre)
                    )
                    """);
            params.addValue("genre", "%" + genre.trim() + "%");
        }
        if (StringUtils.hasText(language)) {
            clauses.add("""
                    (lower(rv.raw_language_str) like lower(:language)
                     or exists (
                        select 1 from video_raw_languages vrl
                        join raw_languages rl on vrl.raw_language_id = rl.id
                        where vrl.video_id = rv.id and lower(rl.source_value) like lower(:language)
                     ))
                    """);
            params.addValue("language", "%" + language.trim() + "%");
        }
        if (isMissingSlug != null) {
            clauses.add(Boolean.TRUE.equals(isMissingSlug)
                    ? "(sc.slug is null or sc.slug = '')"
                    : "(sc.slug is not null and sc.slug <> '')");
        }
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private String rawCountSql(String where) {
        return """
                select count(*)
                from raw_videos rv
                left join raw_category rc on rv.data_source_category_id = rc.id
                left join standard_category sc on rc.category_id = sc.id
                """ + where;
    }

    private String rawCardSql(String where) {
        return """
                select rv.id, rv.source_vid, rv.title, rv.alias_title, rv.season, rv.subtitle,
                       rv.year, rv.score, rv.total_episodes, rv.duration,
                       sc.name as category_name, rc.source_name, ds.name as data_source_name,
                       rv.enrichment_status, rv.normalization_status, rv.created_at, rv.updated_at,
                       ci.original_url as cover_original_url, ci.storage_path as cover_storage_path,
                       ci.storage_type as cover_storage_type, sd.domain_value as cover_domain,
                       (
                           select string_agg(ra.source_value, ', ' order by ra.source_value)
                           from video_raw_areas vra
                           join raw_areas ra on vra.raw_area_id = ra.id
                           where vra.video_id = rv.id
                       ) as area
                from raw_videos rv
                left join raw_category rc on rv.data_source_category_id = rc.id
                left join standard_category sc on rc.category_id = sc.id
                left join data_sources ds on coalesce(rv.data_source_id, rc.data_source_id) = ds.id
                left join cover_images ci on rv.cover_image_id = ci.id
                left join source_domains sd on ci.source_domain_id = sd.id
                """ + where;
    }

    private String unifiedWhere(
            MapSqlParameterSource params,
            String title,
            UUID categoryId,
            String year,
            String area,
            BigDecimal minScore,
            Boolean hasDoubanId,
            Boolean hasTmdbId,
            String contentVisibility) {
        List<String> clauses = new ArrayList<>();
        if (StringUtils.hasText(title)) {
            clauses.add("lower(uv.title) like lower(:title)");
            params.addValue("title", "%" + title.trim() + "%");
        }
        if (categoryId != null) {
            clauses.add("uv.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        if (StringUtils.hasText(year)) {
            clauses.add("uv.year = :year");
            params.addValue("year", year.trim());
        }
        if (minScore != null) {
            clauses.add("uv.score >= :minScore");
            params.addValue("minScore", minScore);
        }
        if (StringUtils.hasText(area)) {
            clauses.add("""
                    exists (
                        select 1 from unified_video_standard_areas uva
                        join standard_areas sa on uva.standard_area_id = sa.id
                        where uva.unified_video_id = uv.id and lower(sa.name) like lower(:area)
                    )
                    """);
            params.addValue("area", "%" + area.trim() + "%");
        }
        if (hasDoubanId != null) {
            clauses.add(Boolean.TRUE.equals(hasDoubanId)
                    ? "(uv.douban_id is not null and uv.douban_id <> '')"
                    : "(uv.douban_id is null or uv.douban_id = '')");
        }
        if (hasTmdbId != null) {
            clauses.add(Boolean.TRUE.equals(hasTmdbId)
                    ? "(uv.tmdb_id is not null and uv.tmdb_id <> '')"
                    : "(uv.tmdb_id is null or uv.tmdb_id = '')");
        }
        if (StringUtils.hasText(contentVisibility)) {
            clauses.add("uv.content_visibility = :contentVisibility");
            params.addValue("contentVisibility", normalizeContentVisibility(contentVisibility));
        }
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private String unifiedCountSql(String where) {
        return "select count(*) from unified_videos uv " + where;
    }

    private String unifiedCardSql(String where) {
        return """
                select uv.id, uv.title, uv.alias_title, uv.year, uv.score, uv.remarks,
                       uv.season, uv.subtitle, uv.last_trend_at, uv.created_at, uv.updated_at,
                       uv.douban_id, uv.tmdb_id, uv.imdb_id, uv.content_visibility,
                       sc.name as category_name,
                       ci.original_url as cover_original_url, ci.storage_path as cover_storage_path,
                       ci.storage_type as cover_storage_type, sd.domain_value as cover_domain,
                       (
                           select string_agg(sa.name, ', ' order by sa.name)
                           from unified_video_standard_areas uva
                           join standard_areas sa on uva.standard_area_id = sa.id
                           where uva.unified_video_id = uv.id
                       ) as area,
                       (
                           select count(*)
                           from raw_video_unified_video rvu
                           where rvu.unified_video_id = uv.id
                       ) as source_count
                from unified_videos uv
                left join standard_category sc on uv.category_id = sc.id
                left join cover_images ci on uv.cover_image_id = ci.id
                left join source_domains sd on ci.source_domain_id = sd.id
                """ + where;
    }

    private String orderBy(Pageable pageable, String alias) {
        if (pageable.getSort().isUnsorted()) {
            return " order by " + alias + ".updated_at desc limit :limit offset :offset";
        }
        List<String> allowed = List.of("createdAt", "updatedAt", "title", "year", "score");
        List<String> orders = pageable.getSort().stream()
                .filter(order -> allowed.contains(order.getProperty()))
                .map(order -> alias + "." + toColumn(order.getProperty()) + " " + order.getDirection().name())
                .toList();
        if (orders.isEmpty()) {
            return " order by " + alias + ".updated_at desc limit :limit offset :offset";
        }
        return " order by " + String.join(", ", orders) + " limit :limit offset :offset";
    }

    private String toColumn(String property) {
        return switch (property) {
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            default -> property;
        };
    }

    private RawVideoCardResponse mapRawCard(ResultSet rs, int rowNum) throws SQLException {
        return new RawVideoCardResponse(
                rs.getObject("id", UUID.class),
                rs.getString("source_vid"),
                rs.getString("title"),
                rs.getString("alias_title"),
                (Integer) rs.getObject("season"),
                rs.getString("subtitle"),
                resolveCoverUrl(
                        rs.getString("cover_storage_type"),
                        rs.getString("cover_original_url"),
                        rs.getString("cover_storage_path"),
                        rs.getString("cover_domain")),
                rs.getString("year"),
                rs.getString("area"),
                rs.getBigDecimal("score"),
                rs.getString("total_episodes"),
                rs.getString("duration"),
                rs.getString("category_name"),
                rs.getString("source_name"),
                rs.getString("data_source_name"),
                rs.getString("enrichment_status"),
                rs.getString("normalization_status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private UnifiedVideoCardResponse mapUnifiedCard(ResultSet rs, int rowNum) throws SQLException {
        return new UnifiedVideoCardResponse(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                resolveCoverUrl(
                        rs.getString("cover_storage_type"),
                        rs.getString("cover_original_url"),
                        rs.getString("cover_storage_path"),
                        rs.getString("cover_domain")),
                rs.getString("year"),
                rs.getString("area"),
                rs.getBigDecimal("score"),
                rs.getString("category_name"),
                rs.getString("remarks"),
                (Integer) rs.getObject("season"),
                rs.getString("subtitle"),
                rs.getString("content_visibility"),
                instant(rs, "last_trend_at"),
                rs.getInt("source_count"),
                StringUtils.hasText(rs.getString("imdb_id")),
                StringUtils.hasText(rs.getString("douban_id")),
                StringUtils.hasText(rs.getString("tmdb_id")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private RawVideoDetailBase mapRawDetailBase(ResultSet rs, int rowNum) throws SQLException {
        return new RawVideoDetailBase(
                rs.getObject("id", UUID.class),
                rs.getString("source_vid"),
                rs.getString("title"),
                rs.getString("alias_title"),
                (Integer) rs.getObject("season"),
                rs.getString("subtitle"),
                rs.getString("description"),
                rs.getString("cover_original_url"),
                rs.getString("cover_storage_path"),
                rs.getString("cover_storage_type"),
                rs.getString("cover_status"),
                rs.getString("cover_domain"),
                rs.getString("year"),
                rs.getString("raw_language_str"),
                rs.getString("remarks"),
                rs.getBigDecimal("score"),
                rs.getDate("published_at") == null ? null : rs.getDate("published_at").toLocalDate(),
                rs.getString("total_episodes"),
                rs.getString("duration"),
                rs.getString("douban_id"),
                rs.getString("tmdb_id"),
                rs.getString("imdb_id"),
                rs.getString("rotten_tomatoes_id"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getObject("data_source_category_id", UUID.class),
                rs.getString("source_name"),
                rs.getString("data_source_name"),
                rs.getObject("resolved_data_source_id", UUID.class),
                rs.getObject("unified_video_id", UUID.class),
                rs.getString("enrichment_status"),
                rs.getString("normalization_status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getString("locked_fields"),
                rs.getString("raw_metadata"));
    }

    private UnifiedVideoDetailBase mapUnifiedDetailBase(ResultSet rs, int rowNum) throws SQLException {
        return new UnifiedVideoDetailBase(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                rs.getString("cover_original_url"),
                rs.getString("cover_storage_path"),
                rs.getString("cover_storage_type"),
                rs.getString("cover_domain"),
                rs.getString("description"),
                rs.getString("year"),
                rs.getBigDecimal("score"),
                rs.getDate("published_at") == null ? null : rs.getDate("published_at").toLocalDate(),
                rs.getString("total_episodes"),
                rs.getString("duration"),
                rs.getString("remarks"),
                (Integer) rs.getObject("season"),
                rs.getString("subtitle"),
                rs.getString("douban_id"),
                rs.getString("tmdb_id"),
                rs.getString("imdb_id"),
                rs.getString("rotten_tomatoes_id"),
                instant(rs, "last_trend_at"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getString("content_visibility"),
                rs.getString("locked_fields"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private List<RawVideoCardResponse> findRawCardsForUnified(UUID unifiedVideoId) {
        return jdbcTemplate.query(
                rawCardSql(" where exists (select 1 from raw_video_unified_video rvu where rvu.raw_video_id = rv.id and rvu.unified_video_id = :id)")
                        + " order by rv.updated_at desc",
                new MapSqlParameterSource("id", unifiedVideoId),
                this::mapRawCard);
    }

    private Set<NamedRef> findNamedRefs(String joinTable, String ownerColumn, String relatedColumn, String relatedTable, UUID ownerId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                select t.id, t.name, t.created_at, t.updated_at
                from %s jt
                join %s t on jt.%s = t.id
                where jt.%s = :ownerId
                order by t.name
                """.formatted(joinTable, relatedTable, relatedColumn, ownerColumn),
                new MapSqlParameterSource("ownerId", ownerId),
                (rs, rowNum) -> new NamedRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"))));
    }

    private Set<String> findUnifiedNames(String joinTable, String relatedColumn, String relatedTable, UUID ownerId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select t.name
                from %s jt
                join %s t on jt.%s = t.id
                where jt.unified_video_id = :ownerId
                order by t.name
                """.formatted(joinTable, relatedTable, relatedColumn),
                new MapSqlParameterSource("ownerId", ownerId),
                String.class));
    }

    private Set<String> findUnifiedTags(UUID unifiedVideoId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select tag
                  from unified_video_tags
                 where unified_video_id = :id
                 order by tag
                """,
                new MapSqlParameterSource("id", unifiedVideoId),
                String.class));
    }

    private Set<String> findRawLanguageNames(UUID rawVideoId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                select coalesce(sl.name, rl.source_value)
                from video_raw_languages vrl
                join raw_languages rl on vrl.raw_language_id = rl.id
                left join standard_languages sl on rl.standard_language_id = sl.id
                where vrl.video_id = :id
                order by coalesce(sl.name, rl.source_value)
                """,
                new MapSqlParameterSource("id", rawVideoId),
                String.class));
    }

    private Set<RawGenreRef> findRawGenres(UUID rawVideoId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                select rg.id, rg.source_value, rg.standard_genre_id, sg.name as standard_genre_name
                from video_raw_genres vrg
                join raw_genres rg on vrg.raw_genre_id = rg.id
                left join standard_genre sg on rg.standard_genre_id = sg.id
                where vrg.video_id = :id
                order by rg.source_value
                """,
                new MapSqlParameterSource("id", rawVideoId),
                (rs, rowNum) -> new RawGenreRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("source_value"),
                        rs.getObject("standard_genre_id", UUID.class),
                        rs.getString("standard_genre_name"))));
    }

    private Set<RawLanguageRef> findRawLanguages(UUID rawVideoId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                select rl.id, rl.source_value, rl.standard_language_id, sl.name as standard_language_name,
                       rl.created_at, rl.updated_at
                from video_raw_languages vrl
                join raw_languages rl on vrl.raw_language_id = rl.id
                left join standard_languages sl on rl.standard_language_id = sl.id
                where vrl.video_id = :id
                order by rl.source_value
                """,
                new MapSqlParameterSource("id", rawVideoId),
                (rs, rowNum) -> new RawLanguageRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("source_value"),
                        rs.getObject("standard_language_id", UUID.class),
                        rs.getString("standard_language_name"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"))));
    }

    private Set<RawAreaRef> findRawAreas(UUID rawVideoId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                select ra.id, ra.source_value, ra.standard_area_id, sa.name as standard_area_name,
                       ra.created_at, ra.updated_at
                from video_raw_areas vra
                join raw_areas ra on vra.raw_area_id = ra.id
                left join standard_areas sa on ra.standard_area_id = sa.id
                where vra.video_id = :id
                order by ra.source_value
                """,
                new MapSqlParameterSource("id", rawVideoId),
                (rs, rowNum) -> new RawAreaRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("source_value"),
                        rs.getObject("standard_area_id", UUID.class),
                        rs.getString("standard_area_name"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"))));
    }

    private Set<StandardAreaRef> findStandardAreas(UUID unifiedVideoId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                select sa.id, sa.name, sa.code, sa.region, sa.created_at, sa.updated_at
                from unified_video_standard_areas uva
                join standard_areas sa on uva.standard_area_id = sa.id
                where uva.unified_video_id = :id
                order by sa.name
                """,
                new MapSqlParameterSource("id", unifiedVideoId),
                (rs, rowNum) -> new StandardAreaRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getString("region"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"))));
    }

    private Pageable sanitize(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE);
        return PageRequest.of(page, size, pageable.getSort());
    }

    private Set<String> readStringSet(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            return objectMapper.readValue(json, STRING_SET);
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private String resolveCoverUrl(String storageType, String originalUrl, String storagePath, String domain) {
        return coverImageUrlResolver.resolve(storageType, originalUrl, storagePath, domain);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record RawVideoDetailBase(
            UUID id,
            String sourceVid,
            String title,
            String aliasTitle,
            Integer season,
            String subtitle,
            String description,
            String coverOriginalUrl,
            String coverStoragePath,
            String coverStorageType,
            String coverStatus,
            String coverDomain,
            String year,
            String rawLanguageStr,
            String remarks,
            BigDecimal score,
            java.time.LocalDate publishedAt,
            String totalEpisodes,
            String duration,
            String doubanId,
            String tmdbId,
            String imdbId,
            String rottenTomatoesId,
            UUID categoryId,
            String categoryName,
            UUID dataSourceCategoryId,
            String sourceCategoryName,
            String dataSourceName,
            UUID dataSourceId,
            UUID unifiedVideoId,
            String enrichmentStatus,
            String normalizationStatus,
            Instant createdAt,
            Instant updatedAt,
            String lockedFieldsJson,
            String rawMetadata) {
    }

    private record UnifiedVideoDetailBase(
            UUID id,
            String title,
            String aliasTitle,
            String coverOriginalUrl,
            String coverStoragePath,
            String coverStorageType,
            String coverDomain,
            String description,
            String year,
            BigDecimal score,
            java.time.LocalDate publishedAt,
            String totalEpisodes,
            String duration,
            String remarks,
            Integer season,
            String subtitle,
            String doubanId,
            String tmdbId,
            String imdbId,
            String rottenTomatoesId,
            Instant lastTrendAt,
            UUID categoryId,
            String categoryName,
            String contentVisibility,
            String lockedFieldsJson,
            Instant createdAt,
            Instant updatedAt) {
    }
}
