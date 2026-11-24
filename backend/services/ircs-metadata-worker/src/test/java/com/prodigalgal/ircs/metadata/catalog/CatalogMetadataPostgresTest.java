package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named="IRCS_TEST_POSTGRES_URL",matches=".+")
class CatalogMetadataPostgresTest {
    private CatalogTestDatabase database;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private TransactionTemplate transactions;
    private CatalogMetadataRepository repository;
    private AuthorityCatalogImportRepository authority;

    @BeforeEach
    void setup() throws Exception {
        database = CatalogTestDatabase.open();
        pool = new HikariDataSource();
        pool.setJdbcUrl(database.url());
        pool.setUsername(System.getenv("IRCS_TEST_POSTGRES_USER"));
        pool.setPassword(System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        pool.setMaximumPoolSize(3);
        pool.setMinimumIdle(0);
        jdbc=new JdbcTemplate(pool); transactions=new TransactionTemplate(new DataSourceTransactionManager(pool));
        var named = new NamedParameterJdbcTemplate(pool);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        repository = new CatalogMetadataRepository(named, mapper);
        authority = new AuthorityCatalogImportRepository(named, repository, new CatalogPeopleRepository(named, mapper));
        jdbc.execute("""
                insert into catalog_regions(id,created_at,updated_at,code,name)
                    values(uuidv7(),now(),now(),'CN','中国大陆') on conflict do nothing;
                insert into catalog_languages(id,created_at,updated_at,code,name,english_name,native_name)
                    values(uuidv7(),now(),now(),'zh','中文','Chinese','中文') on conflict do nothing;
                insert into catalog_genres(id,created_at,updated_at,code,name)
                    values(uuidv7(),now(),now(),'action','动作') on conflict do nothing
                """);
    }

    @AfterEach
    void cleanup() {
        if (pool != null) pool.close();
        if (database != null) database.close();
    }

    @Test
    void fillsRealMetadataWithoutCreatingOrNeedingAnyRawResource() {
        UUID id=enqueue(); var target=repository.claim(1).getFirst();
        assertThat(transactions.<Boolean>execute(status->repository.apply(target,evidence()))).isTrue();
        assertThat(jdbc.queryForObject("select description from catalog_entries where id=?",String.class,id)).isEqualTo("Verified overview");
        assertThat(jdbc.queryForObject("select score from catalog_entries where id=?",BigDecimal.class,id)).isEqualByComparingTo("8.2");
        assertThat(jdbc.queryForObject("select actor_names::text from catalog_entries where id=?",String.class,id)).contains("Actor");
        assertThat(jdbc.queryForObject("select genre_codes::text from catalog_entries where id=?",String.class,id)).contains("action");
        assertThat(jdbc.queryForObject("select metadata_status from catalog_entries where id=?",String.class,id)).isEqualTo("SYNCED");
        assertThat(jdbc.queryForObject("select count(*) from source_records",Integer.class)).isZero();
        assertThat(repository.pendingIndexes()).hasSize(1);
    }

    @Test
    void manualArrayAndObjectLocksAndNonemptyValuesSurviveInFlightFetches() {
        for(String locks:List.of("[\"actors\",\"score\"]","{\"actors\":true,\"score\":true}")) {
            UUID id=enqueue(); var target=repository.claim(1).getFirst();
            jdbc.update("update catalog_entries set locked_fields=cast(? as jsonb),score=0,description='Manual text' where id=?",locks,id);
            transactions.execute(status->repository.apply(target,evidence()));
            assertThat(jdbc.queryForObject("select score from catalog_entries where id=?",BigDecimal.class,id)).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("select description from catalog_entries where id=?",String.class,id)).isEqualTo("Manual text");
            assertThat(jdbc.queryForObject("select actor_names::text from catalog_entries where id=?",String.class,id)).isEqualTo("[]");
            assertThat(jdbc.queryForObject("select metadata_status from catalog_entries where id=?",String.class,id)).isEqualTo("DIRTY");
        }
    }

    @Test
    void expiredOwnersCannotApplyOrAcknowledgeReclaimedRequests() {
        enqueue(); var old=repository.claim(1).getFirst();
        jdbc.update("update catalog_metadata_enrichment_jobs set lease_until=now()-interval '1 second'");
        var current=repository.claim(1).getFirst();
        assertThat(transactions.<Boolean>execute(status->repository.apply(old,evidence()))).isFalse();
        assertThat(repository.renew(old)).isFalse(); assertThat(repository.defer(old,false,"OLD")).isFalse();
        assertThat(transactions.<Boolean>execute(status->repository.apply(current,evidence()))).isTrue();
    }

    @Test
    void concurrentClaimsAreUniqueAndDeferredWorkDoesNotSpin() throws Exception {
        enqueue();
        try(var executor=Executors.newFixedThreadPool(3)) {
            List<Callable<List<CatalogMetadataTarget>>> calls=java.util.stream.IntStream.range(0,12)
                    .mapToObj(n->(Callable<List<CatalogMetadataTarget>>)()->repository.claim(1)).toList();
            List<CatalogMetadataTarget> claimed=new java.util.ArrayList<>();
            for(var future:executor.invokeAll(calls))claimed.addAll(future.get());
            assertThat(claimed).hasSize(1);
            assertThat(repository.defer(claimed.getFirst(),true,"NO_VERIFIABLE_PROVIDER")).isTrue();
            assertThat(repository.claim(1)).isEmpty();
            assertThat(jdbc.queryForObject("select next_attempt_at>now()+interval '23 hours' from catalog_metadata_enrichment_jobs",Boolean.class)).isTrue();
        }
    }

    @Test
    void identityAndScopeChangesAreRecheckedAtCommitAndPartialDataStaysDirty() {
        UUID id=enqueue(); var target=repository.claim(1).getFirst();
        assertThatThrownBy(()->transactions.execute(status->repository.apply(target,
                new CatalogMetadataEvidence("99","movie",0,evidence().metadata())))).isInstanceOf(CatalogMetadataUnavailableException.class);
        jdbc.update("update catalog_entries set content_visibility='HIDDEN' where id=?",id);
        assertThatThrownBy(()->transactions.execute(status->repository.apply(target,evidence()))).isInstanceOf(CatalogMetadataUnavailableException.class);
        jdbc.update("update catalog_entries set content_visibility='PUBLIC' where id=?",id);
        var partial=new EnrichedMetadataDTO(); partial.setTmdbId("42"); partial.setDescription("Only overview");
        assertThat(transactions.<Boolean>execute(status->repository.apply(target,new CatalogMetadataEvidence("42","movie",0,partial)))).isTrue();
        assertThat(jdbc.queryForObject("select metadata_status from catalog_entries where id=?",String.class,id)).isEqualTo("DIRTY");
    }

    @Test
    void indexAcknowledgementCannotClearANewerGeneration() {
        UUID id=enqueue(); var target=repository.claim(1).getFirst();
        transactions.execute(status->repository.apply(target,evidence()));
        var old=repository.pendingIndexes().getFirst();
        jdbc.update("update catalog_entries set title=title || ' v2' where id=?",id);
        repository.indexed(old); assertThat(repository.pendingIndexes()).hasSize(1);
        repository.indexed(repository.pendingIndexes().getFirst()); assertThat(repository.pendingIndexes()).isEmpty();
    }

    @Test
    void commitsTypedWorksAndEpisodesBeforeCreatingDiscovery() throws Exception {
        jdbc.update("""
                insert into catalog_authority_import_jobs(media_type,tmdb_id,season_number,priority)
                values ('movie',42,-1,100),('tv',42,1,200)
                """);
        assertThat(jdbc.queryForObject("select count(*) from catalog_resource_discovery_jobs", Integer.class)).isZero();
        var tv = authority.claim().orElseThrow();
        assertThat(tv.mediaType()).isEqualTo("tv");
        var premiere = LocalDate.of(2026, 4, 15);
        var season = new AuthorityCatalogEvidence.Season(1, 23, "Season 1", premiere);
        var episode = new AuthorityCatalogEvidence.Episode(2, 999, "Episode 2", premiere.plusDays(7));
        var tvEvidence = new AuthorityCatalogEvidence("tv",42,1,premiere,"series",authorityFacts("Example Series"),
                List.of(season), List.of(episode), List.of());
        assertThat(transactions.<Boolean>execute(status -> authority.publish(tv,tvEvidence))).isTrue();
        var movie = authority.claim().orElseThrow();
        var movieEvidence = new AuthorityCatalogEvidence("movie",42,-1,premiere,"movie",authorityFacts("Example Movie"),
                List.of(), List.of(), List.of());
        assertThat(transactions.<Boolean>execute(status -> authority.publish(movie,movieEvidence))).isTrue();

        assertThat(jdbc.queryForList("select media_type from catalog_works order by media_type", String.class))
                .containsExactly("movie", "tv");
        assertThat(jdbc.queryForObject("select tmdb_episode_id from catalog_episodes", Long.class)).isEqualTo(999L);
        assertThat(jdbc.queryForList("select target_season from catalog_resource_discovery_jobs order by target_season", Integer.class))
                .containsExactly(0, 1);
        assertThat(repository.pendingIndexes()).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from source_origins", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from media_image_sources", Integer.class)).isEqualTo(1);
        assertThat(transactions.<Boolean>execute(status -> authority.publish(tv,tvEvidence))).isFalse();
    }

    @Test
    void importsRecentMissingEpisodeTargetsOnlyAfterTheSeasonHasResources() throws Exception {
        jdbc.update("""
                insert into catalog_authority_import_jobs(media_type,tmdb_id,season_number,priority)
                values ('tv',42,1,100)
                """);
        LocalDate today = jdbc.queryForObject("select current_date", LocalDate.class);
        var season = new AuthorityCatalogEvidence.Season(1, 23, "Season 1", today.minusDays(30));
        var episodes = List.of(
                new AuthorityCatalogEvidence.Episode(1, 1001, "Episode 1", today.minusDays(20)),
                new AuthorityCatalogEvidence.Episode(2, 1002, "Episode 2", today.minusDays(5)),
                new AuthorityCatalogEvidence.Episode(3, 1003, "Episode 3", today.minusDays(4)),
                new AuthorityCatalogEvidence.Episode(4, 1004, "Episode 4", today.minusDays(3)),
                new AuthorityCatalogEvidence.Episode(5, 1005, "Episode 5", today.minusDays(2)),
                new AuthorityCatalogEvidence.Episode(6, 1006, "Episode 6", today.plusDays(1)));
        var evidence = new AuthorityCatalogEvidence("tv", 42, 1, today, "series", authorityFacts("Example Series"),
                List.of(season), episodes, List.of());
        var first = authority.claim().orElseThrow();
        assertThat(transactions.<Boolean>execute(status -> authority.publish(first, evidence))).isTrue();
        assertThat(jdbc.queryForList("select target_episode from catalog_resource_discovery_jobs", Integer.class))
                .containsExactly(0);

        jdbc.update("""
                insert into catalog_magnet_links(id,catalog_entry_id,provider_code,info_hash,magnet_uri,title,
                        matched_external_id_type,matched_external_id_value,status)
                select uuidv7(),id,'authority-test',repeat('A',40),'magnet:?xt=urn:btih:'||repeat('A',40),
                       'Season fixture','TMDB','42','APPROVED' from catalog_entries
                """);
        jdbc.update("update catalog_authority_import_jobs set status='PENDING', next_attempt_at=now()");
        var refreshed = authority.claim().orElseThrow();
        assertThat(transactions.<Boolean>execute(status -> authority.publish(refreshed, evidence))).isTrue();

        assertThat(jdbc.queryForList("select target_episode from catalog_resource_discovery_jobs order by target_episode",
                Integer.class)).containsExactly(0, 3, 4, 5);
        assertThat(jdbc.queryForList("select distinct target_season from catalog_resource_discovery_jobs", Integer.class))
                .containsExactly(1);

        jdbc.update("update catalog_authority_import_jobs set status='PENDING', next_attempt_at=now()");
        var repeated = authority.claim().orElseThrow();
        assertThat(transactions.<Boolean>execute(status -> authority.publish(repeated, evidence))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from catalog_resource_discovery_jobs", Integer.class)).isEqualTo(4);

        jdbc.update("update catalog_resource_discovery_jobs set status='SUBMITTED', fulfilled_at=now() where target_episode=5");
        jdbc.update("update catalog_authority_import_jobs set status='PENDING', next_attempt_at=now()");
        var afterCompletion = authority.claim().orElseThrow();
        assertThat(transactions.<Boolean>execute(status -> authority.publish(afterCompletion, evidence))).isTrue();
        assertThat(jdbc.queryForList("select target_episode from catalog_resource_discovery_jobs order by target_episode",
                Integer.class)).containsExactly(0, 2, 3, 4, 5);
    }

    @Test
    void oneSeasonAdultRejectionBlocksAnInFlightImportForTheSameWork() throws Exception {
        jdbc.update("""
                insert into catalog_authority_import_jobs(media_type,tmdb_id,season_number,priority)
                values ('tv',42,1,200),('tv',42,2,100)
                """);
        var rejected = authority.claim().orElseThrow();
        var inFlight = authority.claim().orElseThrow();
        transactions.executeWithoutResult(status -> authority.defer(rejected, "ADULT_SOURCE_RECORD", true));
        var date = LocalDate.of(2026, 4, 15);
        var season = new AuthorityCatalogEvidence.Season(2, 23, "Season 2", date);
        var evidence = new AuthorityCatalogEvidence("tv",42,2,date,"series",authorityFacts("Example Series"),
                List.of(season), List.of(), List.of());
        assertThatThrownBy(() -> transactions.execute(status -> authority.publish(inFlight,evidence)))
                .isInstanceOf(CatalogMetadataUnavailableException.class)
                .hasMessage("ADULT_SOURCE_RECORD");
        assertThat(jdbc.queryForObject("select count(*) from catalog_works", Integer.class)).isZero();
    }

    @Test
    void warmupUsesOnlyTheActivePublicSnapshotAndRespectsItsCap() throws Exception {
        jdbc.update("""
                update provider_catalog_snapshot_states
                   set active_slot=1,snapshot_date=date '2026-11-22',row_count=4 where media_type='movie';
                insert into provider_catalog_snapshot_items(media_type,slot,tmdb_id,title,popularity,video)
                values ('movie',1,100,'First',200,false),('movie',1,99,'Second',100,false),
                       ('movie',1,98,'Third',10,false),('movie',1,2,'Clip',1000,true),
                       ('movie',0,5,'Inactive',3000,false),('tv',1,1,'TV',100,false)
                """);
        assertThat(authority.seed("movie",2)).isEqualTo(2);
        assertThat(authority.seed("movie",2)).isZero();
        assertThat(jdbc.queryForList("select tmdb_id from catalog_authority_import_jobs order by tmdb_id",Long.class))
                .containsExactly(99L,100L);
    }

    @Test
    void storesPeopleByProviderIdAndKeepsPriorRolesOnIncompleteReplies() throws Exception {
        jdbc.update("insert into catalog_authority_import_jobs(media_type,tmdb_id,season_number) values ('tv',42,1)");
        var date = LocalDate.of(2026, 4, 15);
        var season = new AuthorityCatalogEvidence.Season(1, 23, "Season 1", date);
        var episode = new AuthorityCatalogEvidence.Episode(2, 999, "Episode 2", date);
        var cast = AuthorityCatalogEvidence.CreditKind.CAST;
        var guest = AuthorityCatalogEvidence.CreditKind.GUEST_CAST;
        var mainRole = new AuthorityCatalogEvidence.Credit("101", "main", "Alex", null, "Acting", "Actor", "Lead", 0, 0);
        var guestRole = new AuthorityCatalogEvidence.Credit("101", "guest", "Alex", null, "Acting", "Actor", "Guest", null, 0);
        var namesake = new AuthorityCatalogEvidence.Credit("102", "another", "Alex", null, "Acting", "Actor", "Friend", 1, 1);
        var scope = new AuthorityCatalogEvidence.CreditSet(null, null, cast, true, List.of(mainRole, namesake));
        var episodeScope = new AuthorityCatalogEvidence.CreditSet(1, 2, guest, true, List.of(guestRole));
        var job = authority.claim().orElseThrow();
        var complete = new AuthorityCatalogEvidence("tv", 42, 1, date, "series", authorityFacts("Example Series"),
                List.of(season), List.of(episode), List.of(scope, episodeScope));
        assertThat(transactions.<Boolean>execute(status -> authority.publish(job, complete))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from catalog_people", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct person_id) from catalog_person_credits where external_credit_id in ('main','guest')",
                Integer.class)).isEqualTo(1);
        UUID guestCreditId = jdbc.queryForObject("select id from catalog_person_credits where episode_number=2", UUID.class);

        jdbc.update("update catalog_authority_import_jobs set credits_backfill_at=now()");
        var backfill = authority.claim().orElseThrow();
        assertThat(backfill.creditBackfill()).isTrue();
        authority.defer(backfill, "SOURCE_OR_WRITE_FAILED", false);
        assertThat(authority.claim()).isEmpty();
        assertThat(jdbc.queryForObject("select status from catalog_authority_import_jobs", String.class)).isEqualTo("SUCCESS");

        jdbc.update("update catalog_authority_import_jobs set credits_backfill_at=now()");
        var rerun = authority.claim().orElseThrow();
        var incomplete = new AuthorityCatalogEvidence("tv", 42, 1, date, "series", authorityFacts("Example Series"),
                List.of(season), List.of(episode),
                List.of(new AuthorityCatalogEvidence.CreditSet(null, null, cast, false, List.of(mainRole))));
        assertThat(transactions.<Boolean>execute(status -> authority.publish(rerun, incomplete))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from catalog_person_credits", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select id from catalog_person_credits where episode_number=2", UUID.class)).isEqualTo(guestCreditId);
        assertThat(jdbc.queryForObject("select credits_backfill_at is null from catalog_authority_import_jobs", Boolean.class)).isTrue();

        jdbc.update("update catalog_authority_import_jobs set status='PENDING',next_attempt_at=now()");
        var refresh = authority.claim().orElseThrow();
        var latest = new AuthorityCatalogEvidence("tv", 42, 1, date, "series", authorityFacts("Example Series"),
                List.of(season), List.of(episode),
                List.of(new AuthorityCatalogEvidence.CreditSet(null, null, cast, true, List.of(mainRole))));
        assertThat(transactions.<Boolean>execute(status -> authority.publish(refresh, latest))).isTrue();
        assertThat(jdbc.queryForList("select external_credit_id from catalog_person_credits order by external_credit_id", String.class))
                .containsExactly("guest", "main");
    }

    private EnrichedMetadataDTO authorityFacts(String title) {
        var facts = evidence().metadata();
        facts.setTitle(title);
        return facts;
    }

    private UUID enqueue() {
        UUID id=UUID.randomUUID(); jdbc.update("insert into catalog_entries(id,title,tmdb_id,created_at,updated_at) values(?,'Known title','42',now(),now())",id);
        jdbc.update("insert into catalog_metadata_enrichment_jobs(catalog_entry_id,provider,media_type,external_id) values(?,'TMDB','movie','42')",id);
        return id;
    }
    private CatalogMetadataEvidence evidence() {
        var facts=new EnrichedMetadataDTO(); facts.setTmdbId("42"); facts.setDescription("Verified overview");
        facts.setYear("2026");facts.setPublishedAt(LocalDate.of(2026,11,10));facts.setPosterUrl("https://image.example/cover.jpg");
        facts.setScore(new BigDecimal("8.2"));facts.setActorNames(Set.of("Actor"));facts.setDirectorNames(Set.of("Director"));
        facts.setArea("CN");facts.setLanguage("zh");facts.setGenreNames(Set.of("动作"));
        return new CatalogMetadataEvidence("42","movie",0,facts);
    }
}
