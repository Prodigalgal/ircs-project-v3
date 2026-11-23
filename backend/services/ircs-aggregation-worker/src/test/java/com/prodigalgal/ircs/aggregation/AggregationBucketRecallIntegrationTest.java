package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Exercises PostgreSQL trigram, JSONB and escaped LIKE behavior against the CI database. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class AggregationBucketRecallIntegrationTest {

    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private String schema;
    private AggregationCandidateRepository repository;
    private AggregationContextSearchClient search;

    @BeforeEach
    void setUp() {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        admin.execute("create extension if not exists pg_trgm with schema public");
        schema = "aggregation_recall_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionInitSql("set search_path to " + schema
                + ", public; set pg_trgm.similarity_threshold = 0.19");
        jdbc = new JdbcTemplate(pool);
        jdbc.execute("""
                create table catalog_categories(slug text primary key, name text);
                create table catalog_entries(
                    id uuid primary key, title text, alias_title text, subtitle text, year text,
                    total_episodes text, duration text, remarks text, category_code text,
                    douban_id text, tmdb_id text, imdb_id text, rotten_tomatoes_id text, season integer,
                    actor_names jsonb default '[]', director_names jsonb default '[]',
                    area_codes jsonb default '[]', updated_at timestamptz default now());
                insert into catalog_categories values ('movie', 'Movie');
                create index on catalog_entries using gin (title gin_trgm_ops);
                create index on catalog_entries using gin (alias_title gin_trgm_ops);
                create index on catalog_entries using gin
                    ((coalesce(title, '') || ' ' || coalesce(subtitle, '')) gin_trgm_ops);
                """);
        search = mock(AggregationContextSearchClient.class);
        when(search.findCandidateCatalogEntryIds(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.notAttempted());
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        when(metadata.populateUnifiedMetadata(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        repository = new AggregationCandidateRepository(new NamedParameterJdbcTemplate(pool),
                AggregationTestFixtures.matchingStrategy(), search, metadata);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
        if (admin != null && schema != null) {
            admin.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void retainsOlderCompleteSubtitleMatchBeyondHundredCandidatePrefilter() {
        addNamesakes();
        UUID correct = insert("Echo", "Last Voyage", "2026", null, null, 0);

        List<UUID> recalled = recall(raw("Echo", "Last Voyage", "2026", Set.of(), Set.of()));

        assertThat(recalled).hasSize(20).contains(correct);
    }

    @Test
    void retainsOlderCastAndDirectorMatchWithoutSubtitleBeyondHundredCandidatePrefilter() {
        addNamesakes();
        UUID correct = insert("Echo", null, "2026", "Mira Vale", "Arun West", 0);

        List<UUID> recalled = recall(raw("Echo", null, "2026",
                Set.of("Mira Vale"), Set.of("Arun West")));

        assertThat(recalled).hasSize(20).contains(correct);
        assertThat(recall(raw("Echo", null, "2026", Set.of(), Set.of())))
                .hasSize(20).doesNotContain(correct);
    }

    @Test
    void includesUnknownCandidateYearsAndDoesNotFilterOnUnknownSourceYear() {
        List<UUID> unknown = new ArrayList<>();
        for (String year : new String[] {null, "", "0", "UNKNOWN", " 未知 ", "不详", "n/a", "待定"}) {
            unknown.add(insert("Yearless Signal", null, year, null, null, 0));
        }
        UUID old = insert("Yearless Signal", null, "1980", null, null, 0);

        assertThat(recall(raw("Yearless Signal", null, "2026", Set.of(), Set.of())))
                .containsAll(unknown).doesNotContain(old);
        assertThat(recall(raw("Yearless Signal", null, "unknown", Set.of(), Set.of())))
                .containsAll(unknown).contains(old);
    }

    @Test
    void actorEvidenceRecallsTitleTypoWithoutRecallingUnrelatedWorkBySameActor() {
        assertThat(similarity("Silent Harbor", "Sxlant Harbur")).isBetween(0.25, 0.34999);
        UUID typo = insert("Sxlant Harbur", null, "2026", "Mira Vale", null, 0);
        UUID unrelated = insert("Quantum Orchard", null, "2026", "Mira Vale", null, 0);

        assertThat(recall(raw("Silent Harbor", null, "2026", Set.of("Mira Vale"), Set.of())))
                .contains(typo).doesNotContain(unrelated);
        assertThat(recall(raw("Silent Harbor", null, "2026", Set.of(), Set.of())))
                .doesNotContain(typo, unrelated);
    }

    @Test
    void directorEvidenceRecallsTitleTypoWithoutRecallingUnrelatedWorkBySameDirector() {
        assertThat(similarity("Silent Harbor", "Silant Herbur")).isBetween(0.20, 0.24999);
        UUID typo = insert("Silant Herbur", null, "2026", null, "Arun West", 0);
        UUID unrelated = insert("Quantum Orchard", null, "2026", null, "Arun West", 0);

        assertThat(recall(raw("Silent Harbor", null, "2026", Set.of(), Set.of("Arun West"))))
                .contains(typo).doesNotContain(unrelated);
        assertThat(recall(raw("Silent Harbor", null, "2026", Set.of(), Set.of())))
                .doesNotContain(typo, unrelated);
    }

    @Test
    void nonemptySearchResultsStillMergeStructuredPostgresRecall() {
        UUID searchHit = insert("Search Index Only", null, "2026", null, null, 0);
        UUID postgresHit = insert("Echo", "Last Voyage", "2026", "Mira Vale", "Arun West", 0);
        when(search.findCandidateCatalogEntryIds("Echo Last Voyage", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(List.of(searchHit)));

        List<UUID> recalled = recall(raw("Echo", "Last Voyage", "2026",
                Set.of("Mira Vale"), Set.of("Arun West")));

        assertThat(recalled).contains(postgresHit, searchHit).doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(20);
        verify(search).findCandidateCatalogEntryIds("Echo Last Voyage", "2026");
    }

    @Test
    void boundsRecallAndTreatsSqlWildcardsAndQuotesAsLiteralInput() {
        for (int index = 0; index < 30; index++) {
            insert("Repeated Signal", null, "2026", null, null, index);
        }
        assertThat(recall(raw("Repeated Signal", null, "2026", Set.of(), Set.of()))).hasSize(20);
        assertThat(recall(raw("%_", null, "2026", Set.of(), Set.of()))).isEmpty();
        assertThat(recall(raw("%' OR 1=1 --_", null, "2026", Set.of(), Set.of()))).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from catalog_entries", Integer.class)).isEqualTo(30);
    }

    @Test
    void recallsTheSameFixtureAcrossDateAndSeasonSpellings() {
        jdbc.update("insert into catalog_categories values ('sports', '体育')");
        UUID correct = insert("11月13日25-26赛季NBA常规赛", "黄蜂VS太阳", "2026", null, null, 0);
        UUID otherFixture = insert("11月13日25-26赛季NBA常规赛", "黄蜂VS湖人", "2026", null, null, 1);
        jdbc.update("update catalog_entries set category_code = 'sports' where id in (?, ?)", correct, otherFixture);
        SourceRecordAggregationRecord raw = new SourceRecordAggregationRecord(UUID.randomUUID(),
                "NBA常规赛:黄蜂VS太阳20261113", null, null, "2026", null, null, null, null, null, null, null,
                "体育", "sports", Set.of(), Set.of(), Set.of(), null, null, null, null,
                "READY", "SUCCESS", "PENDING");

        assertThat(recall(raw)).contains(correct);
        assertThat(repository.findMatchPlan(raw)).isEqualTo(AggregationMatchPlan.rootOnly(correct));
    }

    @Test
    void loadsOnlyRequestedMemberEvidenceAndDetectsTheReviewLimit() {
        jdbc.execute("""
                create table source_records(
                    id uuid primary key, created_at timestamptz default now(), title text, alias_title text, subtitle text, remarks text,
                    year text, season integer, category_code text, douban_id text, tmdb_id text,
                    imdb_id text, rotten_tomatoes_id text);
                create table catalog_source_bindings(source_record_id uuid primary key, catalog_entry_id uuid);
                create index on catalog_source_bindings(catalog_entry_id, source_record_id);
                """);
        UUID full = UUID.randomUUID();
        UUID single = UUID.randomUUID();
        UUID rawId = UUID.randomUUID();
        jdbc.update("""
                with inserted as (
                    insert into source_records(id, title) select gen_random_uuid(), 'Matched Work'
                    from generate_series(1, ?) returning id
                )
                insert into catalog_source_bindings select id, ? from inserted
                """, AggregationMemberIdentity.REVIEW_MEMBER_LIMIT + 2, full);
        jdbc.update("""
                insert into source_records(id, title, alias_title, subtitle, remarks, year, season, category_code,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id)
                values (?, 'Other Work', 'Alias', 'Subtitle', 'Remarks', '2025', 2, 'series',
                    '1234567', '4567', 'tt9876543', 'other_work')
                """, rawId);
        jdbc.update("insert into catalog_source_bindings values (?, ?)", rawId, single);
        JdbcAggregationRepository identities = JdbcAggregationRepository.forTest(
                new NamedParameterJdbcTemplate(pool), AggregationTestFixtures.matchingStrategy());

        assertThat(identities.findBoundMemberIdentities(List.of(single)))
                .containsExactly(new AggregationMemberIdentity(rawId, "Other Work", "Alias", "Subtitle", "Remarks",
                        "2025", 2, "series", "1234567", "4567", "tt9876543", "other_work"));
        assertThat(identities.findBoundMemberIdentities(List.of(full, UUID.randomUUID())))
                .hasSize(AggregationMemberIdentity.REVIEW_MEMBER_LIMIT + 1)
                .allMatch(member -> "Matched Work".equals(member.title()));
        assertThat(identities.findBoundMemberIdentities(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void bindingCorrectionSnapshotDetectsEvidenceChangesAndPreservesWorkReferences() {
        jdbc.execute("""
                create table source_records(id uuid primary key);
                create table catalog_source_bindings(source_record_id uuid, catalog_entry_id uuid,
                    primary key(source_record_id, catalog_entry_id));
                create table member_catalog_favorites(catalog_entry_id uuid);
                create table member_playback_progress(catalog_entry_id uuid, last_source_record_id uuid, last_source_playback_item_id uuid);
                create table catalog_magnet_links(catalog_entry_id uuid);
                create table source_playlists(id uuid primary key, source_record_id uuid);
                create table source_playback_items(id uuid primary key, playlist_id uuid);
                """);
        UUID source = insert("Old mixed work", null, "2026", null, null, 0);
        UUID target = insert("Correct work", null, "2026", null, null, 0);
        UUID moved = UUID.randomUUID();
        UUID retained = UUID.randomUUID();
        UUID targetMember = UUID.randomUUID();
        UUID playlist = UUID.randomUUID();
        UUID episode = UUID.randomUUID();
        jdbc.update("insert into source_records values (?), (?), (?)", moved, retained, targetMember);
        jdbc.update("insert into catalog_source_bindings values (?, ?), (?, ?), (?, ?)",
                moved, source, retained, source, targetMember, target);
        jdbc.update("insert into member_catalog_favorites values (?)", source);
        jdbc.update("insert into catalog_magnet_links values (?)", source);
        jdbc.update("insert into source_playlists values (?, ?)", playlist, moved);
        jdbc.update("insert into source_playback_items values (?, ?)", episode, playlist);

        var identities = JdbcAggregationRepository.forTest(new NamedParameterJdbcTemplate(pool),
                AggregationTestFixtures.matchingStrategy());
        var selected = List.of(moved);
        var before = identities.findBindingCorrectionSnapshot(source, target, selected);
        assertThat(before.expectedBindings()).isOne();
        assertThat(before.fingerprint()).matches("[0-9a-f]{64}");
        jdbc.update("update source_records set id=id where id=?", targetMember);
        assertThat(identities.findBindingCorrectionSnapshot(source, target, selected).fingerprint())
                .isNotEqualTo(before.fingerprint());
        jdbc.update("insert into member_playback_progress values (?, null, ?)", target, episode);
        assertThat(identities.findBindingCorrectionSnapshot(source, target, selected).histories()).isOne();
        jdbc.update("delete from member_playback_progress");

        new TransactionTemplate(new DataSourceTransactionManager(pool)).executeWithoutResult(status -> {
            identities.lockSelectedCorrectionBindings(selected);
            assertThat(identities.lockRetainedCorrectionBinding(source, selected)).isTrue();
            assertThat(identities.moveCorrectionBindings(source, target, selected)).isOne();
        });

        assertThat(identities.findExistingBinding(moved)).contains(target);
        assertThat(identities.findExistingBinding(retained)).contains(source);
        var after = identities.findBindingCorrectionSnapshot(source, target, selected);
        assertThat(after.sourceMembers()).isOne();
        assertThat(after.targetMembers()).isEqualTo(2);
        assertThat(after.expectedBindings()).isZero();
        assertThat(after.favorites()).isOne();
        assertThat(after.magnets()).isOne();
        assertThat(jdbc.queryForObject("select count(*) from catalog_entries", Integer.class)).isEqualTo(2);
    }

    private void addNamesakes() {
        for (int index = 1; index <= 125; index++) {
            insert("Echo", "Other Story " + index, "2026", "Other Actor " + index,
                    "Other Director " + index, index);
        }
    }

    private UUID insert(String title, String subtitle, String year, String actor, String director, int recency) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into catalog_entries(id, title, subtitle, year, category_code,
                    actor_names, director_names, updated_at)
                values (?, ?, ?, ?, 'movie', jsonb_build_array(cast(? as text)),
                    jsonb_build_array(cast(? as text)), timestamp with time zone '2026-01-01 00:00:00+00'
                    + (? * interval '1 day'))
                """, id, title, subtitle, year, actor, director, recency);
        return id;
    }

    private double similarity(String left, String right) {
        return jdbc.queryForObject("select similarity(?, ?)", Double.class, left, right);
    }

    private List<UUID> recall(SourceRecordAggregationRecord raw) {
        return repository.findContextUnifiedCandidates(List.of(raw)).stream()
                .map(CatalogEntryAggregationCandidate::id).toList();
    }

    private SourceRecordAggregationRecord raw(String title, String subtitle, String year,
            Set<String> actors, Set<String> directors) {
        return new SourceRecordAggregationRecord(UUID.randomUUID(), title, null, null, year,
                null, null, null, null, null, subtitle, null, "Movie", "movie", actors, directors,
                Set.of(), null, null, null, null, "READY", "SUCCESS", "PENDING");
    }
}
