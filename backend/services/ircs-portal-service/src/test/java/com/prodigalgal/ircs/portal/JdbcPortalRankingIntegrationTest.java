package com.prodigalgal.ircs.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.cache.CacheRegistry;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class JdbcPortalRankingIntegrationTest {

    @Test
    void releaseDatesUseTheSiteCalendarEvenOnUtcOrAmericanDatabaseSessions() {
        var transactions=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(pool));
        transactions.executeWithoutResult(status->{
            jdbc.execute("set local time zone 'Pacific/Honolulu'");
            UUID aired=work("北京时间已播出","series",7.0);
            UUID tomorrow=work("北京时间明天播出","series",7.0);
            event(aired,today,2,12);
            event(tomorrow,today.plusDays(1),2,13);
            resource(aired, 2, 12);
            assertThat(repository.findRecentlyUpdated(publicPrincipal,20)).extracting(PortalMovieCard::id).containsExactly(aired);
        });
    }

    private final IrcsRequestPrincipal publicPrincipal = IrcsRequestPrincipal.publicPrincipal();
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private String schema;
    private JdbcPortalRepository repository;
    private Instant sampleTime;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "portal_ranking_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(3_000);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        createSchema();
        sampleTime = jdbc.queryForObject("select current_timestamp", Timestamp.class).toInstant();
        today = jdbc.queryForObject("select (now() at time zone 'Asia/Shanghai')::date", Date.class).toLocalDate();
        PortalStandardNameCache names = mock(PortalStandardNameCache.class);
        when(names.resolveGenreNames(any())).thenReturn(List.of());
        when(names.resolveAreaNames(any())).thenReturn(List.of());
        when(names.resolveLanguageNames(any())).thenReturn(List.of());
        repository = new JdbcPortalRepository(new NamedParameterJdbcTemplate(pool),
                mock(PortalCoverImageUrlResolver.class), JsonMapper.builder().findAndAddModules().build(), names);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) pool.close();
        if (admin != null && schema != null) admin.execute("drop schema if exists " + schema + " cascade");
    }

    @Test
    void legacyHighRatingAndRecentlyTouchedRowsAreNotGlobalTrendingEvidence() {
        UUID legacy = work("Old perfect score", "movie", 10.0);
        jdbc.update("update unified_videos set published_at = current_date - 1000, last_trend_at = now(), "
                + "updated_at = now() where id = ?", legacy);
        UUID current = work("Currently trending", "movie", 6.0);
        observation(current, "TMDB", "trending-day", "day", 3, sampleTime);

        List<PortalMovieCard> ranked = repository.findTrending(publicPrincipal, 20);

        assertThat(ranked).extracting(PortalMovieCard::id).containsExactly(current);
        assertThat(ranked.getFirst().ranking().sources()).containsExactly("TMDB");
    }

    @Test
    void dayAndWeekFromOneProviderContributeAtMostOneHeatSignal() {
        UUID single = work("Single board", "movie", 8.0);
        UUID repeated = work("Two boards", "movie", 8.0);
        observation(single, "TMDB", "trending-day", "day", 1, sampleTime);
        observation(repeated, "TMDB", "trending-day", "day", 1, sampleTime);
        observation(repeated, "TMDB", "trending-week", "week", 1, sampleTime);

        List<PortalMovieCard> cards = repository.findTrending(publicPrincipal, 20);
        double singleHeat = card(cards, single).ranking().heat().doubleValue();

        assertThat(card(cards, repeated).ranking().heat().doubleValue()).isCloseTo(singleHeat, offset(0.000000000001));
        assertThat(card(cards, repeated).ranking().sources()).containsExactly("TMDB");
    }

    @Test
    void independentProviderAgreementRaisesHeatInsteadOfAddingIncompatibleRatings() {
        UUID single = work("One source", "movie", 10.0);
        UUID agreement = work("Two independent sources", "movie", 5.0);
        observation(single, "TMDB", "trending-day", "day", 1, sampleTime);
        observation(agreement, "TMDB", "trending-day", "day", 1, sampleTime);
        observation(agreement, "Douban", "chart", "chart", 1, sampleTime);

        List<PortalMovieCard> cards = repository.findTrending(publicPrincipal, 20);

        assertThat(cards).extracting(PortalMovieCard::id).containsExactly(agreement, single);
        assertThat(card(cards, agreement).ranking().sources()).containsExactly("Douban", "TMDB");
        assertThat(card(cards, agreement).ranking().heat()).isGreaterThan(card(cards, single).ranking().heat());
    }

    @Test
    void timeDecayStalenessAndSevenDayExpiryAreAppliedToActualSourceSamples() {
        UUID fresh = work("Fresh lower rank", "movie", 5.0);
        UUID aging = work("Old number one", "movie", 10.0);
        UUID expired = work("Expired number one", "movie", 10.0);
        UUID future = work("Invalid future sample", "movie", 10.0);
        observation(fresh, "TMDB", "trending-day", "day", 5, sampleTime);
        observation(aging, "TMDB", "trending-day", "day", 1, sampleTime.minus(Duration.ofDays(3)));
        observation(expired, "TMDB", "trending-day", "day", 1, sampleTime.minus(Duration.ofDays(8)));
        observation(future, "TMDB", "trending-day", "day", 1, sampleTime.plus(Duration.ofMinutes(10)));

        List<PortalMovieCard> cards = repository.findTrending(publicPrincipal, 20);

        assertThat(cards).extracting(PortalMovieCard::id).containsExactly(fresh, aging);
        assertThat(card(cards, fresh).ranking().stale()).isFalse();
        assertThat(card(cards, aging).ranking().stale()).isTrue();
    }

    @Test
    void recentlyUpdatedOrdersRealEventDatesAndExcludesFutureDatesAndMetadataTouches() {
        UUID yesterday = work("Yesterday episode", "series", 10.0);
        UUID current = work("Today episode", "series", 5.0);
        UUID future = work("Tomorrow episode", "series", 10.0);
        UUID merelyTouched = work("Old metadata repaired today", "movie", 10.0);
        event(yesterday, today.minusDays(1), 2, 11);
        event(current, today, 2, 12);
        event(future, today.plusDays(1), 2, 13);
        resource(yesterday, 2, 11);
        resource(current, 2, 12);
        jdbc.update("update unified_videos set updated_at = now() - interval '30 days' where id = ?", current);
        jdbc.update("update unified_videos set updated_at = now(), published_at = current_date where id in (?, ?)",
                yesterday, merelyTouched);

        List<PortalMovieCard> cards = repository.findRecentlyUpdated(publicPrincipal, 20);

        assertThat(cards).extracting(PortalMovieCard::id).containsExactly(current, yesterday);
        assertThat(cards.getFirst().ranking().eventDate()).isEqualTo(today);
        assertThat(cards.getFirst().ranking().eventType()).isEqualTo("EPISODE_AIR");
        assertThat(cards.getFirst().ranking().latestSeason()).isEqualTo(2);
        assertThat(cards.getFirst().ranking().latestEpisode()).isEqualTo(12);
    }

    @Test
    void recentRailFiltersMissingOrWrongEpisodeResourcesBeforeApplyingLimit() {
        UUID olderAvailable = work("Older available episode", "series", 8.0);
        UUID newerPlaceholder = work("Newer external placeholder", "series", 8.0);
        UUID wrongEpisodeOnly = work("Wrong episode only", "series", 8.0);
        event(olderAvailable, today.minusDays(1), 2, 11);
        resource(olderAvailable, 2, 11);
        resource(olderAvailable, 2, 14);
        event(newerPlaceholder, today, 2, 12);
        event(wrongEpisodeOnly, today, 2, 13);
        resource(wrongEpisodeOnly, 2, 14);

        var card = repository.findRecentlyUpdated(publicPrincipal, 1).getFirst();
        assertThat(card.id()).isEqualTo(olderAvailable);
        assertThat(card.ranking().availableEpisode()).isEqualTo(14);
        assertThat(card.ranking().latestEpisode()).isEqualTo(11);
    }

    @Test
    void yesterdayEventSurvivesDailyBoardRotationWithoutPretendingItsSampleIsFresh() {
        UUID yesterday = work("Retained broadcast", "series", 8.0);
        event(yesterday, today.minusDays(1), 2, 12);
        resource(yesterday, 2, 12);
        jdbc.update("update external_catalog_events set observed_at = now() - interval '1 day'");
        observation(yesterday, "TMDB", "airing-today", "event", 1, sampleTime.minus(Duration.ofDays(1)));
        jdbc.update("delete from external_trend_observations");

        List<PortalMovieCard> cards = repository.findRecentlyUpdated(publicPrincipal, 20);

        assertThat(cards).extracting(PortalMovieCard::id).containsExactly(yesterday);
        assertThat(cards.getFirst().ranking().sources()).containsExactly("TMDB");
        assertThat(cards.getFirst().ranking().stale()).isTrue();
        assertThat(repository.findTrending(publicPrincipal, 20)).isEmpty();
    }

    @Test
    void latestEpisodeSamplingAndTrendingSamplingDoNotOverwriteEachOthersFreshness() {
        UUID work = work("Fresh episode with old heat sample", "series", 8.0);
        observation(work, "TMDB", "trending-day", "day", 1, sampleTime.minus(Duration.ofHours(8)));
        event(work, today, 2, 12);
        resource(work, 2, 12);

        PortalRankingInfo trending = repository.findTrending(publicPrincipal, 20).getFirst().ranking();
        PortalRankingInfo recent = repository.findRecentlyUpdated(publicPrincipal, 20).getFirst().ranking();

        assertThat(trending.stale()).isTrue();
        assertThat(recent.stale()).isFalse();
        assertThat(recent.observedAt()).isAfter(trending.observedAt());
    }

    @Test
    void categoryDiversityDoesNotLetOneCategoryOccupyEveryLeadingPosition() {
        UUID movie = work("Movie leader", "movie", 8.0);
        UUID secondMovie = work("Movie runner up", "movie", 8.0);
        UUID series = work("Series leader", "series", 8.0);
        observation(movie, "TMDB", "trending-day", "day", 1, sampleTime);
        observation(secondMovie, "TMDB", "trending-day", "day", 2, sampleTime);
        observation(series, "TMDB", "trending-day", "day", 10, sampleTime);

        assertThat(repository.findTrending(publicPrincipal, 3)).extracting(PortalMovieCard::id)
                .containsExactly(movie, series, secondMovie);
    }

    @Test
    void publicRankingsRetainVisibilityAndAdultIsolationEvenForStrongSourceSignals() {
        UUID safe = work("Safe", "movie", 8.0);
        UUID restricted = work("Restricted", "movie", 8.0);
        UUID assessed = work("Assessed adult", "movie", 8.0);
        UUID privateWork = work("Private", "movie", 8.0);
        UUID adultCategory = work("Adult category", "adult", 8.0);
        jdbc.update("update unified_videos set adult_restricted = true where id = ?", restricted);
        jdbc.update("update unified_videos set adult_assessment = '{\"level\":\"ADULT\"}'::jsonb where id = ?", assessed);
        jdbc.update("update unified_videos set content_visibility = 'HIDDEN' where id = ?", privateWork);
        for (UUID id : List.of(safe, restricted, assessed, privateWork, adultCategory)) {
            observation(id, "TMDB", "trending-day", "day", 1, sampleTime);
            event(id, today, 0, 0);
            resource(id, 0, 0);
        }

        assertThat(repository.findTrending(publicPrincipal, 20)).extracting(PortalMovieCard::id).containsExactly(safe);
        assertThat(repository.findRecentlyUpdated(publicPrincipal, 20)).extracting(PortalMovieCard::id).containsExactly(safe);
        assertThat(repository.findTrending(adminPrincipal(), 20)).extracting(PortalMovieCard::id)
                .containsExactlyInAnyOrder(safe, restricted, assessed, privateWork, adultCategory);
    }

    @Test
    void categoryGenreAndTagScopesAreAppliedBeforeGlobalRanking() {
        UUID allowed = work("Allowed movie", "movie", 8.0);
        UUID wrongGenre = work("Wrong genre", "movie", 8.0);
        UUID missingTag = work("Missing tag", "movie", 8.0);
        UUID wrongCategory = work("Series", "series", 8.0);
        for (UUID id : List.of(allowed, missingTag, wrongCategory)) {
            jdbc.update("update unified_videos set genre_codes = '[\"action\"]'::jsonb where id = ?", id);
        }
        for (UUID id : List.of(allowed, wrongGenre, wrongCategory)) {
            jdbc.update("insert into unified_video_tags values (?, 'featured')", id);
        }
        for (UUID id : List.of(allowed, wrongGenre, missingTag, wrongCategory)) {
            observation(id, "TMDB", "trending-day", "day", 1, sampleTime);
        }
        IrcsRequestPrincipal scoped = new IrcsRequestPrincipal("scoped", IrcsPermissions.ROLE_MEMBER,
                Set.of(), Set.of(), Set.of("movie"), Set.of("action"), Set.of("featured"), Set.of("PUBLIC"));

        assertThat(repository.findTrending(scoped, 20)).extracting(PortalMovieCard::id).containsExactly(allowed);
        jdbc.update("update unified_videos set updated_at = now() - interval '1 day' where id = ?", allowed);

        var sections = repository.findCategorySections(scoped, List.of("movie", "series"), 1);

        assertThat(sections).containsOnlyKeys("movie");
        assertThat(sections.get("movie")).extracting(PortalMovieCard::id).containsExactly(allowed);
    }

    @Test
    void categorySectionsPickTheLatestEligibleWorkPerCategory() {
        UUID currentMovie = work("Current movie", "movie", 7.0);
        UUID olderMovie = work("Older movie", "movie", 8.0);
        UUID privateMovie = work("Private movie", "movie", 8.0);
        UUID restrictedSeries = work("Restricted series", "series", 8.0);
        UUID currentSeries = work("Current series", "series", 8.0);
        jdbc.update("update unified_videos set published_at = current_date - 1 where id in (?, ?)",
                currentMovie, currentSeries);
        jdbc.update("update unified_videos set published_at = current_date - 2 where id = ?", olderMovie);
        jdbc.update("update unified_videos set published_at = current_date, content_visibility = 'HIDDEN' where id = ?",
                privateMovie);
        jdbc.update("update unified_videos set published_at = current_date, adult_restricted = true where id = ?",
                restrictedSeries);

        var sections = repository.findCategorySections(publicPrincipal, List.of("series", "movie"), 1);

        assertThat(sections.keySet()).containsExactly("movie", "series");
        assertThat(sections.get("movie")).extracting(PortalMovieCard::id).containsExactly(currentMovie);
        assertThat(sections.get("series")).extracting(PortalMovieCard::id).containsExactly(currentSeries);
    }

    @Test
    void cardsReleaseTheQueryConnectionBeforeReadingColdDictionaries() {
        UUID populated = work("Present codes", "movie", 8.0);
        UUID empty = work("Empty codes", "movie", 7.0);
        jdbc.execute("create table standard_areas(code text primary key, name text)");
        jdbc.update("insert into standard_areas values ('cn', '中国大陆'), ('us', '美国')");
        jdbc.update("insert into standard_genre values (?, '剧情', 'drama'), (?, '动作', 'action'), (?, '恐怖', 'horror')",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                update unified_videos
                   set published_at = current_date,
                       area_codes = '["cn","zz"," cn ","us"," "]'::jsonb,
                       genre_codes = '["drama","x-sci","action"," drama ","horror",""]'::jsonb
                 where id = ?
                """, populated);
        jdbc.update("update unified_videos set area_codes = null, genre_codes = null where id = ?", empty);

        List<PortalMovieCard> cards = repositoryWithColdLookups().findCategorySection(publicPrincipal, "movie", 2);

        assertThat(cards).extracting(PortalMovieCard::id).containsExactly(populated, empty);
        assertThat(cards.getFirst().area()).isEqualTo("zz, 中国大陆, 美国");
        assertThat(cards.getFirst().genres()).containsExactly("x-sci", "剧情", "动作");
        assertThat(cards.getLast().area()).isEmpty();
        assertThat(cards.getLast().genres()).isEmpty();
    }

    @Test
    void categoryCardsReleaseTheQueryConnectionBeforeReadingColdCoverConfiguration() {
        UUID movie = work("Managed cover", "movie", 8.0);
        UUID cover = UUID.randomUUID();
        jdbc.execute("create table system_configs(config_key text primary key, config_value text)");
        jdbc.update("insert into system_configs values ('app.storage.public-path', '/configured-media')");
        jdbc.update("insert into cover_images(id, storage_type, storage_path) values (?, 'LOCAL', 'posters/movie.webp')", cover);
        jdbc.update("update unified_videos set cover_image_id = ? where id = ?", cover, movie);

        var sections = repositoryWithColdLookups().findCategorySections(publicPrincipal, List.of("movie"), 1);

        assertThat(sections).containsOnlyKeys("movie");
        assertThat(sections.get("movie")).singleElement().satisfies(card -> {
            assertThat(card.id()).isEqualTo(movie);
            assertThat(card.posterUrl()).isEqualTo("/configured-media/posters/movie.webp");
        });
    }

    @Test
    void recentEpisodesRequireAValidResourceAndMirrorsDoNotInventEpisodeProgress() {
        UUID work = work("Resource gap", "series", 8.0);
        event(work, today, 2, 12);
        assertThat(repository.findRecentlyUpdated(publicPrincipal, 20)).isEmpty();

        UUID raw = raw(work, 2, false);
        for (int i = 0; i < 15; i++) episode(raw, "HD", "https://source.example/mirror" + i + ".m3u8");
        episode(raw, "第12集", " ");
        assertThat(repository.findRecentlyUpdated(publicPrincipal, 20)).isEmpty();

        episode(raw, "第10集", "https://source.example/episode10.m3u8");
        assertThat(repository.findRecentlyUpdated(publicPrincipal, 20)).isEmpty();
        episode(raw, "第12集", "https://source.example/episode12.m3u8");
        episode(raw, "S01E20", "https://source.example/wrong-season.m3u8");
        UUID adultRaw = raw(work, 2, true);
        episode(adultRaw, "第30集", "https://source.example/restricted.m3u8");
        PortalRankingInfo progress = repository.findRecentlyUpdated(publicPrincipal, 20).getFirst().ranking();
        assertThat(progress.availableEpisode()).isEqualTo(12);
        assertThat(progress.latestEpisode()).isEqualTo(12);
    }

    @Test
    void invalidUrlsAndWrongSeasonOrAdultBindingsDoNotBecomeAvailableResources() {
        UUID work = work("No safe current-season resource", "series", 8.0);
        event(work, today, 2, 12);
        episode(raw(work, 1, false), "第12集", "https://source.example/season1.m3u8");
        episode(raw(work, 2, true), "第12集", "https://source.example/adult.m3u8");
        UUID current = raw(work, 2, false);
        episode(current, "第12集", "javascript:invalid");
        episode(current, "第12集", "/missing-source-domain.m3u8");

        assertThat(repository.findRecentlyUpdated(publicPrincipal, 20)).isEmpty();
    }

    @Test
    void authorityDetailAndAvailabilityExposeOnlyConfirmedEpisodesOfTheBoundSeason() {
        UUID video = work("Authoritative season", "series", 7.5);
        UUID authority = authorityProjection(video, "tv", 2);
        jdbc.update("""
                insert into authority_catalog_episodes(work_id,season_number,episode_number,tmdb_episode_id)
                values (?,2,1,101), (?,2,2,102)
                """, authority, authority);
        UUID correct = raw(video, 2, false);
        episode(correct, "S02E01", "https://source.example/correct");
        episode(correct, "S01E02", "https://source.example/wrong-explicit");
        episode(correct, "第99集", "https://source.example/extra");
        episode(correct, "资源合集", "https://source.example/ambiguous");
        episode(correct, "S02E02", "javascript:invalid");
        episode(raw(video, 1, false), "S02E02", "https://source.example/conflicting-raw");
        episode(raw(video, null, false), "第2集", "https://source.example/missing-season");
        episode(raw(video, 2, true), "S02E02", "https://source.example/restricted");

        assertThat(detailEpisodeNames(video)).containsExactly("S02E01");
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 2, 2)", Boolean.class, video)).isFalse();
        assertThat(jdbc.queryForObject("select available_episode from catalog_resource_inventory(?, 2)", Integer.class, video))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 1, 1)", Boolean.class, video)).isFalse();

        UUID verifiedBinding = raw(video, null, false);
        jdbc.update("insert into authority_resource_bindings(raw_video_id,unified_video_id,evidence_kind) values (?,?,'TEST')",
                verifiedBinding, video);
        episode(verifiedBinding, "第2集", "https://source.example/bound");
        assertThat(detailEpisodeNames(video)).containsExactlyInAnyOrder("S02E01", "第2集");
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 2, 2)", Boolean.class, video)).isTrue();
    }

    @Test
    void specialsRequireSeasonZeroAndWholeMovieAndSourceFirstLabelsRemainSupported() {
        UUID special = work("Specials", "series", 7.5);
        UUID authority = authorityProjection(special, "tv", 0);
        jdbc.update("""
                insert into authority_catalog_episodes(work_id,season_number,episode_number,tmdb_episode_id)
                values (?,0,1,201)
                """, authority);
        UUID rawSpecial = raw(special, null, false);
        episode(rawSpecial, "S00E01", "https://source.example/special");
        episode(rawSpecial, "S01E01", "https://source.example/regular");
        assertThat(detailEpisodeNames(special)).containsExactly("S00E01");

        UUID movie = work("Whole film", "movie", 7.5);
        authorityProjection(movie, "movie", -1);
        episode(raw(movie, null, false), "正片", "https://source.example/movie");
        assertThat(detailEpisodeNames(movie)).containsExactly("正片");

        UUID sport = work("Source-first event", "sports", 7.5);
        episode(raw(sport, null, false), "全场回放", "https://source.example/event");
        assertThat(detailEpisodeNames(sport)).containsExactly("全场回放");
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, sport)).isTrue();
    }

    @Test
    void sourceFailuresExposeLastSuccessAndStalenessWithoutErasingUsefulSnapshot() {
        UUID work = work("Last good snapshot", "movie", 8.0);
        observation(work, "TMDB", "trending-day", "day", 1, sampleTime.minus(Duration.ofHours(8)));
        jdbc.update("insert into external_trend_boards values ('TMDB', 'trending-day', 'movie', 'day', "
                + "now(), now() - interval '8 hours', 'FAILED', 'PROVIDER_UNAVAILABLE', 20)");
        jdbc.update("insert into external_trend_boards values ('IMDb', 'popular', 'movie', 'popular', "
                + "now(), null, 'UNAVAILABLE', 'ACCESS_NOT_CONFIGURED', 0)");

        List<PortalTrendSourceState> sources = repository.findTrendSourceStates();
        PortalTrendSourceState tmdb = sources.stream().filter(source -> "TMDB".equals(source.provider())).findFirst().orElseThrow();
        PortalTrendSourceState imdb = sources.stream().filter(source -> "IMDb".equals(source.provider())).findFirst().orElseThrow();

        assertThat(tmdb.status()).isEqualTo("FAILED");
        assertThat(tmdb.lastSuccessAt()).isNotNull();
        assertThat(tmdb.itemCount()).isEqualTo(20);
        assertThat(tmdb.stale()).isTrue();
        assertThat(imdb.lastSuccessAt()).isNull();
        assertThat(imdb.stale()).isTrue();
        assertThat(repository.findTrending(publicPrincipal, 20)).extracting(PortalMovieCard::id).containsExactly(work);
    }

    private JdbcPortalRepository repositoryWithColdLookups() {
        var namedJdbc = new NamedParameterJdbcTemplate(pool);
        var names = new PortalStandardNameCache(namedJdbc, new CacheRegistry(), Duration.ofMinutes(30));
        var covers = new PortalCoverImageUrlResolver(new MockEnvironment(), SystemConfigRepository.forTest(jdbc));
        return new JdbcPortalRepository(namedJdbc, covers, JsonMapper.builder().findAndAddModules().build(), names);
    }

    private UUID work(String title, String category, double rating) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into unified_videos(id, title, category_code, score, season) values (?, ?, ?, ?, ?)",
                id, title, category, BigDecimal.valueOf(rating), "series".equals(category) ? 2 : null);
        return id;
    }

    private void observation(UUID id, String provider, String board, String window, int position, Instant observedAt) {
        jdbc.update("""
                insert into external_trend_observations(provider, board, media_type, source_window, external_id,
                    unified_video_id, position, observed_at)
                values (?, ?, 'movie', ?, ?, ?, ?, ?)
                """, provider, board, window, id.toString(), id, position, Timestamp.from(observedAt));
    }

    private void event(UUID id, LocalDate date, int season, int episode) {
        jdbc.update("""
                insert into external_catalog_events(unified_video_id, provider, external_id, season, episode,
                    event_date, event_type, observed_at)
                values (?, 'TMDB', ?, ?, ?, ?, 'EPISODE_AIR', ?)
                """, id, id.toString(), season, episode, Date.valueOf(date), Timestamp.from(sampleTime));
    }

    private UUID raw(UUID id, Integer season, boolean adult) {
        UUID rawId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        jdbc.update("insert into data_sources(id,adult_restricted) values(?,?)", source, adult);
        jdbc.update("insert into raw_videos(id, season, data_source_id) values (?, ?, ?)", rawId, season, source);
        jdbc.update("insert into raw_video_unified_video values (?, ?)", rawId, id);
        return rawId;
    }

    private void episode(UUID rawId, String name, String url) {
        UUID playlist = UUID.randomUUID();
        jdbc.update("insert into playlists(id,video_id) values (?, ?)", playlist, rawId);
        jdbc.update("insert into episodes(id, playlist_id, name, url) values (?, ?, ?, ?)", UUID.randomUUID(), playlist, name, url);
    }

    private UUID authorityProjection(UUID video, String type, int season) {
        UUID work = UUID.randomUUID();
        jdbc.update("""
                insert into authority_catalog_works(id,media_type,tmdb_id,title,title_key)
                values (?,?,42,'Test','test')
                """, work, type);
        if ("tv".equals(type)) {
            jdbc.update("""
                    insert into authority_catalog_seasons(work_id,season_number,tmdb_season_id,verified_at)
                    values (?,?,100,now())
                    """, work, season);
        }
        jdbc.update("""
                insert into authority_catalog_projections(work_id,media_type,season_number,unified_video_id) values (?,?,?,?)
                """, work, type, season, video);
        jdbc.update("update unified_videos set season=nullif(?,-1) where id=?", season, video);
        return work;
    }

    private List<String> detailEpisodeNames(UUID video) {
        return repository.findMovieDetail(publicPrincipal, video).orElseThrow().sources().stream()
                .flatMap(source -> source.episodes().stream()).map(PortalMovieDetailResponse.Episode::title).toList();
    }

    private void resource(UUID unifiedVideoId, int season, int episode) {
        UUID rawId = raw(unifiedVideoId, season == 0 ? null : season, false);
        episode(rawId, episode == 0 ? "正片" : "第" + episode + "集",
                "https://source.example/resource" + UUID.randomUUID() + ".m3u8");
    }

    private static PortalMovieCard card(List<PortalMovieCard> cards, UUID id) {
        return cards.stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow();
    }

    private static IrcsRequestPrincipal adminPrincipal() {
        return new IrcsRequestPrincipal("admin", IrcsPermissions.ROLE_ADMIN, Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL), Set.of(IrcsPermissions.ALL), Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL), Set.of(IrcsPermissions.ALL));
    }

    private void createSchema() throws Exception {
        jdbc.execute("""
                create table unified_videos(id uuid primary key, title text, alias_title text, season integer, subtitle text,
                    score numeric, year text, description text, total_episodes text, duration text, remarks text,
                    last_trend_at timestamp, published_at date, updated_at timestamp default now(),
                    category_code text, cover_image_id uuid, area_codes jsonb default '[]', genre_codes jsonb default '[]',
                    language_codes jsonb default '[]', actor_names jsonb default '[]', director_names jsonb default '[]',
                    douban_id text, tmdb_id text, imdb_id text,
                    content_visibility text default 'PUBLIC', adult_restricted boolean default false, adult_assessment jsonb)
                """);
        jdbc.execute("create table standard_category(id uuid primary key, name text, slug text)");
        jdbc.execute("create table cover_images(id uuid primary key, storage_type text, original_url text, storage_path text, source_domain_id uuid)");
        jdbc.execute("create table source_domains(id uuid primary key, domain_value text)");
        jdbc.execute("create table standard_genre(id uuid primary key, name text, code text)");
        jdbc.execute("create table unified_video_tags(unified_video_id uuid, tag text)");
        jdbc.execute("create table data_sources(id uuid primary key, name text, adult_restricted boolean default false)");
        jdbc.execute("create table raw_videos(id uuid primary key, season integer, data_source_id uuid)");
        jdbc.execute("create table raw_video_unified_video(raw_video_id uuid, unified_video_id uuid)");
        jdbc.execute("create table playlists(id uuid primary key, video_id uuid, name text)");
        jdbc.execute("create table episodes(id uuid primary key, playlist_id uuid, name text, url text, source_domain_id uuid)");
        jdbc.execute("""
                create table magnet_links(unified_video_id uuid, status text, id uuid, title text, magnet_uri text,
                    size_label text, published_at timestamp, quality text, resolution text, seeders integer,
                    provider_code text, tags jsonb)
                """);
        for (String category : List.of("movie", "series", "adult")) {
            jdbc.update("insert into standard_category values (?, ?, ?)", UUID.randomUUID(), category, category);
        }
        jdbc.execute(Files.readString(migrationPath("16-02-global-rankings-and-discovery.sql")));
        jdbc.execute(Files.readString(migrationPath("22-01-catalog-target-resources.sql")));
        jdbc.execute(Files.readString(migrationPath("23-01-authority-first-catalog.sql")));
        jdbc.execute(Files.readString(migrationPath("23-03-authority-playback-episodes.sql")));
    }

    private static Path migrationPath(String filename) {
        Path relative = Path.of("platform/ircs-migrator/src/main/resources/db/changelog/2026/11", filename);
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve(relative))) return root.resolve(relative);
            if (Files.isRegularFile(root.resolve("backend").resolve(relative))) return root.resolve("backend").resolve(relative);
        }
        throw new IllegalStateException("Cannot locate ranking migration");
    }
}
