package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contentmetadata.PersonNames;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class CatalogMetadataRepository {
    private static final String OWNED = """
            unified_video_id = :id and status = 'DISPATCHING' and lease_token = :token and lease_until > now()
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public List<CatalogMetadataTarget> claim(int limit) {
        return jdbc.query("""
                with due as (
                    select j.unified_video_id from catalog_metadata_requests j
                    join unified_videos uv on uv.id = j.unified_video_id
                    where ((j.status in ('PENDING','RETRY','UNAVAILABLE') and j.next_attempt_at <= now())
                        or (j.status = 'DISPATCHING' and j.lease_until <= now()))
                      and coalesce(uv.adult_restricted, false) = false
                      and lower(coalesce(uv.adult_assessment ->> 'adultRestricted', 'false')) = 'false'
                      and upper(coalesce(uv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
                      and uv.content_visibility = 'PUBLIC'
                    order by j.next_attempt_at, j.created_at, j.unified_video_id limit :limit
                    for update of j skip locked
                ), claimed as (
                    update catalog_metadata_requests j set status='DISPATCHING', lease_token=:token,
                        lease_until=now()+interval '10 minutes', last_attempt_at=now(),
                        attempts=least(j.attempts+1,1000000), updated_at=now()
                    from due where j.unified_video_id=due.unified_video_id returning j.*
                )
                select j.*, uv.title, uv.alias_title, uv.year,
                    case when j.provider='TMDB' then j.external_id else (
                        select identity.external_id from external_catalog_identities identity
                        where identity.unified_video_id=j.unified_video_id and identity.provider='TMDB'
                          and identity.media_type=j.media_type and identity.season=j.season
                        limit 1) end as verified_tmdb_id
                from claimed j join unified_videos uv on uv.id=j.unified_video_id
                """, new MapSqlParameterSource("token", UUID.randomUUID()).addValue("limit", Math.max(1, Math.min(5, limit))),
                (rs, n) -> new CatalogMetadataTarget(rs.getObject("unified_video_id", UUID.class),
                        rs.getObject("lease_token", UUID.class), rs.getString("provider"), rs.getString("media_type"),
                        rs.getString("external_id"), rs.getInt("season"), rs.getInt("attempts"), rs.getString("title"),
                        rs.getString("alias_title"), rs.getString("year"), rs.getString("verified_tmdb_id")));
    }

    public boolean renew(CatalogMetadataTarget target) {
        return jdbc.update("update catalog_metadata_requests set lease_until=now()+interval '10 minutes' where " + OWNED,
                owned(target)) == 1;
    }

    public boolean defer(CatalogMetadataTarget target, boolean unavailable, String code) {
        long seconds = unavailable ? Duration.ofDays(1).toSeconds()
                : Math.min(21600, 300L << Math.min(6, Math.max(0, target.attempts() - 1)));
        return jdbc.update("""
                update catalog_metadata_requests set status=:status, next_attempt_at=now()+(:seconds*interval '1 second'),
                    lease_token=null, lease_until=null, last_error=:error, updated_at=now() where
                """ + OWNED, owned(target).addValue("status", unavailable ? "UNAVAILABLE" : "RETRY")
                .addValue("seconds", seconds).addValue("error", code != null && code.matches("[A-Z0-9_]{1,120}") ? code : "SOURCE_FAILED")) == 1;
    }

    @Transactional(timeout = 15)
    public boolean apply(CatalogMetadataTarget target, CatalogMetadataEvidence evidence) {
        if (evidence == null || evidence.metadata() == null || target.tmdbId() == null
                || !target.tmdbId().equals(evidence.tmdbId()) || !target.mediaType().equals(evidence.mediaType())
                || target.season() != evidence.season()) throw new CatalogMetadataUnavailableException("CATALOG_IDENTITY_MISMATCH");
        List<String> rows = jdbc.query("""
                select to_jsonb(uv)::text from unified_videos uv
                join catalog_metadata_requests j on j.unified_video_id=uv.id
                where j.unified_video_id=:id and j.status='DISPATCHING' and j.lease_token=:token and j.lease_until>now()
                for update of uv, j
                """, owned(target), (rs, n) -> rs.getString(1));
        if (rows.isEmpty()) return false;
        JsonNode current = mapper.readTree(rows.getFirst());
        String currentTmdb = AuthoritativeIds.normalizeTmdb(current.path("tmdb_id").asText(null));
        if (currentTmdb != null && !currentTmdb.equals(evidence.tmdbId())) {
            throw new CatalogMetadataUnavailableException("CATALOG_IDENTITY_CHANGED");
        }
        // A moderator can change visibility/adult scope while the HTTP request is in flight.
        if (!"PUBLIC".equals(current.path("content_visibility").asText()) || current.path("adult_restricted").asBoolean()
                || !"false".equalsIgnoreCase(current.path("adult_assessment").path("adultRestricted").asText("false"))
                || "ADULT".equalsIgnoreCase(current.path("adult_assessment").path("level").asText())) {
            throw new CatalogMetadataUnavailableException("CATALOG_SCOPE_CHANGED");
        }
        EnrichedMetadataDTO facts = evidence.metadata();
        Map<String, Object> changes = new LinkedHashMap<>();
        scalar(current, changes, "alias_title", facts.getOriginalTitle(), "aliasTitle");
        scalar(current, changes, "description", facts.getDescription(), "description");
        scalar(current, changes, "year", facts.getYear(), "year");
        scalar(current, changes, "tmdb_id", evidence.tmdbId(), "tmdbId", "externalIds");
        scalar(current, changes, "imdb_id", AuthoritativeIds.normalizeImdb(facts.getImdbId()), "imdbId", "externalIds");
        if (facts.getScore() != null && facts.getScore().signum() >= 0 && facts.getScore().doubleValue() <= 10) {
            scalar(current, changes, "score", facts.getScore(), "score");
        }
        if (facts.getPublishedAt() != null) scalar(current, changes, "published_at", Date.valueOf(facts.getPublishedAt()), "publishedAt");
        scalar(current, changes, "cover_image_id", missing(current, "cover_image_id")
                && !locked(current, "coverImageUrl", "coverImageId", "cover") ? registerCover(facts.getPosterUrl()) : null,
                "coverImageUrl", "coverImageId", "cover");
        array(current, changes, "actor_names", PersonNames.normalize(facts.getActorNames()), "actors", "actorNames");
        array(current, changes, "director_names", PersonNames.normalize(facts.getDirectorNames()), "directors", "directorNames");
        Map<String, Set<String>> codes = resolveCodes(facts);
        array(current, changes, "area_codes", codes.get("AREA"), "area", "areas", "areaCodes");
        array(current, changes, "language_codes", codes.get("LANGUAGE"), "language", "languages", "languageCodes");
        array(current, changes, "genre_codes", codes.get("GENRE"), "genres", "genreCodes");
        if (!changes.isEmpty()) {
            List<String> assignments = new ArrayList<>();
            MapSqlParameterSource p = owned(target);
            changes.forEach((column, value) -> {
                assignments.add(column + " = " + (column.endsWith("_names") || column.endsWith("_codes")
                        ? "cast(:" + column + " as jsonb)" : ":" + column));
                p.addValue(column, value);
            });
            jdbc.update("update unified_videos set " + String.join(", ", assignments)
                    + ", updated_at=now(), version=coalesce(version,0)+1 where id=:id", p);
        }
        // Enrichment completion is separate from resource readiness; incomplete data remains DIRTY.
        jdbc.update("""
                update unified_videos uv set metadata_status=case when
                    nullif(btrim(title),'') is not null and nullif(btrim(year),'') is not null
                    and nullif(btrim(description),'') is not null and published_at is not null and cover_image_id is not null
                    and score is not null and actor_names <> '[]'::jsonb and director_names <> '[]'::jsonb
                    and area_codes <> '[]'::jsonb and language_codes <> '[]'::jsonb and genre_codes <> '[]'::jsonb
                    then 'SYNCED' else 'DIRTY' end where id=:id
                """, owned(target));
        return jdbc.update("""
                update catalog_metadata_requests set status='SUCCESS', last_success_at=now(), last_error=null,
                    lease_token=null, lease_until=null, index_pending=true, index_generation=index_generation+1,
                    updated_at=now() where
                """ + OWNED, owned(target)) == 1;
    }

    public List<IndexPending> pendingIndexes() {
        return jdbc.query("select unified_video_id,index_generation from catalog_metadata_requests where index_pending order by updated_at limit 20",
                Map.of(), (rs, n) -> new IndexPending(rs.getObject(1, UUID.class), rs.getLong(2)));
    }

    public void indexed(IndexPending pending) {
        jdbc.update("update catalog_metadata_requests set index_pending=false where unified_video_id=:id and index_generation=:generation",
                Map.of("id", pending.id(), "generation", pending.generation()));
    }

    UUID createAuthorityProjection(AuthorityCatalogEvidence evidence) {
        EnrichedMetadataDTO facts = evidence.metadata();
        UUID id = IrcsUuidGenerators.nextId();
        Map<String, Set<String>> codes = resolveCodes(facts);
        UUID cover = registerCover(facts.getPosterUrl());
        List<String> locks = new ArrayList<>(List.of(
                "title", "aliasTitle", "year", "category", "externalIds", "season", "totalEpisodes",
                "description", "score", "publishedAt"));
        if (cover != null) locks.add("cover");
        if (!facts.getActorNames().isEmpty()) locks.add("actors");
        if (!facts.getDirectorNames().isEmpty()) locks.add("directors");
        if (!codes.get("AREA").isEmpty()) locks.add("areas");
        if (!codes.get("LANGUAGE").isEmpty()) locks.add("languages");
        if (!codes.get("GENRE").isEmpty()) locks.add("genres");
        MapSqlParameterSource p = new MapSqlParameterSource("id", id)
                .addValue("title", facts.getTitle()).addValue("alias", facts.getOriginalTitle())
                .addValue("description", facts.getDescription()).addValue("year", facts.getYear())
                .addValue("score", facts.getScore()).addValue("published", facts.getPublishedAt())
                .addValue("cover", cover).addValue("locks", json(locks))
                .addValue("tmdb", Long.toString(evidence.tmdbId())).addValue("imdb", facts.getImdbId())
                .addValue("season", evidence.seasonNumber() < 0 ? null : evidence.seasonNumber())
                .addValue("episodes", evidence.episodes().isEmpty() ? null : Integer.toString(evidence.episodes().size()))
                .addValue("category", evidence.categoryCode())
                .addValue("actors", json(facts.getActorNames())).addValue("directors", json(facts.getDirectorNames()))
                .addValue("areas", json(codes.get("AREA"))).addValue("languages", json(codes.get("LANGUAGE")))
                .addValue("genres", json(codes.get("GENRE")));
        jdbc.update("""
                insert into unified_videos(id, created_at, updated_at, version, title, alias_title,
                    description, year, score, published_at, cover_image_id, tmdb_id, imdb_id, season,
                    total_episodes, category_code, content_visibility, metadata_status, locked_fields,
                    actor_names, director_names, area_codes, language_codes, genre_codes)
                values (:id, now(), now(), 0, left(:title,255), left(:alias,255),
                    left(:description,5000), :year, :score, :published, :cover, :tmdb, :imdb, :season,
                    :episodes, :category, 'PUBLIC', 'SYNCED',
                    cast(:locks as jsonb),
                    cast(:actors as jsonb), cast(:directors as jsonb), cast(:areas as jsonb),
                    cast(:languages as jsonb), cast(:genres as jsonb))
                """, p);
        return id;
    }

    void refreshAuthorityProjection(UUID id, AuthorityCatalogEvidence evidence, String previousTitle, String previousAlias) {
        var facts = evidence.metadata();
        // Refresh observed fields and keep an explicit title override made by moderation.
        jdbc.update("""
                update unified_videos set
                    title=case when title=left(:previousTitle,255) then left(:title,255) else title end,
                    alias_title=case when alias_title is not distinct from left(:previousAlias,255)
                                    then left(:alias,255) else alias_title end,
                    score=coalesce(:score,score), imdb_id=coalesce(:imdb,imdb_id),
                    total_episodes=coalesce(:episodes,total_episodes), version=coalesce(version,0)+1
                 where id=:id and content_visibility='PUBLIC' and not coalesce(adult_restricted,false)
                """, new MapSqlParameterSource("id", id).addValue("previousTitle", previousTitle)
                .addValue("previousAlias", previousAlias).addValue("title", facts.getTitle())
                .addValue("alias", facts.getOriginalTitle()).addValue("score", facts.getScore())
                .addValue("imdb", facts.getImdbId())
                .addValue("episodes", evidence.episodes().isEmpty() ? null : Integer.toString(evidence.episodes().size())));
    }

    private Map<String, Set<String>> resolveCodes(EnrichedMetadataDTO facts) {
        MapSqlParameterSource p = new MapSqlParameterSource("area", json(facts.getArea() == null ? Set.of() : Set.of(facts.getArea())))
                .addValue("language", json(facts.getLanguage() == null ? Set.of() : Set.of(facts.getLanguage())))
                .addValue("genres", json(facts.getGenreNames() == null ? Set.of() : facts.getGenreNames()));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String kind : List.of("AREA", "LANGUAGE", "GENRE")) result.put(kind, new java.util.LinkedHashSet<>());
        jdbc.query("""
                with inputs as materialized (
                    select 'AREA' as kind, lower(value) as token from jsonb_array_elements_text(cast(:area as jsonb))
                    union all select 'LANGUAGE',lower(value) from jsonb_array_elements_text(cast(:language as jsonb))
                    union all select 'GENRE',lower(value) from jsonb_array_elements_text(cast(:genres as jsonb))
                )
                select distinct 'AREA' as kind,a.code from standard_areas a join inputs i
                    on i.kind='AREA' and i.token in(lower(a.name),lower(a.code))
                union select distinct 'LANGUAGE',l.code from standard_languages l join inputs i
                    on i.kind='LANGUAGE' and i.token in(lower(l.name),lower(l.code),lower(l.english_name),lower(l.native_name))
                union select distinct 'GENRE',g.code from standard_genre g join inputs i
                    on i.kind='GENRE' and i.token in(lower(g.name),lower(g.code))
                """, p, rs -> { if (rs.getString(2) != null) result.get(rs.getString(1)).add(rs.getString(2)); });
        return result;
    }

    private UUID registerCover(String poster) {
        if (poster == null || poster.isBlank()) return null;
        URI uri;
        try { uri = URI.create(poster); } catch (IllegalArgumentException ex) { return null; }
        if (uri.getHost() == null || uri.getUserInfo() != null || !Set.of("https","http").contains(uri.getScheme())) return null;
        UUID domain = findOrCreateDomain(uri.getScheme() + "://" + uri.getHost());
        var p = new MapSqlParameterSource("id", IrcsUuidGenerators.nextId())
                .addValue("url", poster).addValue("domain", domain);
        var inserted = jdbc.queryForList("""
                insert into cover_images(id,created_at,updated_at,version,storage_type,original_url,source_domain_id,status,retry_count,next_retry_time)
                values(:id,now(),now(),0,'EXTERNAL',:url,:domain,'UNPROCESSED',0,now())
                on conflict(original_url,source_domain_id) do nothing returning id
                """, p, UUID.class);
        return inserted.isEmpty() ? jdbc.queryForObject("""
                select id from cover_images where original_url=:url and source_domain_id=:domain
                """, p, UUID.class) : inserted.getFirst();
    }

    private UUID findOrCreateDomain(String domain) {
        var p = new MapSqlParameterSource("hash", domainHash(domain)).addValue("domain", domain);
        String select = "select id from source_domains where domain_hash=:hash";
        var existing = jdbc.queryForList(select, p, UUID.class);
        if (!existing.isEmpty()) return existing.getFirst();
        var inserted = jdbc.queryForList("""
                insert into source_domains(id,created_at,updated_at,version,domain_hash,domain_value)
                values(:id,now(),now(),0,:hash,:domain)
                on conflict(domain_hash) do nothing returning id
                """, p.addValue("id", IrcsUuidGenerators.nextId()), UUID.class);
        return inserted.isEmpty() ? jdbc.queryForObject(select, p, UUID.class) : inserted.getFirst();
    }

    private String domainHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void scalar(JsonNode current, Map<String,Object> changes, String column, Object value, String... locks) {
        if (value != null && (!(value instanceof String s) || !s.isBlank()) && missing(current,column) && !locked(current,locks)) {
            changes.put(column,value);
        }
    }

    private void array(JsonNode current, Map<String,Object> changes, String column, Set<String> values, String... locks) {
        if (values != null && !values.isEmpty() && (missing(current,column) || current.path(column).isEmpty()) && !locked(current,locks)) {
            changes.put(column,json(values));
        }
    }

    private boolean missing(JsonNode current, String column) {
        JsonNode value=current.path(column);
        return value.isMissingNode() || value.isNull() || value.isTextual() && value.asText().isBlank();
    }

    private boolean locked(JsonNode current, String... fields) {
        JsonNode locks=current.path("locked_fields");
        for (String field: fields) {
            if (locks.isObject() && locks.has(field)) return true;
            if (locks.isArray()) for (JsonNode value: locks) if (field.equals(value.asText())) return true;
        }
        return false;
    }

    private String json(Object value) { return mapper.writeValueAsString(value); }
    private MapSqlParameterSource owned(CatalogMetadataTarget target) {
        return new MapSqlParameterSource("id",target.id()).addValue("token",target.leaseToken());
    }
    public record IndexPending(UUID id,long generation) { }
}
