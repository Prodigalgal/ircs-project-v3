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
        requireCategory(kind);
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
        requireCategory(kind);
        return mapCategoryNoiseToFallback(rawId);
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
        requireCategory(kind);
        return switch (kind) {
            case CATEGORY -> """
                    select id, source_name as raw_value
                    from raw_category
                    where id = :rawId
                      and category_id is null
                      and source_name is not null
                    limit 1
                    """;
            default -> throw unsupportedKind(kind);
        };
    }

    private String standardSql(LlmCleaningKind kind) {
        requireCategory(kind);
        return switch (kind) {
            case CATEGORY -> "select id, name from standard_category where name is not null order by name";
            default -> throw unsupportedKind(kind);
        };
    }

    private String rawTable(LlmCleaningKind kind) {
        requireCategory(kind);
        return switch (kind) {
            case CATEGORY -> "raw_category";
            default -> throw unsupportedKind(kind);
        };
    }

    private String standardColumn(LlmCleaningKind kind) {
        requireCategory(kind);
        return switch (kind) {
            case CATEGORY -> "category_id";
            default -> throw unsupportedKind(kind);
        };
    }

    private void requireCategory(LlmCleaningKind kind) {
        if (kind != LlmCleaningKind.CATEGORY) {
            throw unsupportedKind(kind);
        }
    }

    private IllegalArgumentException unsupportedKind(LlmCleaningKind kind) {
        return new IllegalArgumentException("Only category LLM cleaning is supported after content metadata flattening: " + kind);
    }

    static String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
