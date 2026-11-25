package com.prodigalgal.ircs.metadata.result.infrastructure;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import com.prodigalgal.ircs.contentmetadata.PersonNames;
import com.prodigalgal.ircs.metadata.result.domain.RawVideoMetadataRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RawVideoMetadataRepository {

    public static final String SELECT_BY_ID = """
            select id, douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                   description, score, year, alias_title, raw_metadata::text, locked_fields,
                   enrichment_status, enrichment_retry_count, aggregation_status, data_hash,
                   actor_names::text, director_names::text, area_codes::text, language_codes::text, genre_codes::text
            from raw_videos
            where id = ?
            """;

    public static final String UPDATE_METADATA = """
            update raw_videos
            set douban_id = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'doubanId') then douban_id else ? end,
                tmdb_id = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'tmdbId') then tmdb_id else ? end,
                imdb_id = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'imdbId') then imdb_id else ? end,
                rotten_tomatoes_id = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'rottenTomatoesId')
                    then rotten_tomatoes_id else ? end,
                description = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'description') then description else ? end,
                score = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'score') then score else ? end,
                year = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'year') then year else ? end,
                alias_title = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'aliasTitle') then alias_title else ? end,
                raw_metadata = cast(? as jsonb),
                enrichment_status = ?,
                enrichment_retry_count = ?,
                aggregation_status = ?,
                actor_names = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'actors')
                    then actor_names else cast(? as jsonb) end,
                director_names = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'directors')
                    then director_names else cast(? as jsonb) end,
                area_codes = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'area')
                    then area_codes else cast(? as jsonb) end,
                language_codes = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'language')
                    then language_codes else cast(? as jsonb) end,
                genre_codes = case when jsonb_exists(coalesce(locked_fields, '[]'::jsonb), 'genres')
                    then genre_codes else cast(? as jsonb) end,
                updated_at = now()
            where id = ? and data_hash is not distinct from ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Optional<RawVideoMetadataRecord> findById(UUID id) {
        return findById(id, false);
    }

    public Optional<RawVideoMetadataRecord> findByIdForUpdate(UUID id) {
        return findById(id, true);
    }

    private Optional<RawVideoMetadataRecord> findById(UUID id, boolean forUpdate) {
        return jdbcTemplate.query(SELECT_BY_ID + (forUpdate ? "\nfor update" : ""), ps -> ps.setObject(1, id), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        });
    }

    public int updateEnrichmentStatusIfPending(UUID id, String dataHash, String status, Integer retryCount) {
        return jdbcTemplate.update("""
                update raw_videos set enrichment_status=?, enrichment_retry_count=?, updated_at=now()
                where id=? and data_hash is not distinct from ? and enrichment_status='PENDING'
                """, status, retryCount, id, dataHash);
    }

    public int save(RawVideoMetadataRecord record) {
        materializeRelations(record);
        return jdbcTemplate.update(
                UPDATE_METADATA,
                record.getDoubanId(),
                record.getTmdbId(),
                record.getImdbId(),
                record.getRottenTomatoesId(),
                record.getDescription(),
                record.getScore(),
                record.getYear(),
                record.getAliasTitle(),
                record.getRawMetadata(),
                record.getEnrichmentStatus(),
                record.getEnrichmentRetryCount(),
                record.getAggregationStatus(),
                objectMapper.writeValueAsString(record.getActorNames()),
                objectMapper.writeValueAsString(record.getDirectorNames()),
                objectMapper.writeValueAsString(record.getAreaCodes()),
                objectMapper.writeValueAsString(record.getLanguageCodes()),
                objectMapper.writeValueAsString(record.getGenreCodes()),
                record.getId(), record.getDataHash());
    }

    private void materializeRelations(RawVideoMetadataRecord record) {
        JsonNode metadata = objectMapper.readTree(record.getRawMetadata() == null ? "{}" : record.getRawMetadata());
        if (metadata == null || !metadata.isObject()) {
            throw new IllegalStateException("Metadata projection requires an object");
        }
        if (!record.isFieldLocked("actors")) {
            LinkedHashSet<String> names = new LinkedHashSet<>(safe(record.getActorNames()));
            collectPeopleValues(metadata, names, "actorNames", "actors", "actor");
            record.setActorNames(PersonNames.normalize(names));
        }
        if (!record.isFieldLocked("directors")) {
            LinkedHashSet<String> names = new LinkedHashSet<>(safe(record.getDirectorNames()));
            collectPeopleValues(metadata, names, "directorNames", "directors", "director");
            record.setDirectorNames(PersonNames.normalize(names));
        }
        Set<String> areas = record.isFieldLocked("area") ? Set.of() : values(metadata, "area", "region");
        Set<String> languages = record.isFieldLocked("language") ? Set.of() : values(metadata, "language", "lang");
        Set<String> genres = record.isFieldLocked("genres") ? Set.of() : values(metadata, "genreNames", "genres", "genre");
        if (areas.isEmpty() && languages.isEmpty() && genres.isEmpty()) {
            return;
        }
        // Static dictionaries have bounded cardinality. Resolve all three relations in one round trip.
        List<Map.Entry<String, String>> codes = jdbcTemplate.query("""
                with inputs as materialized (
                    select 'AREA' as kind, lower(value) as token from jsonb_array_elements_text(cast(? as jsonb))
                    union all select 'LANGUAGE', lower(value) from jsonb_array_elements_text(cast(? as jsonb))
                    union all select 'GENRE', lower(value) from jsonb_array_elements_text(cast(? as jsonb))
                )
                select distinct 'AREA' as kind, a.code from standard_areas a join inputs i
                  on i.kind='AREA' and i.token in (lower(a.name), lower(a.code))
                  where nullif(trim(a.code),'') is not null
                union select distinct 'LANGUAGE', l.code from standard_languages l join inputs i
                  on i.kind='LANGUAGE' and i.token in (lower(l.name), lower(l.code),
                      lower(coalesce(l.english_name,'')), lower(coalesce(l.native_name,'')))
                  where nullif(trim(l.code),'') is not null
                union select distinct 'GENRE', g.code from standard_genre g join inputs i
                  on i.kind='GENRE' and i.token in (lower(g.name), lower(g.code))
                  where nullif(trim(g.code),'') is not null
                """, (rs, index) -> Map.entry(rs.getString("kind"), rs.getString("code")),
                objectMapper.writeValueAsString(areas), objectMapper.writeValueAsString(languages),
                objectMapper.writeValueAsString(genres));
        if (!record.isFieldLocked("area")) {
            record.setAreaCodes(mergeCodes(record.getAreaCodes(), codes, "AREA"));
        }
        if (!record.isFieldLocked("language")) {
            record.setLanguageCodes(mergeCodes(record.getLanguageCodes(), codes, "LANGUAGE"));
        }
        if (!record.isFieldLocked("genres")) {
            record.setGenreCodes(mergeCodes(record.getGenreCodes(), codes, "GENRE"));
        }
    }

    private Set<String> mergeCodes(Set<String> current, List<Map.Entry<String, String>> resolved, String kind) {
        LinkedHashSet<String> result = new LinkedHashSet<>(safe(current));
        resolved.stream().filter(entry -> entry.getKey().equals(kind)).map(Map.Entry::getValue).forEach(result::add);
        return result;
    }

    private Set<String> values(JsonNode metadata, String... keys) {
        Set<String> result = new LinkedHashSet<>();
        collectValues(metadata, result, keys);
        return result;
    }

    private void collectValues(JsonNode metadata, Set<String> target, String... keys) {
        for (String key : keys) {
            collectValue(metadata.get(key), target, true);
        }
    }

    private void collectPeopleValues(JsonNode metadata, Set<String> target, String... keys) {
        for (String key : keys) {
            // PersonNames must decode entities and reject whole placeholders before splitting.
            collectValue(metadata.get(key), target, false);
        }
    }

    private void collectValue(JsonNode value, Set<String> target, boolean splitDelimited) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(child -> collectValue(child, target, splitDelimited));
        } else if (value.isTextual()) {
            String[] tokens = splitDelimited ? value.asText().split("[,，、/|;；]+") : new String[]{value.asText()};
            for (String token : tokens) {
                if (!token.isBlank()) {
                    target.add(token.trim());
                }
            }
        }
    }

    private Set<String> safe(Set<String> values) {
        return values == null ? Set.of() : values;
    }

    private RawVideoMetadataRecord mapRow(ResultSet rs) throws SQLException {
        return new RawVideoMetadataRecord(
                rs.getObject("id", UUID.class),
                rs.getString("douban_id"),
                rs.getString("tmdb_id"),
                rs.getString("imdb_id"),
                rs.getString("rotten_tomatoes_id"),
                rs.getString("description"),
                rs.getBigDecimal("score"),
                rs.getString("year"),
                rs.getString("alias_title"),
                rs.getString("raw_metadata"),
                readLockedFields(rs.getString("locked_fields")),
                rs.getString("enrichment_status"),
                (Integer) rs.getObject("enrichment_retry_count"),
                rs.getString("aggregation_status"),
                rs.getString("data_hash"),
                readSet(rs.getString("actor_names")), readSet(rs.getString("director_names")),
                readSet(rs.getString("area_codes")), readSet(rs.getString("language_codes")),
                readSet(rs.getString("genre_codes")));
    }

    private Set<String> readSet(String json) {
        return json == null ? Set.of() : objectMapper.readValue(json, new TypeReference<Set<String>>() {});
    }

    private Set<String> readLockedFields(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptySet();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception error) {
            throw new IllegalStateException("Cannot apply metadata with malformed field locks", error);
        }
    }
}
