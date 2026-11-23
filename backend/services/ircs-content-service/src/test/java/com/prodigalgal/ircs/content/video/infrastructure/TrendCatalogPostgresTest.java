package com.prodigalgal.ircs.content.video.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.content.maintenance.application.ContentMaintenanceGate;
import com.prodigalgal.ircs.content.video.application.TrendCatalogEntryService;
import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.content.video.messaging.ContentCommandPublisher;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real migration and PostgreSQL transaction/locking semantics; every test owns one isolated database. */
class TrendCatalogPostgresTest {

    @Test
    void authorityEventDatesUseSiteCalendarAtTheUtcDayBoundary() {
        LocalDate eventDay=LocalDate.of(2026,11,18);
        assertThat(TrendSnapshotRepository.eventHasOccurred(eventDay,Instant.parse("2026-11-17T16:01:00Z"))).isTrue();
        assertThat(TrendSnapshotRepository.eventHasOccurred(eventDay,Instant.parse("2026-11-17T15:59:00Z"))).isFalse();
    }

    private CatalogTestDatabase database;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private TransactionTemplate transactions;
    private TrendCatalogEntryService entries;
    private TrendSnapshotRepository snapshots;
    private ContentCommandPublisher publisher;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        database = CatalogTestDatabase.open();
        String user = requiredEnvironment("IRCS_TEST_POSTGRES_USER");
        String password = requiredEnvironment("IRCS_TEST_POSTGRES_PASSWORD");
        pool = new HikariDataSource();
        pool.setJdbcUrl(database.url());
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(6);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(5000);
        jdbc = new JdbcTemplate(pool);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
        transactions.setTimeout(10);
        now = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(60);
        var covers = Mockito.mock(VideoAdminCoverImageRepository.class);
        when(covers.getOrCreateCoverImage(any())).thenReturn(Optional.empty());
        publisher = Mockito.mock(ContentCommandPublisher.class);
        var gate = Mockito.mock(ContentMaintenanceGate.class);
        var named = new NamedParameterJdbcTemplate(pool);
        entries = new TrendCatalogEntryService(new TrendCatalogRepository(named, covers), publisher, gate);
        snapshots = new TrendSnapshotRepository(named);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) pool.close();
        if (database != null) database.close();
    }

    @Test
    void sameNumericTmdbIdDoesNotMergeMovieAndTv() {
        var movie = resolve(candidate("TMDB", "42", "movie", "Same Title", "2026", 0, 0, "trending-day", "day", 1, now, null));
        var tv = resolve(candidate("TMDB", "42", "tv", "Same Title", "2026", 1, 1, "trending-day", "day", 1, now, null));

        assertThat(movie.id()).isNotEqualTo(tv.id());
        assertThat(count("catalog_entries")).isEqualTo(2);
        assertThat(count("catalog_external_ids")).isEqualTo(2);
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(2);
    }

    @Test
    void separatesTvSeasonsAndDoesNotGuessAnUnknownSeasonWhenMultipleSeasonsExist() {
        var first = resolve(candidate("TMDB", "42", "tv", "Example Show", "2026", 1, 5, "popular", "popular", 1, now, null));
        var second = resolve(candidate("TMDB", "42", "tv", "Example Show", "2026", 2, 5, "popular", "popular", 1, now, null));
        var unknownCandidate = candidate("TMDB", "42", "tv", "Example Show", "2026", 0, 0, "popular", "popular", 1, now, null);
        var unknown = resolve(unknownCandidate);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(unknown.id()).isNotIn(first.id(), second.id());
        assertThat(resolve(unknownCandidate).id()).isEqualTo(unknown.id());
        assertThat(count("catalog_entries")).isEqualTo(3);
    }

    @Test
    void exactTitleYearAndMediaTypeCanBindAnExistingWorkWithoutChangingManualFields() {
        UUID existing = seedVideo("Known Film", "2026", "movie", null, null);
        jdbc.update("update catalog_entries set score=9.3, locked_fields='[\"title\",\"score\"]'::jsonb, metadata_status='SYNCED' where id=?", existing);

        var result = resolve(candidate("TMDB", "42", "movie", "KNOWN FILM", "2026", 0, 0, "popular", "popular", 1, now, null));

        assertThat(result.id()).isEqualTo(existing);
        assertThat(result.created()).isFalse();
        assertThat(jdbc.queryForObject("select title from catalog_entries where id=?", String.class, existing)).isEqualTo("Known Film");
        assertThat(jdbc.queryForObject("select score from catalog_entries where id=?", BigDecimal.class, existing)).isEqualByComparingTo("9.3");
        assertThat(jdbc.queryForObject("select metadata_status from catalog_entries where id=?", String.class, existing)).isEqualTo("SYNCED");
        assertThat(count("catalog_external_ids")).isEqualTo(1);
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
    }

    @Test
    void exactTitleCannotOverrideConflictingAuthoritativeIdYearOrMediaType() {
        UUID conflicting = seedVideo("Known Film", "2026", "movie", null, "99");
        UUID otherYear = seedVideo("Different Year", "2025", "movie", null, null);
        UUID otherType = seedVideo("Different Type", "2026", "series", 1, null);

        var idConflict = resolve(candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "popular", "popular", 1, now, null));
        var yearConflict = resolve(candidate("TMDB", "43", "movie", "Different Year", "2026", 0, 0, "popular", "popular", 1, now, null));
        var typeConflict = resolve(candidate("TMDB", "44", "movie", "Different Type", "2026", 0, 0, "popular", "popular", 1, now, null));

        assertThat(idConflict.id()).isNotEqualTo(conflicting);
        assertThat(yearConflict.id()).isNotEqualTo(otherYear);
        assertThat(typeConflict.id()).isNotEqualTo(otherType);
        assertThat(jdbc.queryForObject("select tmdb_id from catalog_entries where id=?", String.class, conflicting)).isEqualTo("99");
    }

    @Test
    void concurrentRepeatedSourceIdentityCreatesOneGhostAndOneDurableDiscoveryRequest() throws Exception {
        var candidate = candidate("TMDB", "42", "movie", "Concurrent Film", "2026", 0, 0, "popular", "popular", 1, now, null);
        List<Callable<TrendCatalogEntryService.Resolution>> calls = new ArrayList<>();
        for (int index = 0; index < 12; index++) calls.add(() -> resolve(candidate));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = executor.invokeAll(calls, 15, TimeUnit.SECONDS);
            UUID canonical = futures.getFirst().get().id();
            long created = 0;
            for (var future : futures) {
                var result = future.get();
                assertThat(result.id()).isEqualTo(canonical);
                if (result.created()) created++;
            }
            assertThat(created).isEqualTo(1);
            verify(publisher).publishUnifiedSearch(canonical, SyncOperation.INDEX);
        }
        assertThat(count("catalog_entries")).isEqualTo(1);
        assertThat(count("catalog_external_ids")).isEqualTo(1);
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select score from catalog_entries", BigDecimal.class)).isNull();
        assertThat(jdbc.queryForObject("select metadata_status from catalog_entries", String.class)).isEqualTo("DIRTY");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void missingResourcesForAnExistingWorkAreDurableAndRecentSubmissionIsNotRescheduled() {
        UUID existing = seedVideo("Known Film", "2026", "movie", null, "42");
        var candidate = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "popular", "popular", 1, now, null);
        assertThat(resolve(candidate).id()).isEqualTo(existing);
        jdbc.update("update catalog_resource_discovery_jobs set status='SUBMITTED', submitted_at=now(), attempts=3, next_attempt_at=now()+interval '1 hour'");

        resolve(candidate);

        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from catalog_resource_discovery_jobs", String.class)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select next_attempt_at>now() from catalog_resource_discovery_jobs", Boolean.class)).isTrue();
    }

    @Test
    void missingResourcesAfterSubmissionCooldownAreRequeuedWithoutCreatingAnotherRequest() {
        var candidate = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "popular", "popular", 1, now, null);
        resolve(candidate);
        jdbc.update("update catalog_resource_discovery_jobs set status='SUBMITTED', submitted_at=now()-interval '7 hours', attempts=3, next_attempt_at=now()+interval '1 hour'");

        resolve(candidate);

        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from catalog_resource_discovery_jobs", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select next_attempt_at<=now() from catalog_resource_discovery_jobs", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isEqualTo(3);
    }

    @Test
    void aRealZeroRatingRemainsDifferentFromMissingRating() {
        var original = candidate("TMDB", "42", "movie", "Zero Rating", "2026", 0, 0, "popular", "popular", 1, now, null).item();
        var zero = new TrendItemPayload(original.title(), original.originalTitle(), original.description(), original.year(),
                original.publishedAt(), BigDecimal.ZERO, original.posterUrl(), original.tmdbId(), original.doubanId(),
                original.imdbId(), original.type(), original.signal());

        UUID id = resolve(TrendCatalogCandidate.from(zero)).id();

        assertThat(jdbc.queryForObject("select score from catalog_entries where id=?", BigDecimal.class, id)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void episodeInventoryUsesActualLinksAndNumberingRatherThanMirrorCount() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        UUID playlist = bindPlaylist(existing, 2, false);
        for (int mirror = 0; mirror < 8; mirror++) episode(playlist, "S02E05", "https://media.example/mirror-" + mirror, null);
        var current = candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 5, "popular", "popular", 1, now, null);
        var next = candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 6, "popular", "popular", 1, now, null);

        assertThat(resolve(current).id()).isEqualTo(existing);
        assertThat(count("catalog_resource_discovery_jobs")).isZero();
        resolve(next);

        assertThat(jdbc.queryForObject("select available_episode from catalog_resource_inventory(?,2)", Integer.class, existing)).isEqualTo(5);
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select target_episode from catalog_resource_discovery_jobs", Integer.class)).isEqualTo(6);
    }

    @Test
    void laterEpisodeCannotSatisfyEarlierTargetAndExactEpisodeDoes() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        UUID playlist = bindPlaylist(existing, 2, false);
        episode(playlist, "S02E14", "https://media.example/14", null);
        resolve(candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 12, "popular", "popular", 1, now, null));
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);

        UUID anotherId = seedVideo("Another Show", "2026", "series", 2, "43");
        UUID anotherPlaylist = bindPlaylist(anotherId, 2, false);
        episode(anotherPlaylist, "S02E14", "https://media.example/14", null);
        episode(anotherPlaylist, "S02E12", "https://media.example/12", null);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 2, 12)", Boolean.class, anotherId)).isTrue();
    }

    @Test
    void approvedMagnetSatisfiesOnlyPublicAdultSafeWholeWorkRequest() {
        UUID existing = seedVideo("Known Film", "2026", "movie", 0, "42");
        jdbc.update("""
                insert into catalog_magnet_links(id,catalog_entry_id,status,provider_code,info_hash,magnet_uri,
                    title,matched_external_id_type,matched_external_id_value)
                values(uuidv7(),?,'CANDIDATE','fixture',repeat('f',40),'magnet:?xt=urn:btih:' || repeat('f',40),
                    'Fixture','TMDB','123')
                """, existing);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, existing)).isFalse();
        jdbc.update("update catalog_magnet_links set status = 'APPROVED'");
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, existing)).isTrue();
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 12)", Boolean.class, existing)).isFalse();
        jdbc.update("update catalog_entries set content_visibility = 'HIDDEN' where id = ?", existing);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, existing)).isFalse();
        jdbc.update("update catalog_entries set content_visibility = 'PUBLIC', adult_restricted = true where id = ?", existing);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, existing)).isFalse();
    }

    @Test
    void invalidLinksOtherSeasonsAndAdultResourcesDoNotHideARealEpisodeGap() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        episode(bindPlaylist(existing, 1, false), "S01E99", "https://media.example/wrong-season", null);
        UUID clean = bindPlaylist(existing, 2, false);
        episode(clean, "S02E99", "javascript:alert(1)", null);
        episode(clean, "S02E99", "   ", null);
        episode(clean, "S01E99", "https://media.example/wrong-label", null);
        episode(clean, "S02E99", "/relative-without-domain", null);
        episode(bindPlaylist(existing, 2, true), "S02E99", "https://media.example/adult", null);

        resolve(candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 5, "popular", "popular", 1, now, null));

        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)", Boolean.class, existing)).isFalse();
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
    }

    @Test
    void restrictedOrInvalidUnifiedAssessmentsCannotExposeRecordedResources() {
        UUID existing = seedVideo("Restricted work", "2026", "series", 2, "42");
        episode(bindPlaylist(existing, 2, false), "S02E05", "https://media.example/episode", null);
        for (String assessment : List.of("{\"adultRestricted\":true}", "{\"level\":\"ADULT\"}",
                "{\"adultRestricted\":\"unknown\"}")) {
            jdbc.update("update catalog_entries set adult_assessment=?::jsonb where id=?", assessment, existing);
            assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)",
                    Boolean.class, existing)).isFalse();
        }
        jdbc.update("update catalog_entries set adult_assessment='{}',adult_restricted=true where id=?", existing);
        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)",
                Boolean.class, existing)).isFalse();
        jdbc.update("update catalog_entries set adult_restricted=false where id=?", existing);
        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)",
                Boolean.class, existing)).isTrue();
    }

    @Test
    void sourceBoundRelativeUrlCanCountAsRecordedButUnknownEpisodeNameCannotSatisfyAnEpisodeTarget() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        episode(bindPlaylist(existing, 2, false), "正片", "/watch/valid", UUID.randomUUID());

        resolve(candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 5, "popular", "popular", 1, now, null));

        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)", Boolean.class, existing)).isTrue();
        assertThat(jdbc.queryForObject("select available_episode from catalog_resource_inventory(?,2)", Integer.class, existing)).isNull();
        assertThat(count("catalog_resource_discovery_jobs")).isEqualTo(1);
    }

    @Test
    void firstNanosecondSamplePersistsAtPostgresPrecisionAndReplayCannotReplaceIt() {
        Instant sampledAt = now.plusNanos(876);
        var candidate = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 1, sampledAt, null);
        UUID id = resolve(candidate).id();
        var board = board(candidate, "SUCCESS", sampledAt);

        assertThat(save(board, List.of(observation(candidate, id)))).isTrue();
        assertThat(save(board, List.of())).isFalse();

        assertThat(jdbc.queryForObject("select last_success_at from provider_ranking_boards", Timestamp.class).toInstant()).isEqualTo(now);
        assertThat(count("provider_ranking_observations")).isEqualTo(1);
    }

    @Test
    void failedSourceRetainsLastGoodSnapshotAndAnOlderSuccessCannotOverwriteIt() {
        Instant successfulAt = now.minusSeconds(3600);
        var candidate = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 1, successfulAt, null);
        UUID id = resolve(candidate).id();
        assertThat(save(board(candidate, "SUCCESS", successfulAt), List.of(observation(candidate, id)))).isTrue();
        assertThat(save(board(candidate, "FAILED", now), List.of())).isTrue();
        var old = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 99, successfulAt.minusSeconds(60), null);

        assertThat(save(board(old, "SUCCESS", old.item().signal().observedAt()), List.of(observation(old, id)))).isFalse();

        assertThat(jdbc.queryForObject("select status from provider_ranking_boards", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_success_at from provider_ranking_boards", Timestamp.class).toInstant()).isEqualTo(successfulAt);
        assertThat(jdbc.queryForObject("select item_count from provider_ranking_boards", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select position from provider_ranking_observations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select heat from catalog_entry_rankings", BigDecimal.class)).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void rotatingDailyBoardDoesNotDeleteYesterdayBroadcastEvent() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant sampledAt = now.minusSeconds(3600);
        var candidate = candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 12, "airing-today", "event", null, sampledAt, yesterday);
        UUID id = resolve(candidate).id();
        save(board(candidate, "SUCCESS", sampledAt), List.of(observation(candidate, id)));

        save(board(candidate, "SUCCESS", now), List.of());

        assertThat(count("provider_ranking_observations")).isZero();
        assertThat(count("catalog_release_events")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select event_date from catalog_entry_recent_release_events", java.sql.Date.class).toLocalDate()).isEqualTo(yesterday);
        assertThat(jdbc.queryForObject("select episode from catalog_entry_recent_release_events", Integer.class)).isEqualTo(12);
    }

    @Test
    void sameProviderDayAndWeekAreNotDoubleCountedAndAnotherProviderAddsCorroboration() {
        var day = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 1, now, null);
        UUID id = resolve(day).id();
        save(board(day, "SUCCESS", now), List.of(observation(day, id)));
        BigDecimal oneBoard = jdbc.queryForObject("select heat from catalog_entry_rankings", BigDecimal.class);
        var week = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-week", "week", 5, now, null);
        save(board(week, "SUCCESS", now), List.of(observation(week, id)));

        BigDecimal twoBoards = jdbc.queryForObject("select heat from catalog_entry_rankings", BigDecimal.class);
        assertThat(twoBoards).isCloseTo(oneBoard, org.assertj.core.data.Offset.offset(new BigDecimal("0.0001")));
        var douban = candidate("Douban", "123", "movie", "Known Film", "2026", 0, 0, "chart", "chart", 2, now, null);
        assertThat(resolve(douban).id()).isEqualTo(id);
        save(board(douban, "SUCCESS", now), List.of(observation(douban, id)));

        assertThat(jdbc.queryForObject("select heat from catalog_entry_rankings", BigDecimal.class))
                .isGreaterThan(oneBoard.multiply(new BigDecimal("1.5")));
        assertThat(jdbc.queryForObject("select sources from catalog_entry_rankings", String.class)).isEqualTo("Douban,TMDB");
    }

    @Test
    void staleSamplesExpireAndLegacyOrEventObservationsNeverAcquirePopularityHeat() {
        var stale = candidate("TMDB", "42", "movie", "Old Film", "2026", 0, 0, "trending-day", "day", 1, now.minusSeconds(8 * 86400L), null);
        UUID id = resolve(stale).id();
        save(board(stale, "SUCCESS", stale.item().signal().observedAt()), List.of(observation(stale, id)));
        assertThat(count("catalog_entry_rankings")).isZero();
        var legacy = candidate("TMDB", "42", "movie", "Old Film", "2026", 0, 0, "legacy", "legacy", 1, now, null);
        save(board(legacy, "SUCCESS", now), List.of(observation(legacy, id)));
        assertThat(jdbc.queryForObject("select heat from catalog_entry_rankings", BigDecimal.class)).isEqualByComparingTo(BigDecimal.ZERO);
        var event = candidate("TMDB", "42", "movie", "Old Film", "2026", 0, 0, "recent-release", "event", null, now, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        save(board(event, "SUCCESS", now), List.of(observation(event, id)));
        assertThat(jdbc.queryForObject("select heat from catalog_entry_rankings", BigDecimal.class)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void failedSnapshotTransactionCannotPartiallyReplaceLastGoodRows() {
        var old = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 1, now.minusSeconds(60), null);
        UUID id = resolve(old).id();
        save(board(old, "SUCCESS", old.item().signal().observedAt()), List.of(observation(old, id)));
        var fresh = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 2, now, null);
        var absent = candidate("TMDB", "99", "movie", "Absent Work", "2026", 0, 0, "trending-day", "day", 3, now, null);

        assertThatThrownBy(() -> save(board(fresh, "SUCCESS", now),
                List.of(observation(fresh, id), observation(absent, UUID.randomUUID()))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("provider_ranking_observations")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select position from provider_ranking_observations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select last_success_at from provider_ranking_boards", Timestamp.class).toInstant()).isEqualTo(old.item().signal().observedAt());
    }

    @Test
    void concurrentSnapshotWritersLeaveTheNewestBoardRegardlessOfLockOrder() throws Exception {
        var old = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 1, now.minusSeconds(60), null);
        UUID id = resolve(old).id();
        var fresh = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 2, now, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<Boolean>>of(
                    () -> save(board(old, "SUCCESS", old.item().signal().observedAt()), List.of(observation(old, id))),
                    () -> save(board(fresh, "SUCCESS", now), List.of(observation(fresh, id)))), 10, TimeUnit.SECONDS);
            for (var result : results) result.get();
        }

        assertThat(jdbc.queryForObject("select position from provider_ranking_observations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select last_success_at from provider_ranking_boards", Timestamp.class).toInstant()).isEqualTo(now);
    }

    private TrendCatalogEntryService.Resolution resolve(TrendCatalogCandidate candidate) {
        return transactions.execute(status -> entries.resolve(candidate));
    }

    private boolean save(TrendBoardPayload board, List<TrendSnapshotRepository.Observation> observations) {
        return Boolean.TRUE.equals(transactions.execute(status -> snapshots.save(board, observations)));
    }

    private static TrendSnapshotRepository.Observation observation(TrendCatalogCandidate candidate, UUID id) {
        return new TrendSnapshotRepository.Observation(candidate, id);
    }

    private static TrendCatalogCandidate candidate(String provider, String id, String type, String title, String year,
            int season, int episode, String board, String window, Integer rank, Instant sampledAt, LocalDate eventDate) {
        return TrendCatalogCandidate.from(new TrendItemPayload(title, null, null, year, null, null, null,
                "TMDB".equals(provider) ? id : null, "Douban".equals(provider) ? id : null,
                "IMDb".equals(provider) ? id : null, type,
                new TrendSignalPayload(provider, board, type, id, rank, window, null, null, sampledAt,
                        eventDate, eventDate == null ? null : "tv".equals(type) ? "EPISODE_AIR" : "RELEASE",
                        season == 0 ? null : season, episode == 0 ? null : episode, null,
                        "https://catalog.example/" + type + "/" + id, "movie".equals(type) ? "movie" : "series")));
    }

    private static TrendBoardPayload board(TrendCatalogCandidate candidate, String status, Instant sampledAt) {
        var signal = candidate.item().signal();
        return new TrendBoardPayload(candidate.provider(), signal.board(), candidate.mediaType(), signal.window(),
                sampledAt, status, "FAILED".equals(status) ? "HTTP_503" : null, List.of());
    }

    private UUID seedVideo(String title, String year, String category, Integer season, String tmdbId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into catalog_entries(id,title,year,category_code,season,tmdb_id,created_at,updated_at) values(?,?,?,?,?,?,now(),now())",
                id, title, year, category, season, tmdbId);
        return id;
    }

    private UUID bindPlaylist(UUID videoId, Integer season, boolean adultRestricted) {
        UUID raw = UUID.randomUUID();
        UUID playlist = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        jdbc.update("""
                insert into collection_sources(id,adult_restricted,created_at,updated_at,name,base_url,list_path,detail_path)
                values(?,?,now(),now(),?,'https://example.invalid','/list','/detail')
                """, source, adultRestricted, source.toString());
        jdbc.update("""
                insert into source_records(id,season,collection_source_id,created_at,updated_at,source_vid,source_hash,
                    title,enrichment_status,normalization_status)
                values(?,?,?,now(),now(),?,?,'Fixture','PENDING','PENDING')
                """, raw, season, source, raw.toString(), raw.toString());
        jdbc.update("insert into catalog_source_bindings(source_record_id,catalog_entry_id) values(?,?)", raw, videoId);
        jdbc.update("""
                insert into source_playlists(id,source_record_id,created_at,updated_at,name)
                values(?,?,now(),now(),'Main')
                """, playlist, raw);
        return playlist;
    }

    private void episode(UUID playlist, String name, String url, UUID sourceDomain) {
        UUID raw = jdbc.queryForObject("select source_record_id from source_playlists where id=?", UUID.class, playlist);
        if (sourceDomain != null) {
            jdbc.update("""
                    insert into source_origins(id,created_at,updated_at,domain_hash,domain_value)
                    values(?,now(),now(),?,'https://media.example')
                    """, sourceDomain, sourceDomain.toString());
        }
        jdbc.update("""
                insert into source_playback_items(id,playlist_id,name,url,source_origin_id,source_record_id,created_at,updated_at)
                values(?,?,?,?,?,?,now(),now())
                """, UUID.randomUUID(), playlist, name, url, sourceDomain, raw);
        jdbc.query("select reconcile_catalog_playback(?)", (rs, row) -> null, raw);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("PostgreSQL integration fixture is required: " + key);
        return value;
    }
}
