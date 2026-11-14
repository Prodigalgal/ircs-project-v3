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
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

final class AggregationCandidateRepository {

    private static final double TITLE_RECALL_THRESHOLD = 0.35;
    private static final int TITLE_RECALL_LIMIT = 20;
    private static final int CONTEXT_BATCH_SIZE = 100;
    private static final int ID_LOOKUP_BATCH_SIZE = 100;
    private static final Set<String> UNKNOWN_YEARS = Set.of(
            "0", "unknown", "null", "none", "n/a", "-", "未知", "不详", "暂无", "待定");
    private static final List<ExternalIdColumn> EXTERNAL_ID_COLUMNS = List.of(
            new ExternalIdColumn("douban", "douban_id", "doubanId", "contextDoubanIds"),
            new ExternalIdColumn("tmdb", "tmdb_id", "tmdbId", "contextTmdbIds"),
            new ExternalIdColumn("imdb", "imdb_id", "imdbId", "contextImdbIds"),
            new ExternalIdColumn("rt", "rotten_tomatoes_id", "rottenTomatoesId", "contextRtIds"));

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
        addCandidatesUpToLimit(candidates, findExternalIdCandidates(rawVideo));
        addCandidatesUpToLimit(candidates, findTitleYearCandidates(rawVideo, false));
        if (candidates.isEmpty()) {
            return AggregationMatchPlan.none();
        }
        List<UnifiedVideoAggregationCandidate> enriched =
                metadataNameRepository.populateUnifiedMetadata(List.copyOf(candidates.values()));
        return matchingStrategy.findMatchPlan(rawVideo, enriched);
    }

    List<UnifiedVideoAggregationCandidate> findContextUnifiedCandidates(List<RawVideoAggregationRecord> rawVideos) {
        if (rawVideos == null || rawVideos.isEmpty()) {
            return List.of();
        }
        Map<UUID, UnifiedVideoAggregationCandidate> candidates = new LinkedHashMap<>();
        Map<ContextSearchKey, List<UnifiedVideoAggregationCandidate>> contextSearchByTitleAndYear = new LinkedHashMap<>();
        for (int start = 0; start < rawVideos.size(); start += CONTEXT_BATCH_SIZE) {
            List<RawVideoAggregationRecord> batch = rawVideos.subList(
                    start, Math.min(start + CONTEXT_BATCH_SIZE, rawVideos.size()));
            Map<Integer, List<UnifiedVideoAggregationCandidate>> external = findContextExternalIdBatch(batch);
            for (int index = 0; index < batch.size(); index++) {
                RawVideoAggregationRecord rawVideo = batch.get(index);
                List<UnifiedVideoAggregationCandidate> recalled = external.getOrDefault(index, List.of());
                addCandidates(candidates, recalled);
                if (recalled.isEmpty()) {
                    ContextSearchKey key = ContextSearchKey.from(rawVideo);
                    List<UnifiedVideoAggregationCandidate> fallback = contextSearchByTitleAndYear.computeIfAbsent(
                            key, ignored -> findContextSearchCandidates(rawVideo));
                    addCandidates(candidates, fallback);
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
        Map<ExternalIdColumn, LinkedHashSet<String>> idsByProvider = new LinkedHashMap<>();
        EXTERNAL_ID_COLUMNS.forEach(column -> idsByProvider.put(column, new LinkedHashSet<>()));
        MapSqlParameterSource params = new MapSqlParameterSource("limit", TITLE_RECALL_LIMIT);
        for (int index = 0; index < rawVideos.size(); index++) {
            RawVideoAggregationRecord rawVideo = rawVideos.get(index);
            List<String> clauses = new ArrayList<>();
            for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
                String value = externalIdValue(rawVideo, column);
                addExternalIdClause(clauses, params, "uv." + column.column(),
                        column.parameterPrefix() + index, value);
                if (isUsableExternalId(column, value)) {
                    idsByProvider.get(column).add(value.trim());
                }
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
        for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
            LinkedHashSet<String> ids = idsByProvider.get(column);
            if (!ids.isEmpty()) {
                batchClauses.add(column.column() + " in (:" + column.batchParameter() + ")");
                params.addValue(column.batchParameter(), ids);
            }
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

    private record ExternalIdColumn(
            String provider, String column, String parameterPrefix, String batchParameter) {
    }

    private record ContextSearchKey(List<String> titles, String year, Integer season,
            List<String> actors, List<String> directors) {
        static ContextSearchKey from(RawVideoAggregationRecord rawVideo) {
            return new ContextSearchKey(
                    AggregationRecallKeys.titles(rawVideo).stream()
                            .map(AggregationTitleNormalizer::normalizeTitle).distinct().toList(),
                    normalizedYear(rawVideo.year()), rawVideo.season(),
                    AggregationRecallKeys.people(rawVideo.actorNames()),
                    AggregationRecallKeys.people(rawVideo.directorNames()));
        }

        private static String normalizedYear(String year) {
            if (!StringUtils.hasText(year)) {
                return "";
            }
            String normalized = year.trim().toLowerCase(Locale.ROOT);
            return UNKNOWN_YEARS.contains(normalized) ? "" : normalized;
        }
    }

    private boolean hasContextExternalId(RawVideoAggregationRecord rawVideo) {
        return EXTERNAL_ID_COLUMNS.stream()
                .anyMatch(column -> isUsableExternalId(column, externalIdValue(rawVideo, column)));
    }

    private List<UnifiedVideoAggregationCandidate> findContextExternalIdCandidates(RawVideoAggregationRecord rawVideo) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
            addExternalIdClause(clauses, params, column.column(), column.parameterPrefix(),
                    externalIdValue(rawVideo, column));
        }
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
                contextSearchClient.findCandidateUnifiedVideoIds(
                        AggregationRecallKeys.completeTitle(rawVideo.title(), rawVideo.subtitle()), rawVideo.year());
        if (searchResult == null || !searchResult.attempted()) {
            return findTitleYearCandidates(rawVideo, false);
        }
        if (searchResult.unifiedVideoIds().isEmpty()) {
            return findTitleYearCandidates(rawVideo, false);
        }
        List<UUID> candidateIds = searchResult.unifiedVideoIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(TITLE_RECALL_LIMIT)
                .toList();
        if (candidateIds.isEmpty()) {
            return findTitleYearCandidates(rawVideo, false);
        }
        List<UnifiedVideoAggregationCandidate> candidates = queryUnifiedCandidatesByIds(candidateIds);
        if (candidates.isEmpty()) {
            return findTitleYearCandidates(rawVideo, false);
        }
        if (AggregationRecallKeys.titles(rawVideo).size() > 1 || rawVideo.season() != null
                || !AggregationRecallKeys.people(rawVideo.actorNames()).isEmpty()
                || !AggregationRecallKeys.people(rawVideo.directorNames()).isEmpty()) {
            // Search recall is title/year-only. A nonempty top-20 must not hide a better
            // complete-title, cast, director or season bucket for a different work.
            Map<UUID, UnifiedVideoAggregationCandidate> combined = new LinkedHashMap<>();
            addCandidatesUpToLimit(combined, findTitleYearCandidates(rawVideo, false));
            addCandidatesUpToLimit(combined, candidates);
            return List.copyOf(combined.values());
        }
        return candidates;
    }

    private List<UnifiedVideoAggregationCandidate> findExternalIdCandidates(RawVideoAggregationRecord rawVideo) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
            addExternalIdClause(clauses, params, column.column(), column.parameterPrefix(),
                    externalIdValue(rawVideo, column));
        }
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

    private List<UnifiedVideoAggregationCandidate> findTitleYearCandidates(RawVideoAggregationRecord rawVideo) {
        return findTitleYearCandidates(rawVideo, true);
    }

    private List<UnifiedVideoAggregationCandidate> findTitleYearCandidates(
            RawVideoAggregationRecord rawVideo, boolean populateMetadata) {
        if (!StringUtils.hasText(rawVideo.title())) {
            return List.of();
        }
        List<String> titles = AggregationRecallKeys.titles(rawVideo);
        if (titles.isEmpty()) {
            return List.of();
        }
        YearRecall yearRecall = yearRecall(rawVideo.year());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("titleRecallThreshold", TITLE_RECALL_THRESHOLD)
                .addValue("limit", TITLE_RECALL_LIMIT);
        List<String> titleScores = new ArrayList<>();
        List<String> titleContains = new ArrayList<>();
        for (int index = 0; index < titles.size(); index++) {
            String parameter = index == 0 ? "title" : "title" + index;
            params.addValue(parameter, titles.get(index));
            params.addValue(parameter + "Like", "%" + escapeLike(titles.get(index).toLowerCase(Locale.ROOT)) + "%");
            titleScores.add("similarity(title, :" + parameter + ")");
            titleScores.add("similarity(coalesce(alias_title, ''), :" + parameter + ")");
            titleScores.add("similarity(concat_ws(' ', title, subtitle), :" + parameter + ")");
            titleContains.add("lower(title) like :" + parameter + "Like");
            titleContains.add("lower(coalesce(alias_title, '')) like :" + parameter + "Like");
        }
        String titleScore = "greatest(" + String.join(", ", titleScores) + ")";
        String actors = peoplePredicate("actor_names", "bucketActors", rawVideo.actorNames(), params);
        String directors = peoplePredicate("director_names", "bucketDirectors", rawVideo.directorNames(), params);
        List<String> buckets = new ArrayList<>();
        buckets.add(titleScore + " >= :titleRecallThreshold");
        buckets.addAll(titleContains);
        String ranking = titleScore;
        if (AggregationTitleNormalizer.isDistinctiveSecondaryTitle(rawVideo.subtitle())) {
            // The base-title bucket may be exact for hundreds of different works. Rank the
            // complete identity independently so an older matching subtitle survives top-K.
            ranking += " + 0.20 * greatest(similarity(title, :title),"
                    + " similarity(coalesce(alias_title, ''), :title),"
                    + " similarity(concat_ws(' ', title, subtitle), :title))";
        }
        if (actors != null) {
            buckets.add("(" + actors + " and " + titleScore + " >= 0.25)");
            ranking += " + case when " + actors + " then 0.03 else 0 end";
        }
        if (directors != null) {
            buckets.add("(" + directors + " and " + titleScore + " >= 0.20)");
            ranking += " + case when " + directors + " then 0.05 else 0 end";
        }
        if (rawVideo.season() != null) {
            params.addValue("bucketSeason", rawVideo.season());
            ranking += " + case when season = :bucketSeason then 0.05 else 0 end";
        }
        String yearPredicate = "";
        if (!yearRecall.buckets().isEmpty()) {
            params.addValue("yearBuckets", yearRecall.buckets()).addValue("unknownYears", UNKNOWN_YEARS);
            yearPredicate = "(year in (:yearBuckets) or year is null or btrim(year) = ''"
                    + " or lower(btrim(year)) in (:unknownYears)) and";
        } else if (yearRecall.exact() != null) {
            params.addValue("year", yearRecall.exact()).addValue("unknownYears", UNKNOWN_YEARS);
            yearPredicate = "(year = :year or year is null or btrim(year) = ''"
                    + " or lower(btrim(year)) in (:unknownYears)) and";
        }
        return queryUnifiedCandidates(
                """
                where %s
                  (%s)
                order by (%s) desc, updated_at desc, id
                limit :limit
                """.formatted(yearPredicate, String.join(" or ", buckets), ranking),
                params,
                populateMetadata);
    }

    private String peoplePredicate(String column, String parameter, Set<String> names, MapSqlParameterSource params) {
        List<String> keys = AggregationRecallKeys.people(names);
        if (keys.isEmpty()) {
            return null;
        }
        params.addValue(parameter, keys);
        return "jsonb_exists_any(coalesce(" + column + ", '[]'::jsonb), ARRAY[:" + parameter + "]::text[])";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record YearRecall(List<String> buckets, String exact) {
        static YearRecall none() {
            return new YearRecall(List.of(), null);
        }
    }

    private YearRecall yearRecall(String value) {
        if (!StringUtils.hasText(value)) {
            return YearRecall.none();
        }
        String normalized = value.trim();
        if (UNKNOWN_YEARS.contains(normalized.toLowerCase(Locale.ROOT))) {
            return YearRecall.none();
        }
        if (normalized.matches("\\d{4}")) {
            int year = Integer.parseInt(normalized);
            return new YearRecall(List.of(
                    Integer.toString(year - 1), normalized, Integer.toString(year + 1)), null);
        }
        return new YearRecall(List.of(), normalized);
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

    List<UnifiedVideoAggregationCandidate> queryUnifiedCandidatesByIds(List<UUID> ids) {
        List<UUID> orderedIds = distinctIds(ids);
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UnifiedVideoAggregationCandidate> byId = new LinkedHashMap<>();
        for (int start = 0; start < orderedIds.size(); start += ID_LOOKUP_BATCH_SIZE) {
            List<UUID> batch = orderedIds.subList(start, Math.min(start + ID_LOOKUP_BATCH_SIZE, orderedIds.size()));
            List<UnifiedVideoAggregationCandidate> candidates = queryUnifiedCandidates(
                    """
                    where id in (:ids)
                    """,
                    new MapSqlParameterSource("ids", batch),
                    false);
            for (UnifiedVideoAggregationCandidate candidate : candidates) {
                byId.putIfAbsent(candidate.id(), candidate);
            }
        }
        return orderedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    List<UnifiedVideoAggregationCandidate> queryUnifiedCandidatesByIdsForWrite(List<UUID> ids) {
        List<UUID> orderedIds = distinctIds(ids);
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UnifiedVideoAggregationCandidate> byId = new LinkedHashMap<>();
        for (int start = 0; start < orderedIds.size(); start += ID_LOOKUP_BATCH_SIZE) {
            List<UUID> batch = orderedIds.subList(start, Math.min(start + ID_LOOKUP_BATCH_SIZE, orderedIds.size()));
            List<UnifiedVideoAggregationCandidate> candidates = jdbcTemplate.query(
                    """
                    select uv.id, uv.title, uv.alias_title, uv.subtitle, uv.year,
                           uv.total_episodes, uv.duration, uv.remarks, sc.name as category_name,
                           uv.douban_id, uv.tmdb_id, uv.imdb_id, uv.rotten_tomatoes_id, uv.season,
                           cast(uv.actor_names as varchar) as actor_names,
                           cast(uv.director_names as varchar) as director_names,
                           cast(uv.area_codes as varchar) as area_codes
                      from unified_videos uv
                      left join standard_category sc on sc.slug = uv.category_code
                     where uv.id in (:ids)
                    """,
                    new MapSqlParameterSource("ids", batch),
                    this::mapUnifiedCandidateForWrite);
            for (UnifiedVideoAggregationCandidate candidate : candidates) {
                byId.putIfAbsent(candidate.id(), candidate);
            }
        }
        return orderedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList()));
    }

    private UnifiedVideoAggregationCandidate mapUnifiedCandidateForWrite(ResultSet rs, int rowNum) throws SQLException {
        UnifiedVideoAggregationCandidate candidate = mapUnifiedCandidate(rs, rowNum);
        return metadataNameRepository.withFlatMetadata(
                candidate,
                rs.getString("actor_names"),
                rs.getString("director_names"),
                rs.getString("area_codes"));
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
        ExternalIdColumn providerColumn = externalIdColumn(column);
        if (isUsableExternalId(providerColumn, value)) {
            clauses.add(column + " = :" + paramName);
            params.addValue(paramName, value.trim());
        }
    }

    private boolean isUsableExternalId(ExternalIdColumn column, String value) {
        return matchingStrategy.isUsableExternalId(column.provider(), value);
    }

    private ExternalIdColumn externalIdColumn(String column) {
        String normalized = column.startsWith("uv.") ? column.substring(3) : column;
        return EXTERNAL_ID_COLUMNS.stream()
                .filter(candidate -> candidate.column().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported external ID column: " + column));
    }

    private String externalIdValue(RawVideoAggregationRecord rawVideo, ExternalIdColumn column) {
        return switch (column.provider()) {
            case "douban" -> rawVideo.doubanId();
            case "tmdb" -> rawVideo.tmdbId();
            case "imdb" -> rawVideo.imdbId();
            case "rt" -> rawVideo.rottenTomatoesId();
            default -> throw new IllegalArgumentException("Unsupported external ID provider: " + column.provider());
        };
    }

    private void addCandidates(
            Map<UUID, UnifiedVideoAggregationCandidate> candidates,
            List<UnifiedVideoAggregationCandidate> recalled) {
        for (UnifiedVideoAggregationCandidate candidate : recalled) {
            if (candidate != null) {
                candidates.putIfAbsent(candidate.id(), candidate);
            }
        }
    }

    private void addCandidatesUpToLimit(
            Map<UUID, UnifiedVideoAggregationCandidate> candidates,
            List<UnifiedVideoAggregationCandidate> recalled) {
        for (UnifiedVideoAggregationCandidate candidate : recalled) {
            if (candidate != null && candidates.size() < TITLE_RECALL_LIMIT) {
                candidates.putIfAbsent(candidate.id(), candidate);
            }
        }
    }
}
