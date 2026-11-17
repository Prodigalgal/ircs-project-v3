package com.prodigalgal.ircs.content.video.infrastructure;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.content.video.domain.VideoAdminText;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.math.BigDecimal;
import java.sql.Date;
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
                select unified_video_id from external_catalog_identities
                 where provider = :provider and media_type = :type and external_id = :externalId and season = :season
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
        List<UUID> byId = jdbc.query("select uv.id from unified_videos uv where " + column + " = :externalId and "
                + typeFilter + " and (:season = 0 or uv.season = :season or (:season = 1 and uv.season is null)) limit 2",
                p, (rs, n) -> rs.getObject(1, UUID.class));
        if (byId.size() == 1) return Optional.of(byId.getFirst());
        if (!byId.isEmpty()) return Optional.empty();
        String year = candidate.item().year();
        if (year == null || !year.matches("(?:18|19|20|21)[0-9]{2}")) return Optional.empty();
        // Exact case-insensitive title + year + type + compatible season, with no contradictory source ID.
        List<ExistingTitle> titles = jdbc.query("""
                select uv.id, uv.title, uv.alias_title
                  from unified_videos uv
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
                insert into unified_videos (id, created_at, updated_at, version, title, alias_title, season,
                    cover_image_id, description, year, score, published_at, douban_id, tmdb_id, imdb_id,
                    rotten_tomatoes_id, locked_fields, category_code, content_visibility, metadata_status)
                values (:id, now(), now(), 0, :title, :originalTitle, nullif(:season, 0), :cover, :description,
                    :year, :score, :publishedAt, :doubanId, :tmdbId, :imdbId, :rtId, '[]'::jsonb,
                    :category, 'PUBLIC', 'DIRTY')
                """, p);
        return id;
    }

    public void bind(TrendCatalogCandidate candidate, UUID id) {
        jdbc.update("""
                insert into external_catalog_identities(provider, media_type, external_id, season, unified_video_id)
                values (:provider, :type, :externalId, :season, :id)
                on conflict (provider, media_type, external_id, season) do nothing
                """, params(candidate).addValue("id", id));
    }

    public void requestMissingMetadata(TrendCatalogCandidate candidate, UUID id) {
        jdbc.update("""
                insert into catalog_metadata_requests(unified_video_id, provider, media_type, external_id, season)
                select uv.id, :provider, :type, :externalId, coalesce(nullif(:season,0),uv.season,0) from unified_videos uv
                 where uv.id = :id and (uv.metadata_status = 'DIRTY' or uv.score is null
                    or nullif(btrim(uv.description), '') is null or uv.cover_image_id is null
                    or coalesce(uv.actor_names, '[]'::jsonb) = '[]'::jsonb
                    or coalesce(uv.director_names, '[]'::jsonb) = '[]'::jsonb
                    or coalesce(uv.genre_codes, '[]'::jsonb) = '[]'::jsonb)
                on conflict (unified_video_id) do update set provider = excluded.provider,
                    media_type = excluded.media_type, external_id = excluded.external_id, season = excluded.season,
                    status = 'PENDING', attempts = 0, next_attempt_at = now(), lease_token = null,
                    lease_until = null, updated_at = now()
                 where catalog_metadata_requests.status <> 'DISPATCHING'
                   and ((catalog_metadata_requests.provider <> 'TMDB' and excluded.provider = 'TMDB')
                        or (catalog_metadata_requests.status = 'SUCCESS'
                            and catalog_metadata_requests.last_success_at < now() - interval '1 day'))
                """, params(candidate).addValue("id", id));
    }

    public boolean requestMissingResources(TrendCatalogCandidate candidate, UUID id) {
        MapSqlParameterSource p = params(candidate).addValue("id", id).addValue("episode", candidate.episode())
                .addValue("priority", discoveryPriority(candidate));
        // A binding alone is not playable. Unknown episode numbering remains an explicit resource gap.
        return jdbc.update("""
                insert into catalog_discovery_requests(unified_video_id, target_season, target_episode, keyword, priority)
                select :id, :season, :episode, :title, :priority
                 where not exists (
                    select 1 from catalog_resource_inventory(:id, :season) inventory
                     where inventory.resource_available
                       and (:episode = 0 or inventory.available_episode >= :episode)
                 )
                on conflict (unified_video_id, target_season, target_episode) do update
                    set priority = greatest(catalog_discovery_requests.priority, excluded.priority),
                        status = case when catalog_discovery_requests.status = 'SUBMITTED'
                            and catalog_discovery_requests.submitted_at < now() - interval '6 hours'
                            then 'PENDING' else catalog_discovery_requests.status end,
                        next_attempt_at = case when catalog_discovery_requests.status = 'SUBMITTED'
                            and catalog_discovery_requests.submitted_at < now() - interval '6 hours'
                            then now() else catalog_discovery_requests.next_attempt_at end,
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
}
