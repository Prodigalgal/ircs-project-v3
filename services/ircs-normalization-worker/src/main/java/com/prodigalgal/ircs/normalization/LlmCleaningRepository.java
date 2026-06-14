package com.prodigalgal.ircs.normalization;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    List<LlmCleaningCandidate> claimCandidates(
            LlmCleaningKind kind,
            int limit,
            String workerId,
            Duration leaseDuration) {
        if (limit <= 0) {
            return List.of();
        }
        Instant claimedUntil = Instant.now().plus(leaseDuration == null || !leaseDuration.isPositive()
                ? Duration.ofMinutes(10)
                : leaseDuration);
        return jdbcTemplate.query(
                claimSql(kind),
                new MapSqlParameterSource()
                        .addValue("limit", limit)
                        .addValue("workerId", workerId)
                        .addValue("claimedUntil", Timestamp.from(claimedUntil)),
                (rs, rowNum) -> new LlmCleaningCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("raw_value")));
    }

    List<LlmCleaningStandard> findStandards(LlmCleaningKind kind) {
        return jdbcTemplate.query(
                standardSql(kind),
                Map.of(),
                (rs, rowNum) -> new LlmCleaningStandard(
                        rs.getObject("id", UUID.class),
                        rs.getString("name")));
    }

    void releaseClaims(LlmCleaningKind kind, List<UUID> ids, String workerId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                """
                update %s
                   set llm_cleaning_claimed_by = null,
                       llm_cleaning_claimed_until = null
                 where id in (:ids)
                   and llm_cleaning_claimed_by = :workerId
                   and %s is null
                """.formatted(rawTable(kind), standardColumn(kind)),
                new MapSqlParameterSource()
                        .addValue("ids", ids)
                        .addValue("workerId", workerId));
    }

    boolean applyMatch(LlmCleaningKind kind, UUID rawId, UUID standardId) {
        int updated = jdbcTemplate.update(
                """
                update %s
                   set %s = :standardId,
                       llm_cleaning_claimed_by = null,
                       llm_cleaning_claimed_until = null,
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
                    llm_cleaning_claimed_by = null,
                    llm_cleaning_claimed_until = null,
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

    private String claimSql(LlmCleaningKind kind) {
        return switch (kind) {
            case GENRE -> """
                    with candidates as (
                        select id
                        from raw_genres
                        where standard_genre_id is null
                          and source_value is not null
                          and (llm_cleaning_claimed_until is null or llm_cleaning_claimed_until <= now())
                        order by updated_at asc, id asc
                        limit :limit
                        for update skip locked
                    )
                    update raw_genres rg
                       set llm_cleaning_claimed_by = :workerId,
                           llm_cleaning_claimed_until = :claimedUntil,
                           updated_at = now()
                      from candidates
                     where rg.id = candidates.id
                    returning rg.id, rg.source_value as raw_value
                    """;
            case LANGUAGE -> """
                    with candidates as (
                        select id
                        from raw_languages
                        where standard_language_id is null
                          and source_value is not null
                          and (llm_cleaning_claimed_until is null or llm_cleaning_claimed_until <= now())
                        order by updated_at asc, id asc
                        limit :limit
                        for update skip locked
                    )
                    update raw_languages rl
                       set llm_cleaning_claimed_by = :workerId,
                           llm_cleaning_claimed_until = :claimedUntil,
                           updated_at = now()
                      from candidates
                     where rl.id = candidates.id
                    returning rl.id, rl.source_value as raw_value
                    """;
            case AREA -> """
                    with candidates as (
                        select id
                        from raw_areas
                        where standard_area_id is null
                          and source_value is not null
                          and (llm_cleaning_claimed_until is null or llm_cleaning_claimed_until <= now())
                        order by updated_at asc, id asc
                        limit :limit
                        for update skip locked
                    )
                    update raw_areas ra
                       set llm_cleaning_claimed_by = :workerId,
                           llm_cleaning_claimed_until = :claimedUntil,
                           updated_at = now()
                      from candidates
                     where ra.id = candidates.id
                    returning ra.id, ra.source_value as raw_value
                    """;
            case CATEGORY -> """
                    with candidates as (
                        select id
                        from raw_category
                        where category_id is null
                          and source_name is not null
                          and (llm_cleaning_claimed_until is null or llm_cleaning_claimed_until <= now())
                        order by updated_at asc, id asc
                        limit :limit
                        for update skip locked
                    )
                    update raw_category rc
                       set llm_cleaning_claimed_by = :workerId,
                           llm_cleaning_claimed_until = :claimedUntil,
                           updated_at = now()
                      from candidates
                     where rc.id = candidates.id
                    returning rc.id, rc.source_name as raw_value
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
