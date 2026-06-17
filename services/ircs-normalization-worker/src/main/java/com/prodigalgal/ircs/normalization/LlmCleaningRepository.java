package com.prodigalgal.ircs.normalization;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
class LlmCleaningRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    Optional<LlmCleaningCandidate> findCandidate(LlmCleaningKind kind, UUID rawId) {
        if (rawId == null) {
            return Optional.empty();
        }
        List<LlmCleaningCandidate> candidates = jdbcTemplate.query(
                candidateSql(kind),
                new MapSqlParameterSource("rawId", rawId),
                (rs, rowNum) -> new LlmCleaningCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("raw_value")));
        return candidates.stream().findFirst();
    }

    List<LlmCleaningStandard> findStandards(LlmCleaningKind kind) {
        return jdbcTemplate.query(
                standardSql(kind),
                Map.of(),
                (rs, rowNum) -> new LlmCleaningStandard(
                        rs.getObject("id", UUID.class),
                        rs.getString("name")));
    }

    boolean applyMatch(LlmCleaningKind kind, UUID rawId, UUID standardId) {
        int updated = jdbcTemplate.update(
                """
                update %s
                   set %s = :standardId,
                       updated_at = now()
                 where id = :rawId
                   and %s is null
                """.formatted(rawTable(kind), standardColumn(kind), standardColumn(kind)),
                new MapSqlParameterSource()
                        .addValue("rawId", rawId)
                        .addValue("standardId", standardId));
        return updated > 0;
    }

    boolean applyNoise(LlmCleaningKind kind, UUID rawId) {
        if (kind == LlmCleaningKind.CATEGORY) {
            return mapCategoryNoiseToFallback(rawId);
        }
        String joinTable = switch (kind) {
            case GENRE -> "video_raw_genres";
            case LANGUAGE -> "video_raw_languages";
            case AREA -> "video_raw_areas";
            case CATEGORY -> throw new IllegalArgumentException("CATEGORY noise uses fallback mapping");
        };
        String joinColumn = switch (kind) {
            case GENRE -> "raw_genre_id";
            case LANGUAGE -> "raw_language_id";
            case AREA -> "raw_area_id";
            case CATEGORY -> throw new IllegalArgumentException("CATEGORY noise uses fallback mapping");
        };
        jdbcTemplate.update(
                """
                delete from %s
                where %s = :rawId
                  and exists (
                        select 1
                        from %s
                        where id = :rawId
                          and %s is null
                    )
                """.formatted(joinTable, joinColumn, rawTable(kind), standardColumn(kind)),
                new MapSqlParameterSource("rawId", rawId));
        int deleted = jdbcTemplate.update(
                "delete from %s where id = :rawId and %s is null".formatted(rawTable(kind), standardColumn(kind)),
                new MapSqlParameterSource("rawId", rawId));
        return deleted > 0;
    }

    private boolean mapCategoryNoiseToFallback(UUID rawId) {
        int updated = jdbcTemplate.update(
                """
                update raw_category
                set category_id = (
                        select id
                        from standard_category
                        where lower(name) = lower(:fallback)
                        order by name
                        limit 1
                    ),
                    updated_at = now()
                where id = :rawId
                  and category_id is null
                  and exists (
                        select 1
                        from standard_category
                        where lower(name) = lower(:fallback)
                    )
                """,
                new MapSqlParameterSource()
                        .addValue("rawId", rawId)
                        .addValue("fallback", "其他"));
        if (updated > 0) {
            return true;
        }
        Boolean fallbackExists = jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                    from standard_category
                    where lower(name) = lower(:fallback)
                )
                """,
                new MapSqlParameterSource("fallback", "其他"),
                Boolean.class);
        if (!Boolean.TRUE.equals(fallbackExists)) {
            throw new LlmCleaningException.ProviderError("Missing fallback category: other");
        }
        return false;
    }

    private String candidateSql(LlmCleaningKind kind) {
        return switch (kind) {
            case GENRE -> """
                    select id, source_value as raw_value
                    from raw_genres
                    where id = :rawId
                      and standard_genre_id is null
                      and source_value is not null
                    limit 1
                    """;
            case LANGUAGE -> """
                    select id, source_value as raw_value
                    from raw_languages
                    where id = :rawId
                      and standard_language_id is null
                      and source_value is not null
                    limit 1
                    """;
            case AREA -> """
                    select id, source_value as raw_value
                    from raw_areas
                    where id = :rawId
                      and standard_area_id is null
                      and source_value is not null
                    limit 1
                    """;
            case CATEGORY -> """
                    select id, source_name as raw_value
                    from raw_category
                    where id = :rawId
                      and category_id is null
                      and source_name is not null
                    limit 1
                    """;
        };
    }

    private String standardSql(LlmCleaningKind kind) {
        return switch (kind) {
            case GENRE -> "select id, name from standard_genre where name is not null order by name";
            case LANGUAGE -> "select id, name from standard_languages where name is not null order by name";
            case AREA -> "select id, name from standard_areas where name is not null order by name";
            case CATEGORY -> "select id, name from standard_category where name is not null order by name";
        };
    }

    private String rawTable(LlmCleaningKind kind) {
        return switch (kind) {
            case GENRE -> "raw_genres";
            case LANGUAGE -> "raw_languages";
            case AREA -> "raw_areas";
            case CATEGORY -> "raw_category";
        };
    }

    private String standardColumn(LlmCleaningKind kind) {
        return switch (kind) {
            case GENRE -> "standard_genre_id";
            case LANGUAGE -> "standard_language_id";
            case AREA -> "standard_area_id";
            case CATEGORY -> "category_id";
        };
    }

    static String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
