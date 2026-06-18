package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.common.metadata.MetadataNameOwnerType;
import com.prodigalgal.ircs.common.metadata.MetadataNameValkeyCache;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RawVideoNormalizationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectProvider<MetadataNameValkeyCache> metadataNameCacheProvider;

    @Autowired
    public RawVideoNormalizationRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectProvider<MetadataNameValkeyCache> metadataNameCacheProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.metadataNameCacheProvider = metadataNameCacheProvider;
    }

    RawVideoNormalizationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    public Optional<RawVideoRecord> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select id, normalization_status, normalization_retry_count, raw_metadata::text, locked_fields::text,
                           title, alias_title, season, subtitle, description, year, area, raw_language_str, remarks, score,
                           total_episodes, duration, douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                           data_source_id, data_hash
                    from raw_videos
                    where id = :id
                    """,
                    Map.of("id", id),
                    (rs, rowNum) -> new RawVideoRecord(
                            rs.getObject("id", UUID.class),
                            rs.getString("normalization_status"),
                            (Integer) rs.getObject("normalization_retry_count"),
                            rs.getString("raw_metadata"),
                            rs.getString("locked_fields"),
                            rs.getString("title"),
                            rs.getString("alias_title"),
                            (Integer) rs.getObject("season"),
                            rs.getString("subtitle"),
                            rs.getString("description"),
                            rs.getString("year"),
                            rs.getString("area"),
                            rs.getString("raw_language_str"),
                            rs.getString("remarks"),
                            rs.getBigDecimal("score"),
                            rs.getString("total_episodes"),
                            rs.getString("duration"),
                            rs.getString("douban_id"),
                            rs.getString("tmdb_id"),
                            rs.getString("imdb_id"),
                            rs.getString("rotten_tomatoes_id"),
                            rs.getObject("data_source_id", UUID.class),
                            rs.getString("data_hash"))));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public MarkReadyResult markReady(UUID id, RawVideoPatch patch) {
        jdbcTemplate.update(
                """
                update raw_videos
                set title = :title,
                    alias_title = :aliasTitle,
                    season = :season,
                    subtitle = :subtitle,
                    description = :description,
                    year = :year,
                    area = :area,
                    raw_language_str = :rawLanguageStr,
                    remarks = :remarks,
                    score = :score,
                    total_episodes = :totalEpisodes,
                    duration = :duration,
                    douban_id = :doubanId,
                    tmdb_id = :tmdbId,
                    imdb_id = :imdbId,
                    rotten_tomatoes_id = :rottenTomatoesId,
                    normalization_status = 'READY',
                    normalization_retry_count = 0,
                    next_normalization_retry_time = null,
                    updated_at = now()
                where id = :id
                """,
                params(id, patch));
        List<LlmCleaningRawTerm> llmCleaningTerms = new ArrayList<>();
        bindRawCategory(
                id,
                patch.rawCategoryDataSourceId(),
                patch.rawCategorySourceCode(),
                patch.rawCategorySourceName())
                .ifPresent(llmCleaningTerms::add);
        llmCleaningTerms.addAll(
                replaceRawRelations("raw_genres", "video_raw_genres", "raw_genre_id", id, patch.rawGenreValues()));
        llmCleaningTerms.addAll(
                replaceRawRelations("raw_languages", "video_raw_languages", "raw_language_id", id, patch.rawLanguageValues()));
        llmCleaningTerms.addAll(
                replaceRawRelations("raw_areas", "video_raw_areas", "raw_area_id", id, patch.rawAreaValues()));
        replaceNamedRelations("actors", "video_actors", "actor_id", id, patch.actorValues());
        replaceNamedRelations("directors", "video_directors", "director_id", id, patch.directorValues());
        evictMetadataNameCache(id);
        return new MarkReadyResult(List.copyOf(llmCleaningTerms));
    }

    public void markFailure(UUID id, String status, int retryCount, Instant nextRetryTime) {
        jdbcTemplate.update(
                """
                update raw_videos
                set normalization_status = :status,
                    normalization_retry_count = :retryCount,
                    next_normalization_retry_time = :nextRetryTime,
                    updated_at = now()
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status)
                        .addValue("retryCount", retryCount)
                        .addValue("nextRetryTime", nextRetryTime == null ? null : Timestamp.from(nextRetryTime)));
    }

    public List<UUID> sampleRawVideoIds(int limit) {
        return jdbcTemplate.queryForList(
                """
                select id
                  from raw_videos
                 order by updated_at asc nulls first, id asc
                 limit :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                UUID.class);
    }

    public long countRawVideos() {
        Long value = jdbcTemplate.queryForObject(
                "select count(*) from raw_videos",
                new MapSqlParameterSource(),
                Long.class);
        return value == null ? 0 : value;
    }

    public int resetAllNormalizationPending() {
        return jdbcTemplate.update(
                """
                update raw_videos
                   set normalization_status = 'PENDING',
                       updated_at = now()
                """,
                new MapSqlParameterSource());
    }

    private MapSqlParameterSource params(UUID id, RawVideoPatch patch) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("title", patch.title())
                .addValue("aliasTitle", patch.aliasTitle())
                .addValue("season", patch.season())
                .addValue("subtitle", patch.subtitle())
                .addValue("description", patch.description())
                .addValue("year", patch.year())
                .addValue("area", patch.area())
                .addValue("rawLanguageStr", patch.rawLanguageStr())
                .addValue("remarks", patch.remarks())
                .addValue("score", patch.score())
                .addValue("totalEpisodes", patch.totalEpisodes())
                .addValue("duration", patch.duration())
                .addValue("doubanId", patch.doubanId())
                .addValue("tmdbId", patch.tmdbId())
                .addValue("imdbId", patch.imdbId())
                .addValue("rottenTomatoesId", patch.rottenTomatoesId());
    }

    private Optional<LlmCleaningRawTerm> bindRawCategory(
            UUID rawVideoId,
            UUID dataSourceId,
            String sourceCode,
            String sourceName) {
        if (dataSourceId == null || !StringUtils.hasText(sourceCode)) {
            return Optional.empty();
        }
        String safeSourceCode = trimToLength(sourceCode, 100);
        String safeSourceName = StringUtils.hasText(sourceName) ? trimToLength(sourceName, 255) : null;
        UUID rawCategoryId = jdbcTemplate.queryForObject(
                """
                insert into raw_category (
                    id, created_at, updated_at, version, data_source_id, source_code, source_name, category_id
                )
                values (
                    :id,
                    now(),
                    now(),
                    0,
                    :dataSourceId,
                    :sourceCode,
                    :sourceName,
                    (
                        select id
                        from standard_category
                        where lower(name) = lower(coalesce(:sourceName, :sourceCode))
                           or lower(slug) = lower(:sourceCode)
                        order by name
                        limit 1
                    )
                )
                on conflict (data_source_id, source_code) do update set
                    source_name = coalesce(excluded.source_name, raw_category.source_name),
                    category_id = coalesce(raw_category.category_id, excluded.category_id),
                    updated_at = excluded.updated_at
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("dataSourceId", dataSourceId)
                        .addValue("sourceCode", safeSourceCode)
                        .addValue("sourceName", safeSourceName),
                UUID.class);
        if (rawCategoryId == null) {
            return Optional.empty();
        }
        jdbcTemplate.update(
                """
                update raw_videos
                set data_source_category_id = :rawCategoryId,
                    updated_at = now()
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", rawVideoId)
                        .addValue("rawCategoryId", rawCategoryId));
        return Optional.of(new LlmCleaningRawTerm(
                "category",
                rawCategoryId,
                StringUtils.hasText(safeSourceName) ? safeSourceName : safeSourceCode));
    }

    private List<LlmCleaningRawTerm> replaceRawRelations(
            String rawTable,
            String joinTable,
            String rawIdColumn,
            UUID rawVideoId,
            Collection<String> values) {
        List<String> safeValues = values == null
                ? List.of()
                : values.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (safeValues.isEmpty()) {
            return List.of();
        }

        upsertRawValues(rawTable, safeValues);
        List<LlmCleaningRawTerm> rawTerms = jdbcTemplate.query(
                "select id, source_value from %s where source_value in (:values)".formatted(rawTable),
                new MapSqlParameterSource("values", safeValues),
                (rs, rowNum) -> new LlmCleaningRawTerm(
                        llmCleaningKind(rawTable),
                        rs.getObject("id", UUID.class),
                        rs.getString("source_value")));
        List<UUID> rawIds = rawTerms.stream().map(LlmCleaningRawTerm::rawId).toList();
        if (rawIds.isEmpty()) {
            return List.of();
        }

        jdbcTemplate.update(
                "delete from %s where video_id = :videoId".formatted(joinTable),
                new MapSqlParameterSource("videoId", rawVideoId));
        List<MapSqlParameterSource> batch = rawIds.stream()
                .map(rawId -> new MapSqlParameterSource()
                        .addValue("videoId", rawVideoId)
                        .addValue("rawId", rawId))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into %s (video_id, %s)
                values (:videoId, :rawId)
                on conflict do nothing
                """.formatted(joinTable, rawIdColumn),
                batch.toArray(MapSqlParameterSource[]::new));
        return rawTerms;
    }

    private void upsertRawValues(String table, List<String> values) {
        List<MapSqlParameterSource> batch = values.stream()
                .map(value -> new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("sourceValue", value))
                .toList();
        jdbcTemplate.batchUpdate(
                rawValueUpsertSql(table),
                batch.toArray(MapSqlParameterSource[]::new));
    }

    private String rawValueUpsertSql(String table) {
        return switch (table) {
            case "raw_genres" -> """
                    insert into raw_genres (
                        id, created_at, updated_at, version, source_value, standard_genre_id
                    )
                    values (
                        :id,
                        now(),
                        now(),
                        0,
                        :sourceValue,
                        (
                            select id
                            from standard_genre
                            where lower(name) = lower(:sourceValue)
                            order by name
                            limit 1
                        )
                    )
                    on conflict (source_value) do update set
                        standard_genre_id = coalesce(raw_genres.standard_genre_id, excluded.standard_genre_id),
                        updated_at = excluded.updated_at
                    """;
            case "raw_languages" -> """
                    insert into raw_languages (
                        id, created_at, updated_at, version, source_value, standard_language_id
                    )
                    values (
                        :id,
                        now(),
                        now(),
                        0,
                        :sourceValue,
                        (
                            select id
                            from standard_languages
                            where lower(name) = lower(:sourceValue)
                               or lower(code) = lower(:sourceValue)
                               or lower(coalesce(english_name, '')) = lower(:sourceValue)
                               or lower(coalesce(native_name, '')) = lower(:sourceValue)
                            order by name
                            limit 1
                        )
                    )
                    on conflict (source_value) do update set
                        standard_language_id = coalesce(raw_languages.standard_language_id, excluded.standard_language_id),
                        updated_at = excluded.updated_at
                    """;
            case "raw_areas" -> """
                    insert into raw_areas (
                        id, created_at, updated_at, version, source_value, standard_area_id
                    )
                    values (
                        :id,
                        now(),
                        now(),
                        0,
                        :sourceValue,
                        (
                            select id
                            from standard_areas
                            where lower(name) = lower(:sourceValue)
                               or lower(code) = lower(:sourceValue)
                            order by name
                            limit 1
                        )
                    )
                    on conflict (source_value) do update set
                        standard_area_id = coalesce(raw_areas.standard_area_id, excluded.standard_area_id),
                        updated_at = excluded.updated_at
                    """;
            default -> """
                    insert into %s (id, created_at, updated_at, version, source_value)
                    values (:id, now(), now(), 0, :sourceValue)
                    on conflict (source_value) do update set updated_at = excluded.updated_at
                    """.formatted(table);
        };
    }

    private void replaceNamedRelations(
            String personTable,
            String joinTable,
            String personIdColumn,
            UUID rawVideoId,
            Collection<String> names) {
        List<String> safeNames = names == null
                ? List.of()
                : names.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (safeNames.isEmpty()) {
            return;
        }

        upsertNamedValues(personTable, safeNames);
        List<UUID> personIds = jdbcTemplate.queryForList(
                "select id from %s where name in (:names)".formatted(personTable),
                new MapSqlParameterSource("names", safeNames),
                UUID.class);
        if (personIds.isEmpty()) {
            return;
        }

        jdbcTemplate.update(
                "delete from %s where video_id = :videoId".formatted(joinTable),
                new MapSqlParameterSource("videoId", rawVideoId));
        List<MapSqlParameterSource> batch = personIds.stream()
                .map(personId -> new MapSqlParameterSource()
                        .addValue("videoId", rawVideoId)
                        .addValue("personId", personId))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into %s (video_id, %s)
                values (:videoId, :personId)
                on conflict do nothing
                """.formatted(joinTable, personIdColumn),
                batch.toArray(MapSqlParameterSource[]::new));
    }

    private void upsertNamedValues(String table, List<String> names) {
        List<MapSqlParameterSource> batch = names.stream()
                .map(name -> new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("name", name))
                .toList();
        jdbcTemplate.batchUpdate(
                """
                insert into %s (id, created_at, updated_at, version, name)
                values (:id, now(), now(), 0, :name)
                on conflict (name) do update set updated_at = excluded.updated_at
                """.formatted(table),
                batch.toArray(MapSqlParameterSource[]::new));
    }

    private String trimToLength(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String llmCleaningKind(String rawTable) {
        return switch (rawTable) {
            case "raw_genres" -> "genre";
            case "raw_languages" -> "language";
            case "raw_areas" -> "area";
            default -> throw new IllegalArgumentException("Unsupported LLM cleaning table: " + rawTable);
        };
    }

    private void evictMetadataNameCache(UUID rawVideoId) {
        MetadataNameValkeyCache metadataNameCache = metadataNameCacheProvider == null
                ? null
                : metadataNameCacheProvider.getIfAvailable();
        if (metadataNameCache != null) {
            metadataNameCache.evictOwner(MetadataNameOwnerType.RAW, List.of(rawVideoId));
        }
    }

    public record MarkReadyResult(List<LlmCleaningRawTerm> llmCleaningTerms) {
    }

    public record LlmCleaningRawTerm(String kind, UUID rawId, String rawValue) {
    }
}
