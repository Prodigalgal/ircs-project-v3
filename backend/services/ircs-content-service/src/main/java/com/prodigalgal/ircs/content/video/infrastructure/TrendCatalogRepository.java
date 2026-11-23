package com.prodigalgal.ircs.content.video.infrastructure;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.content.video.domain.VideoAdminText;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TrendCatalogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final VideoAdminCoverImageRepository covers;

    public Optional<AuthorityProjection> findAuthorityProjection(TrendCatalogCandidate candidate) {
        String imdb = "IMDb".equals(candidate.provider()) ? candidate.externalId() : null;
        Long tmdb = tmdbId(candidate);
        if (tmdb == null && imdb == null) return Optional.empty();
        MapSqlParameterSource p = new MapSqlParameterSource("type", candidate.mediaType())
                .addValue("tmdb", tmdb, Types.BIGINT).addValue("imdb", imdb, Types.VARCHAR)
                .addValue("requestedSeason", authoritySeason(candidate)).addValue("episode", candidate.episode());
        return jdbc.query("""
                with matches as (
                    select id,title from catalog_works
                    where identity_status='VERIFIED' and media_type=:type and (tmdb_id=:tmdb or imdb_id=:imdb) limit 2
                )
                select uv.id,coalesce(scope.season_number,-1),w.title
                from matches w join catalog_entries uv on uv.work_id=w.id
                left join catalog_seasons scope on scope.id=uv.season_id
                where (select count(*) from matches)=1
                  and (coalesce(scope.season_number,-1)=:requestedSeason
                       or :requestedSeason=-1 and scope.season_number<>0)
                  and (:episode=0 or exists(select 1 from catalog_episodes ep
                     where ep.work_id=w.id and ep.season_id is not distinct from uv.season_id and ep.episode_number=:episode))
                  and uv.content_visibility='PUBLIC' and not coalesce(uv.adult_restricted,false)
                order by scope.season_number nulls first limit 1
                """, p, (rs, row) -> new AuthorityProjection(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3)))
                .stream().findFirst();
    }

    public void requestAuthorityImport(TrendCatalogCandidate candidate) {
        Long tmdb = tmdbId(candidate);
        if (tmdb == null) return;
        Integer position = candidate.item().signal() == null ? null : candidate.item().signal().position();
        int priority = candidate.episode() > 0 ? 2000 : 1000 - Math.min(900, position == null ? 100 : position);
        jdbc.update("""
                insert into catalog_authority_import_jobs(media_type,tmdb_id,season_number,priority)
                select :type,:tmdb,:season,:priority
                 where not exists(select 1 from catalog_authority_import_jobs
                      where media_type=:type and tmdb_id=:tmdb and last_error='ADULT_SOURCE_RECORD')
                on conflict (media_type,tmdb_id,season_number) do update
                    set priority=greatest(catalog_authority_import_jobs.priority,excluded.priority),
                        status=case when catalog_authority_import_jobs.status in ('SUCCESS','UNAVAILABLE')
                                    and catalog_authority_import_jobs.next_attempt_at<=now()
                                    and (catalog_authority_import_jobs.last_success_at is null
                                         or catalog_authority_import_jobs.last_success_at<now()-interval '12 hours')
                               then 'PENDING' else catalog_authority_import_jobs.status end,
                        updated_at=now()
                    where catalog_authority_import_jobs.priority<excluded.priority
                       or catalog_authority_import_jobs.status in ('SUCCESS','UNAVAILABLE')
                          and catalog_authority_import_jobs.next_attempt_at<=now()
                          and (catalog_authority_import_jobs.last_success_at is null
                               or catalog_authority_import_jobs.last_success_at<now()-interval '12 hours')
                """, new MapSqlParameterSource("type", candidate.mediaType()).addValue("tmdb", tmdb)
                .addValue("season", authoritySeason(candidate)).addValue("priority", priority));
    }

    public void requestAuthorityResources(TrendCatalogCandidate candidate, AuthorityProjection projection) {
        // A true season zero is distinct from the legacy discovery query's unspecified season.
        if (projection.seasonNumber() == 0) return;
        MapSqlParameterSource p = new MapSqlParameterSource("id", projection.id())
                .addValue("season", Math.max(0, projection.seasonNumber())).addValue("episode", candidate.episode())
                .addValue("title", VideoAdminText.trimToLength(projection.title(), 255))
                .addValue("priority", discoveryPriority(candidate).add(BigDecimal.TEN));
        requestMissingResources(p);
    }

    private Integer authoritySeason(TrendCatalogCandidate candidate) {
        var signal = candidate.item().signal();
        return "movie".equals(candidate.mediaType()) || signal == null || signal.season() == null
                ? -1 : signal.season();
    }

    private Long tmdbId(TrendCatalogCandidate candidate) {
        if (!"TMDB".equals(candidate.provider())) return null;
        try {
            return Long.valueOf(candidate.externalId());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void lockIdentity(TrendCatalogCandidate candidate) {
        // Serialize cross-provider exact-title candidates as well as repeated source IDs.
        List<String> keys = List.of("catalog:id:" + candidate.identityKey(),
                "catalog:title:" + candidate.mediaType() + ":" + candidate.titleKey()).stream().sorted().toList();
        for (String key : keys) {
            jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key, 0))",
                    new MapSqlParameterSource("key", key), rs -> { });
        }
    }

    public Optional<UUID> findIdentity(TrendCatalogCandidate candidate) {
        return jdbc.query("""
                select entry.id from catalog_external_ids identity
                join catalog_entries entry on entry.work_id=identity.work_id
                left join catalog_seasons scope on scope.id=entry.season_id
                where identity.provider=:provider and identity.media_type=:type and identity.external_id=:externalId
                  and identity.target_kind='WORK'
                  and (:season=0 or scope.season_number=:season or :season=1 and scope.id is null)
                order by scope.season_number nulls first, entry.id limit 1
                """, params(candidate), (rs, n) -> rs.getObject(1, UUID.class)).stream().findFirst();
    }

    public Optional<UUID> findExisting(TrendCatalogCandidate candidate) {
        String column = switch (candidate.provider()) {
            case "TMDB" -> "tmdb_id";
            case "Douban" -> "douban_id";
            case "IMDb" -> "imdb_id";
            case "RottenTomatoes" -> "rotten_tomatoes_id";
            default -> throw new IllegalArgumentException("INVALID_TREND_PROVIDER");
        };
        MapSqlParameterSource p = params(candidate);
        String typeFilter = "movie".equals(candidate.mediaType())
                ? "uv.category_code = 'movie'"
                : "uv.category_code in ('series','short-drama','anime','variety','documentary','sports','news','education','music')";
        List<UUID> byId = jdbc.query("select uv.id from catalog_entries uv where " + column + " = :externalId and "
                + typeFilter + " and (:season = 0 or uv.season = :season or (:season = 1 and uv.season is null)) limit 2",
                p, (rs, n) -> rs.getObject(1, UUID.class));
        if (byId.size() == 1) return Optional.of(byId.getFirst());
        if (!byId.isEmpty()) return Optional.empty();
        String year = candidate.item().year();
        if (year == null || !year.matches("(?:18|19|20|21)[0-9]{2}")) return Optional.empty();
        // Exact case-insensitive title + year + type + compatible season, with no contradictory source ID.
        List<ExistingTitle> titles = jdbc.query("""
                select uv.id, uv.title, uv.alias_title
                  from catalog_entries uv
                 where uv.year = :year and %s
                   and (nullif(btrim(uv.%s), '') is null or uv.%s = :externalId)
                   and (:season = 0 or uv.season = :season or (:season = 1 and uv.season is null))
                   and (lower(uv.title) = lower(:title) or lower(uv.alias_title) = lower(:title)
                        or (:originalTitle is not null and (lower(uv.title) = lower(:originalTitle)
                                                         or lower(uv.alias_title) = lower(:originalTitle))))
                 limit 3
                """.formatted(typeFilter, column, column), p, (rs, n) -> new ExistingTitle(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
        List<UUID> exact = titles.stream().filter(title -> candidate.titleKey().equals(TrendCatalogCandidate.titleKey(title.title()))
                || candidate.titleKey().equals(TrendCatalogCandidate.titleKey(title.aliasTitle()))
                || (candidate.item().originalTitle() != null
                    && TrendCatalogCandidate.titleKey(candidate.item().originalTitle()).equals(TrendCatalogCandidate.titleKey(title.title()))))
                .map(ExistingTitle::id).distinct().toList();
        return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
    }

    public UUID create(TrendCatalogCandidate candidate) {
        var item = candidate.item();
        UUID id = IrcsUuidGenerators.nextId();
        UUID cover = covers.getOrCreateCoverImage(item.posterUrl()).orElse(null);
        BigDecimal score = item.score() != null && item.score().signum() >= 0
                && item.score().compareTo(BigDecimal.TEN) <= 0 ? item.score() : null;
        MapSqlParameterSource p = params(candidate).addValue("id", id).addValue("cover", cover)
                .addValue("description", VideoAdminText.cleanText(item.description(), 5000))
                .addValue("score", score)
                .addValue("publishedAt", item.publishedAt() == null ? null : Date.valueOf(item.publishedAt()))
                .addValue("doubanId", "Douban".equals(candidate.provider()) ? candidate.externalId() : null)
                .addValue("tmdbId", "TMDB".equals(candidate.provider()) ? candidate.externalId() : null)
                .addValue("imdbId", "IMDb".equals(candidate.provider()) ? candidate.externalId() : null)
                .addValue("rtId", "RottenTomatoes".equals(candidate.provider())
                        ? AuthoritativeIds.normalizeRottenTomatoes(candidate.externalId().replaceFirst("^(m|tv)/", "").split("/")[0]) : null)
                .addValue("category", candidate.categoryCode());
        jdbc.update("""
                with provisional_work as (
                    insert into catalog_works(id,media_type,title,original_title,title_key,original_title_key,release_year,
                        released_on,identity_status)
                    values(uuidv7(),:type,:title,:originalTitle,:titleKey,lower(:originalTitle),cast(:year as smallint),
                        :publishedAt,'PROVISIONAL') returning id
                )
                insert into catalog_entries (id, work_id, created_at, updated_at, version, title, alias_title, season,
                    media_image_source_id, description, year, score, published_at, douban_id, tmdb_id, imdb_id,
                    rotten_tomatoes_id, locked_fields, category_code, content_visibility, metadata_status)
                select :id, provisional_work.id, now(), now(), 0, :title, :originalTitle, nullif(:season, 0), :cover, :description,
                    :year, :score, :publishedAt, :doubanId, :tmdbId, :imdbId, :rtId, '[]'::jsonb,
                    :category, 'PUBLIC', 'DIRTY' from provisional_work
                """, p.addValue("titleKey", candidate.titleKey()));
        return id;
    }

    public void bind(TrendCatalogCandidate candidate, UUID id) {
        int bound = jdbc.update("""
                insert into catalog_external_ids(provider, media_type, external_id, target_kind, work_id)
                select :provider,:type,:externalId,'WORK',work_id from catalog_entries where id=:id
                on conflict (provider,media_type,target_kind,external_id) do update set observed_at=now()
                where catalog_external_ids.work_id=excluded.work_id
                """, params(candidate).addValue("id", id));
        if (bound != 1) throw new IllegalStateException("Provider identity is already assigned to another work");
    }

    public void requestMissingMetadata(TrendCatalogCandidate candidate, UUID id) {
        jdbc.update("""
                insert into catalog_metadata_enrichment_jobs(catalog_entry_id, provider, media_type, external_id, season)
                select uv.id, :provider, :type, :externalId, coalesce(nullif(:season,0),uv.season,0) from catalog_entries uv
                 where uv.id = :id and (uv.metadata_status = 'DIRTY' or uv.score is null
                    or nullif(btrim(uv.description), '') is null or uv.media_image_source_id is null
                    or coalesce(uv.actor_names, '[]'::jsonb) = '[]'::jsonb
                    or coalesce(uv.director_names, '[]'::jsonb) = '[]'::jsonb
                    or coalesce(uv.genre_codes, '[]'::jsonb) = '[]'::jsonb)
                on conflict (catalog_entry_id) do update set provider = excluded.provider,
                    media_type = excluded.media_type, external_id = excluded.external_id, season = excluded.season,
                    status = 'PENDING', attempts = 0, next_attempt_at = now(), lease_token = null,
                    lease_until = null, updated_at = now()
                 where catalog_metadata_enrichment_jobs.status <> 'DISPATCHING'
                   and ((catalog_metadata_enrichment_jobs.provider <> 'TMDB' and excluded.provider = 'TMDB')
                        or (catalog_metadata_enrichment_jobs.status = 'SUCCESS'
                            and catalog_metadata_enrichment_jobs.last_success_at < now() - interval '1 day'))
                """, params(candidate).addValue("id", id));
    }

    public boolean requestMissingResources(TrendCatalogCandidate candidate, UUID id) {
        MapSqlParameterSource p = params(candidate).addValue("id", id).addValue("episode", candidate.episode())
                .addValue("priority", discoveryPriority(candidate));
        return requestMissingResources(p);
    }

    private boolean requestMissingResources(MapSqlParameterSource p) {
        // A binding alone is not playable. Episode targets require an exact parsed episode match.
        return jdbc.update("""
                insert into catalog_resource_discovery_jobs(catalog_entry_id, target_season, target_episode, keyword, priority)
                select :id, :season, :episode, :title, :priority
                 where not catalog_has_target_resources(:id, :season, :episode)
                on conflict (catalog_entry_id, target_season, target_episode) do update
                    set priority = greatest(catalog_resource_discovery_jobs.priority, excluded.priority),
                        status = case when catalog_resource_discovery_jobs.status = 'SUBMITTED'
                            and catalog_resource_discovery_jobs.submitted_at < now() - interval '6 hours'
                            then 'PENDING' else catalog_resource_discovery_jobs.status end,
                        next_attempt_at = case when catalog_resource_discovery_jobs.status = 'SUBMITTED'
                            and catalog_resource_discovery_jobs.submitted_at < now() - interval '6 hours'
                            then now() else catalog_resource_discovery_jobs.next_attempt_at end,
                        updated_at = now()
                """, p) > 0;
    }

    private BigDecimal discoveryPriority(TrendCatalogCandidate candidate) {
        var signal = candidate.item().signal();
        BigDecimal heat = signal == null || signal.position() == null ? BigDecimal.ZERO
                : BigDecimal.ONE.divide(BigDecimal.valueOf(20L + signal.position()), 8, java.math.RoundingMode.HALF_UP);
        if (candidate.episode() > 0) heat = heat.add(new BigDecimal("0.10"));
        if (signal != null && signal.eventDate() != null
                && !signal.eventDate().isBefore((signal.observedAt() == null ? java.time.Instant.now() : signal.observedAt())
                        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().minusDays(7))) {
            heat = heat.add(new BigDecimal("0.05"));
        }
        return heat;
    }

    private MapSqlParameterSource params(TrendCatalogCandidate candidate) {
        return new MapSqlParameterSource("provider", candidate.provider()).addValue("type", candidate.mediaType())
                .addValue("externalId", candidate.externalId()).addValue("season", candidate.season())
                .addValue("title", VideoAdminText.trimToLength(candidate.item().title(), 255))
                .addValue("originalTitle", VideoAdminText.cleanText(candidate.item().originalTitle(), 255), java.sql.Types.VARCHAR)
                .addValue("year", candidate.item().year() != null && candidate.item().year().matches("(?:18|19|20|21)[0-9]{2}")
                        ? candidate.item().year() : null);
    }

    private record ExistingTitle(UUID id, String title, String aliasTitle) { }
    public record AuthorityProjection(UUID id, int seasonNumber, String title) { }
}
