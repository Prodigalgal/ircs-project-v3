package com.prodigalgal.ircs.aggregation;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.adult.AdultAssessment;
import com.prodigalgal.ircs.common.adult.AdultAssessmentInput;
import com.prodigalgal.ircs.common.adult.AdultContentAssessor;
import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.common.metadata.JsonStringArrays;
import com.prodigalgal.ircs.common.metadata.MetadataNameOwnerType;
import com.prodigalgal.ircs.common.metadata.MetadataNameCache;
import com.prodigalgal.ircs.common.normalization.StandardContentCategoryClassifier;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcAggregationRepository {

    private static final Pattern LOCKED_FIELD_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final List<String> ALLOWED_CATEGORY_CODES = StandardContentCategoryClassifier.stableCategoryCodes();

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AggregationCandidateRepository candidateRepository;
    private final AggregationMetadataNameRepository metadataNameRepository;
    private final AdultAssessmentEvaluator adultAssessmentEvaluator;
    private final ObjectMapper objectMapper;
    private final AggregationMatchingStrategy matchingStrategy;

    public JdbcAggregationRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            AggregationMatchingStrategy matchingStrategy,
            AggregationContextSearchClient contextSearchClient,
            ObjectProvider<MetadataNameCache> metadataNameCacheProvider,
            ObjectMapper objectMapper,
            AdultAssessmentEvaluator adultAssessmentEvaluator,
            @Value("${app.aggregation.jdbc.query-timeout-seconds:30}") int queryTimeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.matchingStrategy = matchingStrategy;
        this.objectMapper = objectMapper;
        this.adultAssessmentEvaluator = adultAssessmentEvaluator == null
                ? AdultAssessmentEvaluator.local()
                : adultAssessmentEvaluator;
        this.metadataNameRepository = new AggregationMetadataNameRepository(
                jdbcTemplate,
                metadataNameCacheProvider,
                objectMapper);
        this.candidateRepository = new AggregationCandidateRepository(
                jdbcTemplate,
                matchingStrategy,
                contextSearchClient,
                metadataNameRepository);
        if (queryTimeoutSeconds > 0 && this.jdbcTemplate.getJdbcTemplate() != null) {
            this.jdbcTemplate.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);
        }
    }

    static JdbcAggregationRepository forTest(
            NamedParameterJdbcTemplate jdbcTemplate,
            AggregationMatchingStrategy matchingStrategy) {
        return new JdbcAggregationRepository(
                jdbcTemplate,
                matchingStrategy,
                (title, year) -> AggregationContextSearchClient.ContextSearchResult.notAttempted(),
                null,
                JsonMapper.builder().build(),
                AdultAssessmentEvaluator.local(),
                30);
    }

    static JdbcAggregationRepository forTest(
            NamedParameterJdbcTemplate jdbcTemplate,
            AggregationMatchingStrategy matchingStrategy,
            AggregationContextSearchClient contextSearchClient) {
        return new JdbcAggregationRepository(
                jdbcTemplate,
                matchingStrategy,
                contextSearchClient,
                null,
                JsonMapper.builder().build(),
                AdultAssessmentEvaluator.local(),
                30);
    }

    public Optional<SourceRecordAggregationRecord> findSourceRecord(UUID sourceRecordId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select id, title, alias_title, description, year, score, published_at,
                           total_episodes, duration, remarks, subtitle, season,
                           category_name, category_code,
                           actor_names, director_names, area_codes,
                           douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                           normalization_status, enrichment_status, aggregation_status
                    from (
                    select rv.*,
                           sc.name as category_name
                    from source_records rv
                    left join catalog_categories sc on sc.slug = rv.category_code
                    where rv.id = :id
                    ) raw_candidate
                    """,
                    new MapSqlParameterSource("id", sourceRecordId),
                    this::mapSourceRecord));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<SourceRecordAggregationRecord> findSourceRecords(Collection<UUID> sourceRecordIds) {
        if (sourceRecordIds == null || sourceRecordIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = sourceRecordIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SourceRecordAggregationRecord> sourceRecords = jdbcTemplate.query(
                """
                select id, title, alias_title, description, year, score, published_at,
                       total_episodes, duration, remarks, subtitle, season,
                       category_name, category_code,
                        actor_names, director_names, area_codes,
                       douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                       normalization_status, enrichment_status, aggregation_status
                from (
                    select rv.*,
                           sc.name as category_name
                    from source_records rv
                    left join catalog_categories sc on sc.slug = rv.category_code
                    where rv.id in (:ids)
                ) raw_candidate
                """,
                new MapSqlParameterSource("ids", ids),
                this::mapSourceRecord);
        return sourceRecords;
    }

    public Optional<UUID> findExistingBinding(UUID sourceRecordId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select catalog_entry_id
                    from catalog_source_bindings
                    where source_record_id = :sourceRecordId
                    limit 1
                    """,
                    new MapSqlParameterSource("sourceRecordId", sourceRecordId),
                    UUID.class));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<AuthorityCatalogProjection> findAuthorityCatalogProjections(
            String mediaType, Set<String> titleKeys, Long tmdbId, String imdbId) {
        if (!StringUtils.hasText(mediaType)) {
            return List.of();
        }
        List<String> recallConditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("mediaType", mediaType);
        if (titleKeys != null && !titleKeys.isEmpty()) {
            recallConditions.add("(work.title_key in (:titleKeys) or work.original_title_key in (:titleKeys))");
            parameters.addValue("titleKeys", List.copyOf(titleKeys));
        }
        if (tmdbId != null) {
            recallConditions.add("work.tmdb_id=:tmdbId");
            parameters.addValue("tmdbId", tmdbId);
        }
        if (StringUtils.hasText(imdbId)) {
            recallConditions.add("lower(work.imdb_id)=lower(:imdbId)");
            parameters.addValue("imdbId", imdbId.trim());
        }
        if (recallConditions.isEmpty()) {
            return List.of();
        }
        String sql = """
                select work.id as work_id,
                       work.media_type,
                       work.tmdb_id,
                       work.imdb_id,
                       work.title_key,
                       work.original_title_key,
                       work.release_year,
                       work.released_on as work_released_on,
                       work.regular_season_count,
                       coalesce(season.season_number,-1) as season_number,
                       season.released_on,
                       entry.id as catalog_entry_id
                  from catalog_works work
                  join catalog_entries entry on entry.work_id=work.id
                  left join catalog_seasons season on season.id=entry.season_id
                 where work.media_type=:mediaType
                   and work.identity_status='VERIFIED'
                   and (%s)
                   and entry.content_visibility='PUBLIC'
                   and not coalesce(entry.adult_restricted, false)
                 order by work.id, season.season_number nulls first
                """.formatted(String.join(" or ", recallConditions));
        return jdbcTemplate.query(sql, parameters,
                (rs, row) -> new AuthorityCatalogProjection(
                        rs.getObject("work_id", UUID.class),
                        rs.getString("media_type"),
                        (Long) rs.getObject("tmdb_id"),
                        rs.getString("imdb_id"),
                        rs.getString("title_key"),
                        rs.getString("original_title_key"),
                        integerValue(rs, "release_year"),
                        rs.getDate("work_released_on") == null ? null : rs.getDate("work_released_on").toLocalDate(),
                        (Integer) rs.getObject("regular_season_count"),
                        rs.getInt("season_number"),
                        rs.getDate("released_on") == null ? null : rs.getDate("released_on").toLocalDate(),
                        rs.getObject("catalog_entry_id", UUID.class)));
    }

    private Integer integerValue(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    public void lockSourceRecords(Collection<UUID> sourceRecordIds) {
        if (sourceRecordIds == null || sourceRecordIds.isEmpty()) { return; }
        jdbcTemplate.query("select id from source_records where id in (:ids) order by id for update",
                new MapSqlParameterSource("ids", sourceRecordIds), (rs, row) -> rs.getObject(1, UUID.class));
    }

    public void configureAggregationTransaction() {
        jdbcTemplate.getJdbcTemplate().execute("set local lock_timeout = '2s'");
    }

    public void lockCatalogEntries(Collection<UUID> catalogEntryIds) {
        if (catalogEntryIds == null || catalogEntryIds.isEmpty()) { return; }
        jdbcTemplate.query("select id from catalog_entries where id in (:ids) order by id for update",
                new MapSqlParameterSource("ids", catalogEntryIds), (rs, row) -> rs.getObject(1, UUID.class));
    }

    public List<CatalogEntryAggregationCandidate> findUnifiedCandidatesByIds(Collection<UUID> ids) {
        return candidateRepository.queryUnifiedCandidatesByIdsForWrite(List.copyOf(ids));
    }

    public boolean isAdultAssessmentPending(UUID catalogEntryId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from catalog_entries where id=:id and adult_checked_at is null)",
                new MapSqlParameterSource("id", catalogEntryId), Boolean.class));
    }

    public List<UUID> findRawIdsForUnified(UUID catalogEntryId) {
        return jdbcTemplate.queryForList(
                """
                select source_record_id
                from catalog_source_bindings
                where catalog_entry_id = :catalogEntryId
                order by source_record_id
                """,
                new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                UUID.class);
    }

    List<AggregationMemberIdentity> findBoundMemberIdentities(Collection<UUID> catalogEntryIds) {
        if (catalogEntryIds.isEmpty()) { return List.of(); }
        // Read one extra link so callers can distinguish a complete view from the review limit.
        // Bound the indexed association lookup before loading the narrow identity projection.
        return jdbcTemplate.query("""
                select rv.id, rv.title, rv.alias_title, rv.subtitle, rv.remarks, rv.year,
                       rv.season, rv.category_code, rv.douban_id, rv.tmdb_id, rv.imdb_id, rv.rotten_tomatoes_id
                from (
                    select source_record_id
                    from catalog_source_bindings
                    where catalog_entry_id in (:ids)
                    limit :limit
                ) linked
                join source_records rv on rv.id = linked.source_record_id
                order by rv.created_at asc, rv.id asc
                """, new MapSqlParameterSource("ids", catalogEntryIds)
                        .addValue("limit", AggregationMemberIdentity.REVIEW_MEMBER_LIMIT + 1),
                (rs, row) -> new AggregationMemberIdentity(
                        rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("alias_title"),
                        rs.getString("subtitle"), rs.getString("remarks"), rs.getString("year"),
                        rs.getObject("season", Integer.class), rs.getString("category_code"),
                        rs.getString("douban_id"), rs.getString("tmdb_id"), rs.getString("imdb_id"),
                        rs.getString("rotten_tomatoes_id")));
    }

    public List<UUID> findDirtyUnifiedIds(int limit) {
        return jdbcTemplate.queryForList(
                """
                select uv.id
                  from catalog_entries uv
                 where uv.metadata_status = 'DIRTY'
                   and exists (select 1 from catalog_source_bindings link where link.catalog_entry_id=uv.id)
                 order by uv.updated_at asc nulls first, uv.id
                 limit :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                UUID.class);
    }

    public List<UUID> findEligiblePendingRawIds(int limit) {
        return jdbcTemplate.queryForList(
                """
                select rv.id
                from source_records rv
                where rv.aggregation_status = 'PENDING'
                  and rv.normalization_status = 'READY'
                  and rv.enrichment_status is distinct from 'PENDING'
                  and nullif(trim(rv.title), '') is not null
                order by rv.aggregation_status_updated_at asc nulls first, rv.updated_at asc nulls first, rv.id
                limit :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                UUID.class);
    }

    public List<UUID> findUnifiedIdsMissingCoverFromRaw(int limit) {
        return jdbcTemplate.queryForList(
                """
                select distinct uv.id
                from catalog_entries uv
                join catalog_source_bindings rvuv on rvuv.catalog_entry_id = uv.id
                join source_records rv on rv.id = rvuv.source_record_id
                where uv.media_image_source_id is null
                  and rv.media_image_source_id is not null
                order by uv.id
                limit :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                UUID.class);
    }

    public List<UnifiedCoverBackfillResult> backfillUnifiedCoverImagesFromRaw(int limit) {
        List<UnifiedCoverBackfillResult> candidates = jdbcTemplate.query(
                """
                select catalog_entry_id, media_image_source_id, storage_type, status
                from (
                    select rvuv.catalog_entry_id,
                           rv.media_image_source_id,
                           ci.storage_type,
                           ci.status,
                           row_number() over (
                               partition by rvuv.catalog_entry_id
                               order by rv.created_at asc, rv.id asc
                           ) as rn
                    from (
                        select distinct uv.id
                        from catalog_entries uv
                        join catalog_source_bindings rvuv on rvuv.catalog_entry_id = uv.id
                        join source_records rv on rv.id = rvuv.source_record_id
                        where uv.media_image_source_id is null
                          and rv.media_image_source_id is not null
                        order by uv.id
                        limit :limit
                    ) cu
                    join catalog_source_bindings rvuv on rvuv.catalog_entry_id = cu.id
                    join source_records rv on rv.id = rvuv.source_record_id
                    join media_image_sources ci on ci.id = rv.media_image_source_id
                    where rv.media_image_source_id is not null
                )
                where rn = 1
                order by catalog_entry_id
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                (rs, rowNum) -> new UnifiedCoverBackfillResult(
                        rs.getObject("catalog_entry_id", UUID.class),
                        rs.getObject("media_image_source_id", UUID.class),
                        rs.getString("storage_type"),
                        rs.getString("status")));
        if (candidates.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource[] batch = candidates.stream()
                .map(result -> new MapSqlParameterSource()
                        .addValue("catalogEntryId", result.catalogEntryId())
                        .addValue("coverImageId", result.coverImageId()))
                .toArray(MapSqlParameterSource[]::new);
        int[] updates = jdbcTemplate.batchUpdate(
                """
                update catalog_entries
                set media_image_source_id = :coverImageId,
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and media_image_source_id is null
                """,
                batch);
        List<UnifiedCoverBackfillResult> backfilled = new ArrayList<>();
        for (int i = 0; i < updates.length; i++) {
            if (updates[i] > 0) {
                backfilled.add(candidates.get(i));
            }
        }
        return backfilled;
    }

    public List<UUID> findAdultAssessmentBackfillIds(int limit) {
        return jdbcTemplate.query(
                """
                select id
                  from catalog_entries
                 where adult_checked_at is null
                    or coalesce(adult_assessment ->> 'ruleVersion', '') <> :ruleVersion
                 order by adult_checked_at asc nulls first,
                          updated_at desc nulls last,
                          id asc
                 limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("ruleVersion", AdultContentAssessor.RULE_VERSION)
                        .addValue("limit", Math.max(1, limit)),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    public Optional<UUID> findMatchingCatalogEntry(SourceRecordAggregationRecord sourceRecord) {
        return candidateRepository.findMatchingCatalogEntry(sourceRecord);
    }

    public AggregationMatchPlan findMatchPlan(SourceRecordAggregationRecord sourceRecord) {
        return candidateRepository.findMatchPlan(sourceRecord);
    }

    AggregationMatchPlan findCorrectionMatch(SourceRecordAggregationRecord sourceRecord, UUID sourceUnifiedId) {
        return candidateRepository.findMatchPlan(sourceRecord, sourceUnifiedId);
    }

    BindingCorrectionSnapshot findBindingCorrectionSnapshot(UUID source, UUID target, List<UUID> rawIds) {
        return jdbcTemplate.queryForObject("""
                with source_refs as (
                    select
                        (select count(*) from catalog_source_bindings where catalog_entry_id=:source) as source_members,
                        (select count(*) from catalog_source_bindings where catalog_entry_id=:target) as target_members,
                        (select count(*) from member_catalog_favorites where catalog_entry_id=:source) as favorites,
                        (select count(*) from member_playback_progress h
                          where h.catalog_entry_id=:source or h.last_source_record_id in (:rawIds)
                             or h.last_source_playback_item_id in (
                                 select ep.id from source_playback_items ep join source_playlists pl on pl.id=ep.playlist_id
                                  where pl.source_record_id in (:rawIds)
                             )) as histories,
                        (select count(*) from catalog_magnet_links where catalog_entry_id=:source) as magnets
                ), chosen_bindings as (
                    select source_record_id from catalog_source_bindings
                     where source_record_id in (:rawIds)
                     group by source_record_id having count(*)=1 and bool_and(catalog_entry_id=:source)
                ), target_member_versions as materialized (
                    select r.id, r.xmin::text as revision
                      from catalog_source_bindings link join source_records r on r.id=link.source_record_id
                     where link.catalog_entry_id=:target
                     order by link.source_record_id
                     limit :memberLimit
                )
                select source_members, target_members, favorites, histories, magnets,
                    (select count(*) from chosen_bindings) as expected_bindings,
                    encode(sha256(convert_to(jsonb_build_array(
                        cast(:source as uuid), cast(:target as uuid), to_jsonb(source_refs),
                        (select jsonb_agg(jsonb_build_array(id, xmin::text) order by id)
                           from source_records where id in (:rawIds)),
                        (select jsonb_agg(jsonb_build_array(id, xmin::text) order by id)
                           from catalog_entries where id in (:source, :target)),
                        (select jsonb_agg(jsonb_build_array(id, revision) order by id) from target_member_versions)
                    )::text, 'UTF8')), 'hex') as fingerprint
                from source_refs
                """, correctionParams(source, target, rawIds)
                        .addValue("memberLimit", AggregationMemberIdentity.REVIEW_MEMBER_LIMIT + 1),
                (rs, row) -> new BindingCorrectionSnapshot(rs.getLong("source_members"), rs.getLong("target_members"),
                        rs.getLong("favorites"), rs.getLong("histories"), rs.getLong("magnets"),
                        rs.getInt("expected_bindings"), rs.getString("fingerprint")));
    }

    boolean lockRetainedCorrectionBinding(UUID source, List<UUID> movedIds) {
        return !jdbcTemplate.queryForList("""
                select source_record_id from catalog_source_bindings
                 where catalog_entry_id=:source and source_record_id not in (:rawIds)
                 order by source_record_id limit 1 for update
                """, new MapSqlParameterSource("source", source).addValue("rawIds", movedIds), UUID.class).isEmpty();
    }

    void lockSelectedCorrectionBindings(List<UUID> rawIds) {
        jdbcTemplate.queryForList("""
                select source_record_id from catalog_source_bindings where source_record_id in (:rawIds)
                 order by source_record_id, catalog_entry_id for update
                """, new MapSqlParameterSource("rawIds", rawIds), UUID.class);
    }

    int moveCorrectionBindings(UUID source, UUID target, List<UUID> rawIds) {
        return jdbcTemplate.update("""
                update catalog_source_bindings set catalog_entry_id=:target
                where catalog_entry_id=:source and source_record_id in (:rawIds)
                """, correctionParams(source, target, rawIds));
    }

    private static MapSqlParameterSource correctionParams(UUID source, UUID target, List<UUID> rawIds) {
        return new MapSqlParameterSource("source", source).addValue("target", target).addValue("rawIds", rawIds);
    }

    record BindingCorrectionSnapshot(long sourceMembers, long targetMembers, long favorites,
            long histories, long magnets, int expectedBindings, String fingerprint) { }

    public List<CatalogEntryAggregationCandidate> findContextUnifiedCandidates(List<SourceRecordAggregationRecord> sourceRecords) {
        return candidateRepository.findContextUnifiedCandidates(sourceRecords);
    }

    public UUID createCatalogEntry(SourceRecordAggregationRecord sourceRecord) {
        UUID catalogEntryId = IrcsUuidGenerators.nextId();
        jdbcTemplate.update(
                """
                insert into catalog_entries (
                    id, created_at, updated_at, version,
                    title, alias_title, description, year, score, published_at,
                    total_episodes, duration, remarks, subtitle, season,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                    category_id, category_code, locked_fields, metadata_status
                ) values (
                    :id, now(), now(), 0,
                    :title, :aliasTitle, :description, :year, :score, :publishedAt,
                    :totalEpisodes, :duration, :remarks, :subtitle, :season,
                    :doubanId, :tmdbId, :imdbId, :rottenTomatoesId,
                    (select id from catalog_categories where slug = :categoryCode limit 1), :categoryCode,
                    '[]'::jsonb, 'SYNCED'
                )
                """,
                paramsForUnified(catalogEntryId, sourceRecord));
        return catalogEntryId;
    }

    public int upsertUnifiedFields(UUID catalogEntryId, SourceRecordAggregationRecord sourceRecord) {
        return jdbcTemplate.update(
                """
                update catalog_entries
                set title = coalesce(nullif(title, ''), :title),
                    alias_title = coalesce(nullif(alias_title, ''), :aliasTitle),
                    description = coalesce(nullif(description, ''), :description),
                    year = coalesce(nullif(year, ''), :year),
                    score = coalesce(score, :score),
                    published_at = coalesce(published_at, :publishedAt),
                    total_episodes = coalesce(nullif(total_episodes, ''), :totalEpisodes),
                    duration = coalesce(nullif(duration, ''), :duration),
                    remarks = coalesce(nullif(remarks, ''), :remarks),
                    subtitle = coalesce(nullif(subtitle, ''), :subtitle),
                    season = coalesce(season, :season),
                    douban_id = coalesce(nullif(douban_id, ''), :doubanId),
                    tmdb_id = coalesce(nullif(tmdb_id, ''), :tmdbId),
                    imdb_id = coalesce(nullif(imdb_id, ''), :imdbId),
                    rotten_tomatoes_id = coalesce(nullif(rotten_tomatoes_id, ''), :rottenTomatoesId),
                    category_id = case
                        when :categoryCode is not null and (
                            category_code is null
                            or nullif(trim(category_code), '') is null
                            or lower(category_code) not in (:allowedCategoryCodes)
                        ) then (select id from catalog_categories where slug = :categoryCode limit 1)
                        else category_id
                    end,
                    category_code = case
                        when :categoryCode is not null and (
                            category_code is null
                            or nullif(trim(category_code), '') is null
                            or lower(category_code) not in (:allowedCategoryCodes)
                        ) then :categoryCode
                        else category_code
                    end,
                    metadata_status = 'SYNCED',
                    updated_at = now()
                where id = :id
                """,
                paramsForUnified(catalogEntryId, sourceRecord));
    }

    public int bindRawToUnified(UUID sourceRecordId, UUID catalogEntryId) {
        return jdbcTemplate.update(
                """
                insert into catalog_source_bindings (source_record_id, catalog_entry_id)
                values (:sourceRecordId, :catalogEntryId)
                on conflict do nothing
                """,
                new MapSqlParameterSource()
                        .addValue("sourceRecordId", sourceRecordId)
                        .addValue("catalogEntryId", catalogEntryId));
    }

    public int recordAuthorityBinding(UUID sourceRecordId, UUID catalogEntryId, String evidenceKind) {
        Integer changed = jdbcTemplate.queryForObject("""
                select affirm_catalog_source_match(:sourceRecordId, :catalogEntryId, :evidenceKind)
                """, new MapSqlParameterSource()
                .addValue("sourceRecordId", sourceRecordId)
                .addValue("catalogEntryId", catalogEntryId)
                .addValue("evidenceKind", evidenceKind), Integer.class);
        return changed == null ? 0 : changed;
    }

    public int enqueueAuthorityCatalogImport(String mediaType, long tmdbId, int seasonNumber) {
        return jdbcTemplate.update("""
                insert into catalog_authority_import_jobs(media_type, tmdb_id, season_number, priority)
                select :mediaType, :tmdbId, :seasonNumber, 30
                 where not exists (
                     select 1 from catalog_authority_import_jobs rejected
                      where rejected.media_type=:mediaType and rejected.tmdb_id=:tmdbId
                        and rejected.last_error='ADULT_SOURCE_RECORD'
                 )
                on conflict (media_type, tmdb_id, season_number) do nothing
                """, new MapSqlParameterSource()
                .addValue("mediaType", mediaType)
                .addValue("tmdbId", tmdbId)
                .addValue("seasonNumber", seasonNumber));
    }

    public void rebuildUnifiedPipelineRelations(UUID catalogEntryId) {
        rebuildUnifiedPipelineMetadataRelations(catalogEntryId);
        rebuildUnifiedPipelineCategory(catalogEntryId);
    }

    public void rebuildUnifiedPipelineMetadataRelations(UUID catalogEntryId) {
        Set<String> lockedFields = findLockedFields(catalogEntryId);
        rebuildUnifiedFlatMetadata(catalogEntryId, lockedFields);
        evictMetadataNameCache(MetadataNameOwnerType.UNIFIED, List.of(catalogEntryId));
    }

    public void rebuildUnifiedPipelineScalars(UUID catalogEntryId) {
        rebuildUnifiedPipelineBasicAttributes(catalogEntryId);
        rebuildUnifiedPipelineExternalIds(catalogEntryId);
        rebuildUnifiedPipelineDynamicFields(catalogEntryId);
    }

    public void rebuildUnifiedPipelineBasicAttributes(UUID catalogEntryId) {
        var display = findUnifiedDisplayState(catalogEntryId);
        if (display.isEmpty()) { return; }
        UnifiedDisplayState current = display.orElseThrow();
        Set<String> lockedFields = lockedFieldsWithCatalog(catalogEntryId, current.storedLocks());
        MapSqlParameterSource params = new MapSqlParameterSource("catalogEntryId", catalogEntryId);
        if (current.sports()) {
            rebuildSportsDisplayIdentity(catalogEntryId, current.identity(), lockedFields, params);
        } else {
            if (!isLocked(lockedFields, "title")) {
                updateUnifiedStringByVote("title", "title", params);
            }
            if (!isLocked(lockedFields, "year")) {
                updateUnifiedStringByVote("year", "year", params);
            }
        }
        if (!isLocked(lockedFields, "score")) {
            updateUnifiedScoreMax(params);
        }
        if (!isLocked(lockedFields, "publishedAt")) {
            updateUnifiedFirstDate("published_at", "published_at", params);
        }
    }

    public void rebuildUnifiedPipelineExternalIds(UUID catalogEntryId) {
        MapSqlParameterSource params = new MapSqlParameterSource("catalogEntryId", catalogEntryId);
        List<String> columns = List.of("douban_id", "tmdb_id", "imdb_id", "rotten_tomatoes_id");
        List<String[]> roots = jdbcTemplate.query("""
                select douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, cast(locked_fields as varchar) as external_id_locks
                  from catalog_entries where id = :catalogEntryId
                """, params, (rs, rowNum) -> new String[] {
                        rs.getString("douban_id"), rs.getString("tmdb_id"), rs.getString("imdb_id"), rs.getString("rotten_tomatoes_id"),
                        rs.getString("external_id_locks")});
        if (roots.isEmpty()) { return; }
        String[] observed = roots.getFirst();
        Set<String> lockedFields = parseLockedFields(observed[4]);
        boolean[] needsEvidence = new boolean[columns.size()];
        boolean[] clearRejected = new boolean[columns.size()];
        boolean needsAnyEvidence = false;
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            needsEvidence[index] = !isLocked(lockedFields, column, column.replace("_", ""), "externalIds", "external_ids")
                    && usableExternalIdOrNull(externalIdProvider(column), observed[index]) == null;
            clearRejected[index] = needsEvidence[index]
                    && matchingStrategy.isRejectedExternalId(externalIdProvider(column), observed[index]);
            needsAnyEvidence |= needsEvidence[index];
        }
        if (!needsAnyEvidence) { return; }
        List<UUID> target = List.of(catalogEntryId);
        List<AggregationMemberIdentity> members = findBoundMemberIdentities(target);
        String[] selected = new String[columns.size()];
        if (members.size() <= AggregationMemberIdentity.REVIEW_MEMBER_LIMIT) {
            for (AggregationMemberIdentity member : members) {
                String[] memberIds = {member.doubanId(), member.tmdbId(), member.imdbId(), member.rottenTomatoesId()};
                for (int index = 0; index < columns.size(); index++) {
                    if (needsEvidence[index] && selected[index] == null) {
                        selected[index] = usableExternalIdOrNull(externalIdProvider(columns.get(index)), memberIds[index]);
                    }
                }
            }
            if (java.util.Arrays.stream(selected).anyMatch(java.util.Objects::nonNull)
                    && !canRefillUnifiedExternalIds(target, members)) {
                java.util.Arrays.fill(selected, null);
            }
        }
        for (int index = 0; index < columns.size(); index++) {
            if (selected[index] == null && !clearRejected[index]) { continue; }
            String column = columns.get(index);
            jdbcTemplate.update("""
                    update catalog_entries set %s = :selectedId, updated_at = now(), version = coalesce(version, 0) + 1
                     where id = :catalogEntryId and coalesce(%s, '') = coalesce(:observedId, '')
                       and coalesce(cast(locked_fields as varchar), '') = coalesce(:observedLocks, '')
                    """.formatted(column, column), new MapSqlParameterSource("catalogEntryId", catalogEntryId)
                    .addValue("selectedId", selected[index]).addValue("observedId", observed[index])
                    .addValue("observedLocks", observed[4]));
        }
    }

    private boolean canRefillUnifiedExternalIds(List<UUID> ids, List<AggregationMemberIdentity> members) {
        // A historical cluster may have been joined by a rejected ID. Keep clearing a
        // placeholder separate from copying a new authority ID into the cluster.
        return findUnifiedCandidatesByIds(ids).stream().findFirst()
                .map(root -> matchingStrategy.clusterBindingConflictReason(root, members, List.of()) == null)
                .orElse(false);
    }

    public void rebuildUnifiedPipelineDynamicFields(UUID catalogEntryId) {
        var display = findUnifiedDisplayState(catalogEntryId);
        if (display.isEmpty()) { return; }
        UnifiedDisplayState current = display.orElseThrow();
        Set<String> lockedFields = lockedFieldsWithCatalog(catalogEntryId, current.storedLocks());
        MapSqlParameterSource params = new MapSqlParameterSource("catalogEntryId", catalogEntryId);
        if (!isLocked(lockedFields, "aliasTitle")) {
            updateUnifiedLongestString("alias_title", "alias_title", params);
        }
        if (!isLocked(lockedFields, "description")) {
            updateUnifiedLongestString("description", "description", params);
        }
        if (!isLocked(lockedFields, "remarks")) {
            updateUnifiedLatestRemarks(params);
        }
        if (!isLocked(lockedFields, "totalEpisodes")) {
            updateUnifiedFirstString("total_episodes", "total_episodes", params);
        }
        if (!isLocked(lockedFields, "duration")) {
            updateUnifiedFirstString("duration", "duration", params);
        }
        if (!isLocked(lockedFields, "season")) {
            updateUnifiedFirstInteger("season", "season", params);
        }
        if (!current.sports() && !isLocked(lockedFields, "subtitle")) {
            updateUnifiedLongestString("subtitle", "subtitle", params);
        }
    }

    private Optional<UnifiedDisplayState> findUnifiedDisplayState(UUID catalogEntryId) {
        return jdbcTemplate.query("""
                select category_code, title, subtitle, year, cast(locked_fields as varchar) as field_locks
                  from catalog_entries where id=:catalogEntryId
                """, new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                (rs, row) -> new UnifiedDisplayState("sports".equals(rs.getString("category_code")),
                        new SportsDisplayIdentity(rs.getString("title"), rs.getString("subtitle"), rs.getString("year")),
                        rs.getString("field_locks"))).stream().findFirst();
    }

    private void rebuildSportsDisplayIdentity(UUID catalogEntryId, SportsDisplayIdentity current,
            Set<String> lockedFields, MapSqlParameterSource params) {
        boolean titleLocked = isLocked(lockedFields, "title");
        boolean subtitleLocked = isLocked(lockedFields, "subtitle");
        boolean yearLocked = isLocked(lockedFields, "year");
        if (titleLocked && subtitleLocked && yearLocked) { return; }
        List<AggregationMemberIdentity> members = findBoundMemberIdentities(List.of(catalogEntryId));
        if (members.size() > AggregationMemberIdentity.REVIEW_MEMBER_LIMIT) { return; }
        // A competition heading and the opposing teams must come from the same observed row.
        // Vote over complete tuples so a long subtitle from another event cannot win alone.
        Map<SportsDisplayIdentity, Integer> votes = new LinkedHashMap<>();
        for (AggregationMemberIdentity member : members) {
            if (!"sports".equals(member.category()) || !StringUtils.hasText(member.title())) { continue; }
            SportsDisplayIdentity source = new SportsDisplayIdentity(member.title(), member.subtitle(), member.year());
            if (titleLocked && !Objects.equals(current.title(), source.title())
                    || subtitleLocked && !Objects.equals(current.subtitle(), source.subtitle())
                    || yearLocked && !Objects.equals(current.year(), source.year())) { continue; }
            votes.merge(source, 1, Integer::sum);
        }
        SportsDisplayIdentity selected = votes.entrySet().stream().max(
                Comparator.<Map.Entry<SportsDisplayIdentity, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().equals(current)))
                .map(Map.Entry::getKey).orElse(current);
        if (!selected.equals(current)) {
            jdbcTemplate.update("""
                    update catalog_entries set title=:sportsTitle, subtitle=:sportsSubtitle, year=:sportsYear,
                      updated_at=now(), version=coalesce(version, 0)+1 where id=:catalogEntryId
                    """, params.addValue("sportsTitle", selected.title()).addValue("sportsSubtitle", selected.subtitle())
                            .addValue("sportsYear", selected.year()));
        }
    }

    private record SportsDisplayIdentity(String title, String subtitle, String year) { }

    private record UnifiedDisplayState(boolean sports, SportsDisplayIdentity identity, String storedLocks) { }

    public void rebuildUnifiedPipelineCategory(UUID catalogEntryId) {
        Set<String> lockedFields = findLockedFields(catalogEntryId);
        if (!isLocked(lockedFields, "category")) {
            MapSqlParameterSource params = new MapSqlParameterSource("catalogEntryId", catalogEntryId);
            voteUnifiedCategory(params);
            voteUnifiedCategoryCode(params);
        }
    }

    public void rebuildUnifiedAdultAssessment(UUID catalogEntryId) {
        lockAdultAssessmentRow(catalogEntryId);
        if (adultFieldsLocked(catalogEntryId)) { return; }
        Optional<UnifiedAdultAssessmentBase> base = findUnifiedAdultAssessmentBase(catalogEntryId);
        if (base.isEmpty()) {
            return;
        }
        AdultAssessment assessment = assessOrDefer(catalogEntryId, new AdultAssessmentInput(
                base.get().title(),
                base.get().aliasTitle(),
                base.get().description(),
                base.get().remarks(),
                base.get().subtitle(),
                base.get().categoryCode(),
                base.get().categoryName(),
                JsonStringArrays.readSet(objectMapper, base.get().actorNamesJson()),
                JsonStringArrays.readSet(objectMapper, base.get().directorNamesJson()),
                JsonStringArrays.readSet(objectMapper, base.get().genreCodesJson()),
                findAdultAssessmentSources(catalogEntryId)));
        writeUnifiedAdultAssessment(catalogEntryId, assessment);
    }

    public List<UUID> rebuildUnifiedAdultAssessments(List<UUID> catalogEntryIds) {
        List<UUID> orderedIds = catalogEntryIds == null
                ? List.of()
                : catalogEntryIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        orderedIds.stream().sorted().forEach(this::lockAdultAssessmentRow);
        Map<UUID, UnifiedAdultAssessmentBase> bases = findUnifiedAdultAssessmentBases(orderedIds);
        if (bases.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AdultAssessmentInput.SourceEvidence>> sourcesByUnifiedId =
                findAdultAssessmentSources(orderedIds);
        Map<UUID, AdultAssessmentInput> inputs = new LinkedHashMap<>();
        for (UUID catalogEntryId : orderedIds) {
            UnifiedAdultAssessmentBase base = bases.get(catalogEntryId);
            if (base == null || adultFieldsLocked(catalogEntryId)) {
                continue;
            }
            inputs.put(catalogEntryId, new AdultAssessmentInput(
                    base.title(),
                    base.aliasTitle(),
                    base.description(),
                    base.remarks(),
                    base.subtitle(),
                    base.categoryCode(),
                    base.categoryName(),
                    JsonStringArrays.readSet(objectMapper, base.actorNamesJson()),
                    JsonStringArrays.readSet(objectMapper, base.directorNamesJson()),
                    JsonStringArrays.readSet(objectMapper, base.genreCodesJson()),
                    sourcesByUnifiedId.getOrDefault(catalogEntryId, List.of())));
        }
        Map<UUID, AdultAssessment> assessments;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            assessments = new LinkedHashMap<>();
            inputs.forEach((id, input) -> assessments.put(id, assessOrDefer(id, input)));
        } else {
            assessments = adultAssessmentEvaluator.assessAll(inputs);
        }
        List<AdultAssessmentWrite> writes = new ArrayList<>(assessments.size());
        for (UUID catalogEntryId : orderedIds) {
            AdultAssessment assessment = assessments.get(catalogEntryId);
            if (assessment == null) {
                continue;
            }
            writes.add(new AdultAssessmentWrite(catalogEntryId, assessment));
        }
        return writeUnifiedAdultAssessments(writes);
    }

    private void lockAdultAssessmentRow(UUID id) {
        jdbcTemplate.query("select id from catalog_entries where id=:id for update",
                new MapSqlParameterSource("id", id), (rs, row) -> rs.getObject("id", UUID.class));
    }

    private boolean adultFieldsLocked(UUID id) {
        return findLockedFields(id).stream().map(value -> value.replace("_", "").toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("adult") || value.equals("adultrestricted") || value.equals("adultassessment"));
    }

    private AdultAssessment assessOrDefer(UUID id, AdultAssessmentInput input) {
        if (!DeferredAdultAssessments.register(id)) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new AdultAssessmentDeferredException("CONTENT_SAFETY_ORCHESTRATION_REQUIRED", 60);
            }
            return adultAssessmentEvaluator.assess(id, input);
        }
        AdultAssessment local = AdultContentAssessor.assess(input);
        if (local.adultRestricted()) {
            DeferredAdultAssessments.registerRestricted(id);
            return local;
        }
        var signals = new ArrayList<>(local.signals());
        signals.add(new com.prodigalgal.ircs.common.adult.AdultAssessmentSignal(
                "model_unavailable", "combinedText", "PENDING_REVIEW", 0,
                "内容安全远端判断将在聚合事务提交后执行"));
        return new AdultAssessment(local.ruleVersion(),
                com.prodigalgal.ircs.common.adult.AdultAssessmentLevel.SUSPECT, false, 0, signals);
    }

    // The full source row digests intentionally include more than just title text: source restriction,
    // bindings, deletion, cover/source-domain changes and standard category changes invalidate a result.
    static final String ADULT_SOURCE_DIGEST = """
            md5(coalesce((select jsonb_agg(jsonb_build_array(to_jsonb(rv),to_jsonb(ds),to_jsonb(ci),to_jsonb(sd))
                                               order by rv.id)::text
                            from catalog_source_bindings links
                            join source_records rv on rv.id=links.source_record_id
                            left join collection_sources ds on ds.id=rv.collection_source_id
                            left join media_image_sources ci on ci.id=rv.media_image_source_id
                            left join source_origins sd on sd.id=ci.source_origin_id
                           where links.catalog_entry_id=uv.id),'')
                || coalesce((select to_jsonb(sc)::text from catalog_categories sc where sc.slug=uv.category_code),''))
            """;

    AdultAssessmentSnapshot captureAdultAssessment(UUID id) {
        if (adultFieldsLocked(id)) { return null; }
        Optional<UnifiedAdultAssessmentBase> maybeBase = findUnifiedAdultAssessmentBase(id);
        if (maybeBase.isEmpty()) { return null; }
        UnifiedAdultAssessmentBase base = maybeBase.orElseThrow();
        AdultAssessmentInput input = new AdultAssessmentInput(base.title(), base.aliasTitle(), base.description(),
                base.remarks(), base.subtitle(), base.categoryCode(), base.categoryName(),
                JsonStringArrays.readSet(objectMapper, base.actorNamesJson()),
                JsonStringArrays.readSet(objectMapper, base.directorNamesJson()),
                JsonStringArrays.readSet(objectMapper, base.genreCodesJson()), findAdultAssessmentSources(id));
        List<AdultAssessmentSnapshot> snapshots = jdbcTemplate.query(
                "select md5(to_jsonb(uv)::text) as unified_digest, " + ADULT_SOURCE_DIGEST
                        + " as source_digest from catalog_entries uv where uv.id=:id",
                new MapSqlParameterSource("id", id), (rs, row) -> new AdultAssessmentSnapshot(
                        id, rs.getString("unified_digest"), rs.getString("source_digest"), input));
        return snapshots.stream().findFirst().orElse(null);
    }

    Map<UUID, AdultAssessment> evaluateAdultAssessments(List<AdultAssessmentSnapshot> snapshots) {
        Map<UUID, AdultAssessmentInput> inputs = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> inputs.put(snapshot.id(), snapshot.input()));
        return adultAssessmentEvaluator.assessAll(inputs);
    }

    boolean applyAdultAssessment(AdultAssessmentSnapshot snapshot, AdultAssessment assessment) {
        try {
            return applyAdultAssessmentWithLocks(snapshot, assessment);
        } catch (org.springframework.dao.DataAccessException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                    throw new AdultAssessmentDeferredException("CONTENT_SAFETY_SNAPSHOT_CHANGED", 2);
                }
            }
            throw ex;
        }
    }

    private boolean applyAdultAssessmentWithLocks(AdultAssessmentSnapshot snapshot, AdultAssessment assessment) {
        lockAdultAssessmentRow(snapshot.id());
        if (adultFieldsLocked(snapshot.id())) { return false; }
        // Lock existing inputs before a new READ COMMITTED statement rechecks their digest.
        // The parent FOR UPDATE also fences FK binding inserts until this short transaction ends.
        MapSqlParameterSource params = new MapSqlParameterSource("id", snapshot.id());
        jdbcTemplate.query("""
                select links.source_record_id from catalog_source_bindings links
                join source_records rv on rv.id=links.source_record_id
                where links.catalog_entry_id=:id order by rv.id for share of links, rv nowait
                """, params, (rs, row) -> rs.getObject(1, UUID.class));
        for (String sourceLock : List.of(
                "select ds.id from collection_sources ds where ds.id in (select rv.collection_source_id from source_records rv join catalog_source_bindings l on l.source_record_id=rv.id where l.catalog_entry_id=:id) order by ds.id for share nowait",
                "select ci.id from media_image_sources ci where ci.id in (select rv.media_image_source_id from source_records rv join catalog_source_bindings l on l.source_record_id=rv.id where l.catalog_entry_id=:id) order by ci.id for share nowait",
                "select sd.id from source_origins sd where sd.id in (select ci.source_origin_id from media_image_sources ci join source_records rv on rv.media_image_source_id=ci.id join catalog_source_bindings l on l.source_record_id=rv.id where l.catalog_entry_id=:id) order by sd.id for share nowait",
                "select sc.id from catalog_categories sc where sc.slug=(select uv.category_code from catalog_entries uv where uv.id=:id) for share nowait")) {
            jdbcTemplate.query(sourceLock, params, (rs, row) -> rs.getObject(1));
        }
        return jdbcTemplate.update("""
                update catalog_entries uv
                   set adult_restricted=:adultRestricted, adult_assessment=cast(:assessment as jsonb),
                       adult_checked_at=now(), updated_at=now(), version=coalesce(uv.version,0)+1
                 where uv.id=:id and md5(to_jsonb(uv)::text)=:unifiedDigest and
                """ + ADULT_SOURCE_DIGEST + "=:sourceDigest",
                new MapSqlParameterSource("id", snapshot.id())
                        .addValue("unifiedDigest", snapshot.unifiedDigest())
                        .addValue("sourceDigest", snapshot.sourceDigest())
                        .addValue("adultRestricted", assessment.adultRestricted())
                        .addValue("assessment", writeJson(assessment))) > 0;
    }

    public List<UUID> rebuildUnifiedPipelineCoverImage(UUID catalogEntryId) {
        List<CoverImageChoice> choices = findCoverImageChoices(catalogEntryId);
        Optional<CoverImageChoice> currentCover = findCurrentUnifiedCover(catalogEntryId);
        if (currentCover.isEmpty() && choices.isEmpty()) {
            return List.of();
        }

        CoverImageChoice selected = currentCover.orElseGet(() -> choices.get(0));
        List<UUID> promoteCandidates = new ArrayList<>();
        if (currentCover.isEmpty() && selected.isLocalStored()) {
            promoteCandidates.add(selected.id());
        }
        int startIndex = currentCover.isPresent() ? 0 : 1;
        for (int i = startIndex; i < choices.size(); i++) {
            CoverImageChoice source = choices.get(i);
            if (shouldReplaceCover(selected, source)) {
                selected = source;
                if (source.isLocalStored()) {
                    promoteCandidates.add(source.id());
                }
            }
        }
        updateUnifiedCoverImage(catalogEntryId, selected.id());
        return promoteCandidates;
    }

    public void mergeDuplicateCatalogEntries(UUID rootCatalogEntryId, List<UUID> victimCatalogEntryIds) {
        if (victimCatalogEntryIds == null || victimCatalogEntryIds.isEmpty()) {
            return;
        }
        List<UUID> victims = victimCatalogEntryIds.stream()
                .filter(victimId -> !rootCatalogEntryId.equals(victimId))
                .distinct()
                .toList();
        if (victims.isEmpty()) {
            return;
        }

        List<UUID> allIds = new ArrayList<>(victims);
        allIds.add(rootCatalogEntryId);
        lockCatalogEntries(allIds);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("rootId", rootCatalogEntryId)
                .addValue("victimIds", victims);
        migrateRawBindingsToRoot(params);
        migrateFavoritesToRoot(params);
        migrateWatchHistoriesToRoot(params);
        mergeVictimScalarsToRoot(rootCatalogEntryId, victims);
        mergeVictimFlatMetadataToRoot(rootCatalogEntryId, victims);
        deleteVictimUnifiedRows(params);
        List<UUID> changedIds = new ArrayList<>(victims.size() + 1);
        changedIds.add(rootCatalogEntryId);
        changedIds.addAll(victims);
        evictMetadataNameCache(MetadataNameOwnerType.UNIFIED, changedIds);
    }

    public int markBound(UUID sourceRecordId) {
        return updateAggregationStatus(sourceRecordId, "BOUND");
    }

    public int markUnifiedSynced(UUID catalogEntryId) {
        return jdbcTemplate.update(
                """
                update catalog_entries
                set metadata_status = 'SYNCED',
                    updated_at = now()
                where id = :catalogEntryId
                  and exists (select 1 from catalog_source_bindings link where link.catalog_entry_id=:catalogEntryId)
                """,
                new MapSqlParameterSource("catalogEntryId", catalogEntryId));
    }

    public int markUnifiedDirty(UUID catalogEntryId) {
        return jdbcTemplate.update(
                """
                update catalog_entries
                set metadata_status = 'DIRTY',
                    updated_at = now()
                where id = :catalogEntryId
                """,
                new MapSqlParameterSource("catalogEntryId", catalogEntryId));
    }

    public int markPending(UUID sourceRecordId) {
        // A queue item can outlive the work it described (ops backfill, manual status edits), so a
        // blind `PENDING` write would demote an already BOUND row and re-queue it indefinitely.
        return jdbcTemplate.update(
                """
            update source_records
            set aggregation_status = 'PENDING',
                aggregation_status_updated_at = now()
            where id = :sourceRecordId
              and aggregation_status not in ('BOUND', 'PENDING')
                """,
                new MapSqlParameterSource().addValue("sourceRecordId", sourceRecordId));
    }

    public int resetStuckProcessing(int timeoutMinutes, int limit) {
        return jdbcTemplate.update(
                """
                with stuck as (
                    select id
                  from source_records
                  where aggregation_status = 'PROCESSING'
                    and aggregation_status_updated_at < now() - (:timeoutMinutes * interval '1 minute')
                  order by aggregation_status_updated_at asc
                  limit :limit
                  for update skip locked
                )
              update source_records rv
              set aggregation_status = 'PENDING',
                  aggregation_status_updated_at = now()
                from stuck
                where rv.id = stuck.id
                """,
                new MapSqlParameterSource()
                        .addValue("timeoutMinutes", timeoutMinutes)
                        .addValue("limit", limit));
    }

    public List<UUID> sampleSourceRecordIds(int limit) {
        return jdbcTemplate.queryForList(
                """
                select id
                  from source_records
                 order by aggregation_status_updated_at asc, id asc
                 limit :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                UUID.class);
    }

    public long countSourceRecords() {
        return count("source_records");
    }

    public long countCatalogEntries() {
        return count("catalog_entries");
    }

    public long countRawUnifiedBindings() {
        return count("catalog_source_bindings");
    }

    // Transaction-scoped advisory locks coordinate every replica and release on rollback.
    public void lockAggregationMaintenance() {
        jdbcTemplate.getJdbcTemplate().execute("select pg_advisory_xact_lock(194223, 1)");
    }

    public void lockAggregationShared() {
        jdbcTemplate.getJdbcTemplate().execute("select pg_advisory_xact_lock_shared(194223, 1)");
    }

    public int deleteAllAggregationResetOwnedRows() {
        int changedRows = 0;
        changedRows += updateIfTableExists("""
                delete from magnet_discovery_provider_runs
                 where job_id in (
                       select id
                         from magnet_discovery_jobs
                        where catalog_entry_id in (select id from catalog_entries)
                 )
                """);
        changedRows += updateIfTableExists("""
                delete from magnet_discovery_jobs
                 where catalog_entry_id in (select id from catalog_entries)
                """);
        changedRows += updateIfTableExists("""
                delete from catalog_magnet_links
                 where catalog_entry_id in (select id from catalog_entries)
                """);
        changedRows += updateIfTableExists("""
                delete from member_catalog_favorites
                 where catalog_entry_id in (select id from catalog_entries)
                """);
        changedRows += updateIfTableExists("""
                delete from member_playback_progress
                 where catalog_entry_id in (select id from catalog_entries)
                """);
        changedRows += updateIfTableExists("delete from catalog_source_bindings");
        changedRows += updateIfTableExists("delete from catalog_entry_tags");
        changedRows += jdbcTemplate.update("delete from catalog_entries", new MapSqlParameterSource());
        evictAllMetadataNameCache();
        return changedRows;
    }

    public int markAllRawAggregationPending() {
        return jdbcTemplate.update(
                """
                update source_records
                   set aggregation_status = 'PENDING',
                       aggregation_status_updated_at = now()
                """,
                new MapSqlParameterSource());
    }

    private int updateAggregationStatus(UUID sourceRecordId, String status) {
        return jdbcTemplate.update(
                """
            update source_records
            set aggregation_status = :status,
                aggregation_status_updated_at = now()
            where id = :sourceRecordId
                """,
                new MapSqlParameterSource()
                        .addValue("sourceRecordId", sourceRecordId)
                        .addValue("status", status));
    }

    private long count(String tableName) {
        Long value = jdbcTemplate.queryForObject("select count(*) from " + tableName, new MapSqlParameterSource(), Long.class);
        return value == null ? 0 : value;
    }

    private int updateIfTableExists(String sql) {
        try {
            return jdbcTemplate.update(sql, new MapSqlParameterSource());
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (isMissingTable(ex)) {
                return 0;
            }
            throw ex;
        }
    }

    private boolean isMissingTable(org.springframework.jdbc.BadSqlGrammarException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("does not exist") || normalized.contains("not found")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void migrateRawBindingsToRoot(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_source_bindings set catalog_entry_id=:rootId
                where catalog_entry_id in (:victimIds)
                """,
                params);
    }

    private void migrateFavoritesToRoot(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                insert into member_catalog_favorites (id, created_at, updated_at, version, member_id, catalog_entry_id)
                select distinct on (member_id) uuidv7(), created_at, now(), 0, member_id, :rootId
                from member_catalog_favorites where catalog_entry_id in (:victimIds)
                order by member_id, created_at, id
                on conflict (member_id, catalog_entry_id) do nothing
                """,
                params);
        jdbcTemplate.update(
                """
                delete from member_catalog_favorites where catalog_entry_id in (:victimIds)
                """,
                params);
    }

    private void migrateWatchHistoriesToRoot(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                insert into member_playback_progress (id, created_at, updated_at, version, member_id, catalog_entry_id,
                    work_id, season_id, catalog_episode_id, last_source_record_id, last_source_playback_item_id,
                    progress_seconds, duration_seconds, episode_name, last_watched_at)
                select distinct on (old.member_id) uuidv7(), old.created_at, old.updated_at, 0, old.member_id, entry.id,
                    entry.work_id, coalesce(playback.season_id,entry.season_id), playback.episode_id,
                    playback.source_record_id, playback.source_playback_item_id,
                    old.progress_seconds, old.duration_seconds, old.episode_name, old.last_watched_at
                from member_playback_progress old cross join catalog_entries entry
                left join catalog_playback_bindings playback on playback.work_id=entry.work_id
                    and playback.season_id is not distinct from entry.season_id
                    and playback.source_playback_item_id=old.last_source_playback_item_id
                where old.catalog_entry_id in (:victimIds) and entry.id=:rootId
                order by old.member_id, old.last_watched_at desc, old.id
                on conflict (member_id, catalog_entry_id) do update set
                    updated_at=excluded.updated_at, version=coalesce(member_playback_progress.version,0)+1,
                    season_id=excluded.season_id, catalog_episode_id=excluded.catalog_episode_id,
                    last_source_record_id=excluded.last_source_record_id,
                    last_source_playback_item_id=excluded.last_source_playback_item_id,
                    progress_seconds=excluded.progress_seconds, duration_seconds=excluded.duration_seconds,
                    episode_name=excluded.episode_name, last_watched_at=excluded.last_watched_at
                where member_playback_progress.last_watched_at < excluded.last_watched_at
                """,
                params);
        jdbcTemplate.update(
                """
                delete from member_playback_progress where catalog_entry_id in (:victimIds)
                """,
                params);
    }

    private Set<String> findLockedFields(UUID catalogEntryId) {
        try {
            String lockedFields = jdbcTemplate.queryForObject(
                    """
                    select cast(locked_fields as varchar)
                    from catalog_entries
                    where id = :catalogEntryId
                    """,
                    new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                    String.class);
            return lockedFieldsWithCatalog(catalogEntryId, lockedFields);
        } catch (EmptyResultDataAccessException ignored) {
            return Set.of();
        }
    }

    private Set<String> lockedFieldsWithCatalog(UUID catalogEntryId, String storedLocks) {
        Set<String> retained = new LinkedHashSet<>(parseLockedFields(storedLocks));
        retainVerifiedCatalogFields(catalogEntryId, retained);
        return retained;
    }

    private void retainVerifiedCatalogFields(UUID catalogEntryId, Set<String> retained) {
        Map<String,String> fields = Map.ofEntries(
                Map.entry("title","title"), Map.entry("year","year"), Map.entry("score","score"),
                Map.entry("published_at","publishedat"), Map.entry("alias_title","aliastitle"),
                Map.entry("description","description"), Map.entry("season","season"),
                Map.entry("actor_names","actors"), Map.entry("director_names","directors"),
                Map.entry("area_codes","area"), Map.entry("language_codes","language"), Map.entry("genre_codes","genres"));
        // Retain only recorded, verified authority values; these are not persisted as manual locks.
        jdbcTemplate.query("""
                select uv.* from catalog_entries uv where uv.id=:catalogEntryId
                  and exists(select 1 from catalog_metadata_enrichment_jobs job
                             where job.catalog_entry_id=uv.id and job.last_success_at is not null)
                """, new MapSqlParameterSource("catalogEntryId",catalogEntryId),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    java.sql.ResultSetMetaData metadata = rs.getMetaData();
                    for (int n=1;n<=metadata.getColumnCount();n++) {
                        String column=metadata.getColumnLabel(n).toLowerCase(Locale.ROOT);
                        String field=fields.get(column);
                        String value=rs.getString(n);
                        if (field!=null && value!=null && !value.isBlank() && !"[]".equals(value)) retained.add(field);
                    }
                });
    }

    private Set<String> parseLockedFields(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        String text = value.trim()
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .toLowerCase(Locale.ROOT);
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        Matcher matcher = LOCKED_FIELD_PATTERN.matcher(text);
        while (matcher.find()) {
            String field = matcher.group(1);
            if (StringUtils.hasText(field)) {
                fields.add(field.trim().toLowerCase(Locale.ROOT));
            }
        }
        String normalized = text
                .replace("[", "")
                .replace("]", "")
                .replace("{", "")
                .replace("}", "")
                .replace("\"", "");
        for (String part : normalized.split(",")) {
            if (StringUtils.hasText(part)) {
                fields.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        addKnownLockedFieldIfPresent(fields, text, "category");
        addKnownLockedFieldIfPresent(fields, text, "actors");
        addKnownLockedFieldIfPresent(fields, text, "directors");
        addKnownLockedFieldIfPresent(fields, text, "genres");
        addKnownLockedFieldIfPresent(fields, text, "area");
        addKnownLockedFieldIfPresent(fields, text, "areas");
        addKnownLockedFieldIfPresent(fields, text, "language");
        addKnownLockedFieldIfPresent(fields, text, "languages");
        addKnownLockedFieldIfPresent(fields, text, "aliastitle");
        addKnownLockedFieldIfPresent(fields, text, "publishedat");
        addKnownLockedFieldIfPresent(fields, text, "totalepisodes");
        addKnownLockedFieldIfPresent(fields, text, "coverimageurl");
        return Set.copyOf(fields);
    }

    private void addKnownLockedFieldIfPresent(Set<String> fields, String text, String field) {
        if (text.contains("\"" + field + "\"") || text.contains(field)) {
            fields.add(field);
        }
    }

    private boolean isLocked(Set<String> lockedFields, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (lockedFields.contains(fieldName.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void updateUnifiedStringByVote(String unifiedColumn, String rawColumn, MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set %s = (
                        select selected_value
                        from (
                            select trim(%s) as selected_value, count(*) as votes
                            from catalog_source_bindings rvuv
                            join source_records rv on rv.id = rvuv.source_record_id
                            where rvuv.catalog_entry_id = :catalogEntryId
                              and nullif(trim(%s), '') is not null
                            group by trim(%s)
                            order by votes desc, selected_value asc
                            limit 1
                        ) voted
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and nullif(trim(%s), '') is not null
                  )
                """.formatted(unifiedColumn, rawColumn, rawColumn, rawColumn, rawColumn),
                params);
    }

    private void updateUnifiedScoreMax(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set score = (
                        select max(rv.score)
                        from catalog_source_bindings rvuv
                        join source_records rv on rv.id = rvuv.source_record_id
                        where rvuv.catalog_entry_id = :catalogEntryId
                          and rv.score is not null
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and rv.score is not null
                  )
                """,
                params);
    }

    private void updateUnifiedFirstDate(String unifiedColumn, String rawColumn, MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set %s = (
                        select %s
                        from catalog_source_bindings rvuv
                        join source_records rv on rv.id = rvuv.source_record_id
                        where rvuv.catalog_entry_id = :catalogEntryId
                          and %s is not null
                        order by rv.created_at asc, rv.id asc
                        limit 1
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and %s is not null
                  )
                """.formatted(unifiedColumn, rawColumn, rawColumn, rawColumn),
                params);
    }

    private void updateUnifiedLongestString(String unifiedColumn, String rawColumn, MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set %s = (
                        select selected_value
                        from (
                            select trim(%s) as selected_value, rv.created_at, rv.id
                            from catalog_source_bindings rvuv
                            join source_records rv on rv.id = rvuv.source_record_id
                            where rvuv.catalog_entry_id = :catalogEntryId
                              and nullif(trim(%s), '') is not null
                        ) values_by_length
                        order by length(selected_value) desc, created_at asc, id asc
                        limit 1
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and nullif(trim(%s), '') is not null
                  )
                """.formatted(unifiedColumn, rawColumn, rawColumn, rawColumn),
                params);
    }

    private void updateUnifiedLatestRemarks(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set remarks = (
                        select trim(rv.remarks)
                        from catalog_source_bindings rvuv
                        join source_records rv on rv.id = rvuv.source_record_id
                        where rvuv.catalog_entry_id = :catalogEntryId
                          and nullif(trim(rv.remarks), '') is not null
                        order by rv.updated_at desc, rv.id asc
                        limit 1
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and nullif(trim(rv.remarks), '') is not null
                  )
                """,
                params);
    }

    private void updateUnifiedFirstString(String unifiedColumn, String rawColumn, MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set %s = (
                        select trim(%s)
                        from catalog_source_bindings rvuv
                        join source_records rv on rv.id = rvuv.source_record_id
                        where rvuv.catalog_entry_id = :catalogEntryId
                          and nullif(trim(%s), '') is not null
                        order by rv.created_at asc, rv.id asc
                        limit 1
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and nullif(trim(%s), '') is not null
                  )
                """.formatted(unifiedColumn, rawColumn, rawColumn, rawColumn),
                params);
    }

    private void updateUnifiedFirstInteger(String unifiedColumn, String rawColumn, MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set %s = (
                        select %s
                        from catalog_source_bindings rvuv
                        join source_records rv on rv.id = rvuv.source_record_id
                        where rvuv.catalog_entry_id = :catalogEntryId
                          and %s is not null
                        order by rv.created_at asc, rv.id asc
                        limit 1
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and %s is not null
                  )
                """.formatted(unifiedColumn, rawColumn, rawColumn, rawColumn),
                params);
    }

    private String externalIdProvider(String column) {
        return switch (column) {
            case "douban_id" -> "douban";
            case "tmdb_id" -> "tmdb";
            case "imdb_id" -> "imdb";
            case "rotten_tomatoes_id" -> "rt";
            default -> throw new IllegalArgumentException("Unsupported external identifier column: " + column);
        };
    }

    private void rebuildUnifiedFlatMetadata(UUID catalogEntryId, Set<String> lockedFields) {
        List<RawFlatMetadataRow> rows = findRawFlatMetadataRows(catalogEntryId);
        if (rows.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("catalogEntryId", catalogEntryId);
        appendFlatJsonUpdate(params, "actorNames", collectFlatValues(rows, RawFlatMetadataRow::actorNames),
                !isLocked(lockedFields, "actors"));
        appendFlatJsonUpdate(params, "directorNames", collectFlatValues(rows, RawFlatMetadataRow::directorNames),
                !isLocked(lockedFields, "directors"));
        appendFlatJsonUpdate(params, "areaCodes", collectFlatValues(rows, RawFlatMetadataRow::areaCodes),
                !isLocked(lockedFields, "area", "areas"));
        appendFlatJsonUpdate(params, "languageCodes", collectFlatValues(rows, RawFlatMetadataRow::languageCodes),
                !isLocked(lockedFields, "language", "languages"));
        appendFlatJsonUpdate(params, "genreCodes", collectFlatValues(rows, RawFlatMetadataRow::genreCodes),
                !isLocked(lockedFields, "genres"));
        if (params.getValues().size() == 1) {
            return;
        }
        String setSql = params.getValues().keySet().stream()
                .filter(name -> !"catalogEntryId".equals(name))
                .map(this::flatJsonSetSql)
                .reduce((left, right) -> left + ",\n                       " + right)
                .orElse("");
        jdbcTemplate.update(
                """
                update catalog_entries
                   set %s,
                       updated_at = now(),
                       version = coalesce(version, 0) + 1
                 where id = :catalogEntryId
                """.formatted(setSql),
                params);
    }

    private List<RawFlatMetadataRow> findRawFlatMetadataRows(UUID catalogEntryId) {
        return jdbcTemplate.query(
                """
                select cast(rv.actor_names as varchar) as actor_names,
                       cast(rv.director_names as varchar) as director_names,
                       cast(rv.area_codes as varchar) as area_codes,
                       cast(rv.language_codes as varchar) as language_codes,
                       cast(rv.genre_codes as varchar) as genre_codes,
                       rv.category_code
                  from catalog_source_bindings rvuv
                  join source_records rv on rv.id = rvuv.source_record_id
                 where rvuv.catalog_entry_id = :catalogEntryId
                """,
                new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                (rs, rowNum) -> new RawFlatMetadataRow(
                        JsonStringArrays.readSet(objectMapper, rs.getString("actor_names")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("director_names")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("area_codes")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("language_codes")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("genre_codes")),
                        rs.getString("category_code")));
    }

    private Set<String> collectFlatValues(
            List<RawFlatMetadataRow> rows,
            java.util.function.Function<RawFlatMetadataRow, Set<String>> extractor) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (RawFlatMetadataRow row : rows) {
            values.addAll(extractor.apply(row));
        }
        return JsonStringArrays.normalize(values);
    }

    private void appendFlatJsonUpdate(
            MapSqlParameterSource params,
            String paramName,
            Set<String> values,
            boolean updateAllowed) {
        if (updateAllowed && !values.isEmpty()) {
            params.addValue(paramName, JsonStringArrays.write(objectMapper, values));
        }
    }

    private String flatJsonSetSql(String paramName) {
        String columnName = switch (paramName) {
            case "actorNames" -> "actor_names";
            case "directorNames" -> "director_names";
            case "areaCodes" -> "area_codes";
            case "languageCodes" -> "language_codes";
            case "genreCodes" -> "genre_codes";
            default -> throw new IllegalArgumentException("Unsupported flat metadata parameter: " + paramName);
        };
        return "%s = cast(:%s as jsonb)".formatted(columnName, paramName);
    }

    private List<CoverImageChoice> findCoverImageChoices(UUID catalogEntryId) {
        return jdbcTemplate.query(
                """
                select ci.id, ci.storage_type, ci.status, ci.file_size
                from catalog_source_bindings rvuv
                join source_records rv on rv.id = rvuv.source_record_id
                join media_image_sources ci on ci.id = rv.media_image_source_id
                where rvuv.catalog_entry_id = :catalogEntryId
                order by rv.created_at asc, rv.id asc
                """,
                new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                (rs, rowNum) -> new CoverImageChoice(
                        rs.getObject("id", UUID.class),
                        rs.getString("storage_type"),
                        rs.getString("status"),
                        rs.getLong("file_size"),
                        rs.wasNull()));
    }

    private Optional<CoverImageChoice> findCurrentUnifiedCover(UUID catalogEntryId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select ci.id, ci.storage_type, ci.status, ci.file_size
                    from catalog_entries uv
                    join media_image_sources ci on ci.id = uv.media_image_source_id
                    where uv.id = :catalogEntryId
                    """,
                    new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                    (rs, rowNum) -> new CoverImageChoice(
                            rs.getObject("id", UUID.class),
                            rs.getString("storage_type"),
                            rs.getString("status"),
                            rs.getLong("file_size"),
                            rs.wasNull())));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private boolean shouldReplaceCover(CoverImageChoice current, CoverImageChoice source) {
        boolean sourceIsManaged = source.isManaged();
        boolean currentIsManaged = current.isManaged();
        if (sourceIsManaged && !currentIsManaged) {
            return true;
        }
        if (sourceIsManaged == currentIsManaged) {
            return source.fileSizeOrZero() > current.fileSizeOrZero() * 1.1;
        }
        return false;
    }

    private void updateUnifiedCoverImage(UUID catalogEntryId, UUID coverImageId) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set media_image_source_id = :coverImageId,
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and (media_image_source_id is null or media_image_source_id <> :coverImageId)
                """,
                new MapSqlParameterSource()
                        .addValue("catalogEntryId", catalogEntryId)
                        .addValue("coverImageId", coverImageId));
    }

    private void voteUnifiedCategory(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set category_id = (
                        select category_id
                        from (
                            select sc.id as category_id, count(*) as votes
                            from catalog_source_bindings rvuv
                            join source_records rv on rv.id = rvuv.source_record_id
                            join catalog_categories sc on sc.slug = rv.category_code
                            where rvuv.catalog_entry_id = :catalogEntryId
                              and nullif(trim(rv.category_code), '') is not null
                            group by sc.id
                            order by votes desc, cast(sc.id as varchar) asc
                            limit 1
                        ) voted
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      join catalog_categories sc on sc.slug = rv.category_code
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and nullif(trim(rv.category_code), '') is not null
                  )
                """,
                params);
    }

    private void voteUnifiedCategoryCode(MapSqlParameterSource params) {
        jdbcTemplate.update(
                """
                update catalog_entries
                set category_code = (
                        select category_code
                        from (
                            select rv.category_code, count(*) as votes
                            from catalog_source_bindings rvuv
                            join source_records rv on rv.id = rvuv.source_record_id
                            where rvuv.catalog_entry_id = :catalogEntryId
                              and nullif(trim(rv.category_code), '') is not null
                            group by rv.category_code
                            order by votes desc, rv.category_code asc
                            limit 1
                        ) voted
                    ),
                    updated_at = now(),
                    version = coalesce(version, 0) + 1
                where id = :catalogEntryId
                  and exists (
                      select 1
                      from catalog_source_bindings rvuv
                      join source_records rv on rv.id = rvuv.source_record_id
                      where rvuv.catalog_entry_id = :catalogEntryId
                        and nullif(trim(rv.category_code), '') is not null
                  )
                """,
                params);
    }

    private Optional<UnifiedAdultAssessmentBase> findUnifiedAdultAssessmentBase(UUID catalogEntryId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select uv.id,
                           uv.title,
                           uv.alias_title,
                           uv.description,
                           uv.remarks,
                           uv.subtitle,
                           uv.category_code,
                           sc.name as category_name,
                           cast(uv.actor_names as varchar) as actor_names,
                           cast(uv.director_names as varchar) as director_names,
                           cast(uv.genre_codes as varchar) as genre_codes
                      from catalog_entries uv
                      left join catalog_categories sc on sc.slug = uv.category_code
                     where uv.id = :catalogEntryId
                    """,
                    new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                    this::mapUnifiedAdultAssessmentBase));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private Map<UUID, UnifiedAdultAssessmentBase> findUnifiedAdultAssessmentBases(Collection<UUID> catalogEntryIds) {
        List<UnifiedAdultAssessmentBase> rows = jdbcTemplate.query(
                """
                select uv.id,
                       uv.title,
                       uv.alias_title,
                       uv.description,
                       uv.remarks,
                       uv.subtitle,
                       uv.category_code,
                       sc.name as category_name,
                       cast(uv.actor_names as varchar) as actor_names,
                       cast(uv.director_names as varchar) as director_names,
                       cast(uv.genre_codes as varchar) as genre_codes
                  from catalog_entries uv
                  left join catalog_categories sc on sc.slug = uv.category_code
                 where uv.id in (:ids)
                """,
                new MapSqlParameterSource("ids", catalogEntryIds),
                this::mapUnifiedAdultAssessmentBase);
        Map<UUID, UnifiedAdultAssessmentBase> byId = new LinkedHashMap<>();
        for (UnifiedAdultAssessmentBase row : rows) {
            byId.putIfAbsent(row.id(), row);
        }
        return byId;
    }

    private List<AdultAssessmentInput.SourceEvidence> findAdultAssessmentSources(UUID catalogEntryId) {
        return jdbcTemplate.query(
                """
                select ds.name as data_source_name,
                       coalesce(ds.adult_restricted, false) as data_source_adult_restricted,
                       rv.source_category_code,
                       rv.source_category_name,
                       coalesce(ds.base_url, sd.domain_value) as source_domain,
                       cast(null as varchar) as raw_metadata
                  from catalog_source_bindings rvuv
                  join source_records rv on rv.id = rvuv.source_record_id
                  left join collection_sources ds on ds.id = rv.collection_source_id
                  left join media_image_sources ci on ci.id = rv.media_image_source_id
                  left join source_origins sd on sd.id = ci.source_origin_id
                 where rvuv.catalog_entry_id = :catalogEntryId
                 order by rv.created_at asc, rv.id asc
                """,
                new MapSqlParameterSource("catalogEntryId", catalogEntryId),
                (rs, rowNum) -> new AdultAssessmentInput.SourceEvidence(
                        rs.getString("data_source_name"),
                        rs.getBoolean("data_source_adult_restricted"),
                        rs.getString("source_category_code"),
                        rs.getString("source_category_name"),
                        rs.getString("source_domain"),
                        rs.getString("raw_metadata")));
    }

    private Map<UUID, List<AdultAssessmentInput.SourceEvidence>> findAdultAssessmentSources(
            Collection<UUID> catalogEntryIds) {
        Map<UUID, List<AdultAssessmentInput.SourceEvidence>> sourcesByUnifiedId = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                select rvuv.catalog_entry_id,
                       ds.name as data_source_name,
                       coalesce(ds.adult_restricted, false) as data_source_adult_restricted,
                       rv.source_category_code,
                       rv.source_category_name,
                       coalesce(ds.base_url, sd.domain_value) as source_domain,
                       cast(null as varchar) as raw_metadata
                  from catalog_source_bindings rvuv
                  join source_records rv on rv.id = rvuv.source_record_id
                  left join collection_sources ds on ds.id = rv.collection_source_id
                  left join media_image_sources ci on ci.id = rv.media_image_source_id
                  left join source_origins sd on sd.id = ci.source_origin_id
                 where rvuv.catalog_entry_id in (:ids)
                 order by rvuv.catalog_entry_id, rv.created_at asc, rv.id asc
                """,
                new MapSqlParameterSource("ids", catalogEntryIds),
                rs -> {
                    UUID catalogEntryId = rs.getObject("catalog_entry_id", UUID.class);
                    sourcesByUnifiedId.computeIfAbsent(catalogEntryId, ignored -> new ArrayList<>())
                            .add(new AdultAssessmentInput.SourceEvidence(
                                    rs.getString("data_source_name"),
                                    rs.getBoolean("data_source_adult_restricted"),
                                    rs.getString("source_category_code"),
                                    rs.getString("source_category_name"),
                                    rs.getString("source_domain"),
                                    rs.getString("raw_metadata")));
                });
        return sourcesByUnifiedId;
    }

    private void writeUnifiedAdultAssessment(UUID catalogEntryId, AdultAssessment assessment) {
        jdbcTemplate.update(
                """
                update catalog_entries
                   set adult_restricted = :adultRestricted,
                       adult_assessment = cast(:adultAssessment as jsonb),
                       adult_checked_at = case when :pending then null else now() end,
                       updated_at = now(),
                       version = coalesce(version, 0) + 1
                 where id = :catalogEntryId
                """,
                new MapSqlParameterSource()
                        .addValue("catalogEntryId", catalogEntryId)
                        .addValue("adultRestricted", assessment.adultRestricted())
                        .addValue("adultAssessment", writeJson(assessment))
                        .addValue("pending", DeferredAdultAssessments.contains(catalogEntryId)));
    }

    private List<UUID> writeUnifiedAdultAssessments(List<AdultAssessmentWrite> writes) {
        if (writes == null || writes.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource[] batch = writes.stream()
                .map(write -> new MapSqlParameterSource()
                        .addValue("catalogEntryId", write.catalogEntryId())
                        .addValue("adultRestricted", write.assessment().adultRestricted())
                        .addValue("adultAssessment", writeJson(write.assessment()))
                        .addValue("pending", DeferredAdultAssessments.contains(write.catalogEntryId())))
                .toArray(MapSqlParameterSource[]::new);
        int[] updates = jdbcTemplate.batchUpdate(
                """
                update catalog_entries
                   set adult_restricted = :adultRestricted,
                       adult_assessment = cast(:adultAssessment as jsonb),
                       adult_checked_at = case when :pending then null else now() end,
                       updated_at = now(),
                       version = coalesce(version, 0) + 1
                 where id = :catalogEntryId
                """,
                batch);
        List<UUID> updatedIds = new ArrayList<>(updates.length);
        for (int i = 0; i < updates.length; i++) {
            if (updates[i] > 0) {
                updatedIds.add(writes.get(i).catalogEntryId());
            }
        }
        return updatedIds;
    }

    private UnifiedAdultAssessmentBase mapUnifiedAdultAssessmentBase(ResultSet rs, int rowNum) throws SQLException {
        return new UnifiedAdultAssessmentBase(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                rs.getString("description"),
                rs.getString("remarks"),
                rs.getString("subtitle"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getString("actor_names"),
                rs.getString("director_names"),
                rs.getString("genre_codes"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalStateException("Failed to serialize adult assessment", ex);
        }
    }

    private void mergeVictimScalarsToRoot(UUID rootCatalogEntryId, List<UUID> victimCatalogEntryIds) {
        for (UUID victimCatalogEntryId : victimCatalogEntryIds) {
            jdbcTemplate.update(
                    """
                    update catalog_entries root
                    set douban_id = coalesce(nullif(root.douban_id, ''), nullif(victim.douban_id, '')),
                        tmdb_id = coalesce(nullif(root.tmdb_id, ''), nullif(victim.tmdb_id, '')),
                        imdb_id = coalesce(nullif(root.imdb_id, ''), nullif(victim.imdb_id, '')),
                        rotten_tomatoes_id = coalesce(
                            nullif(root.rotten_tomatoes_id, ''),
                            nullif(victim.rotten_tomatoes_id, '')
                        ),
                        description = coalesce(nullif(root.description, ''), nullif(victim.description, '')),
                        score = coalesce(root.score, victim.score),
                        published_at = coalesce(root.published_at, victim.published_at),
                        alias_title = coalesce(nullif(root.alias_title, ''), nullif(victim.alias_title, '')),
                        total_episodes = coalesce(nullif(root.total_episodes, ''), nullif(victim.total_episodes, '')),
                        duration = coalesce(nullif(root.duration, ''), nullif(victim.duration, '')),
                        remarks = coalesce(nullif(root.remarks, ''), nullif(victim.remarks, '')),
                        season = coalesce(root.season, victim.season),
                        subtitle = coalesce(nullif(root.subtitle, ''), nullif(victim.subtitle, '')),
                        last_trend_at = case
                            when root.last_trend_at is null then victim.last_trend_at
                            when victim.last_trend_at is null then root.last_trend_at
                            when victim.last_trend_at > root.last_trend_at then victim.last_trend_at
                            else root.last_trend_at
                        end,
                        metadata_status = 'DIRTY',
                        updated_at = now(),
                        version = coalesce(root.version, 0) + 1
                    from catalog_entries victim
                    where root.id = :rootId
                      and victim.id = :victimId
                    """,
                    new MapSqlParameterSource()
                            .addValue("rootId", rootCatalogEntryId)
                            .addValue("victimId", victimCatalogEntryId));
        }
    }

    private void mergeVictimFlatMetadataToRoot(UUID rootCatalogEntryId, List<UUID> victimCatalogEntryIds) {
        if (victimCatalogEntryIds == null || victimCatalogEntryIds.isEmpty()) {
            return;
        }
        List<RawFlatMetadataRow> rows = jdbcTemplate.query(
                """
                select cast(actor_names as varchar) as actor_names,
                       cast(director_names as varchar) as director_names,
                       cast(area_codes as varchar) as area_codes,
                       cast(language_codes as varchar) as language_codes,
                       cast(genre_codes as varchar) as genre_codes,
                       category_code
                 from catalog_entries
                 where id = :rootId
                    or id in (:victimIds)
                 order by case when id = :rootId then 0 else 1 end, id
                """,
                new MapSqlParameterSource()
                        .addValue("rootId", rootCatalogEntryId)
                        .addValue("victimIds", victimCatalogEntryIds),
                (rs, rowNum) -> new RawFlatMetadataRow(
                        JsonStringArrays.readSet(objectMapper, rs.getString("actor_names")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("director_names")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("area_codes")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("language_codes")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("genre_codes")),
                        rs.getString("category_code")));
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                """
                update catalog_entries
                   set actor_names = cast(:actorNames as jsonb),
                       director_names = cast(:directorNames as jsonb),
                       area_codes = cast(:areaCodes as jsonb),
                       language_codes = cast(:languageCodes as jsonb),
                       genre_codes = cast(:genreCodes as jsonb),
                       category_code = case
                           when nullif(:categoryCode, '') is not null and (
                               category_code is null
                               or nullif(trim(category_code), '') is null
                               or lower(category_code) not in (:allowedCategoryCodes)
                           ) then :categoryCode
                           else category_code
                       end,
                       updated_at = now(),
                       version = coalesce(version, 0) + 1
                 where id = :rootId
                """,
                new MapSqlParameterSource()
                        .addValue("rootId", rootCatalogEntryId)
                        .addValue("actorNames", JsonStringArrays.write(
                                objectMapper,
                                collectFlatValues(rows, RawFlatMetadataRow::actorNames)))
                        .addValue("directorNames", JsonStringArrays.write(
                                objectMapper,
                                collectFlatValues(rows, RawFlatMetadataRow::directorNames)))
                        .addValue("areaCodes", JsonStringArrays.write(
                                objectMapper,
                                collectFlatValues(rows, RawFlatMetadataRow::areaCodes)))
                        .addValue("languageCodes", JsonStringArrays.write(
                                objectMapper,
                                collectFlatValues(rows, RawFlatMetadataRow::languageCodes)))
                        .addValue("genreCodes", JsonStringArrays.write(
                                objectMapper,
                                collectFlatValues(rows, RawFlatMetadataRow::genreCodes)))
                        .addValue("categoryCode", firstCategoryCode(rows))
                        .addValue("allowedCategoryCodes", ALLOWED_CATEGORY_CODES));
    }

    private String firstCategoryCode(List<RawFlatMetadataRow> rows) {
        for (RawFlatMetadataRow row : rows) {
            if (StringUtils.hasText(row.categoryCode())) {
                return row.categoryCode().trim();
            }
        }
        return null;
    }

    private void deleteVictimUnifiedRows(MapSqlParameterSource params) {
        jdbcTemplate.update("delete from catalog_entries where id in (:victimIds)", params);
    }

    private SourceRecordAggregationRecord mapSourceRecord(ResultSet rs, int rowNum) throws SQLException {
        Date publishedAt = rs.getDate("published_at");
        return new SourceRecordAggregationRecord(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                rs.getString("description"),
                rs.getString("year"),
                rs.getBigDecimal("score"),
                publishedAt == null ? null : publishedAt.toLocalDate(),
                rs.getString("total_episodes"),
                rs.getString("duration"),
                rs.getString("remarks"),
                rs.getString("subtitle"),
                (Integer) rs.getObject("season"),
                rs.getString("category_name"),
                rs.getString("category_code"),
                rs.getString("douban_id"),
                rs.getString("tmdb_id"),
                rs.getString("imdb_id"),
                rs.getString("rotten_tomatoes_id"),
                rs.getString("normalization_status"),
                rs.getString("enrichment_status"),
                rs.getString("aggregation_status"))
                .withMetadata(JsonStringArrays.readSet(objectMapper, rs.getString("actor_names")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("director_names")),
                        JsonStringArrays.readSet(objectMapper, rs.getString("area_codes")));
    }

    private void evictMetadataNameCache(MetadataNameOwnerType ownerType, Collection<UUID> ownerIds) {
        List<UUID> ids = List.copyOf(ownerIds);
        afterCommitOrNow(() -> metadataNameRepository.evictOwner(ownerType, ids));
    }

    private void evictAllMetadataNameCache() {
        afterCommitOrNow(metadataNameRepository::evictAll);
    }

    private void afterCommitOrNow(Runnable invalidation) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() { invalidation.run(); }
                    });
        } else {
            invalidation.run();
        }
    }

    private MapSqlParameterSource paramsForUnified(UUID catalogEntryId, SourceRecordAggregationRecord sourceRecord) {
        return new MapSqlParameterSource()
                .addValue("id", catalogEntryId)
                .addValue("title", trimToLength(sourceRecord.title(), 255))
                .addValue("aliasTitle", trimToLength(sourceRecord.aliasTitle(), 255))
                .addValue("description", sourceRecord.description())
                .addValue("year", trimToLength(sourceRecord.year(), 20))
                .addValue("score", sourceRecord.score())
                .addValue("publishedAt", sourceRecord.publishedAt() == null ? null : Date.valueOf(sourceRecord.publishedAt()))
                .addValue("totalEpisodes", trimToLength(sourceRecord.totalEpisodes(), 50))
                .addValue("duration", trimToLength(sourceRecord.duration(), 50))
                .addValue("remarks", trimToLength(sourceRecord.remarks(), 255))
                .addValue("subtitle", trimToLength(sourceRecord.subtitle(), 255))
                .addValue("season", sourceRecord.season())
                .addValue("categoryCode", trimToLength(sourceRecord.categoryCode(), 100))
                .addValue("allowedCategoryCodes", ALLOWED_CATEGORY_CODES)
                .addValue("doubanId", trimToLength(usableExternalIdOrNull("douban", sourceRecord.doubanId()), 20))
                .addValue("tmdbId", trimToLength(usableExternalIdOrNull("tmdb", sourceRecord.tmdbId()), 20))
                .addValue("imdbId", trimToLength(usableExternalIdOrNull("imdb", sourceRecord.imdbId()), 20))
                .addValue("rottenTomatoesId", trimToLength(usableExternalIdOrNull("rt", sourceRecord.rottenTomatoesId()), 50));
    }

    private String usableExternalIdOrNull(String provider, String value) {
        return matchingStrategy.normalizeExternalId(provider, value);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private record CoverImageChoice(UUID id, String storageType, String status, long fileSize, boolean fileSizeWasNull) {
        boolean isManaged() {
            return !"EXTERNAL".equals(storageType);
        }

        boolean isLocalStored() {
            return "LOCAL".equals(storageType) && "LOCAL_STORED".equals(status);
        }

        long fileSizeOrZero() {
            return fileSizeWasNull ? 0L : fileSize;
        }
    }

    private record RawFlatMetadataRow(
            Set<String> actorNames,
            Set<String> directorNames,
            Set<String> areaCodes,
            Set<String> languageCodes,
            Set<String> genreCodes,
            String categoryCode) {
    }

    private record UnifiedAdultAssessmentBase(
            UUID id,
            String title,
            String aliasTitle,
            String description,
            String remarks,
            String subtitle,
            String categoryCode,
            String categoryName,
            String actorNamesJson,
            String directorNamesJson,
            String genreCodesJson) {
    }

    private record AdultAssessmentWrite(UUID catalogEntryId, AdultAssessment assessment) {
    }
}
