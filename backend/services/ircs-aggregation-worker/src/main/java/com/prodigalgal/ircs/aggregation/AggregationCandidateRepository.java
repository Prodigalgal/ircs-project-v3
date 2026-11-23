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
    private static final int TITLE_BUCKET_LIMIT = 100;
    private static final String COMPLETE_TITLE_SQL = "(coalesce(title, '') || ' ' || coalesce(subtitle, ''))";
    private static final String RECALL_COLUMNS = """
            id, title, alias_title, subtitle, year, total_episodes, duration, remarks, category_code,
            douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, season, actor_names, director_names, updated_at
            """;
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

    Optional<UUID> findMatchingCatalogEntry(SourceRecordAggregationRecord sourceRecord) {
        AggregationMatchPlan plan = findMatchPlan(sourceRecord);
        return plan.hasRoot() ? Optional.of(plan.rootCatalogEntryId()) : Optional.empty();
    }

    AggregationMatchPlan findMatchPlan(SourceRecordAggregationRecord sourceRecord) {
        return findMatchPlan(sourceRecord, null);
    }

    AggregationMatchPlan findMatchPlan(SourceRecordAggregationRecord sourceRecord, UUID excludedUnifiedId) {
        Map<UUID, CatalogEntryAggregationCandidate> candidates = new LinkedHashMap<>();
        addCandidatesUpToLimit(candidates, findExternalIdCandidates(sourceRecord).stream()
                .filter(candidate -> !candidate.id().equals(excludedUnifiedId)).toList());
        addCandidatesUpToLimit(candidates, findTitleYearCandidates(sourceRecord, false).stream()
                .filter(candidate -> !candidate.id().equals(excludedUnifiedId)).toList());
        if (candidates.isEmpty()) {
            return AggregationMatchPlan.none();
        }
        List<CatalogEntryAggregationCandidate> enriched = excludedUnifiedId == null
                ? metadataNameRepository.populateUnifiedMetadata(List.copyOf(candidates.values()))
                : queryUnifiedCandidatesByIdsForWrite(List.copyOf(candidates.keySet()));
        return matchingStrategy.findMatchPlan(sourceRecord, enriched);
    }

    List<CatalogEntryAggregationCandidate> findContextUnifiedCandidates(List<SourceRecordAggregationRecord> sourceRecords) {
        if (sourceRecords == null || sourceRecords.isEmpty()) {
            return List.of();
        }
        Map<UUID, CatalogEntryAggregationCandidate> candidates = new LinkedHashMap<>();
        Map<ContextSearchKey, List<CatalogEntryAggregationCandidate>> contextSearchByTitleAndYear = new LinkedHashMap<>();
        for (int start = 0; start < sourceRecords.size(); start += CONTEXT_BATCH_SIZE) {
            List<SourceRecordAggregationRecord> batch = sourceRecords.subList(
                    start, Math.min(start + CONTEXT_BATCH_SIZE, sourceRecords.size()));
            Map<Integer, List<CatalogEntryAggregationCandidate>> external = findContextExternalIdBatch(batch);
            for (int index = 0; index < batch.size(); index++) {
                SourceRecordAggregationRecord sourceRecord = batch.get(index);
                List<CatalogEntryAggregationCandidate> recalled = external.getOrDefault(index, List.of());
                addCandidates(candidates, recalled);
                if (recalled.isEmpty()) {
                    ContextSearchKey key = ContextSearchKey.from(sourceRecord);
                    List<CatalogEntryAggregationCandidate> fallback = contextSearchByTitleAndYear.computeIfAbsent(
                            key, ignored -> findContextSearchCandidates(sourceRecord));
                    addCandidates(candidates, fallback);
                }
            }
        }
        return metadataNameRepository.populateUnifiedMetadata(List.copyOf(candidates.values()));
    }

    private Map<Integer, List<CatalogEntryAggregationCandidate>> findContextExternalIdBatch(
            List<SourceRecordAggregationRecord> sourceRecords) {
        if (sourceRecords.stream().filter(this::hasContextExternalId).count() <= 1) {
            for (int index = 0; index < sourceRecords.size(); index++) {
                if (hasContextExternalId(sourceRecords.get(index))) {
                    return Map.of(index, findContextExternalIdCandidates(sourceRecords.get(index)));
                }
            }
            return Map.of();
        }
        List<String> queries = new ArrayList<>();
        Map<ExternalIdColumn, LinkedHashSet<String>> idsByProvider = new LinkedHashMap<>();
        EXTERNAL_ID_COLUMNS.forEach(column -> idsByProvider.put(column, new LinkedHashSet<>()));
        MapSqlParameterSource params = new MapSqlParameterSource("limit", TITLE_RECALL_LIMIT);
        for (int index = 0; index < sourceRecords.size(); index++) {
            SourceRecordAggregationRecord sourceRecord = sourceRecords.get(index);
            List<String> clauses = new ArrayList<>();
            for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
                String value = externalIdValue(sourceRecord, column);
                addExternalIdClause(clauses, params, "uv." + column.column(),
                        column.parameterPrefix() + index, value);
                String canonical = matchingStrategy.normalizeExternalId(column.provider(), value);
                if (canonical != null) { idsByProvider.get(column).add(canonical); }
            }
            if (!clauses.isEmpty()) {
                queries.add("""
                        (select %d as context_index, uv.id, uv.title, uv.alias_title, uv.subtitle, uv.year,
                                uv.total_episodes, uv.duration, uv.remarks, sc.name as category_name,
                                uv.douban_id, uv.tmdb_id, uv.imdb_id, uv.rotten_tomatoes_id, uv.season,
                                uv.updated_at as context_updated_at
                           from context_candidates uv
                           left join catalog_categories sc on sc.slug = uv.category_code
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
                      from catalog_entries
                     where %s
                )
                """.formatted(String.join(" or ", batchClauses))
                        + String.join(" union all ", queries) + " order by context_index, context_updated_at desc",
                params,
                (rs, rowNum) -> new ContextCandidate(rs.getInt("context_index"), mapUnifiedCandidate(rs, rowNum)));
        Map<Integer, List<CatalogEntryAggregationCandidate>> byInput = new LinkedHashMap<>();
        for (ContextCandidate row : rows) {
            byInput.computeIfAbsent(row.index(), ignored -> new ArrayList<>()).add(row.candidate());
        }
        return byInput;
    }

    private record ContextCandidate(int index, CatalogEntryAggregationCandidate candidate) {
    }

    private record ExternalIdColumn(
            String provider, String column, String parameterPrefix, String batchParameter) {
    }

    private record ContextSearchKey(String identityProfile, List<String> titles, String year, Integer season,
            List<String> actors, List<String> directors) {
        static ContextSearchKey from(SourceRecordAggregationRecord sourceRecord) {
            return new ContextSearchKey(
                    AggregationCategoryProfile.POLICY_VERSION + ":" + AggregationCategoryProfile.from(AggregationCategoryProfile.category(sourceRecord)),
                    AggregationRecallKeys.titles(sourceRecord).stream()
                            .map(AggregationTitleNormalizer::normalizeTitle).distinct().toList(),
                    normalizedYear(sourceRecord.year()), sourceRecord.season(),
                    AggregationRecallKeys.people(sourceRecord.actorNames()),
                    AggregationRecallKeys.people(sourceRecord.directorNames()));
        }

        private static String normalizedYear(String year) {
            if (!StringUtils.hasText(year)) {
                return "";
            }
            String normalized = year.trim().toLowerCase(Locale.ROOT);
            return UNKNOWN_YEARS.contains(normalized) ? "" : normalized;
        }
    }

    private boolean hasContextExternalId(SourceRecordAggregationRecord sourceRecord) {
        return EXTERNAL_ID_COLUMNS.stream()
                .anyMatch(column -> isUsableExternalId(column, externalIdValue(sourceRecord, column)));
    }

    private List<CatalogEntryAggregationCandidate> findContextExternalIdCandidates(SourceRecordAggregationRecord sourceRecord) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
            addExternalIdClause(clauses, params, column.column(), column.parameterPrefix(),
                    externalIdValue(sourceRecord, column));
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

    private List<CatalogEntryAggregationCandidate> findContextSearchCandidates(SourceRecordAggregationRecord sourceRecord) {
        AggregationContextSearchClient.ContextSearchResult searchResult =
                contextSearchClient.findCandidateCatalogEntryIds(
                        AggregationRecallKeys.completeTitle(sourceRecord.title(), sourceRecord.subtitle()), sourceRecord.year());
        if (searchResult == null || !searchResult.attempted()) {
            return findTitleYearCandidates(sourceRecord, false);
        }
        if (searchResult.catalogEntryIds().isEmpty()) {
            return findTitleYearCandidates(sourceRecord, false);
        }
        List<UUID> candidateIds = searchResult.catalogEntryIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(TITLE_RECALL_LIMIT)
                .toList();
        if (candidateIds.isEmpty()) {
            return findTitleYearCandidates(sourceRecord, false);
        }
        List<CatalogEntryAggregationCandidate> candidates = queryUnifiedCandidatesByIds(candidateIds);
        if (candidates.isEmpty()) {
            return findTitleYearCandidates(sourceRecord, false);
        }
        if (AggregationRecallKeys.titles(sourceRecord).size() > 1 || sourceRecord.season() != null
                || !AggregationRecallKeys.people(sourceRecord.actorNames()).isEmpty()
                || !AggregationRecallKeys.people(sourceRecord.directorNames()).isEmpty()) {
            // Search recall is title/year-only. A nonempty top-20 must not hide a better
            // complete-title, cast, director or season bucket for a different work.
            Map<UUID, CatalogEntryAggregationCandidate> combined = new LinkedHashMap<>();
            addCandidatesUpToLimit(combined, findTitleYearCandidates(sourceRecord, false));
            addCandidatesUpToLimit(combined, candidates);
            return List.copyOf(combined.values());
        }
        return candidates;
    }

    private List<CatalogEntryAggregationCandidate> findExternalIdCandidates(SourceRecordAggregationRecord sourceRecord) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (ExternalIdColumn column : EXTERNAL_ID_COLUMNS) {
            addExternalIdClause(clauses, params, column.column(), column.parameterPrefix(),
                    externalIdValue(sourceRecord, column));
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

    private List<CatalogEntryAggregationCandidate> findTitleYearCandidates(SourceRecordAggregationRecord sourceRecord) {
        return findTitleYearCandidates(sourceRecord, true);
    }

    private List<CatalogEntryAggregationCandidate> findTitleYearCandidates(
            SourceRecordAggregationRecord sourceRecord, boolean populateMetadata) {
        if (!StringUtils.hasText(sourceRecord.title())) {
            return List.of();
        }
        List<String> titles = AggregationRecallKeys.titles(sourceRecord);
        if (titles.isEmpty()) {
            return List.of();
        }
        YearRecall yearRecall = yearRecall(sourceRecord.year());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("titleRecallThreshold", TITLE_RECALL_THRESHOLD)
                .addValue("bucketLimit", TITLE_BUCKET_LIMIT)
                .addValue("limit", TITLE_RECALL_LIMIT);
        List<String> titleScores = new ArrayList<>();
        List<String> titleContains = new ArrayList<>();
        for (int index = 0; index < titles.size(); index++) {
            String parameter = index == 0 ? "title" : "title" + index;
            params.addValue(parameter, titles.get(index));
            params.addValue(parameter + "Like", "%" + escapeLike(titles.get(index).toLowerCase(Locale.ROOT)) + "%");
            params.addValue(parameter + "Exact", escapeLike(titles.get(index)));
            titleScores.add("similarity(title, :" + parameter + ")");
            titleScores.add("similarity(coalesce(alias_title, ''), :" + parameter + ")");
            titleScores.add("similarity(" + COMPLETE_TITLE_SQL + ", :" + parameter + ")");
            titleContains.add("lower(title) like :" + parameter + "Like");
            titleContains.add("lower(coalesce(alias_title, '')) like :" + parameter + "Like");
        }
        String titleScore = "greatest(" + String.join(", ", titleScores) + ")";
        String actors = peoplePredicate("actor_names", "bucketActors", sourceRecord.actorNames(), params);
        String directors = peoplePredicate("director_names", "bucketDirectors", sourceRecord.directorNames(), params);
        List<String> buckets = new ArrayList<>();
        buckets.add("recall_title_score >= :titleRecallThreshold");
        buckets.addAll(titleContains);
        String ranking = "recall_title_score";
        String bucketBonus = "";
        if (AggregationTitleNormalizer.isDistinctiveSecondaryTitle(sourceRecord.subtitle())) {
            // The base-title bucket may be exact for hundreds of different works. Rank the
            // complete identity independently so an older matching subtitle survives top-K.
            ranking += " + 0.20 * recall_complete_score";
        }
        if (actors != null) {
            buckets.add("(" + actors + " and recall_title_score >= 0.25)");
            bucketBonus += " + case when " + actors + " then 0.03 else 0 end";
        }
        if (directors != null) {
            buckets.add("(" + directors + " and recall_title_score >= 0.20)");
            bucketBonus += " + case when " + directors + " then 0.05 else 0 end";
        }
        if (sourceRecord.season() != null) {
            params.addValue("bucketSeason", sourceRecord.season());
            bucketBonus += " + case when season = :bucketSeason then 0.05 else 0 end";
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
        List<String> titleBuckets = new ArrayList<>();
        for (int index = 0; index < titles.size(); index++) {
            String parameter = index == 0 ? "title" : "title" + index;
            // The service pool fixes pg_trgm.similarity_threshold at 0.19, below both
            // people-assisted score floors. All three expressions have matching GIN indexes.
            String prefilter = titles.get(index).codePointCount(0, titles.get(index).length()) < 3
                    ? "(title ilike :" + parameter + "Exact or alias_title ilike :" + parameter
                            + "Exact or " + COMPLETE_TITLE_SQL + " ilike :" + parameter + "Exact)"
                    : "(title % :" + parameter + " or alias_title % :" + parameter
                            + " or " + COMPLETE_TITLE_SQL + " % :" + parameter
                            + " or title ilike :" + parameter + "Like or alias_title ilike :" + parameter + "Like)";
            String bucketScore = "greatest(similarity(title, :" + parameter + "),"
                    + " similarity(coalesce(alias_title, ''), :" + parameter + "),"
                    + " similarity(" + COMPLETE_TITLE_SQL + ", :" + parameter + "))";
            titleBuckets.add("""
                    (select %s
                       from catalog_entries
                      where %s %s
                      order by case when title = :title or %s = :title then 0 else 1 end,
                               (%s%s) desc, updated_at desc, id
                      limit :bucketLimit)
                    """.formatted(RECALL_COLUMNS, yearPredicate, prefilter, COMPLETE_TITLE_SQL, bucketScore, bucketBonus));
        }
        // Materialize before evaluating the complete multi-key score: at most six buckets
        // of 100 rows each, instead of repeated similarity/JSON work across the whole catalog.
        String sql = """
                with recall_pool as materialized (
                    select distinct on (id) * from (
                        %s
                    ) bucket_candidates order by id
                ), scored_candidates as materialized (
                    select *, %s as recall_title_score,
                        greatest(similarity(title, :title), similarity(coalesce(alias_title, ''), :title),
                                 similarity(%s, :title)) as recall_complete_score
                    from recall_pool
                )
                select r.id, r.title, r.alias_title, r.subtitle, r.year,
                       r.total_episodes, r.duration, r.remarks, sc.name as category_name,
                       r.douban_id, r.tmdb_id, r.imdb_id, r.rotten_tomatoes_id, r.season
                from scored_candidates r
                left join catalog_categories sc on sc.slug = r.category_code
                where (%s)
                order by (%s%s) desc, r.updated_at desc, r.id
                limit :limit
                """.formatted(String.join("\nunion all\n", titleBuckets), titleScore, COMPLETE_TITLE_SQL,
                        String.join(" or ", buckets), ranking, bucketBonus);
        List<CatalogEntryAggregationCandidate> candidates = jdbcTemplate.query(sql, params, this::mapUnifiedCandidate);
        return populateMetadata ? metadataNameRepository.populateUnifiedMetadata(candidates) : candidates;
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

    private List<CatalogEntryAggregationCandidate> queryUnifiedCandidates(
            String whereAndOrderBy,
            MapSqlParameterSource params) {
        return queryUnifiedCandidates(whereAndOrderBy, params, true);
    }

    private List<CatalogEntryAggregationCandidate> queryUnifiedCandidates(
            String whereAndOrderBy,
            MapSqlParameterSource params,
            boolean populateMetadata) {
        List<CatalogEntryAggregationCandidate> candidates = jdbcTemplate.query(
                """
                select id, title, alias_title, subtitle, year,
                       total_episodes, duration, remarks,
                       category_name,
                       douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                       season
                from (
                    select uv.*,
                           sc.name as category_name
                    from catalog_entries uv
                    left join catalog_categories sc on sc.slug = uv.category_code
                ) unified_candidate
                %s
                """.formatted(whereAndOrderBy),
                params,
                this::mapUnifiedCandidate);
        return populateMetadata ? metadataNameRepository.populateUnifiedMetadata(candidates) : candidates;
    }

    List<CatalogEntryAggregationCandidate> queryUnifiedCandidatesByIds(List<UUID> ids) {
        List<UUID> orderedIds = distinctIds(ids);
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, CatalogEntryAggregationCandidate> byId = new LinkedHashMap<>();
        for (int start = 0; start < orderedIds.size(); start += ID_LOOKUP_BATCH_SIZE) {
            List<UUID> batch = orderedIds.subList(start, Math.min(start + ID_LOOKUP_BATCH_SIZE, orderedIds.size()));
            List<CatalogEntryAggregationCandidate> candidates = queryUnifiedCandidates(
                    """
                    where id in (:ids)
                    """,
                    new MapSqlParameterSource("ids", batch),
                    false);
            for (CatalogEntryAggregationCandidate candidate : candidates) {
                byId.putIfAbsent(candidate.id(), candidate);
            }
        }
        return orderedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    List<CatalogEntryAggregationCandidate> queryUnifiedCandidatesByIdsForWrite(List<UUID> ids) {
        List<UUID> orderedIds = distinctIds(ids);
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, CatalogEntryAggregationCandidate> byId = new LinkedHashMap<>();
        for (int start = 0; start < orderedIds.size(); start += ID_LOOKUP_BATCH_SIZE) {
            List<UUID> batch = orderedIds.subList(start, Math.min(start + ID_LOOKUP_BATCH_SIZE, orderedIds.size()));
            List<CatalogEntryAggregationCandidate> candidates = jdbcTemplate.query(
                    """
                    select uv.id, uv.title, uv.alias_title, uv.subtitle, uv.year,
                           uv.total_episodes, uv.duration, uv.remarks, sc.name as category_name,
                           uv.douban_id, uv.tmdb_id, uv.imdb_id, uv.rotten_tomatoes_id, uv.season,
                           cast(uv.actor_names as varchar) as actor_names,
                           cast(uv.director_names as varchar) as director_names,
                           cast(uv.area_codes as varchar) as area_codes
                      from catalog_entries uv
                      left join catalog_categories sc on sc.slug = uv.category_code
                     where uv.id in (:ids)
                    """,
                    new MapSqlParameterSource("ids", batch),
                    this::mapUnifiedCandidateForWrite);
            for (CatalogEntryAggregationCandidate candidate : candidates) {
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

    private CatalogEntryAggregationCandidate mapUnifiedCandidateForWrite(ResultSet rs, int rowNum) throws SQLException {
        CatalogEntryAggregationCandidate candidate = mapUnifiedCandidate(rs, rowNum);
        return metadataNameRepository.withFlatMetadata(
                candidate,
                rs.getString("actor_names"),
                rs.getString("director_names"),
                rs.getString("area_codes"));
    }

    private CatalogEntryAggregationCandidate mapUnifiedCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new CatalogEntryAggregationCandidate(
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
        String canonical = matchingStrategy.normalizeExternalId(providerColumn.provider(), value);
        if (canonical != null) {
            clauses.add(column + " = :" + paramName);
            params.addValue(paramName, canonical);
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

    private String externalIdValue(SourceRecordAggregationRecord sourceRecord, ExternalIdColumn column) {
        return switch (column.provider()) {
            case "douban" -> sourceRecord.doubanId();
            case "tmdb" -> sourceRecord.tmdbId();
            case "imdb" -> sourceRecord.imdbId();
            case "rt" -> sourceRecord.rottenTomatoesId();
            default -> throw new IllegalArgumentException("Unsupported external ID provider: " + column.provider());
        };
    }

    private void addCandidates(
            Map<UUID, CatalogEntryAggregationCandidate> candidates,
            List<CatalogEntryAggregationCandidate> recalled) {
        for (CatalogEntryAggregationCandidate candidate : recalled) {
            if (candidate != null) {
                candidates.putIfAbsent(candidate.id(), candidate);
            }
        }
    }

    private void addCandidatesUpToLimit(
            Map<UUID, CatalogEntryAggregationCandidate> candidates,
            List<CatalogEntryAggregationCandidate> recalled) {
        for (CatalogEntryAggregationCandidate candidate : recalled) {
            if (candidate != null && candidates.size() < TITLE_RECALL_LIMIT) {
                candidates.putIfAbsent(candidate.id(), candidate);
            }
        }
    }
}
