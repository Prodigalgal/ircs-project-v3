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
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
        pool.setConnectionInitSql("set search_path to " + schema + ", public");
        jdbc = new JdbcTemplate(pool);
        jdbc.execute("""
                create table standard_category(slug text primary key, name text);
                create table unified_videos(
                    id uuid primary key, title text, alias_title text, subtitle text, year text,
                    total_episodes text, duration text, remarks text, category_code text,
                    douban_id text, tmdb_id text, imdb_id text, rotten_tomatoes_id text, season integer,
                    actor_names jsonb default '[]', director_names jsonb default '[]',
                    area_codes jsonb default '[]', updated_at timestamptz default now());
                insert into standard_category values ('movie', 'Movie');
                """);
        search = mock(AggregationContextSearchClient.class);
        when(search.findCandidateUnifiedVideoIds(org.mockito.ArgumentMatchers.anyString(),
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
    void retainsOlderCompleteSubtitleMatchBeyondTwentyNewerNamesakes() {
        addNamesakes();
        UUID correct = insert("Echo", "Last Voyage", "2026", null, null, 0);

        List<UUID> recalled = recall(raw("Echo", "Last Voyage", "2026", Set.of(), Set.of()));

        assertThat(recalled).hasSize(20).contains(correct);
    }

    @Test
    void retainsOlderCastAndDirectorMatchBeyondTwentyNewerNamesakes() {
        addNamesakes();
        UUID correct = insert("Echo", "Last Voyage", "2026", "Mira Vale", "Arun West", 0);

        List<UUID> recalled = recall(raw("Echo", "Last Voyage", "2026",
                Set.of("Mira Vale"), Set.of("Arun West")));

        assertThat(recalled).hasSize(20).contains(correct);
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
        when(search.findCandidateUnifiedVideoIds("Echo Last Voyage", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(List.of(searchHit)));

        List<UUID> recalled = recall(raw("Echo", "Last Voyage", "2026",
                Set.of("Mira Vale"), Set.of("Arun West")));

        assertThat(recalled).contains(postgresHit, searchHit).doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(20);
        verify(search).findCandidateUnifiedVideoIds("Echo Last Voyage", "2026");
    }

    @Test
    void boundsRecallAndTreatsSqlWildcardsAndQuotesAsLiteralInput() {
        for (int index = 0; index < 30; index++) {
            insert("Repeated Signal", null, "2026", null, null, index);
        }
        assertThat(recall(raw("Repeated Signal", null, "2026", Set.of(), Set.of()))).hasSize(20);
        assertThat(recall(raw("%_", null, "2026", Set.of(), Set.of()))).isEmpty();
        assertThat(recall(raw("%' OR 1=1 --_", null, "2026", Set.of(), Set.of()))).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from unified_videos", Integer.class)).isEqualTo(30);
    }

    private void addNamesakes() {
        for (int index = 1; index <= 25; index++) {
            insert("Echo", "Other Story " + index, "2026", "Other Actor " + index,
                    "Other Director " + index, index);
        }
    }

    private UUID insert(String title, String subtitle, String year, String actor, String director, int recency) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into unified_videos(id, title, subtitle, year, category_code,
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

    private List<UUID> recall(RawVideoAggregationRecord raw) {
        return repository.findContextUnifiedCandidates(List.of(raw)).stream()
                .map(UnifiedVideoAggregationCandidate::id).toList();
    }

    private RawVideoAggregationRecord raw(String title, String subtitle, String year,
            Set<String> actors, Set<String> directors) {
        return new RawVideoAggregationRecord(UUID.randomUUID(), title, null, null, year,
                null, null, null, null, null, subtitle, null, "Movie", "movie", actors, directors,
                Set.of(), null, null, null, null, "READY", "SUCCESS", "PENDING");
    }
}
