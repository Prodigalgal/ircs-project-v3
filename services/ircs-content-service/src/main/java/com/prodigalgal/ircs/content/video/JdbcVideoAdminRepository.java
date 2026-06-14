package com.prodigalgal.ircs.content.video;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.NamedRef;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawAreaRef;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawGenreRef;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawLanguageRef;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawVideoCardResponse;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawVideoCreateRequest;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawVideoDetailResponse;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.RawVideoUpdateRequest;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.StandardAreaRef;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.UnifiedVideoCardResponse;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.UnifiedVideoCreateRequest;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.UnifiedVideoDetailResponse;
import com.prodigalgal.ircs.content.video.VideoAdminDtos.UnifiedVideoUpdateRequest;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class JdbcVideoAdminRepository {

    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Page<RawVideoCardResponse> findRawVideos(
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

    public Optional<RawVideoDetailResponse> findRawDetail(UUID id) {
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
                    resolveCoverUrl(base.coverOriginalUrl(), base.coverStoragePath(), base.coverDomain()),
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

    public UUID createRawVideo(RawVideoCreateRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        DataSourceBinding binding = findRawCategoryBinding(request.dataSourceCategoryId()).orElse(DataSourceBinding.empty());
        jdbcTemplate.update(
                """
                insert into raw_videos (
                    id, created_at, updated_at, version, source_vid, source_hash, data_hash,
                    title, alias_title, season, subtitle, cover_image_id, description, year, raw_language_str,
                    remarks, score, published_at, total_episodes, duration,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, locked_fields,
                    data_source_category_id, data_source_id, enrichment_status, enrichment_retry_count,
                    normalization_status, normalization_retry_count, next_normalization_retry_time,
                    aggregation_status, playlist_retry_count, raw_metadata
                ) values (
                    :id, :now, :now, 0, :sourceVid, :sourceHash, null,
                    :title, :aliasTitle, :season, :subtitle, :coverImageId, :description, :year, :rawLanguageStr,
                    :remarks, :score, :publishedAt, :totalEpisodes, :duration,
                    :doubanId, :tmdbId, :imdbId, :rottenTomatoesId, cast(:lockedFields as jsonb),
                    :dataSourceCategoryId, :dataSourceId, 'PENDING', 0,
                    'READY', 0, null,
                    'PENDING', 0, null
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("sourceVid", "MANUAL_" + id)
                        .addValue("sourceHash", sha256("MANUAL:" + id))
                        .addValue("title", trimToLength(request.title(), 255))
                        .addValue("aliasTitle", trimToLength(request.aliasTitle(), 255))
                        .addValue("season", request.season())
                        .addValue("subtitle", trimToLength(request.subtitle(), 255))
                        .addValue("coverImageId", getOrCreateCoverImage(request.coverImageUrl()).orElse(null))
                        .addValue("description", cleanText(request.description(), 5000))
                        .addValue("year", normalizeYear(request.year()))
                        .addValue("rawLanguageStr", request.rawLanguageStr())
                        .addValue("remarks", cleanText(request.remarks(), 255))
                        .addValue("score", request.score())
                        .addValue("publishedAt", request.publishedAt() == null ? null : Date.valueOf(request.publishedAt()))
                        .addValue("totalEpisodes", trimToLength(request.totalEpisodes(), 50))
                        .addValue("duration", trimToLength(request.duration(), 50))
                        .addValue("doubanId", normalizeExternalId(request.doubanId(), 20))
                        .addValue("tmdbId", normalizeExternalId(request.tmdbId(), 20))
                        .addValue("imdbId", normalizeExternalId(request.imdbId(), 20))
                        .addValue("rottenTomatoesId", normalizeExternalId(request.rottenTomatoesId(), 50))
                        .addValue("lockedFields", "[]")
                        .addValue("dataSourceCategoryId", request.dataSourceCategoryId())
                        .addValue("dataSourceId", binding.dataSourceId()));
        replaceUuidRelations("video_actors", "video_id", "actor_id", "actors", id, request.actorIds());
        replaceUuidRelations("video_directors", "video_id", "director_id", "directors", id, request.directorIds());
        replaceUuidRelations("video_raw_genres", "video_id", "raw_genre_id", "raw_genres", id, request.rawGenreIds());
        replaceUuidRelations("video_raw_areas", "video_id", "raw_area_id", "raw_areas", id, request.rawAreaIds());
        return id;
    }

    public RawVideoSnapshot rawSnapshot(UUID id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select rv.id, rv.title, rv.alias_title, rv.year, rv.season,
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
                            rs.getObject("unified_video_id", UUID.class)));
        } catch (EmptyResultDataAccessException ex) {
            throw new ContentApiException(HttpStatus.NOT_FOUND, "Raw video not found: " + id);
        }
    }

    public void updateRawVideo(UUID id, RawVideoUpdateRequest request) {
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfPresent(updates, "title", trimToLength(request.title(), 255));
        putIfPresent(updates, "alias_title", trimToLength(request.aliasTitle(), 255));
        putIfPresent(updates, "season", request.season());
        putIfPresent(updates, "subtitle", trimToLength(request.subtitle(), 255));
        putIfPresent(updates, "cover_image_id", getOrCreateCoverImage(request.coverImageUrl()).orElse(null), request.coverImageUrl() != null);
        putIfPresent(updates, "description", cleanText(request.description(), 5000));
        putIfPresent(updates, "year", normalizeYear(request.year()));
        putIfPresent(updates, "raw_language_str", request.rawLanguageStr());
        putIfPresent(updates, "remarks", cleanText(request.remarks(), 255));
        putIfPresent(updates, "score", request.score());
        putIfPresent(updates, "published_at", request.publishedAt() == null ? null : Date.valueOf(request.publishedAt()));
        putIfPresent(updates, "total_episodes", trimToLength(request.totalEpisodes(), 50));
        putIfPresent(updates, "duration", trimToLength(request.duration(), 50));
        putIfPresent(updates, "douban_id", normalizeExternalId(request.doubanId(), 20));
        putIfPresent(updates, "tmdb_id", normalizeExternalId(request.tmdbId(), 20));
        putIfPresent(updates, "imdb_id", normalizeExternalId(request.imdbId(), 20));
        putIfPresent(updates, "rotten_tomatoes_id", normalizeExternalId(request.rottenTomatoesId(), 50));
        if (request.lockedFields() != null) {
            updates.put("locked_fields", json(request.lockedFields()));
        }
        if (request.dataSourceCategoryId() != null) {
            DataSourceBinding binding = findRawCategoryBinding(request.dataSourceCategoryId())
                    .orElseThrow(() -> new ContentApiException(HttpStatus.BAD_REQUEST,
                            "DataSourceCategory not found: " + request.dataSourceCategoryId()));
            updates.put("data_source_category_id", request.dataSourceCategoryId());
            updates.put("data_source_id", binding.dataSourceId());
        }
        if (!updates.isEmpty()) {
            updateTable("raw_videos", id, updates);
        }
        if (request.actorIds() != null) {
            replaceUuidRelations("video_actors", "video_id", "actor_id", "actors", id, request.actorIds());
        }
        if (request.directorIds() != null) {
            replaceUuidRelations("video_directors", "video_id", "director_id", "directors", id, request.directorIds());
        }
        if (request.rawGenreIds() != null) {
            replaceUuidRelations("video_raw_genres", "video_id", "raw_genre_id", "raw_genres", id, request.rawGenreIds());
        }
        if (request.rawAreaIds() != null) {
            replaceUuidRelations("video_raw_areas", "video_id", "raw_area_id", "raw_areas", id, request.rawAreaIds());
        }
    }

    public void setRawAggregationStatus(UUID rawVideoId, String status) {
        jdbcTemplate.update(
                "update raw_videos set aggregation_status = :status, updated_at = now() where id = :id",
                new MapSqlParameterSource().addValue("id", rawVideoId).addValue("status", status));
    }

    public void setRawNormalizationPending(UUID rawVideoId) {
        int updated = jdbcTemplate.update(
                """
                update raw_videos
                set normalization_status = 'PENDING',
                    normalization_retry_count = 0,
                    next_normalization_retry_time = null,
                    updated_at = now()
                where id = :id
                """,
                new MapSqlParameterSource("id", rawVideoId));
        if (updated == 0) {
            throw new ContentApiException(HttpStatus.NOT_FOUND, "Raw video not found: " + rawVideoId);
        }
    }

    public void unbindRaw(UUID rawVideoId) {
        jdbcTemplate.update(
                "delete from raw_video_unified_video where raw_video_id = :id",
                new MapSqlParameterSource("id", rawVideoId));
        setRawAggregationStatus(rawVideoId, "PENDING");
    }

    public void bindRawToUnified(UUID rawVideoId, UUID unifiedVideoId) {
        ensureExists("unified_videos", unifiedVideoId, "Unified video not found: " + unifiedVideoId);
        jdbcTemplate.update(
                "delete from raw_video_unified_video where raw_video_id = :rawVideoId",
                new MapSqlParameterSource("rawVideoId", rawVideoId));
        jdbcTemplate.update(
                """
                insert into raw_video_unified_video (raw_video_id, unified_video_id)
                values (:rawVideoId, :unifiedVideoId)
                on conflict do nothing
                """,
                new MapSqlParameterSource()
                        .addValue("rawVideoId", rawVideoId)
                        .addValue("unifiedVideoId", unifiedVideoId));
        setRawAggregationStatus(rawVideoId, "BOUND");
        markUnifiedDirty(unifiedVideoId);
    }

    public List<UUID> findRawUnifiedBindings(List<UUID> rawIds) {
        if (CollectionUtils.isEmpty(rawIds)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                """
                select distinct unified_video_id
                from raw_video_unified_video
                where raw_video_id in (:ids)
                """,
                new MapSqlParameterSource("ids", rawIds),
                UUID.class);
    }

    public void deleteRawVideos(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        jdbcTemplate.update("delete from episodes where playlist_id in (select id from playlists where video_id in (:ids))", params);
        jdbcTemplate.update("delete from playlists where video_id in (:ids)", params);
        jdbcTemplate.update("delete from video_actors where video_id in (:ids)", params);
        jdbcTemplate.update("delete from video_directors where video_id in (:ids)", params);
        jdbcTemplate.update("delete from video_raw_genres where video_id in (:ids)", params);
        jdbcTemplate.update("delete from video_raw_languages where video_id in (:ids)", params);
        jdbcTemplate.update("delete from video_raw_areas where video_id in (:ids)", params);
        jdbcTemplate.update("delete from raw_video_unified_video where raw_video_id in (:ids)", params);
        jdbcTemplate.update("delete from raw_videos where id in (:ids)", params);
    }

    public Page<UnifiedVideoCardResponse> findUnifiedVideos(
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

    public Optional<UnifiedVideoDetailResponse> findUnifiedDetail(UUID id) {
        try {
            UnifiedVideoDetailBase base = jdbcTemplate.queryForObject(
                    """
                    select uv.*, sc.name as category_name,
                           ci.original_url as cover_original_url, ci.storage_path as cover_storage_path,
                           sd.domain_value as cover_domain
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
                    resolveCoverUrl(base.coverOriginalUrl(), base.coverStoragePath(), base.coverDomain()),
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

    public UUID createUnifiedVideo(UnifiedVideoCreateRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        UUID coverImageId = getOrCreateCoverImage(request.coverImageUrl()).orElse(null);
        jdbcTemplate.update(
                """
                insert into unified_videos (
                    id, created_at, updated_at, version, title, alias_title, season, subtitle,
                    cover_image_id, description, year, area, score, published_at,
                    total_episodes, duration, remarks,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                    locked_fields, category_id, content_visibility, metadata_status
                ) values (
                    :id, :now, :now, 0, :title, :aliasTitle, :season, :subtitle,
                    :coverImageId, :description, :year, null, :score, :publishedAt,
                    :totalEpisodes, :duration, :remarks,
                    :doubanId, :tmdbId, :imdbId, :rottenTomatoesId,
                    cast(:lockedFields as jsonb), :categoryId, :contentVisibility, 'DIRTY'
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("title", trimToLength(request.title(), 255))
                        .addValue("aliasTitle", trimToLength(request.aliasTitle(), 255))
                        .addValue("season", request.season())
                        .addValue("subtitle", trimToLength(request.subtitle(), 255))
                        .addValue("coverImageId", coverImageId)
                        .addValue("description", cleanText(request.description(), 5000))
                        .addValue("year", normalizeYear(request.year()))
                        .addValue("score", request.score())
                        .addValue("publishedAt", request.publishedAt() == null ? null : Date.valueOf(request.publishedAt()))
                        .addValue("totalEpisodes", trimToLength(request.totalEpisodes(), 50))
                        .addValue("duration", trimToLength(request.duration(), 50))
                        .addValue("remarks", cleanText(request.remarks(), 255))
                        .addValue("doubanId", normalizeExternalId(request.doubanId(), 20))
                        .addValue("tmdbId", normalizeExternalId(request.tmdbId(), 20))
                        .addValue("imdbId", normalizeExternalId(request.imdbId(), 20))
                        .addValue("rottenTomatoesId", normalizeExternalId(request.rottenTomatoesId(), 50))
                        .addValue("lockedFields", jsonOrEmpty(request.lockedFields()))
                        .addValue("categoryId", request.categoryId())
                        .addValue("contentVisibility", normalizeContentVisibility(request.contentVisibility())));
        replaceUuidRelations("unified_video_standard_areas", "unified_video_id", "standard_area_id", "standard_areas", id,
                request.standardAreaIds());
        replaceNamedRelations("unified_video_actors", "unified_video_id", "actor_id", "actors", id, request.actors());
        replaceNamedRelations("unified_video_directors", "unified_video_id", "director_id", "directors", id, request.directors());
        replaceNamedRelations("unified_video_genres", "unified_video_id", "genre_id", "standard_genre", id, request.genres());
        replaceUnifiedTags(id, request.tags());
        replaceNamedRelations("unified_video_standard_languages", "unified_video_id", "standard_language_id",
                "standard_languages", id, request.languages());
        bindRawVideosToUnified(id, request.bindVideoIds());
        return id;
    }

    public List<UUID> updateTrendTimeByDoubanIds(Collection<String> ids, Instant now) {
        return updateTrendTimeByExternalIds("douban_id", ids, now);
    }

    public List<UUID> updateTrendTimeByTmdbIds(Collection<String> ids, Instant now) {
        return updateTrendTimeByExternalIds("tmdb_id", ids, now);
    }

    public Set<String> findExistingDoubanIds(Collection<String> ids) {
        return findExistingExternalIds("douban_id", ids);
    }

    public Set<String> findExistingTmdbIds(Collection<String> ids) {
        return findExistingExternalIds("tmdb_id", ids);
    }

    public List<String> findIdsByYearAndTitleSimilarity(List<String> years, String title, double threshold) {
        if (years == null || years.isEmpty() || !StringUtils.hasText(title)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                """
                select id::text
                  from unified_videos uv
                 where uv.year in (:years)
                   and similarity(uv.title, :title) >= :threshold
                 order by similarity(uv.title, :title) desc,
                          uv.updated_at desc nulls last
                 limit 5
                """,
                new MapSqlParameterSource()
                        .addValue("years", years)
                        .addValue("title", title.trim())
                        .addValue("threshold", threshold),
                String.class);
    }

    public boolean updateTrendTimeById(UUID id, Instant now) {
        if (id == null) {
            return false;
        }
        int updated = jdbcTemplate.update(
                """
                update unified_videos
                   set last_trend_at = :now,
                       updated_at = :now
                 where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", Timestamp.from(now)));
        return updated > 0;
    }

    public UUID createTrendGhost(TrendItemPayload item, Instant trendTime) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        UUID coverImageId = getOrCreateCoverImage(item.posterUrl()).orElse(null);
        jdbcTemplate.update(
                """
                insert into unified_videos (
                    id, created_at, updated_at, version, title, alias_title, season, subtitle,
                    cover_image_id, description, year, area, score, published_at,
                    total_episodes, duration, remarks,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                    last_trend_at, locked_fields, category_id, content_visibility, metadata_status
                ) values (
                    :id, :now, :now, 0, :title, null, null, null,
                    :coverImageId, :description, :year, null, :score, :publishedAt,
                    null, null, null,
                    :doubanId, :tmdbId, :imdbId, null,
                    :lastTrendAt, cast(:lockedFields as jsonb), null, 'PUBLIC', 'DIRTY'
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("title", trimToLength(item.title(), 255))
                        .addValue("coverImageId", coverImageId)
                        .addValue("description", cleanText(item.description(), 5000))
                        .addValue("year", normalizeYear(item.year()))
                        .addValue("score", item.score())
                        .addValue("publishedAt", item.publishedAt() == null ? null : Date.valueOf(item.publishedAt()))
                        .addValue("doubanId", normalizeExternalId(item.doubanId(), 20))
                        .addValue("tmdbId", normalizeExternalId(item.tmdbId(), 20))
                        .addValue("imdbId", normalizeExternalId(item.imdbId(), 20))
                        .addValue("lastTrendAt", Timestamp.from(trendTime))
                        .addValue("lockedFields", jsonOrEmpty(Set.of())));
        return id;
    }

    private List<UUID> updateTrendTimeByExternalIds(String column, Collection<String> ids, Instant now) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                """
                update unified_videos
                   set last_trend_at = :now,
                       updated_at = :now
                 where %s in (:ids)
                 returning id
                """.formatted(column),
                new MapSqlParameterSource()
                        .addValue("ids", ids)
                        .addValue("now", Timestamp.from(now)),
                UUID.class);
    }

    private Set<String> findExistingExternalIds(String column, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        List<String> values = jdbcTemplate.queryForList(
                """
                select %s
                  from unified_videos
                 where %s in (:ids)
                """.formatted(column, column),
                new MapSqlParameterSource("ids", ids),
                String.class);
        return new LinkedHashSet<>(values);
    }

    public void updateUnifiedVideo(UUID id, UnifiedVideoUpdateRequest request) {
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfPresent(updates, "title", trimToLength(request.title(), 255));
        putIfPresent(updates, "alias_title", trimToLength(request.aliasTitle(), 255));
        putIfPresent(updates, "season", request.season());
        putIfPresent(updates, "subtitle", trimToLength(request.subtitle(), 255));
        putIfPresent(updates, "cover_image_id", getOrCreateCoverImage(request.coverImageUrl()).orElse(null), request.coverImageUrl() != null);
        putIfPresent(updates, "description", cleanText(request.description(), 5000));
        putIfPresent(updates, "year", normalizeYear(request.year()));
        putIfPresent(updates, "score", request.score());
        putIfPresent(updates, "published_at", request.publishedAt() == null ? null : Date.valueOf(request.publishedAt()));
        putIfPresent(updates, "total_episodes", trimToLength(request.totalEpisodes(), 50));
        putIfPresent(updates, "duration", trimToLength(request.duration(), 50));
        putIfPresent(updates, "remarks", cleanText(request.remarks(), 255));
        putIfPresent(updates, "douban_id", normalizeExternalId(request.doubanId(), 20));
        putIfPresent(updates, "tmdb_id", normalizeExternalId(request.tmdbId(), 20));
        putIfPresent(updates, "imdb_id", normalizeExternalId(request.imdbId(), 20));
        putIfPresent(updates, "rotten_tomatoes_id", normalizeExternalId(request.rottenTomatoesId(), 50));
        putIfPresent(updates, "category_id", request.categoryId());
        putIfPresent(
                updates,
                "content_visibility",
                normalizeContentVisibility(request.contentVisibility()),
                request.contentVisibility() != null);
        if (request.lockedFields() != null) {
            updates.put("locked_fields", json(request.lockedFields()));
        }
        updates.put("metadata_status", "DIRTY");
        updateTable("unified_videos", id, updates);
        if (request.standardAreaIds() != null) {
            replaceUuidRelations("unified_video_standard_areas", "unified_video_id", "standard_area_id", "standard_areas", id,
                    request.standardAreaIds());
        }
        if (request.actors() != null) {
            replaceNamedRelations("unified_video_actors", "unified_video_id", "actor_id", "actors", id, request.actors());
        }
        if (request.directors() != null) {
            replaceNamedRelations("unified_video_directors", "unified_video_id", "director_id", "directors", id,
                    request.directors());
        }
        if (request.genres() != null) {
            replaceNamedRelations("unified_video_genres", "unified_video_id", "genre_id", "standard_genre", id,
                    request.genres());
        }
        if (request.tags() != null) {
            replaceUnifiedTags(id, request.tags());
        }
        if (request.languages() != null) {
            replaceNamedRelations("unified_video_standard_languages", "unified_video_id", "standard_language_id",
                    "standard_languages", id, request.languages());
        }
        unbindRawVideos(request.unbindVideoIds());
        bindRawVideosToUnified(id, request.bindVideoIds());
    }

    public void bindRawVideosToUnified(UUID unifiedVideoId, Set<UUID> rawIds) {
        if (rawIds == null) {
            return;
        }
        for (UUID rawId : rawIds) {
            bindRawToUnified(rawId, unifiedVideoId);
        }
    }

    public void unbindRawVideos(Set<UUID> rawIds) {
        if (rawIds == null) {
            return;
        }
        for (UUID rawId : rawIds) {
            unbindRaw(rawId);
        }
    }

    public List<UUID> findRawIdsForUnified(UUID unifiedVideoId) {
        return jdbcTemplate.queryForList(
                "select raw_video_id from raw_video_unified_video where unified_video_id = :id",
                new MapSqlParameterSource("id", unifiedVideoId),
                UUID.class);
    }

    public List<UUID> recalculateUnifiedFromSources(UUID unifiedVideoId) {
        ensureExists("unified_videos", unifiedVideoId, "Unified video not found: " + unifiedVideoId);
        List<UUID> rawIds = findRawIdsForUnified(unifiedVideoId);
        for (UUID rawId : rawIds) {
            jdbcTemplate.update(
                    """
                    update unified_videos uv
                    set title = coalesce(nullif(uv.title, ''), rv.title),
                        alias_title = coalesce(nullif(uv.alias_title, ''), rv.alias_title),
                        description = coalesce(nullif(uv.description, ''), rv.description),
                        year = coalesce(nullif(uv.year, ''), rv.year),
                        score = coalesce(uv.score, rv.score),
                        published_at = coalesce(uv.published_at, rv.published_at),
                        total_episodes = coalesce(nullif(uv.total_episodes, ''), rv.total_episodes),
                        duration = coalesce(nullif(uv.duration, ''), rv.duration),
                        remarks = coalesce(nullif(uv.remarks, ''), rv.remarks),
                        douban_id = coalesce(nullif(uv.douban_id, ''), rv.douban_id),
                        tmdb_id = coalesce(nullif(uv.tmdb_id, ''), rv.tmdb_id),
                        imdb_id = coalesce(nullif(uv.imdb_id, ''), rv.imdb_id),
                        rotten_tomatoes_id = coalesce(nullif(uv.rotten_tomatoes_id, ''), rv.rotten_tomatoes_id),
                        metadata_status = 'SYNCED',
                        updated_at = now()
                    from raw_videos rv
                    where uv.id = :unifiedVideoId
                      and rv.id = :rawVideoId
                    """,
                    new MapSqlParameterSource()
                            .addValue("unifiedVideoId", unifiedVideoId)
                            .addValue("rawVideoId", rawId));
            jdbcTemplate.update(
                    "update raw_videos set aggregation_status = 'BOUND', updated_at = now() where id = :rawVideoId",
                    new MapSqlParameterSource("rawVideoId", rawId));
        }
        if (rawIds.isEmpty()) {
            jdbcTemplate.update(
                    "update unified_videos set metadata_status = 'SYNCED', updated_at = now() where id = :id",
                    new MapSqlParameterSource("id", unifiedVideoId));
        }
        return rawIds;
    }

    public void markUnifiedDirty(UUID unifiedVideoId) {
        jdbcTemplate.update(
                "update unified_videos set metadata_status = 'DIRTY', updated_at = now() where id = :id",
                new MapSqlParameterSource("id", unifiedVideoId));
    }

    public void deleteUnifiedVideos(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        List<UUID> rawIds = jdbcTemplate.queryForList(
                "select raw_video_id from raw_video_unified_video where unified_video_id in (:ids)",
                new MapSqlParameterSource("ids", ids),
                UUID.class);
        if (!rawIds.isEmpty()) {
            jdbcTemplate.update(
                    "update raw_videos set aggregation_status = 'PENDING', updated_at = now() where id in (:rawIds)",
                    new MapSqlParameterSource("rawIds", rawIds));
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        jdbcTemplate.update("delete from raw_video_unified_video where unified_video_id in (:ids)", params);
        jdbcTemplate.update("delete from unified_video_actors where unified_video_id in (:ids)", params);
        jdbcTemplate.update("delete from unified_video_directors where unified_video_id in (:ids)", params);
        jdbcTemplate.update("delete from unified_video_genres where unified_video_id in (:ids)", params);
        jdbcTemplate.update("delete from unified_video_standard_languages where unified_video_id in (:ids)", params);
        jdbcTemplate.update("delete from unified_video_standard_areas where unified_video_id in (:ids)", params);
        jdbcTemplate.update("delete from unified_videos where id in (:ids)", params);
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
                       sd.domain_value as cover_domain,
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
                       sd.domain_value as cover_domain,
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
                resolveCoverUrl(rs.getString("cover_original_url"), rs.getString("cover_storage_path"), rs.getString("cover_domain")),
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
                resolveCoverUrl(rs.getString("cover_original_url"), rs.getString("cover_storage_path"), rs.getString("cover_domain")),
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

    private String normalizeContentVisibility(String value) {
        if (!StringUtils.hasText(value)) {
            return IrcsPermissions.VISIBILITY_PUBLIC;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (IrcsPermissions.VISIBILITY_PUBLIC.equals(normalized)
                || IrcsPermissions.VISIBILITY_MEMBER.equals(normalized)
                || IrcsPermissions.VISIBILITY_ADMIN.equals(normalized)
                || IrcsPermissions.VISIBILITY_DRAFT.equals(normalized)
                || IrcsPermissions.VISIBILITY_HIDDEN.equals(normalized)) {
            return normalized;
        }
        throw new ContentApiException(HttpStatus.BAD_REQUEST, "Invalid contentVisibility: " + value);
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
        Set<String> names = new LinkedHashSet<>(jdbcTemplate.queryForList(
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
        return names;
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

    private void replaceUuidRelations(
            String joinTable,
            String ownerColumn,
            String relatedColumn,
            String relatedTable,
            UUID ownerId,
            Set<UUID> relatedIds) {
        jdbcTemplate.update(
                "delete from %s where %s = :ownerId".formatted(joinTable, ownerColumn),
                new MapSqlParameterSource("ownerId", ownerId));
        if (CollectionUtils.isEmpty(relatedIds)) {
            return;
        }
        ensureAllExist(relatedTable, relatedIds);
        List<MapSqlParameterSource> batch = relatedIds.stream()
                .map(id -> new MapSqlParameterSource().addValue("ownerId", ownerId).addValue("relatedId", id))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into %s (%s, %s)
                values (:ownerId, :relatedId)
                on conflict do nothing
                """.formatted(joinTable, ownerColumn, relatedColumn),
                batch.toArray(MapSqlParameterSource[]::new));
    }

    private void replaceNamedRelations(
            String joinTable,
            String ownerColumn,
            String relatedColumn,
            String relatedTable,
            UUID ownerId,
            Set<String> names) {
        jdbcTemplate.update(
                "delete from %s where %s = :ownerId".formatted(joinTable, ownerColumn),
                new MapSqlParameterSource("ownerId", ownerId));
        if (CollectionUtils.isEmpty(names)) {
            return;
        }
        List<String> safeNames = names.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (safeNames.isEmpty()) {
            return;
        }
        List<UUID> ids = jdbcTemplate.queryForList(
                "select id from %s where name in (:names)".formatted(relatedTable),
                new MapSqlParameterSource("names", safeNames),
                UUID.class);
        if (ids.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batch = ids.stream()
                .map(id -> new MapSqlParameterSource().addValue("ownerId", ownerId).addValue("relatedId", id))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into %s (%s, %s)
                values (:ownerId, :relatedId)
                on conflict do nothing
                """.formatted(joinTable, ownerColumn, relatedColumn),
                batch.toArray(MapSqlParameterSource[]::new));
    }

    private void replaceUnifiedTags(UUID unifiedVideoId, Set<String> tags) {
        jdbcTemplate.update(
                "delete from unified_video_tags where unified_video_id = :id",
                new MapSqlParameterSource("id", unifiedVideoId));
        Set<String> safeTags = normalizeTags(tags);
        if (safeTags.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batch = safeTags.stream()
                .map(tag -> new MapSqlParameterSource()
                        .addValue("id", unifiedVideoId)
                        .addValue("tag", tag))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into unified_video_tags (unified_video_id, tag)
                values (:id, :tag)
                on conflict do nothing
                """,
                batch.toArray(MapSqlParameterSource[]::new));
    }

    private Optional<DataSourceBinding> findRawCategoryBinding(UUID rawCategoryId) {
        if (rawCategoryId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select id, data_source_id, category_id from raw_category where id = :id",
                    new MapSqlParameterSource("id", rawCategoryId),
                    (rs, rowNum) -> new DataSourceBinding(
                            rs.getObject("id", UUID.class),
                            rs.getObject("data_source_id", UUID.class),
                            rs.getObject("category_id", UUID.class))));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private Optional<UUID> getOrCreateCoverImage(String coverImageUrl) {
        if (!StringUtils.hasText(coverImageUrl)) {
            return Optional.empty();
        }
        String trimmed = coverImageUrl.trim();
        String domain = domainOf(trimmed);
        UUID sourceDomainId = getOrCreateSourceDomain(domain);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    insert into cover_images (
                        id, created_at, updated_at, version, storage_type, original_url, storage_path,
                        file_hash, file_size, mime_type, source_domain_id, status, retry_count,
                        next_retry_time, last_error
                    ) values (
                        :id, now(), now(), 0, 'EXTERNAL', :url, null,
                        null, null, null, :sourceDomainId, 'UNPROCESSED', 0,
                        now(), null
                    )
                    on conflict (original_url, source_domain_id) do update
                    set updated_at = cover_images.updated_at
                    returning id
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", IrcsUuidGenerators.nextId())
                            .addValue("url", trimmed)
                            .addValue("sourceDomainId", sourceDomainId),
                    UUID.class));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private UUID getOrCreateSourceDomain(String domain) {
        String hash = sha256(domain);
        return jdbcTemplate.queryForObject(
                """
                insert into source_domains (id, created_at, updated_at, version, domain_hash, domain_value, remark, data_source_id)
                values (:id, now(), now(), 0, :hash, :domain, null, null)
                on conflict (domain_hash) do update
                set updated_at = source_domains.updated_at
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("hash", hash)
                        .addValue("domain", domain),
                UUID.class);
    }

    private void updateTable(String table, UUID id, Map<String, Object> updates) {
        if (updates.isEmpty()) {
            return;
        }
        List<String> assignments = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        int i = 0;
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String param = "p" + i++;
            if ("locked_fields".equals(entry.getKey())) {
                assignments.add(entry.getKey() + " = cast(:" + param + " as jsonb)");
            } else {
                assignments.add(entry.getKey() + " = :" + param);
            }
            params.addValue(param, entry.getValue());
        }
        assignments.add("updated_at = now()");
        int updated = jdbcTemplate.update(
                "update %s set %s where id = :id".formatted(table, String.join(", ", assignments)),
                params);
        if (updated == 0) {
            throw new ContentApiException(HttpStatus.NOT_FOUND, table + " not found: " + id);
        }
    }

    private void ensureExists(String table, UUID id, String message) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from %s where id = :id)".formatted(table),
                new MapSqlParameterSource("id", id),
                Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ContentApiException(HttpStatus.NOT_FOUND, message);
        }
    }

    private void ensureAllExist(String table, Set<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from %s where id in (:ids)".formatted(table),
                new MapSqlParameterSource("ids", ids),
                Long.class);
        if (count == null || count != ids.size()) {
            throw new ContentApiException(HttpStatus.BAD_REQUEST, "Invalid relation id for table " + table);
        }
    }

    private void putIfPresent(Map<String, Object> updates, String column, Object value) {
        putIfPresent(updates, column, value, value != null);
    }

    private void putIfPresent(Map<String, Object> updates, String column, Object value, boolean present) {
        if (present) {
            updates.put(column, value);
        }
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

    private String jsonOrEmpty(Set<String> values) {
        return json(values == null ? Collections.emptySet() : values);
    }

    private String json(Set<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Collections.emptySet() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize jsonb field", e);
        }
    }

    private String resolveCoverUrl(String originalUrl, String storagePath, String domain) {
        if (StringUtils.hasText(storagePath) && StringUtils.hasText(domain)) {
            String left = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
            String right = storagePath.startsWith("/") ? storagePath.substring(1) : storagePath;
            return left + "/" + right;
        }
        return originalUrl;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String domainOf(String url) {
        try {
            URI uri = URI.create(url);
            if (StringUtils.hasText(uri.getHost())) {
                String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "https";
                return scheme + "://" + uri.getHost();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "external";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String normalizeYear(String year) {
        if (!StringUtils.hasText(year)) {
            return null;
        }
        String trimmed = year.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
    }

    private String normalizeExternalId(String id, int maxLength) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        String trimmed = id.trim();
        if ("0".equals(trimmed) || "0.0".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimToLength(trimmed, maxLength);
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = cleanText(tag, 128);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String cleanText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replace("\u0000", "").trim();
        return trimToLength(cleaned, maxLength);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public record RawVideoSnapshot(
            UUID id,
            String title,
            String aliasTitle,
            String year,
            Integer season,
            UUID unifiedVideoId) {
    }

    private record DataSourceBinding(UUID rawCategoryId, UUID dataSourceId, UUID categoryId) {
        static DataSourceBinding empty() {
            return new DataSourceBinding(null, null, null);
        }
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
