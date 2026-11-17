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
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real migration and PostgreSQL transaction/locking semantics; every test owns one isolated schema. */
class TrendCatalogPostgresTest {

    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private TransactionTemplate transactions;
    private TrendCatalogEntryService entries;
    private TrendSnapshotRepository snapshots;
    private ContentCommandPublisher publisher;
    private String schema;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        String url = requiredEnvironment("IRCS_TEST_POSTGRES_URL");
        String user = requiredEnvironment("IRCS_TEST_POSTGRES_USER");
        String password = requiredEnvironment("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "trend_catalog_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(6);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(5000);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
        transactions.setTimeout(10);
        now = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(60);
        createSourceTables();
        String migration = Files.readString(migrationPath());
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(migration);
            }
            return null;
        });
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
        if (admin != null && schema != null) admin.execute("drop schema if exists " + schema + " cascade");
    }

    @Test
    void sameNumericTmdbIdDoesNotMergeMovieAndTv() {
        var movie = resolve(candidate("TMDB", "42", "movie", "Same Title", "2026", 0, 0, "trending-day", "day", 1, now, null));
        var tv = resolve(candidate("TMDB", "42", "tv", "Same Title", "2026", 1, 1, "trending-day", "day", 1, now, null));

        assertThat(movie.id()).isNotEqualTo(tv.id());
        assertThat(count("unified_videos")).isEqualTo(2);
        assertThat(count("external_catalog_identities")).isEqualTo(2);
        assertThat(count("catalog_discovery_requests")).isEqualTo(2);
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
        assertThat(count("unified_videos")).isEqualTo(3);
    }

    @Test
    void exactTitleYearAndMediaTypeCanBindAnExistingWorkWithoutChangingManualFields() {
        UUID existing = seedVideo("Known Film", "2026", "movie", null, null);
        jdbc.update("update unified_videos set score=9.3, locked_fields='[\"title\",\"score\"]'::jsonb, metadata_status='SYNCED' where id=?", existing);

        var result = resolve(candidate("TMDB", "42", "movie", "KNOWN FILM", "2026", 0, 0, "popular", "popular", 1, now, null));

        assertThat(result.id()).isEqualTo(existing);
        assertThat(result.created()).isFalse();
        assertThat(jdbc.queryForObject("select title from unified_videos where id=?", String.class, existing)).isEqualTo("Known Film");
        assertThat(jdbc.queryForObject("select score from unified_videos where id=?", BigDecimal.class, existing)).isEqualByComparingTo("9.3");
        assertThat(jdbc.queryForObject("select metadata_status from unified_videos where id=?", String.class, existing)).isEqualTo("SYNCED");
        assertThat(count("external_catalog_identities")).isEqualTo(1);
        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
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
        assertThat(jdbc.queryForObject("select tmdb_id from unified_videos where id=?", String.class, conflicting)).isEqualTo("99");
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
        assertThat(count("unified_videos")).isEqualTo(1);
        assertThat(count("external_catalog_identities")).isEqualTo(1);
        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select score from unified_videos", BigDecimal.class)).isNull();
        assertThat(jdbc.queryForObject("select metadata_status from unified_videos", String.class)).isEqualTo("DIRTY");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void missingResourcesForAnExistingWorkAreDurableAndRecentSubmissionIsNotRescheduled() {
        UUID existing = seedVideo("Known Film", "2026", "movie", null, "42");
        var candidate = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "popular", "popular", 1, now, null);
        assertThat(resolve(candidate).id()).isEqualTo(existing);
        jdbc.update("update catalog_discovery_requests set status='SUBMITTED', submitted_at=now(), attempts=3, next_attempt_at=now()+interval '1 hour'");

        resolve(candidate);

        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from catalog_discovery_requests", String.class)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select attempts from catalog_discovery_requests", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select next_attempt_at>now() from catalog_discovery_requests", Boolean.class)).isTrue();
    }

    @Test
    void missingResourcesAfterSubmissionCooldownAreRequeuedWithoutCreatingAnotherRequest() {
        var candidate = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "popular", "popular", 1, now, null);
        resolve(candidate);
        jdbc.update("update catalog_discovery_requests set status='SUBMITTED', submitted_at=now()-interval '7 hours', attempts=3, next_attempt_at=now()+interval '1 hour'");

        resolve(candidate);

        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from catalog_discovery_requests", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select next_attempt_at<=now() from catalog_discovery_requests", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_discovery_requests", Integer.class)).isEqualTo(3);
    }

    @Test
    void aRealZeroRatingRemainsDifferentFromMissingRating() {
        var original = candidate("TMDB", "42", "movie", "Zero Rating", "2026", 0, 0, "popular", "popular", 1, now, null).item();
        var zero = new TrendItemPayload(original.title(), original.originalTitle(), original.description(), original.year(),
                original.publishedAt(), BigDecimal.ZERO, original.posterUrl(), original.tmdbId(), original.doubanId(),
                original.imdbId(), original.type(), original.signal());

        UUID id = resolve(TrendCatalogCandidate.from(zero)).id();

        assertThat(jdbc.queryForObject("select score from unified_videos where id=?", BigDecimal.class, id)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void episodeInventoryUsesActualLinksAndNumberingRatherThanMirrorCount() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        UUID playlist = bindPlaylist(existing, 2, false, "{}");
        for (int mirror = 0; mirror < 8; mirror++) episode(playlist, "S02E05", "https://media.example/mirror-" + mirror, null);
        var current = candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 5, "popular", "popular", 1, now, null);
        var next = candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 6, "popular", "popular", 1, now, null);

        assertThat(resolve(current).id()).isEqualTo(existing);
        assertThat(count("catalog_discovery_requests")).isZero();
        resolve(next);

        assertThat(jdbc.queryForObject("select available_episode from catalog_resource_inventory(?,2)", Integer.class, existing)).isEqualTo(5);
        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select target_episode from catalog_discovery_requests", Integer.class)).isEqualTo(6);
    }

    @Test
    void invalidLinksOtherSeasonsAndAdultResourcesDoNotHideARealEpisodeGap() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        episode(bindPlaylist(existing, 1, false, "{}"), "S01E99", "https://media.example/wrong-season", null);
        UUID clean = bindPlaylist(existing, 2, false, "{}");
        episode(clean, "S02E99", "javascript:alert(1)", null);
        episode(clean, "S02E99", "   ", null);
        episode(clean, "S01E99", "https://media.example/wrong-label", null);
        episode(clean, "S02E99", "/relative-without-domain", null);
        episode(bindPlaylist(existing, 2, true, "{}"), "S02E99", "https://media.example/adult", null);
        episode(bindPlaylist(existing, 2, false, "{\"adultRestricted\":true}"), "S02E99", "https://media.example/adult-flag", null);
        episode(bindPlaylist(existing, 2, false, "{\"level\":\"ADULT\"}"), "S02E99", "https://media.example/adult-level", null);
        episode(bindPlaylist(existing, 2, false, "{\"adultRestricted\":\"unknown\"}"), "S02E99", "https://media.example/invalid-adult-flag", null);

        resolve(candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 5, "popular", "popular", 1, now, null));

        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)", Boolean.class, existing)).isFalse();
        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
    }

    @Test
    void sourceBoundRelativeUrlCanCountAsRecordedButUnknownEpisodeNameCannotSatisfyAnEpisodeTarget() {
        UUID existing = seedVideo("Known Show", "2026", "series", 2, "42");
        episode(bindPlaylist(existing, 2, false, "{}"), "正片", "/watch/valid", UUID.randomUUID());

        resolve(candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 5, "popular", "popular", 1, now, null));

        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?,2)", Boolean.class, existing)).isTrue();
        assertThat(jdbc.queryForObject("select available_episode from catalog_resource_inventory(?,2)", Integer.class, existing)).isNull();
        assertThat(count("catalog_discovery_requests")).isEqualTo(1);
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

        assertThat(jdbc.queryForObject("select status from external_trend_boards", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_success_at from external_trend_boards", Timestamp.class).toInstant()).isEqualTo(successfulAt);
        assertThat(jdbc.queryForObject("select item_count from external_trend_boards", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select position from external_trend_observations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select heat from external_catalog_rankings", BigDecimal.class)).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void rotatingDailyBoardDoesNotDeleteYesterdayBroadcastEvent() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant sampledAt = now.minusSeconds(3600);
        var candidate = candidate("TMDB", "42", "tv", "Known Show", "2026", 2, 12, "airing-today", "event", null, sampledAt, yesterday);
        UUID id = resolve(candidate).id();
        save(board(candidate, "SUCCESS", sampledAt), List.of(observation(candidate, id)));

        save(board(candidate, "SUCCESS", now), List.of());

        assertThat(count("external_trend_observations")).isZero();
        assertThat(count("external_catalog_events")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select event_date from external_catalog_recent_events", java.sql.Date.class).toLocalDate()).isEqualTo(yesterday);
        assertThat(jdbc.queryForObject("select episode from external_catalog_recent_events", Integer.class)).isEqualTo(12);
    }

    @Test
    void sameProviderDayAndWeekAreNotDoubleCountedAndAnotherProviderAddsCorroboration() {
        var day = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-day", "day", 1, now, null);
        UUID id = resolve(day).id();
        save(board(day, "SUCCESS", now), List.of(observation(day, id)));
        BigDecimal oneBoard = jdbc.queryForObject("select heat from external_catalog_rankings", BigDecimal.class);
        var week = candidate("TMDB", "42", "movie", "Known Film", "2026", 0, 0, "trending-week", "week", 5, now, null);
        save(board(week, "SUCCESS", now), List.of(observation(week, id)));

        BigDecimal twoBoards = jdbc.queryForObject("select heat from external_catalog_rankings", BigDecimal.class);
        assertThat(twoBoards).isCloseTo(oneBoard, org.assertj.core.data.Offset.offset(new BigDecimal("0.0001")));
        var douban = candidate("Douban", "123", "movie", "Known Film", "2026", 0, 0, "chart", "chart", 2, now, null);
        assertThat(resolve(douban).id()).isEqualTo(id);
        save(board(douban, "SUCCESS", now), List.of(observation(douban, id)));

        assertThat(jdbc.queryForObject("select heat from external_catalog_rankings", BigDecimal.class))
                .isGreaterThan(oneBoard.multiply(new BigDecimal("1.5")));
        assertThat(jdbc.queryForObject("select sources from external_catalog_rankings", String.class)).isEqualTo("Douban,TMDB");
    }

    @Test
    void staleSamplesExpireAndLegacyOrEventObservationsNeverAcquirePopularityHeat() {
        var stale = candidate("TMDB", "42", "movie", "Old Film", "2026", 0, 0, "trending-day", "day", 1, now.minusSeconds(8 * 86400L), null);
        UUID id = resolve(stale).id();
        save(board(stale, "SUCCESS", stale.item().signal().observedAt()), List.of(observation(stale, id)));
        assertThat(count("external_catalog_rankings")).isZero();
        var legacy = candidate("TMDB", "42", "movie", "Old Film", "2026", 0, 0, "legacy", "legacy", 1, now, null);
        save(board(legacy, "SUCCESS", now), List.of(observation(legacy, id)));
        assertThat(jdbc.queryForObject("select heat from external_catalog_rankings", BigDecimal.class)).isEqualByComparingTo(BigDecimal.ZERO);
        var event = candidate("TMDB", "42", "movie", "Old Film", "2026", 0, 0, "recent-release", "event", null, now, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        save(board(event, "SUCCESS", now), List.of(observation(event, id)));
        assertThat(jdbc.queryForObject("select heat from external_catalog_rankings", BigDecimal.class)).isEqualByComparingTo(BigDecimal.ZERO);
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

        assertThat(count("external_trend_observations")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select position from external_trend_observations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select last_success_at from external_trend_boards", Timestamp.class).toInstant()).isEqualTo(old.item().signal().observedAt());
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

        assertThat(jdbc.queryForObject("select position from external_trend_observations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select last_success_at from external_trend_boards", Timestamp.class).toInstant()).isEqualTo(now);
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
        jdbc.update("insert into unified_videos(id,title,year,category_code,season,tmdb_id) values(?,?,?,?,?,?)",
                id, title, year, category, season, tmdbId);
        return id;
    }

    private UUID bindPlaylist(UUID videoId, Integer season, boolean adultRestricted, String adultAssessment) {
        UUID raw = UUID.randomUUID();
        UUID playlist = UUID.randomUUID();
        jdbc.update("insert into raw_videos(id,season,adult_restricted,adult_assessment) values(?,?,?,?::jsonb)", raw, season, adultRestricted, adultAssessment);
        jdbc.update("insert into raw_video_unified_video(raw_video_id,unified_video_id) values(?,?)", raw, videoId);
        jdbc.update("insert into playlists(id,video_id) values(?,?)", playlist, raw);
        return playlist;
    }

    private void episode(UUID playlist, String name, String url, UUID sourceDomain) {
        jdbc.update("insert into episodes(id,playlist_id,name,url,source_domain_id) values(?,?,?,?,?)", UUID.randomUUID(), playlist, name, url, sourceDomain);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void createSourceTables() {
        jdbc.execute("""
                create table unified_videos(
                    id uuid primary key, created_at timestamptz default now(), updated_at timestamptz default now(),
                    version bigint default 0, title varchar(255) not null, alias_title text, season integer,
                    cover_image_id uuid, description text, year varchar(10), score numeric, published_at date,
                    douban_id varchar(255), tmdb_id varchar(255), imdb_id varchar(255), rotten_tomatoes_id varchar(255),
                    locked_fields jsonb default '[]', category_code varchar(50), content_visibility varchar(30) default 'PUBLIC',
                    metadata_status varchar(30) default 'DIRTY', adult_restricted boolean default false, adult_assessment jsonb default '{}',
                    actor_names jsonb default '[]', director_names jsonb default '[]', genre_codes jsonb default '[]'
                );
                create table raw_videos(id uuid primary key, season integer, adult_restricted boolean default false, adult_assessment jsonb default '{}');
                create table raw_video_unified_video(raw_video_id uuid not null, unified_video_id uuid not null);
                create table playlists(id uuid primary key,video_id uuid not null);
                create table episodes(id uuid primary key,playlist_id uuid not null,name text,url text,source_domain_id uuid);
                """);
    }

    private static Path migrationPath() {
        Path relative = Path.of("platform/ircs-migrator/src/main/resources/db/changelog/2026/11/16-02-global-rankings-and-discovery.sql");
        for (Path parent = Path.of(System.getProperty("user.dir")).toAbsolutePath(); parent != null; parent = parent.getParent()) {
            Path candidate = parent.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Real global-rankings migration was not found in checkout");
    }

    private static String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("PostgreSQL integration fixture is required: " + key);
        return value;
    }
}
