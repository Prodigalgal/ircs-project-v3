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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private CatalogTestDatabase database;
    private JdbcPortalRepository repository;
    private Instant sampleTime;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        database = CatalogTestDatabase.open();
        pool = new HikariDataSource();
        pool.setDataSource(database.dataSource());
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(3_000);
        jdbc = new JdbcTemplate(pool);
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
        if (database != null) database.close();
    }

    @Test
    void legacyHighRatingAndRecentlyTouchedRowsAreNotGlobalTrendingEvidence() {
        UUID legacy = work("Old perfect score", "movie", 10.0);
        jdbc.update("update catalog_entries set published_at = current_date - 1000, last_trend_at = now(), "
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
        jdbc.update("update catalog_entries set updated_at = now() - interval '30 days' where id = ?", current);
        jdbc.update("update catalog_entries set updated_at = now(), published_at = current_date where id in (?, ?)",
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
        jdbc.update("update catalog_release_events set observed_at = now() - interval '1 day'");
        observation(yesterday, "TMDB", "airing-today", "event", 1, sampleTime.minus(Duration.ofDays(1)));
        jdbc.update("delete from provider_ranking_observations");

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
        jdbc.update("update catalog_entries set adult_restricted = true where id = ?", restricted);
        jdbc.update("update catalog_entries set adult_assessment = '{\"level\":\"ADULT\"}'::jsonb where id = ?", assessed);
        jdbc.update("update catalog_entries set content_visibility = 'HIDDEN' where id = ?", privateWork);
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
            jdbc.update("update catalog_entries set genre_codes = '[\"action\"]'::jsonb where id = ?", id);
        }
        for (UUID id : List.of(allowed, wrongGenre, wrongCategory)) {
            jdbc.update("insert into catalog_entry_tags values (?, 'featured')", id);
        }
        for (UUID id : List.of(allowed, wrongGenre, missingTag, wrongCategory)) {
            observation(id, "TMDB", "trending-day", "day", 1, sampleTime);
        }
        IrcsRequestPrincipal scoped = new IrcsRequestPrincipal("scoped", IrcsPermissions.ROLE_MEMBER,
                Set.of(), Set.of(), Set.of("movie"), Set.of("action"), Set.of("featured"), Set.of("PUBLIC"));

        assertThat(repository.findTrending(scoped, 20)).extracting(PortalMovieCard::id).containsExactly(allowed);
        jdbc.update("update catalog_entries set updated_at = now() - interval '1 day' where id = ?", allowed);

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
        jdbc.update("update catalog_entries set published_at = current_date - 1 where id in (?, ?)",
                currentMovie, currentSeries);
        jdbc.update("update catalog_entries set published_at = current_date - 2 where id = ?", olderMovie);
        jdbc.update("update catalog_entries set published_at = current_date, content_visibility = 'HIDDEN' where id = ?",
                privateMovie);
        jdbc.update("update catalog_entries set published_at = current_date, adult_restricted = true where id = ?",
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
        jdbc.update("insert into catalog_regions(id,created_at,updated_at,name,code) values (?,now(),now(),'中国大陆','cn'), (?,now(),now(),'美国','us')",
                UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("insert into catalog_genres(id,created_at,updated_at,name,code) values (?,now(),now(),'剧情','drama'), (?,now(),now(),'动作','action'), (?,now(),now(),'恐怖','horror')",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                update catalog_entries
                   set published_at = current_date,
                       area_codes = '["cn","zz"," cn ","us"," "]'::jsonb,
                       genre_codes = '["drama","x-sci","action"," drama ","horror",""]'::jsonb
                 where id = ?
                """, populated);


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
        jdbc.update("insert into application_settings(id,created_at,updated_at,config_key,config_value) "
                + "values (?,now(),now(),'app.storage.public-path','/configured-media')",
                UUID.randomUUID());
        UUID originId = UUID.randomUUID();
        String hash = "a".repeat(64);
        jdbc.update("""
                insert into source_origins(id,created_at,updated_at,domain_hash,domain_value)
                values(?,now(),now(),?,'https://images.example')
                """, originId, hash);
        String path = jdbc.queryForObject("select reserve_cover_asset(?,100,'image/webp','posters/movie.webp')",
                String.class, hash);
        jdbc.update("""
                insert into media_image_sources(id,created_at,updated_at,storage_type,storage_path,original_url,file_hash,
                    source_origin_id,status)
                values (?,now(),now(),'LOCAL',?,'/movie.webp',?,?,'LOCAL_STORED')
                """, cover, path, hash, originId);
        jdbc.update("update catalog_entries set media_image_source_id = ? where id = ?", cover, movie);

        var sections = repositoryWithColdLookups().findCategorySections(publicPrincipal, List.of("movie"), 1);

        assertThat(sections).containsOnlyKeys("movie");
        assertThat(sections.get("movie")).singleElement().satisfies(card -> {
            assertThat(card.id()).isEqualTo(movie);
            assertThat(card.posterUrl()).isEqualTo("/configured-media/" + path);
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
                insert into catalog_episodes(work_id,season_number,episode_number,tmdb_episode_id,title)
                values (?,2,1,101,'One'), (?,2,2,102,'Two')
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

        assertThat(detailEpisodeNames(video)).isEmpty();
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 2, 1)", Boolean.class, video)).isFalse();
        recordAuthorityBinding(correct, video);
        assertThat(detailEpisodeNames(video)).containsExactly("S02E01");
        UUID canonicalEpisodeOne = jdbc.queryForObject(
                "select id from catalog_episodes where work_id=? and season_number=2 and episode_number=1", UUID.class, authority);
        assertThat(detailEpisodes(video).getFirst().catalogEpisodeId()).isEqualTo(canonicalEpisodeOne.toString());
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 2, 2)", Boolean.class, video)).isFalse();
        assertThat(jdbc.queryForObject("select available_episode from catalog_resource_inventory(?, 2)", Integer.class, video))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 1, 1)", Boolean.class, video)).isFalse();

        UUID verifiedBinding = raw(video, null, false);
        recordAuthorityBinding(verifiedBinding, video);
        episode(verifiedBinding, "第2集", "https://source.example/bound");
        assertThat(detailEpisodeNames(video)).containsExactlyInAnyOrder("S02E01", "第2集");
        assertThat(detailEpisodes(video)).extracting(PortalMovieDetailResponse.Episode::catalogEpisodeId)
                .containsExactlyInAnyOrder(canonicalEpisodeOne.toString(), jdbc.queryForObject(
                        "select id from catalog_episodes where work_id=? and season_number=2 and episode_number=2",
                        UUID.class, authority).toString());
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 2, 2)", Boolean.class, video)).isTrue();
    }

    @Test
    void specialsRequireSeasonZeroAndWholeMovieAndSourceFirstLabelsRemainSupported() {
        UUID special = work("Specials", "series", 7.5);
        UUID authority = authorityProjection(special, "tv", 0);
        jdbc.update("""
                insert into catalog_episodes(work_id,season_number,episode_number,tmdb_episode_id,title)
                values (?,0,1,201,'Special')
                """, authority);
        UUID rawSpecial = raw(special, null, false);
        recordAuthorityBinding(rawSpecial, special);
        episode(rawSpecial, "S00E01", "https://source.example/special");
        episode(rawSpecial, "S01E01", "https://source.example/regular");
        assertThat(detailEpisodeNames(special)).containsExactly("S00E01");

        UUID movie = work("Whole film", "movie", 7.5);
        authorityProjection(movie, "movie", -1);
        UUID movieSource = raw(movie, null, false);
        episode(movieSource, "正片", "https://source.example/movie");
        assertThat(detailEpisodeNames(movie)).isEmpty();
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, movie)).isFalse();
        assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?, 0)", Boolean.class, movie)).isFalse();
        jdbc.update("""
                insert into catalog_magnet_links(catalog_entry_id,id,status,provider_code,info_hash,magnet_uri,
                    title,matched_external_id_type,matched_external_id_value)
                values(?,uuidv7(),'APPROVED','fixture',repeat('a',40),'magnet:?xt=urn:btih:' || repeat('a',40),
                    'Whole film','TMDB','123')
                """, movie);
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, movie)).isTrue();
        jdbc.update("delete from catalog_magnet_links where catalog_entry_id=?", movie);
        recordAuthorityBinding(movieSource, movie);
        assertThat(detailEpisodeNames(movie)).containsExactly("正片");
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, movie)).isTrue();

        UUID sport = work("Source-first event", "sports", 7.5);
        episode(raw(sport, null, false), "全场回放", "https://source.example/event");
        assertThat(detailEpisodeNames(sport)).containsExactly("全场回放");
        assertThat(jdbc.queryForObject("select catalog_has_target_resources(?, 0, 0)", Boolean.class, sport)).isTrue();
    }

    @Test
    void sourceFailuresExposeLastSuccessAndStalenessWithoutErasingUsefulSnapshot() {
        UUID work = work("Last good snapshot", "movie", 8.0);
        observation(work, "TMDB", "trending-day", "day", 1, sampleTime.minus(Duration.ofHours(8)));
        jdbc.update("insert into provider_ranking_boards values ('TMDB', 'trending-day', 'movie', 'day', "
                + "now(), now() - interval '8 hours', 'FAILED', 'PROVIDER_UNAVAILABLE', 20)");
        jdbc.update("insert into provider_ranking_boards values ('IMDb', 'popular', 'movie', 'popular', "
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
        jdbc.update("""
                insert into catalog_entries(id, created_at, updated_at, title, category_code, score, season)
                values (?, now(), now(), ?, ?, ?, ?)
                """, id, title, category, BigDecimal.valueOf(rating), "series".equals(category) ? 2 : null);
        return id;
    }

    private void observation(UUID id, String provider, String board, String window, int position, Instant observedAt) {
        jdbc.update("""
                insert into provider_ranking_observations(provider, board, media_type, source_window, external_id,
                    catalog_entry_id, position, observed_at)
                values (?, ?, 'movie', ?, ?, ?, ?, ?)
                """, provider, board, window, id.toString(), id, position, Timestamp.from(observedAt));
    }

    private void event(UUID id, LocalDate date, int season, int episode) {
        jdbc.update("""
                insert into catalog_release_events(catalog_entry_id, provider, external_id, season, episode,
                    event_date, event_type, observed_at)
                values (?, 'TMDB', ?, ?, ?, ?, 'EPISODE_AIR', ?)
                """, id, id.toString(), season, episode, Date.valueOf(date), Timestamp.from(sampleTime));
    }

    private UUID raw(UUID id, Integer season, boolean adult) {
        UUID rawId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        jdbc.update("""
                insert into collection_sources(id,created_at,updated_at,name,base_url,list_path,detail_path,adult_restricted)
                values (?,now(),now(),?,'https://source.example','/list','/detail',?)
                """, source, "Fixture source " + source, adult);
        jdbc.update("""
                insert into source_records(id,created_at,updated_at,collection_source_id,source_vid,source_hash,title,season,
                    data_hash,enrichment_status,normalization_status)
                values (?,now(),now(),?,?,?,?, ?,?,'PENDING','PENDING')
                """, rawId, source, rawId.toString(), UUID.randomUUID().toString().replace("-", ""), "Source fixture", season,
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        UUID version = UUID.randomUUID();
        jdbc.update("""
                insert into source_record_versions(id,source_record_id,data_hash,payload,observed_at)
                values (?,?,(select data_hash from source_records where id=?),'{}',now())
                """, version, rawId, rawId);
        jdbc.update("update source_records set current_version_id=?,playback_version_id=?,current_observed_at=now() where id=?",
                version, version, rawId);
        jdbc.update("insert into catalog_source_bindings(source_record_id,catalog_entry_id) values (?,?)", rawId, id);
        return rawId;
    }

    private void episode(UUID rawId, String name, String url) {
        UUID playlist = jdbc.queryForObject("""
                insert into source_playlists(id,created_at,updated_at,name,source_record_id,source_record_version_id)
                select uuidv7(),now(),now(),'Main',id,current_version_id from source_records where id=?
                on conflict (source_record_id,name) do update set updated_at=now()
                returning id
                """, UUID.class, rawId);
        jdbc.update("insert into source_playback_items(id,created_at,updated_at,name,url,playlist_id,source_record_id) values (?,now(),now(),?,?,?,?)",
                UUID.randomUUID(), name, url, playlist, rawId);
        jdbc.query("select reconcile_catalog_playback(?)", (org.springframework.jdbc.core.RowCallbackHandler) rs -> {}, rawId);
    }

    private void recordAuthorityBinding(UUID rawId, UUID entryId) {
        jdbc.queryForObject("select affirm_catalog_source_match(?,?,'MANUAL_REVIEW')", Integer.class, rawId, entryId);
    }

    private UUID authorityProjection(UUID video, String type, int season) {
        UUID work = UUID.randomUUID();
        jdbc.update("""
                insert into catalog_works(id,media_type,tmdb_id,title,title_key,last_verified_at,identity_status)
                values (?,?,?,'Test','test',now(),'VERIFIED')
                """, work, type, Math.abs(UUID.randomUUID().getMostSignificantBits() % 2_000_000_000L) + 1);
        if ("tv".equals(type)) {
            jdbc.update("""
                    insert into catalog_seasons(work_id,season_number,tmdb_season_id,verified_at)
                    values (?,?,?,now())
                    """, work, season, Math.abs(UUID.randomUUID().getMostSignificantBits() % 2_000_000_000L) + 1);
        }
        UUID seasonId = "tv".equals(type) ? jdbc.queryForObject(
                "select id from catalog_seasons where work_id=? and season_number=?", UUID.class, work, season) : null;
        jdbc.update("update catalog_entries set work_id=?,season_id=?,season=nullif(?,-1) where id=?", work, seasonId, season, video);
        return work;
    }

    private List<String> detailEpisodeNames(UUID video) {
        return detailEpisodes(video).stream().map(PortalMovieDetailResponse.Episode::title).toList();
    }

    private List<PortalMovieDetailResponse.Episode> detailEpisodes(UUID video) {
        return repository.findMovieDetail(publicPrincipal, video).orElseThrow().sources().stream()
                .flatMap(source -> source.episodes().stream()).toList();
    }

    private void resource(UUID catalogEntryId, int season, int episode) {
        UUID rawId = raw(catalogEntryId, season == 0 ? null : season, false);
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

}
