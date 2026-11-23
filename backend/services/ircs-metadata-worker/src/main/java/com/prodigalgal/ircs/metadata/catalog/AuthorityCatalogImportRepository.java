package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class AuthorityCatalogImportRepository {

    private static final String OWNED = """
            media_type=:type and tmdb_id=:tmdb and season_number=:requested
            and status='DISPATCHING' and lease_token=:token and lease_until>now()
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogMetadataRepository projectionRepository;
    private final CatalogPeopleRepository peopleRepository;

    Optional<Import> claim() {
        return jdbc.query("""
                with due as (
                    select media_type,tmdb_id,season_number,
                        (status in ('SUCCESS','DISPATCHING') and credits_backfill_at is not null) as backfill
                    from catalog_authority_import_jobs
                     where (status in ('PENDING','RETRY') and next_attempt_at<=now())
                        or (status='DISPATCHING' and lease_until<=now())
                        or (status='SUCCESS' and credits_backfill_at<=now())
                     order by backfill,priority desc,
                        case when status='SUCCESS' then credits_backfill_at else next_attempt_at end,
                        created_at,media_type,tmdb_id,season_number
                     limit 1 for update skip locked
                )
                update catalog_authority_import_jobs j set status='DISPATCHING',lease_token=:token,
                       credits_backfill_at=case when due.backfill then j.credits_backfill_at end,
                       lease_until=now()+interval '5 minutes',attempts=least(attempts+1,1000000),updated_at=now()
                  from due where (j.media_type,j.tmdb_id,j.season_number)=(due.media_type,due.tmdb_id,due.season_number)
                returning j.media_type,j.tmdb_id,j.season_number,j.priority,j.attempts,j.lease_token,due.backfill
                """, Map.of("token", UUID.randomUUID()),
                (rs, row) -> new Import(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getObject(6, UUID.class), rs.getBoolean(7))).stream().findFirst();
    }

    @Transactional(timeout = 20)
    public boolean publish(Import job, AuthorityCatalogEvidence evidence) {
        if (evidence.tmdbId() != job.tmdbId() || !job.mediaType().equals(evidence.mediaType())
                || job.seasonNumber() >= 0 && job.seasonNumber() != evidence.seasonNumber()) {
            throw new CatalogMetadataUnavailableException("CATALOG_IDENTITY_MISMATCH");
        }
        var facts = evidence.metadata();
        String title = titleKey(facts.getTitle());
        if (title.isBlank()) throw new CatalogMetadataUnavailableException("CATALOG_TITLE_UNVERIFIED");
        MapSqlParameterSource p = params(job).addValue("id", IrcsUuidGenerators.nextId())
                .addValue("title", facts.getTitle()).addValue("titleKey", title)
                .addValue("original", facts.getOriginalTitle()).addValue("originalKey", titleKey(facts.getOriginalTitle()))
                .addValue("year", evidence.releasedOn() == null ? null : evidence.releasedOn().getYear())
                .addValue("released", evidence.releasedOn()).addValue("imdb", facts.getImdbId())
                .addValue("season", evidence.seasonNumber())
                .addValue("seasonCount", evidence.seasons().stream().filter(season -> season.number() > 0).count());
        List<Integer> owned = jdbc.query("select priority from catalog_authority_import_jobs where " + OWNED + " for update",
                p, (rs, row) -> rs.getInt(1));
        if (owned.isEmpty()) return false;

        // Different season jobs may finish together. Use one shared transaction lock per work.
        lockWork(job);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from catalog_authority_import_jobs
                    where media_type=:type and tmdb_id=:tmdb and last_error='ADULT_SOURCE_RECORD')
                """, p, Boolean.class))) {
            throw new CatalogMetadataUnavailableException("ADULT_SOURCE_RECORD");
        }
        Optional<PreviousWork> previous = jdbc.query("""
                select title,original_title from catalog_works where media_type=:type and tmdb_id=:tmdb
                """, p, (rs, row) -> new PreviousWork(rs.getString(1), rs.getString(2))).stream().findFirst();
        UUID work = jdbc.queryForObject("""
                with promote as (
                    update catalog_works candidate set identity_status='VERIFIED', tmdb_id=:tmdb,
                        title=left(:title,500), original_title=left(:original,500), title_key=left(:titleKey,500),
                        original_title_key=nullif(left(:originalKey,500),''),
                        release_year=coalesce(:year,candidate.release_year), released_on=coalesce(:released,candidate.released_on),
                        imdb_id=coalesce(:imdb,candidate.imdb_id), regular_season_count=:seasonCount,last_verified_at=now()
                     from catalog_external_ids identity
                    where identity.work_id=candidate.id and identity.provider='TMDB'
                      and identity.media_type=:type and identity.target_kind='WORK'
                      and identity.external_id=cast(:tmdb as text) and candidate.identity_status='PROVISIONAL'
                      and candidate.media_type=:type
                      and not exists(select from catalog_works existing where existing.media_type=:type and existing.tmdb_id=:tmdb)
                    returning candidate.id
                ), imported as (
                insert into catalog_works(id,media_type,tmdb_id,title,original_title,title_key,original_title_key,
                    release_year,released_on,imdb_id,regular_season_count,last_verified_at)
                select :id,:type,:tmdb,left(:title,500),left(:original,500),left(:titleKey,500),nullif(left(:originalKey,500),''),
                    :year,:released,:imdb,:seasonCount,now()
                where not exists(select from promote)
                on conflict (media_type,tmdb_id) do update set title=excluded.title,original_title=excluded.original_title,
                    title_key=excluded.title_key,original_title_key=excluded.original_title_key,
                    release_year=coalesce(excluded.release_year,catalog_works.release_year),
                    released_on=coalesce(excluded.released_on,catalog_works.released_on),
                    imdb_id=coalesce(excluded.imdb_id,catalog_works.imdb_id),
                    regular_season_count=excluded.regular_season_count,last_verified_at=now()
                returning id
                )
                select id from promote union all select id from imported
                """, p, UUID.class);
        replaceSeasonFacts(work, evidence);
        peopleRepository.store(work, evidence.creditSets());
        p.addValue("work", work).addValue("priority", owned.getFirst());
        UUID video = ensureProjection(p, evidence, previous);
        p.addValue("video", video);
        storeAuthorityIdentities(p);
        projectionRepository.recordMetadataObservation(video, "TMDB", Long.toString(evidence.tmdbId()), facts);
        scheduleResources(p);
        jdbc.update("""
                update catalog_authority_import_jobs set status='SUCCESS',lease_token=null,lease_until=null,
                    attempts=0,last_success_at=now(),last_error=null,credits_backfill_at=null,updated_at=now() where
                """ + OWNED, p);
        return true;
    }

    private void replaceSeasonFacts(UUID work, AuthorityCatalogEvidence evidence) {
        if (evidence.seasons().isEmpty()) return;
        for (var season : evidence.seasons()) {
            int changed = jdbc.update("""
                    insert into catalog_seasons(work_id,season_number,tmdb_season_id,title,released_on,verified_at)
                    values (:work,:season,:id,left(:title,500),:released,case when :verified then now() else null end)
                    on conflict (work_id,season_number) do update
                        set tmdb_season_id=coalesce(catalog_seasons.tmdb_season_id,excluded.tmdb_season_id),
                            title=excluded.title,released_on=excluded.released_on,
                            verified_at=coalesce(excluded.verified_at,catalog_seasons.verified_at)
                        where catalog_seasons.tmdb_season_id is null or catalog_seasons.tmdb_season_id=excluded.tmdb_season_id
                    """, new MapSqlParameterSource("work", work).addValue("season", season.number())
                    .addValue("id", season.tmdbId()).addValue("title", season.title()).addValue("released", season.releasedOn())
                    .addValue("verified", season.number() == evidence.seasonNumber()));
            if (changed != 1) throw new CatalogMetadataUnavailableException("CATALOG_SEASON_IDENTITY_CHANGED");
        }
        // Provider omissions leave numbered identities intact, including IDs held by member progress.
        if (evidence.episodes().isEmpty()) return;
        var batch = evidence.episodes().stream().map(episode -> new MapSqlParameterSource("work", work)
                .addValue("season", evidence.seasonNumber()).addValue("episode", episode.number())
                .addValue("id", episode.tmdbId()).addValue("title", episode.title()).addValue("aired", episode.airedOn()))
                .toArray(MapSqlParameterSource[]::new);
        int[] changed = jdbc.batchUpdate("""
                insert into catalog_episodes(work_id,season_number,episode_number,tmdb_episode_id,title,aired_on)
                values(:work,:season,:episode,:id,left(:title,500),:aired)
                on conflict (work_id,season_number,episode_number) do update
                    set title=excluded.title,aired_on=excluded.aired_on,
                        tmdb_episode_id=coalesce(catalog_episodes.tmdb_episode_id,excluded.tmdb_episode_id)
                    where catalog_episodes.tmdb_episode_id is null or catalog_episodes.tmdb_episode_id=excluded.tmdb_episode_id
                """, batch);
        for (int count : changed) {
            if (count == 0) throw new CatalogMetadataUnavailableException("CATALOG_EPISODE_IDENTITY_CHANGED");
        }
    }

    private UUID ensureProjection(MapSqlParameterSource p, AuthorityCatalogEvidence evidence,
            Optional<PreviousWork> previous) {
        var existing = jdbc.query("""
                select entry.id from catalog_entries entry
                left join catalog_seasons scope on scope.id=entry.season_id
                where entry.work_id=:work and coalesce(scope.season_number,-1)=:season
                """, p, (rs, row) -> rs.getObject(1, UUID.class)).stream().findFirst();
        if (existing.isPresent()) {
            previous.ifPresent(facts -> projectionRepository.refreshAuthorityProjection(existing.get(), evidence,
                    facts.title(), facts.originalTitle()));
            return existing.get();
        }
        return projectionRepository.createAuthorityProjection(evidence, (UUID) p.getValue("work"));
    }

    private void storeAuthorityIdentities(MapSqlParameterSource p) {
        int work = jdbc.update("""
                insert into catalog_external_ids(provider,media_type,external_id,target_kind,work_id,verified_at)
                values('TMDB',:type,cast(:tmdb as varchar),'WORK',:work,now())
                on conflict (provider,media_type,target_kind,external_id) do update set verified_at=now()
                where catalog_external_ids.work_id=excluded.work_id
                """, p);
        if (work != 1) throw new CatalogMetadataUnavailableException("CATALOG_IDENTITY_CHANGED");
        jdbc.update("""
                insert into catalog_external_ids(provider,media_type,external_id,target_kind,work_id,season_id,episode_id,verified_at)
                select 'TMDB',:type,cast(s.tmdb_season_id as varchar),'SEASON',s.work_id,s.id,null,now()
                from catalog_seasons s where s.work_id=:work and s.tmdb_season_id is not null
                union all
                select 'TMDB',:type,cast(ep.tmdb_episode_id as varchar),'EPISODE',ep.work_id,null,ep.id,now()
                from catalog_episodes ep where ep.work_id=:work and ep.season_number=:season and ep.tmdb_episode_id is not null
                on conflict (provider,media_type,target_kind,external_id) do nothing
                """, p);
        jdbc.update("""
                insert into catalog_external_ids(provider,media_type,external_id,target_kind,work_id,verified_at)
                select 'IMDb',:type,imdb_id,'WORK',id,now() from catalog_works where id=:work and imdb_id is not null
                on conflict (provider,media_type,target_kind,external_id) do update set verified_at=now()
                where catalog_external_ids.work_id=excluded.work_id
                """, p);
    }

    private void scheduleResources(MapSqlParameterSource p) {
        jdbc.update("""
                insert into catalog_resource_discovery_jobs(catalog_entry_id,target_season,target_episode,keyword,priority)
                select :video,greatest(:season,0),0,left(:title,255),:priority/1000.0
                 where :priority>=50 and :season<>0
                   and exists(select 1 from catalog_entries where id=:video and content_visibility='PUBLIC'
                       and not coalesce(adult_restricted,false))
                   and not catalog_has_target_resources(:video,greatest(:season,0),0)
                on conflict (catalog_entry_id,target_season,target_episode) do nothing
                """, p);
    }

    @Transactional(timeout = 15)
    public void defer(Import job, String reason, boolean unavailable) {
        boolean adult = "ADULT_SOURCE_RECORD".equals(reason);
        boolean keepPublished = job.creditBackfill() && !adult;
        MapSqlParameterSource p = params(job).addValue("reason", reason).addValue("keepPublished", keepPublished)
                .addValue("status", keepPublished ? "SUCCESS" : unavailable ? "UNAVAILABLE" : "RETRY")
                .addValue("seconds", unavailable ? 86400 : Math.min(21600, 60L << Math.min(9, job.attempts() - 1)));
        int retained = jdbc.update("""
                update catalog_authority_import_jobs set status=:status,lease_token=null,lease_until=null,
                    credits_backfill_at=case when :keepPublished then now()+(:seconds*interval '1 second') end,
                    next_attempt_at=now()+(:seconds*interval '1 second'),last_error=:reason,updated_at=now() where
                """ + OWNED, p);
        if (retained == 1 && adult) {
            lockWork(job);
            jdbc.update("""
                    update catalog_entries set content_visibility='HIDDEN',adult_restricted=true,updated_at=now()
                    where work_id in(select work.id from catalog_works work
                        where work.media_type=:type and work.tmdb_id=:tmdb)
                    """, p);
        }
    }

    int seed(String type, int cap) {
        return jdbc.update("""
                insert into catalog_authority_import_jobs(media_type,tmdb_id,season_number,priority)
                select s.media_type,selected.tmdb_id,-1,
                    greatest(50,99-((row_number() over(order by selected.popularity desc,selected.tmdb_id)-1)/10)::integer)
                  from provider_catalog_snapshot_states s
                  cross join lateral (
                      select entry.tmdb_id,entry.popularity from provider_catalog_snapshot_items entry
                       where entry.media_type=s.media_type and entry.slot=s.active_slot and not entry.video
                         and not exists(select 1 from catalog_authority_import_jobs j
                             where j.media_type=entry.media_type and j.tmdb_id=entry.tmdb_id and j.season_number=-1)
                       order by entry.popularity desc,entry.tmdb_id
                       limit (select greatest(0,:cap-count(*))
                              from catalog_authority_import_jobs where media_type=:type and priority>=50)
                  ) selected
                 where s.media_type=:type and s.snapshot_date is not null
                on conflict do nothing
                """, Map.of("type", type, "cap", cap));
    }

    ChangeCursor cursor(String type, LocalDate firstDay) {
        var p = Map.of("type", type, "day", firstDay);
        jdbc.update("""
                insert into provider_catalog_sync_cursors(media_type,change_day) values(:type,:day)
                on conflict(media_type) do nothing
                """, p);
        return jdbc.queryForObject("select change_day,next_page from provider_catalog_sync_cursors where media_type=:type",
                p, (rs, row) -> new ChangeCursor(type, rs.getObject(1, LocalDate.class), rs.getInt(2)));
    }

    @Transactional(timeout = 10)
    public boolean saveChanges(ChangeCursor cursor, TmdbChangePage changes) {
        var p = new MapSqlParameterSource("type", cursor.type()).addValue("day", cursor.day()).addValue("page", cursor.page())
                .addValue("nextDay", changes.last() ? cursor.day().plusDays(1) : cursor.day())
                .addValue("nextPage", changes.last() ? 1 : cursor.page() + 1);
        if (jdbc.update("""
                update provider_catalog_sync_cursors set change_day=:nextDay,next_page=:nextPage,updated_at=now()
                where media_type=:type and change_day=:day and next_page=:page
                """, p) != 1) return false;
        if (!changes.ids().isEmpty()) {
            jdbc.update("""
                    update catalog_authority_import_jobs set status='PENDING',next_attempt_at=now(),updated_at=now()
                    where media_type=:type and tmdb_id in (:ids)
                      and (status='SUCCESS' and last_success_at < cast(:day as date) + interval '1 day'
                          or status='UNAVAILABLE' and last_error is distinct from 'ADULT_SOURCE_RECORD' and next_attempt_at<=now())
                    """, p.addValue("ids", changes.ids()));
        }
        return true;
    }


    private MapSqlParameterSource params(Import job) {
        return new MapSqlParameterSource("type", job.mediaType()).addValue("tmdb", job.tmdbId())
                .addValue("requested", job.seasonNumber()).addValue("token", job.token());
    }

    private void lockWork(Import job) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key,0))",
                Map.of("key", "authority-work:" + job.mediaType() + ":" + job.tmdbId()), rs -> { });
    }

    private static String titleKey(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    record Import(String mediaType, long tmdbId, int seasonNumber, int priority, int attempts, UUID token,
            boolean creditBackfill) { }
    record ChangeCursor(String type, LocalDate day, int page) { }
    private record PreviousWork(String title, String originalTitle) { }
}
