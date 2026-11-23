package com.prodigalgal.ircs.content.video.infrastructure;


import com.prodigalgal.ircs.content.video.api.ContentApiException;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.cleanText;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.normalizeExternalId;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.normalizeTags;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.normalizeYear;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.normalizeContentVisibility;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.sha256;
import static com.prodigalgal.ircs.content.video.domain.VideoAdminText.trimToLength;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.metadata.JsonStringArrays;
import com.prodigalgal.ircs.common.metadata.MetadataNameOwnerType;
import com.prodigalgal.ircs.common.metadata.MetadataNameCache;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordCardResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordCreateRequest;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordDetailResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.SourceRecordUpdateRequest;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryCardResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryCreateRequest;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryDetailResponse;
import com.prodigalgal.ircs.content.video.dto.VideoAdminDtos.CatalogEntryUpdateRequest;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class JdbcVideoAdminRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MetadataNameCache> metadataNameCacheProvider;
    private final VideoAdminCoverImageRepository coverImageRepository;
    private final VideoAdminQueryRepository queryRepository;

    public Page<SourceRecordCardResponse> findSourceRecords(
            Pageable pageable,
            String title,
            UUID categoryId,
            String enrichmentStatus,
            String normalizationStatus,
            String aggregationStatus,
            String year,
            String area,
            BigDecimal minScore,
            Boolean isMissingSlug,
            UUID dataSourceId,
            String sourceCategoryName,
            String genre,
            String language) {
        return queryRepository.findSourceRecords(
                pageable,
                title,
                categoryId,
                enrichmentStatus,
                normalizationStatus,
                aggregationStatus,
                year,
                area,
                minScore,
                isMissingSlug,
                dataSourceId,
                sourceCategoryName,
                genre,
                language);
    }

    public Optional<SourceRecordDetailResponse> findRawDetail(UUID id) {
        return queryRepository.findRawDetail(id);
    }

    public UUID createSourceRecord(SourceRecordCreateRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, data_hash,
                    title, alias_title, season, subtitle, media_image_source_id, description, year, raw_language_str,
                    remarks, score, published_at, total_episodes, duration,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, locked_fields,
                    collection_source_id, actor_names, director_names,
                    area_codes, language_codes, genre_codes, category_code,
                    source_category_code, source_category_name,
                    enrichment_status, enrichment_retry_count,
                    normalization_status, normalization_retry_count, next_normalization_retry_time,
                    aggregation_status, playlist_retry_count, raw_metadata
                ) values (
                    :id, :now, :now, 0, :sourceVid, :sourceHash, null,
                    :title, :aliasTitle, :season, :subtitle, :coverImageId, :description, :year, :rawLanguageStr,
                    :remarks, :score, :publishedAt, :totalEpisodes, :duration,
                    :doubanId, :tmdbId, :imdbId, :rottenTomatoesId, cast(:lockedFields as jsonb),
                    :dataSourceId, cast(:actorNames as jsonb), cast(:directorNames as jsonb),
                    cast(:areaCodes as jsonb), cast(:languageCodes as jsonb), cast(:genreCodes as jsonb), :categoryCode,
                    :sourceCategoryCode, :sourceCategoryName,
                    'PENDING', 0,
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
                        .addValue("coverImageId", coverImageRepository.getOrCreateCoverImage(request.coverImageUrl()).orElse(null))
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
                        .addValue("dataSourceId", request.dataSourceId())
                        .addValue("actorNames", jsonStringArray(request.actorNames()))
                        .addValue("directorNames", jsonStringArray(request.directorNames()))
                        .addValue("areaCodes", jsonStringArray(request.areaCodes()))
                        .addValue("languageCodes", jsonStringArray(request.languageCodes()))
                        .addValue("genreCodes", jsonStringArray(request.genreCodes()))
                        .addValue("categoryCode", firstText(request.categoryCode(), findCategoryCode(request.categoryId()).orElse(null)))
                        .addValue("sourceCategoryCode", trimToLength(request.sourceCategoryCode(), 100))
                        .addValue("sourceCategoryName", trimToLength(request.sourceCategoryName(), 255)));
        evictMetadataNameCache(MetadataNameOwnerType.RAW, List.of(id));
        return id;
    }

    public SourceRecordSnapshot rawSnapshot(UUID id) {
        return queryRepository.rawSnapshot(id);
    }

    public void updateSourceRecord(UUID id, SourceRecordUpdateRequest request) {
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfPresent(updates, "title", trimToLength(request.title(), 255));
        putIfPresent(updates, "alias_title", trimToLength(request.aliasTitle(), 255));
        putIfPresent(updates, "season", request.season());
        putIfPresent(updates, "subtitle", trimToLength(request.subtitle(), 255));
        putIfPresent(updates, "media_image_source_id", coverImageRepository.getOrCreateCoverImage(request.coverImageUrl()).orElse(null), request.coverImageUrl() != null);
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
        putIfPresent(updates, "collection_source_id", request.dataSourceId(), request.dataSourceId() != null);
        putJsonIfPresent(updates, "actor_names", request.actorNames(), request.actorNames() != null);
        putJsonIfPresent(updates, "director_names", request.directorNames(), request.directorNames() != null);
        putJsonIfPresent(updates, "area_codes", request.areaCodes(), request.areaCodes() != null);
        putJsonIfPresent(updates, "language_codes", request.languageCodes(), request.languageCodes() != null);
        putJsonIfPresent(updates, "genre_codes", request.genreCodes(), request.genreCodes() != null);
        putIfPresent(updates, "category_code", trimToLength(request.categoryCode(), 100), request.categoryCode() != null);
        putIfPresent(updates, "source_category_code", trimToLength(request.sourceCategoryCode(), 100), request.sourceCategoryCode() != null);
        putIfPresent(updates, "source_category_name", trimToLength(request.sourceCategoryName(), 255), request.sourceCategoryName() != null);
        if (!updates.isEmpty()) {
            updateTable("source_records", id, updates);
        }
        if (request.actorNames() != null
                || request.directorNames() != null
                || request.areaCodes() != null
                || request.languageCodes() != null
                || request.genreCodes() != null) {
            evictMetadataNameCache(MetadataNameOwnerType.RAW, List.of(id));
        }
    }

    public void setRawAggregationStatus(UUID sourceRecordId, String status) {
        jdbcTemplate.update(
                "update source_records set aggregation_status = :status, updated_at = now() where id = :id",
                new MapSqlParameterSource().addValue("id", sourceRecordId).addValue("status", status));
    }

    public void setRawNormalizationPending(UUID sourceRecordId) {
        int updated = jdbcTemplate.update(
                """
                update source_records
                set normalization_status = 'PENDING',
                    normalization_retry_count = 0,
                    next_normalization_retry_time = null,
                    updated_at = now()
                where id = :id
                """,
                new MapSqlParameterSource("id", sourceRecordId));
        if (updated == 0) {
            throw new ContentApiException(HttpStatus.NOT_FOUND, "Raw video not found: " + sourceRecordId);
        }
    }

    public void unbindRaw(UUID sourceRecordId) {
        jdbcTemplate.update(
                "delete from catalog_source_bindings where source_record_id = :id",
                new MapSqlParameterSource("id", sourceRecordId));
        setRawAggregationStatus(sourceRecordId, "PENDING");
    }

    public void lockSourceRecord(UUID sourceRecordId) {
        jdbcTemplate.query("select id from source_records where id=:id for update",
                new MapSqlParameterSource("id", sourceRecordId), rs -> { });
    }

    public void bindRawToUnified(UUID sourceRecordId, UUID catalogEntryId) {
        ensureExists("catalog_entries", catalogEntryId, "Unified video not found: " + catalogEntryId);
        MapSqlParameterSource target = new MapSqlParameterSource("sourceRecordId", sourceRecordId)
                .addValue("catalogEntryId", catalogEntryId);
        jdbcTemplate.update(
                "delete from catalog_source_bindings where source_record_id=:sourceRecordId and catalog_entry_id<>:catalogEntryId",
                target);
        jdbcTemplate.update(
                """
                insert into catalog_source_bindings (source_record_id, catalog_entry_id)
                values (:sourceRecordId, :catalogEntryId)
                on conflict do nothing
                """,
                target);
        jdbcTemplate.queryForObject("""
                select affirm_catalog_source_match(:sourceRecordId, :catalogEntryId, 'MANUAL_REVIEW')
                """, target, Integer.class);
        setRawAggregationStatus(sourceRecordId, "BOUND");
        markUnifiedDirty(catalogEntryId);
    }

    public List<UUID> findRawUnifiedBindings(List<UUID> rawIds) {
        if (CollectionUtils.isEmpty(rawIds)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                """
                select distinct catalog_entry_id
                from catalog_source_bindings
                where source_record_id in (:ids)
                """,
                new MapSqlParameterSource("ids", rawIds),
                UUID.class);
    }

    public void deleteSourceRecords(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        jdbcTemplate.queryForList("select id from source_records where id in (:ids) order by id for update", params, UUID.class);
        List<UUID> unifiedIds = jdbcTemplate.queryForList(
                "select distinct catalog_entry_id from catalog_source_bindings where source_record_id in (:ids)",
                params,
                UUID.class);
        jdbcTemplate.update("delete from source_playlists where source_record_id in (:ids)", params);
        jdbcTemplate.update("delete from catalog_source_bindings where source_record_id in (:ids)", params);
        jdbcTemplate.update("delete from source_records where id in (:ids)", params);
        evictMetadataNameCache(MetadataNameOwnerType.RAW, ids);
        evictMetadataNameCache(MetadataNameOwnerType.UNIFIED, unifiedIds);
    }

    public Page<CatalogEntryCardResponse> findCatalogEntries(
            Pageable pageable,
            String title,
            UUID categoryId,
            String year,
            String area,
            BigDecimal minScore,
            Boolean hasDoubanId,
            Boolean hasTmdbId,
            String contentVisibility,
            String metadataStatus,
            String genre,
            String language,
            String actor,
            String director) {
        return queryRepository.findCatalogEntries(
                pageable,
                title,
                categoryId,
                year,
                area,
                minScore,
                hasDoubanId,
                hasTmdbId,
                contentVisibility,
                metadataStatus,
                genre,
                language,
                actor,
                director);
    }

    public Optional<CatalogEntryDetailResponse> findUnifiedDetail(UUID id) {
        return queryRepository.findUnifiedDetail(id);
    }

    public UUID createCatalogEntry(CatalogEntryCreateRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        UUID coverImageId = coverImageRepository.getOrCreateCoverImage(request.coverImageUrl()).orElse(null);
        jdbcTemplate.update(
                """
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, alias_title, season, subtitle,
                    media_image_source_id, description, year, area, score, published_at,
                    total_episodes, duration, remarks,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                    locked_fields, category_id, category_code, actor_names, director_names,
                    area_codes, language_codes, genre_codes, content_visibility, metadata_status
                ) values (
                    :id, :now, :now, 0, :title, :aliasTitle, :season, :subtitle,
                    :coverImageId, :description, :year, null, :score, :publishedAt,
                    :totalEpisodes, :duration, :remarks,
                    :doubanId, :tmdbId, :imdbId, :rottenTomatoesId,
                    cast(:lockedFields as jsonb), :categoryId, :categoryCode, cast(:actorNames as jsonb), cast(:directorNames as jsonb),
                    cast(:areaCodes as jsonb), cast(:languageCodes as jsonb), cast(:genreCodes as jsonb), :contentVisibility, 'DIRTY'
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
                        .addValue("categoryCode", firstText(request.categoryCode(), findCategoryCode(request.categoryId()).orElse(null)))
                        .addValue("actorNames", jsonStringArray(request.actorNames()))
                        .addValue("directorNames", jsonStringArray(request.directorNames()))
                        .addValue("areaCodes", jsonStringArray(request.areaCodes()))
                        .addValue("languageCodes", jsonStringArray(request.languageCodes()))
                        .addValue("genreCodes", jsonStringArray(request.genreCodes()))
                        .addValue("contentVisibility", normalizeContentVisibility(request.contentVisibility())));
        replaceUnifiedTags(id, request.tags());
        bindSourceRecordsToUnified(id, request.bindVideoIds());
        evictMetadataNameCache(MetadataNameOwnerType.UNIFIED, List.of(id));
        return id;
    }

    public void updateCatalogEntry(UUID id, CatalogEntryUpdateRequest request) {
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfPresent(updates, "title", trimToLength(request.title(), 255));
        putIfPresent(updates, "alias_title", trimToLength(request.aliasTitle(), 255));
        putIfPresent(updates, "season", request.season());
        putIfPresent(updates, "subtitle", trimToLength(request.subtitle(), 255));
        putIfPresent(updates, "media_image_source_id", coverImageRepository.getOrCreateCoverImage(request.coverImageUrl()).orElse(null), request.coverImageUrl() != null);
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
                "category_code",
                firstText(request.categoryCode(), findCategoryCode(request.categoryId()).orElse(null)),
                request.categoryCode() != null || request.categoryId() != null);
        putJsonIfPresent(updates, "actor_names", request.actorNames(), request.actorNames() != null);
        putJsonIfPresent(updates, "director_names", request.directorNames(), request.directorNames() != null);
        putJsonIfPresent(updates, "area_codes", request.areaCodes(), request.areaCodes() != null);
        putJsonIfPresent(updates, "language_codes", request.languageCodes(), request.languageCodes() != null);
        putJsonIfPresent(updates, "genre_codes", request.genreCodes(), request.genreCodes() != null);
        putIfPresent(
                updates,
                "content_visibility",
                normalizeContentVisibility(request.contentVisibility()),
                request.contentVisibility() != null);
        if (request.lockedFields() != null) {
            updates.put("locked_fields", json(request.lockedFields()));
        }
        updates.put("metadata_status", "DIRTY");
        updateTable("catalog_entries", id, updates);
        if (request.tags() != null) {
            replaceUnifiedTags(id, request.tags());
        }
        unbindSourceRecords(request.unbindVideoIds());
        bindSourceRecordsToUnified(id, request.bindVideoIds());
        if (request.areaCodes() != null
                || request.actorNames() != null
                || request.directorNames() != null
                || request.genreCodes() != null
                || request.languageCodes() != null) {
            evictMetadataNameCache(MetadataNameOwnerType.UNIFIED, List.of(id));
        }
    }

    public void bindSourceRecordsToUnified(UUID catalogEntryId, Set<UUID> rawIds) {
        if (rawIds == null) {
            return;
        }
        for (UUID rawId : rawIds) {
            bindRawToUnified(rawId, catalogEntryId);
        }
    }

    public void unbindSourceRecords(Set<UUID> rawIds) {
        if (rawIds == null) {
            return;
        }
        for (UUID rawId : rawIds) {
            unbindRaw(rawId);
        }
    }

    public List<UUID> findRawIdsForUnified(UUID catalogEntryId) {
        return jdbcTemplate.queryForList(
                "select source_record_id from catalog_source_bindings where catalog_entry_id = :id",
                new MapSqlParameterSource("id", catalogEntryId),
                UUID.class);
    }

    public List<UUID> findRawIdsForCatalogEntries(Collection<UUID> catalogEntryIds) {
        if (CollectionUtils.isEmpty(catalogEntryIds)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "select distinct source_record_id from catalog_source_bindings where catalog_entry_id in (:ids)",
                new MapSqlParameterSource("ids", catalogEntryIds),
                UUID.class);
    }

    public List<UUID> recalculateUnifiedFromSources(UUID catalogEntryId) {
        ensureExists("catalog_entries", catalogEntryId, "Unified video not found: " + catalogEntryId);
        List<UUID> rawIds = findRawIdsForUnified(catalogEntryId);
        for (UUID rawId : rawIds) {
            jdbcTemplate.update(
                    """
                    update catalog_entries uv
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
                    from source_records rv
                    where uv.id = :catalogEntryId
                      and rv.id = :sourceRecordId
                    """,
                    new MapSqlParameterSource()
                            .addValue("catalogEntryId", catalogEntryId)
                            .addValue("sourceRecordId", rawId));
            jdbcTemplate.update(
                    "update source_records set aggregation_status = 'BOUND', updated_at = now() where id = :sourceRecordId",
                    new MapSqlParameterSource("sourceRecordId", rawId));
        }
        if (rawIds.isEmpty()) {
            jdbcTemplate.update(
                    "update catalog_entries set metadata_status = 'SYNCED', updated_at = now() where id = :id",
                    new MapSqlParameterSource("id", catalogEntryId));
        }
        return rawIds;
    }

    public void markUnifiedDirty(UUID catalogEntryId) {
        jdbcTemplate.update(
                "update catalog_entries set metadata_status = 'DIRTY', updated_at = now() where id = :id",
                new MapSqlParameterSource("id", catalogEntryId));
    }

    public void deleteCatalogEntries(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        List<UUID> rawIds = jdbcTemplate.queryForList(
                "select source_record_id from catalog_source_bindings where catalog_entry_id in (:ids)",
                new MapSqlParameterSource("ids", ids),
                UUID.class);
        if (!rawIds.isEmpty()) {
            jdbcTemplate.update(
                    "update source_records set aggregation_status = 'PENDING', updated_at = now() where id in (:rawIds)",
                    new MapSqlParameterSource("rawIds", rawIds));
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        jdbcTemplate.update("delete from catalog_source_bindings where catalog_entry_id in (:ids)", params);
        jdbcTemplate.update("delete from catalog_entries where id in (:ids)", params);
        evictMetadataNameCache(MetadataNameOwnerType.UNIFIED, ids);
    }

    private void evictMetadataNameCache(MetadataNameOwnerType ownerType, Collection<UUID> ownerIds) {
        MetadataNameCache metadataNameCache = metadataNameCacheProvider == null
                ? null
                : metadataNameCacheProvider.getIfAvailable();
        if (metadataNameCache != null) {
            metadataNameCache.evictOwner(ownerType, ownerIds);
        }
    }

    private void replaceUnifiedTags(UUID catalogEntryId, Set<String> tags) {
        jdbcTemplate.update(
                "delete from catalog_entry_tags where catalog_entry_id = :id",
                new MapSqlParameterSource("id", catalogEntryId));
        Set<String> safeTags = normalizeTags(tags);
        if (safeTags.isEmpty()) {
            return;
        }
        List<MapSqlParameterSource> batch = safeTags.stream()
                .map(tag -> new MapSqlParameterSource()
                        .addValue("id", catalogEntryId)
                        .addValue("tag", tag))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into catalog_entry_tags (catalog_entry_id, tag)
                values (:id, :tag)
                on conflict do nothing
                """,
                batch.toArray(MapSqlParameterSource[]::new));
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
            if (isJsonbColumn(entry.getKey())) {
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

    private void putIfPresent(Map<String, Object> updates, String column, Object value) {
        putIfPresent(updates, column, value, value != null);
    }

    private void putIfPresent(Map<String, Object> updates, String column, Object value, boolean present) {
        if (present) {
            updates.put(column, value);
        }
    }

    private void putJsonIfPresent(
            Map<String, Object> updates,
            String column,
            Collection<String> values,
            boolean present) {
        if (present) {
            updates.put(column, jsonStringArray(values));
        }
    }

    private boolean isJsonbColumn(String column) {
        return Set.of(
                "locked_fields",
                "actor_names",
                "director_names",
                "area_codes",
                "language_codes",
                "genre_codes").contains(column);
    }

    private String jsonOrEmpty(Set<String> values) {
        return json(values == null ? Collections.emptySet() : values);
    }

    private String jsonStringArray(Collection<String> values) {
        return JsonStringArrays.write(objectMapper, values);
    }

    private String firstText(String preferred, String fallback) {
        return firstText(preferred, fallback, 100);
    }

    private String firstText(String preferred, String fallback, int maxLength) {
        if (StringUtils.hasText(preferred)) {
            return trimToLength(preferred, maxLength);
        }
        return StringUtils.hasText(fallback) ? trimToLength(fallback, maxLength) : null;
    }

    private Optional<String> findCategoryCode(UUID... categoryIds) {
        List<UUID> ids = java.util.Arrays.stream(categoryIds)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<String> codes = jdbcTemplate.queryForList(
                """
                select slug
                  from catalog_categories
                 where id in (:ids)
                   and nullif(trim(slug), '') is not null
                 order by name
                 limit 1
                """,
                new MapSqlParameterSource("ids", ids),
                String.class);
        return codes.isEmpty() ? Optional.empty() : Optional.of(codes.getFirst());
    }

    private String json(Set<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Collections.emptySet() : values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize jsonb field", e);
        }
    }

    public record SourceRecordSnapshot(
            UUID id,
            String title,
            String aliasTitle,
            String year,
            Integer season,
            String dataHash,
            UUID catalogEntryId) {
    }

}
