package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
class CatalogReferenceSyncRepository {
    private static final String OWNED = """
            provider=:provider and feed=:feed and lease_token=:token and lease_until>now()
            """;
    private static final String IMDB_OWNER = """
            from catalog_external_ids identity join catalog_works work on work.id=identity.work_id
             where identity.provider='IMDb' and identity.target_kind='WORK' and identity.verified_at is not null
               and work.identity_status='VERIFIED' and work.media_type=identity.media_type
               and work.imdb_id=identity.external_id
               and identity.external_id=:externalId and work.id=:work
            """;
    private static final String CHANGED_FACTS = """
            cast(:facts as jsonb) is distinct from (
                select previous.facts from catalog_metadata_observations previous
                 where previous.work_id=:work and previous.season_id is null
                   and previous.provider=:provider and previous.external_id=:externalId
                 order by previous.observed_at desc,previous.id desc limit 1)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    Optional<Lease> claim(String provider, String feed, Duration leaseDuration) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                insert into catalog_reference_feed_states(provider,feed,lease_token,lease_until)
                values(:provider,:feed,:token,now()+(:seconds*interval '1 second'))
                on conflict (provider,feed) do update set lease_token=excluded.lease_token,lease_until=excluded.lease_until
                  where catalog_reference_feed_states.next_attempt_at<=now()
                    and (catalog_reference_feed_states.lease_until is null
                         or catalog_reference_feed_states.lease_until<=now())
                returning input_fingerprint,etag,last_modified
                """, Map.of("provider", provider, "feed", feed, "token", token, "seconds", leaseDuration.toSeconds()),
                (rs, row) -> new Lease(provider, feed, token, rs.getString(1), rs.getString(2), rs.getString(3)))
                .stream().findFirst();
    }

    boolean complete(Lease lease, Duration delay, String fingerprint, String etag, String lastModified) {
        return jdbc.update("""
                update catalog_reference_feed_states
                   set input_fingerprint=:fingerprint,etag=:etag,last_modified=:lastModified,
                       next_attempt_at=now()+(:seconds*interval '1 second'),last_success_at=now(),last_error=null,
                       lease_token=null,lease_until=null where
                """ + OWNED, leaseParameters(lease).addValue("seconds", delay.toSeconds())
                .addValue("fingerprint", fingerprint).addValue("etag", etag).addValue("lastModified", lastModified)) == 1;
    }

    void fail(Lease lease, Duration delay, String reason) {
        jdbc.update("""
                update catalog_reference_feed_states set next_attempt_at=now()+(:seconds*interval '1 second'),
                    last_error=:reason,lease_token=null,lease_until=null where
                """ + OWNED, leaseParameters(lease).addValue("seconds", delay.toSeconds()).addValue("reason", reason));
    }

    Map<String, ImdbTarget> imdbTargets() {
        var targets = new LinkedHashMap<String, ImdbTarget>();
        jdbc.query("""
                select external_id,work_id,media_type from (
                    select i.external_id,i.work_id,i.media_type,count(*) over(partition by i.external_id) as owners
                      from catalog_external_ids i join catalog_works w on w.id=i.work_id
                     where i.provider='IMDb' and i.target_kind='WORK' and i.verified_at is not null
                       and w.identity_status='VERIFIED' and w.media_type=i.media_type and w.imdb_id=i.external_id
                ) linked where owners=1 order by external_id
                """, Map.of(), rs -> {
            targets.put(rs.getString(1), new ImdbTarget(rs.getObject(2, UUID.class), rs.getString(3)));
        });
        return targets;
    }

    @Transactional(timeout = 20)
    public void storeImdbTitles(Lease lease, Map<String, ImdbTarget> targets, List<ImdbCatalogSnapshotReader.Title> titles) {
        requireLease(lease);
        var batch = new ArrayList<MapSqlParameterSource>(titles.size());
        for (var title : titles) {
            ImdbTarget target = targets.get(title.imdbId());
            if (target != null && target.mediaType().equals(title.mediaType())) {
                batch.add(observationParameters(lease, target.workId(), title.imdbId(), title));
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into catalog_metadata_observations(work_id,provider,external_id,facts)
                    select :work,:provider,:externalId,cast(:facts as jsonb) where exists (select 1
                    """ + IMDB_OWNER + ") and " + CHANGED_FACTS, batch.toArray(MapSqlParameterSource[]::new));
        }
    }

    @Transactional(timeout = 20)
    public void storeImdbRatings(Lease lease, Map<String, ImdbTarget> targets, List<ImdbCatalogSnapshotReader.Rating> ratings) {
        requireLease(lease);
        var batch = new ArrayList<MapSqlParameterSource>(ratings.size());
        for (var rating : ratings) {
            ImdbTarget target = targets.get(rating.imdbId());
            if (target != null) {
                batch.add(new MapSqlParameterSource("work", target.workId()).addValue("provider", lease.provider())
                        .addValue("externalId", rating.imdbId()).addValue("rating", rating.rating())
                        .addValue("votes", rating.votes()));
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into catalog_rating_observations(work_id,provider,rating,vote_count,observed_on)
                    select :work,:provider,:rating,:votes,(now() at time zone 'UTC')::date
                    where exists (select 1
                    """ + IMDB_OWNER + """
                    )
                    and not exists (
                        select 1 from (
                            select rating,vote_count from catalog_rating_observations
                             where work_id=:work and provider=:provider and season_id is null
                             order by observed_on desc limit 1
                        ) latest where latest.rating=:rating and latest.vote_count=:votes
                    )
                    on conflict (work_id,season_id,provider,observed_on) do update
                      set rating=excluded.rating,vote_count=excluded.vote_count
                    """, batch.toArray(MapSqlParameterSource[]::new));
        }
    }

    List<DoubanTarget> dueDoubanTargets(String provider, int limit) {
        return jdbc.query("""
                select work.id,work.media_type,work.title,imdb.external_id,douban.external_id
                  from catalog_works work
                  left join catalog_reference_work_refreshes refresh on refresh.work_id=work.id and refresh.provider=:provider
                  left join catalog_external_ids imdb on imdb.work_id=work.id and imdb.provider='IMDb'
                    and imdb.target_kind='WORK' and imdb.verified_at is not null and imdb.external_id=work.imdb_id
                    and imdb.media_type=work.media_type
                  left join lateral (
                    select min(id.external_id) as external_id from catalog_external_ids id
                     where id.work_id=work.id and id.provider=:provider and id.target_kind='WORK'
                       and id.media_type=work.media_type and id.verified_at is not null
                    having count(*)=1
                  ) douban on true
                 where work.identity_status='VERIFIED' and (refresh.next_attempt_at is null or refresh.next_attempt_at<=now())
                   and (imdb.external_id is not null or douban.external_id is not null)
                   and exists (select from catalog_entries entry where entry.work_id=work.id
                       and entry.content_visibility='PUBLIC' and not entry.adult_restricted)
                 order by refresh.next_attempt_at nulls first,work.id limit :limit
                """, Map.of("provider", provider, "limit", limit), (rs, row) -> new DoubanTarget(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    @Transactional(timeout = 10)
    public boolean storeDouban(Lease lease, DoubanTarget target, EnrichedMetadataDTO facts, Duration nextCheck) {
        requireLease(lease);
        var p = observationParameters(lease, target.workId(), facts.getDoubanId(), facts)
                .addValue("mediaType", target.mediaType()).addValue("imdbId", target.imdbId())
                .addValue("knownDoubanId", target.doubanId());
        int linked = jdbc.update("""
                insert into catalog_external_ids(provider,media_type,target_kind,external_id,work_id,verified_at)
                select :provider,:mediaType,'WORK',:externalId,work.id,now()
                  from catalog_works work where work.id=:work and work.media_type=:mediaType
                   and work.identity_status='VERIFIED'
                   and exists (select from catalog_external_ids proof where proof.work_id=work.id
                       and proof.media_type=work.media_type and proof.target_kind='WORK' and proof.verified_at is not null
                       and ((proof.provider=:provider and proof.external_id=:knownDoubanId)
                            or (proof.provider='IMDb' and proof.external_id=:imdbId and work.imdb_id=proof.external_id)))
                   and not exists (select from catalog_external_ids other where other.work_id=work.id
                       and other.provider=:provider and other.target_kind='WORK' and other.external_id<>:externalId
                       and other.verified_at is not null)
                on conflict (provider,media_type,target_kind,external_id) do update set verified_at=excluded.verified_at
                 where catalog_external_ids.work_id=excluded.work_id
                """, p);
        if (linked == 0) {
            recordDoubanResult(lease.provider(), target.workId(), nextCheck, "IDENTITY_CONFLICT");
            return false;
        }
        jdbc.update("""
                insert into catalog_metadata_observations(work_id,provider,external_id,facts)
                select :work,:provider,:externalId,cast(:facts as jsonb) where
                """ + CHANGED_FACTS, p);
        if (facts.getScore() != null) {
            jdbc.update("""
                    insert into catalog_rating_observations(work_id,provider,rating,observed_on)
                    select :work,:provider,:rating,(now() at time zone 'UTC')::date
                    where cast(:rating as numeric) between 0 and 10 and cast(:rating as numeric) is distinct from (
                        select rating from catalog_rating_observations where work_id=:work and provider=:provider
                         and season_id is null order by observed_on desc limit 1
                    )
                    on conflict (work_id,season_id,provider,observed_on) do update set rating=excluded.rating
                    """, p.addValue("rating", facts.getScore()));
        }
        recordDoubanResult(lease.provider(), target.workId(), nextCheck, "MATCHED");
        return true;
    }

    @Transactional(timeout = 10)
    public void deferDoubanTarget(Lease lease, DoubanTarget target, Duration delay, String result) {
        requireLease(lease);
        recordDoubanResult(lease.provider(), target.workId(), delay, result);
    }

    private void recordDoubanResult(String provider, UUID workId, Duration delay, String result) {
        jdbc.update("""
                insert into catalog_reference_work_refreshes(provider,work_id,next_attempt_at,last_result)
                select :provider,id,now()+(:seconds*interval '1 second'),:result from catalog_works where id=:workId
                on conflict (provider,work_id) do update set next_attempt_at=excluded.next_attempt_at,
                    last_checked_at=excluded.last_checked_at,last_result=excluded.last_result
                """, Map.of("provider", provider, "workId", workId, "seconds", delay.toSeconds(), "result", result));
    }

    private void requireLease(Lease lease) {
        if (jdbc.query("""
                select 1 from catalog_reference_feed_states where
                """ + OWNED + " for update", leaseParameters(lease), (rs, row) -> rs.getInt(1)).isEmpty()) {
            throw new IllegalStateException("REFERENCE_SYNC_LEASE_LOST");
        }
    }

    private MapSqlParameterSource observationParameters(Lease lease, UUID workId, String externalId, Object facts) {
        return new MapSqlParameterSource("provider", lease.provider()).addValue("work", workId)
                .addValue("externalId", externalId).addValue("facts", mapper.writeValueAsString(facts));
    }

    private MapSqlParameterSource leaseParameters(Lease lease) {
        return new MapSqlParameterSource("provider", lease.provider()).addValue("feed", lease.feed())
                .addValue("token", lease.token());
    }

    record Lease(String provider, String feed, UUID token, String fingerprint, String etag, String lastModified) { }
    record ImdbTarget(UUID workId, String mediaType) { }
    record DoubanTarget(UUID workId, String mediaType, String title, String imdbId, String doubanId) { }
}
