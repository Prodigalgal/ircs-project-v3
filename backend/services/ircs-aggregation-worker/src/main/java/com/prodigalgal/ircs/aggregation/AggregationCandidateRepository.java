package com.prodigalgal.ircs.aggregation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

final class AggregationCandidateRepository {

    private static final double TITLE_RECALL_THRESHOLD = 0.35;
    private static final int TITLE_RECALL_LIMIT = 20;
    private static final int CONTEXT_BATCH_SIZE = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AggregationMatchingStrategy matchingStrategy;
    private final AggregationContextSearchClient contextSearchClient;
    private final AggregationMetadataNameRepository metadataNameRepository;

    AggregationCandidateRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            AggregationMatchingStrategy matchingStrategy,
            AggregationContextSearchClient contextSearchClient,
            AggregationMetadataNameRepository metadataNameRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.matchingStrategy = matchingStrategy;
        this.contextSearchClient = contextSearchClient;
        this.metadataNameRepository = metadataNameRepository;
    }

    Optional<UUID> findMatchingUnifiedVideo(RawVideoAggregationRecord rawVideo) {
        AggregationMatchPlan plan = findMatchPlan(rawVideo);
        return plan.hasRoot() ? Optional.of(plan.rootUnifiedVideoId()) : Optional.empty();
    }

    AggregationMatchPlan findMatchPlan(RawVideoAggregationRecord rawVideo) {
        Map<UUID, UnifiedVideoAggregationCandidate> candidates = new LinkedHashMap<>();
        for (UnifiedVideoAggregationCandidate candidate : findExternalIdCandidates(rawVideo)) {
            candidates.putIfAbsent(candidate.id(), candidate);
        }
        for (UnifiedVideoAggregationCandidate candidate : findTitleYearCandidates(rawVideo)) {
            candidates.putIfAbsent(candidate.id(), candidate);
        }
        if (candidates.isEmpty()) {
            return AggregationMatchPlan.none();
        }
        return matchingStrategy.findMatchPlan(rawVideo, List.copyOf(candidates.values()));
    }

    List<UnifiedVideoAggregationCandidate> findContextUnifiedCandidates(List<RawVideoAggregationRecord> rawVideos) {
        if (rawVideos == null || rawVideos.isEmpty()) {
            return List.of();
        }
        Map<UUID, UnifiedVideoAggregationCandidate> candidates = new LinkedHashMap<>();
        for (int start = 0; start < rawVideos.size(); start += CONTEXT_BATCH_SIZE) {
            List<RawVideoAggregationRecord> batch = rawVideos.subList(
                    start, Math.min(start + CONTEXT_BATCH_SIZE, rawVideos.size()));
            Map<Integer, List<UnifiedVideoAggregationCandidate>> external = findContextExternalIdBatch(batch);
            for (int index = 0; index < batch.size(); index++) {
                RawVideoAggregationRecord rawVideo = batch.get(index);
                List<UnifiedVideoAggregationCandidate> recalled = hasContextExternalId(rawVideo)
                        ? external.getOrDefault(index, List.of())
                        : findContextSearchCandidates(rawVideo);
                for (UnifiedVideoAggregationCandidate candidate : recalled) {
                    candidates.putIfAbsent(candidate.id(), candidate);
                }
            }
        }
        return metadataNameRepository.populateUnifiedMetadata(List.copyOf(candidates.values()));
    }

    private Map<Integer, List<UnifiedVideoAggregationCandidate>> findContextExternalIdBatch(
            List<RawVideoAggregationRecord> rawVideos) {
        if (rawVideos.stream().filter(this::hasContextExternalId).count() <= 1) {
            for (int index = 0; index < rawVideos.size(); index++) {
                if (hasContextExternalId(rawVideos.get(index))) {
                    return Map.of(index, findContextExternalIdCandidates(rawVideos.get(index)));
                }
            }
            return Map.of();
        }
        List<String> queries = new ArrayList<>();
        LinkedHashSet<String> doubanIds = new LinkedHashSet<>();
        LinkedHashSet<String> tmdbIds = new LinkedHashSet<>();
        MapSqlParameterSource params = new MapSqlParameterSource("limit", TITLE_RECALL_LIMIT);
        for (int index = 0; index < rawVideos.size(); index++) {
            RawVideoAggregationRecord rawVideo = rawVideos.get(index);
            List<String> clauses = new ArrayList<>();
            addExternalIdClause(clauses, params, "uv.douban_id", "doubanId" + index, rawVideo.doubanId());
            addExternalIdClause(clauses, params, "uv.tmdb_id", "tmdbId" + index, rawVideo.tmdbId());
            if (isValidExternalId(rawVideo.doubanId())) {
                doubanIds.add(rawVideo.doubanId().trim());
            }
            if (isValidExternalId(rawVideo.tmdbId())) {
                tmdbIds.add(rawVideo.tmdbId().trim());
            }
            if (!clauses.isEmpty()) {
                queries.add("""
                        (select %d as context_index, uv.id, uv.title, uv.alias_title, uv.subtitle, uv.year,
                                uv.total_episodes, uv.duration, uv.remarks, sc.name as category_name,
                                uv.douban_id, uv.tmdb_id, uv.imdb_id, uv.rotten_tomatoes_id, uv.season,
                                uv.updated_at as context_updated_at
                           from context_candidates uv
                           left join standard_category sc on sc.slug = uv.category_code
                          where %s
                          order by uv.updated_at desc
                          limit :limit)
                        """.formatted(index, String.join(" or ", clauses)));
            }
        }
        if (queries.isEmpty()) {
            return Map.of();
        }
        // Each input retains its own top-20 recall; a global LIMIT would silently lose matches.
        List<String> batchClauses = new ArrayList<>();
        if (!doubanIds.isEmpty()) {
            batchClauses.add("douban_id in (:contextDoubanIds)");
            params.addValue("contextDoubanIds", doubanIds);
        }
        if (!tmdbIds.isEmpty()) {
            batchClauses.add("tmdb_id in (:contextTmdbIds)");
            params.addValue("contextTmdbIds", tmdbIds);
        }
        List<ContextCandidate> rows = jdbcTemplate.query(
                """
                with context_candidates as materialized (
                    select id, title, alias_title, subtitle, year, total_episodes, duration, remarks,
                           category_code, douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, season, updated_at
                      from unified_videos
                     where %s
                )
                """.formatted(String.join(" or ", batchClauses))
                        + String.join(" union all ", queries) + " order by context_index, context_updated_at desc",
                params,
                (rs, rowNum) -> new ContextCandidate(rs.getInt("context_index"), mapUnifiedCandidate(rs, rowNum)));
        Map<Integer, List<UnifiedVideoAggregationCandidate>> byInput = new LinkedHashMap<>();
        for (ContextCandidate row : rows) {
            byInput.computeIfAbsent(row.index(), ignored -> new ArrayList<>()).add(row.candidate());
        }
        return byInput;
    }

    private record ContextCandidate(int index, UnifiedVideoAggregationCandidate candidate) {
    }

    private boolean hasContextExternalId(RawVideoAggregationRecord rawVideo) {
        return isValidExternalId(rawVideo.doubanId()) || isValidExternalId(rawVideo.tmdbId());
    }

    private List<UnifiedVideoAggregationCandidate> findContextExternalIdCandidates(RawVideoAggregationRecord rawVideo) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        addExternalIdClause(clauses, params, "douban_id", "doubanId", rawVideo.doubanId());
        addExternalIdClause(clauses, params, "tmdb_id", "tmdbId", rawVideo.tmdbId());
        if (clauses.isEmpty()) {
            return List.of();
        }

        return queryUnifiedCandidates(
                """
                where %s
                order by updated_at desc
                limit :limit
                """.formatted(String.join(" or ", clauses)),
                params.addValue("limit", TITLE_RECALL_LIMIT),
                false);
    }

    private List<UnifiedVideoAggregationCandidate> findContextSearchCandidates(RawVideoAggregationRecord rawVideo) {
        AggregationContextSearchClient.ContextSearchResult searchResult =
                contextSearchClient.findCandidateUnifiedVideoIds(rawVideo.title(), rawVideo.year());
        if (!searchResult.attempted()) {
            return findTitleYearCandidates(rawVideo, false);
        }
        if (searchResult.unifiedVideoIds().isEmpty()) {
            return List.of();
        }
        return queryUnifiedCandidatesByIds(searchResult.unifiedVideoIds());
    }

    private List<UnifiedVideoAggregationCandidate> findExternalIdCandidates(RawVideoAggregationRecord rawVideo) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        addExternalIdClause(clauses, params, "douban_id", "doubanId", rawVideo.doubanId());
        addExternalIdClause(clauses, params, "tmdb_id", "tmdbId", rawVideo.tmdbId());
        addExternalIdClause(clauses, params, "imdb_id", "imdbId", rawVideo.imdbId());
        addExternalIdClause(clauses, params, "rotten_tomatoes_id", "rottenTomatoesId", rawVideo.rottenTomatoesId());
        if (clauses.isEmpty()) {
            return List.of();
        }

        return queryUnifiedCandidates(
                """
                where %s
                order by updated_at desc
                limit :limit
                """.formatted(String.join(" or ", clauses)),
                params.addValue("limit", TITLE_RECALL_LIMIT));
    }

    private List<UnifiedVideoAggregationCandidate> findTitleYearCandidates(RawVideoAggregationRecord rawVideo) {
        return findTitleYearCandidates(rawVideo, true);
    }

    private List<UnifiedVideoAggregationCandidate> findTitleYearCandidates(
            RawVideoAggregationRecord rawVideo, boolean populateMetadata) {
        if (!StringUtils.hasText(rawVideo.title())) {
            return List.of();
        }
        String title = rawVideo.title().trim();
        String year = validOrNull(rawVideo.year());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("title", title)
                .addValue("titleLike", "%" + title.toLowerCase(Locale.ROOT) + "%")
                .addValue("titleRecallThreshold", TITLE_RECALL_THRESHOLD)
                .addValue("limit", TITLE_RECALL_LIMIT);
        if (year != null) {
            params.addValue("year", year);
        }
        return queryUnifiedCandidates(
                """
                where %s
                  (
                    similarity(title, :title) >= :titleRecallThreshold
                    or similarity(coalesce(alias_title, ''), :title) >= :titleRecallThreshold
                    or lower(title) like :titleLike
                    or lower(coalesce(alias_title, '')) like :titleLike
                  )
                order by greatest(
                    similarity(title, :title),
                    similarity(coalesce(alias_title, ''), :title)
                ) desc, updated_at desc
                limit :limit
                """.formatted(year == null ? "" : "year = :year and"),
                params,
                populateMetadata);
    }

    private List<UnifiedVideoAggregationCandidate> queryUnifiedCandidates(
            String whereAndOrderBy,
            MapSqlParameterSource params) {
        return queryUnifiedCandidates(whereAndOrderBy, params, true);
    }

    private List<UnifiedVideoAggregationCandidate> queryUnifiedCandidates(
            String whereAndOrderBy,
            MapSqlParameterSource params,
            boolean populateMetadata) {
        List<UnifiedVideoAggregationCandidate> candidates = jdbcTemplate.query(
                """
                select id, title, alias_title, subtitle, year,
                       total_episodes, duration, remarks,
                       category_name,
                       douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                       season
                from (
                    select uv.*,
                           sc.name as category_name
                    from unified_videos uv
                    left join standard_category sc on sc.slug = uv.category_code
                ) unified_candidate
                %s
                """.formatted(whereAndOrderBy),
                params,
                this::mapUnifiedCandidate);
        return populateMetadata ? metadataNameRepository.populateUnifiedMetadata(candidates) : candidates;
    }

    private List<UnifiedVideoAggregationCandidate> queryUnifiedCandidatesByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UnifiedVideoAggregationCandidate> candidates = queryUnifiedCandidates(
                """
                where id in (:ids)
                """,
                new MapSqlParameterSource("ids", ids),
                false);
        Map<UUID, UnifiedVideoAggregationCandidate> byId = new LinkedHashMap<>();
        for (UnifiedVideoAggregationCandidate candidate : candidates) {
            byId.putIfAbsent(candidate.id(), candidate);
        }
        List<UnifiedVideoAggregationCandidate> ordered = new ArrayList<>();
        for (UUID id : ids) {
            UnifiedVideoAggregationCandidate candidate = byId.get(id);
            if (candidate != null && ordered.stream().noneMatch(existing -> existing.id().equals(id))) {
                ordered.add(candidate);
            }
        }
        return List.copyOf(ordered);
    }

    private UnifiedVideoAggregationCandidate mapUnifiedCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new UnifiedVideoAggregationCandidate(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                rs.getString("subtitle"),
                rs.getString("year"),
                rs.getString("total_episodes"),
                rs.getString("duration"),
                rs.getString("remarks"),
                rs.getString("category_name"),
                rs.getString("douban_id"),
                rs.getString("tmdb_id"),
                rs.getString("imdb_id"),
                rs.getString("rotten_tomatoes_id"),
                (Integer) rs.getObject("season"));
    }

    private void addExternalIdClause(
            List<String> clauses,
            MapSqlParameterSource params,
            String column,
            String paramName,
            String value) {
        if (isValidExternalId(value)) {
            clauses.add(column + " = :" + paramName);
            params.addValue(paramName, value.trim());
        }
    }

    private boolean isValidExternalId(String value) {
        return StringUtils.hasText(value) && !"0".equals(value.trim());
    }

    private String validOrNull(String value) {
        return isValidExternalId(value) ? value.trim() : null;
    }
}
