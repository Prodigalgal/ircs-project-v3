package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoubanSubjectPageParserTest {
    @Test
    void readsVerifiedSubjectIdentityAndAvailableFields() {
        String html = """
                <html><head>
                  <link rel="canonical" href="https://movie.douban.com//subject/26266893/">
                  <meta property="og:url" content="https://movie.douban.com/subject/26266893/">
                  <meta property="og:title" content="流浪地球">
                </head><body>
                  <h1><span property="v:itemreviewed">流浪地球</span> <span class="year">(2019)</span></h1>
                  <div id="info"><span>IMDb:</span><a href="https://www.imdb.com/title/tt7605074/">IMDb 条目</a>
                    <span>原名:</span><span>The Wandering Earth</span></div>
                  <strong property="v:average">7.9</strong>
                </body></html>
                """;

        DoubanSubjectPageParser.Subject subject = DoubanSubjectPageParser.read(html, "26266893");

        assertThat(subject.id()).isEqualTo("26266893");
        assertThat(subject.imdbId()).isEqualTo("tt7605074");
        assertThat(subject.title()).isEqualTo("流浪地球");
        assertThat(subject.originalTitle()).isEqualTo("The Wandering Earth");
        assertThat(subject.year()).isEqualTo("2019");
        assertThat(subject.score()).isEqualByComparingTo("7.9");
    }

    @Test
    void rejectsMissingIdentityChallengeAndConflictingIdentityEvidence() {
        assertError("<html><title>Just a moment</title><body>challenge</body></html>", "26266893",
                "DOUBAN_SUPPLEMENT_INVALID_RESPONSE");
        assertError("<html><head><link rel='canonical' href='https://movie.douban.com/subject/1/'></head><body><h1>x</h1></body></html>",
                "1", "DOUBAN_SUPPLEMENT_INVALID_RESPONSE");
        assertError(page("1", "2", "Title"), "1", "DOUBAN_SUPPLEMENT_IDENTITY_CONFLICT");
        assertError(page("2", "2", "Title"), "1", "DOUBAN_SUPPLEMENT_IDENTITY_CONFLICT");
    }

    @Test
    void neverTakesAnImdbIdFromTheNextFieldAndRejectsConflictingImdbEvidence() {
        String page = page("42", "42", "Title");
        String followingField = """
                <div id="info"><span class="pl">IMDb:</span><br>
                <span class="pl">导演:</span>Composer tt1234567<br></div></body>
                """;
        assertThat(DoubanSubjectPageParser.read(page.replace("</body>", followingField), "42").imdbId()).isNull();

        String conflictingField = """
                <div id="info"><span class="pl">IMDb:</span>
                <a href="https://www.imdb.com/title/tt1234567/">tt7654321</a><br></div></body>
                """;
        assertError(page.replace("</body>", conflictingField), "42", CatalogDoubanSupplementSource.IDENTITY_CONFLICT);
    }

    @Test
    void usesOgTitleWhenStructuredHeadingIsUnavailableAndLeavesInvalidOptionalsNull() {
        String html = """
                <html><head><meta property="og:url" content="https://movie.douban.com/subject/42/">
                <meta property="og:title" content="Fallback title"></head><body>
                <div id="info">导演: <a href="/celebrity/123/">tt7654321</a></div>
                <strong property="v:average">11.0</strong></body></html>
                """;

        DoubanSubjectPageParser.Subject subject = DoubanSubjectPageParser.read(html, "42");

        assertThat(subject.title()).isEqualTo("Fallback title");
        assertThat(subject.imdbId()).isNull();
        assertThat(subject.year()).isNull();
        assertThat(subject.originalTitle()).isNull();
        assertThat(subject.score()).isNull();
    }

    @Test
    void readsOrderedDistinctAliasesMainlandReleaseDateAndFormattedVoteCount() {
        String html = page("42", "42", "Title").replace("</body>", """
                <div id="info">
                  <span class="pl">又名:</span> Alpha / A/B / Alpha / Beta<br>
                  <span class="pl">上映日期:</span> 2022-02-29(中国大陆) / 2021-12-31（中国大陆） / 2019-02-05(中国大陆)<br>
                  <span class="pl">首播:</span> 2018年3月4日（中国大陆）<br>
                </div>
                <strong property="v:votes">12,345</strong></body>
                """);

        var subject = DoubanSubjectPageParser.read(html, "42");

        assertThat(subject.aliases()).isEqualTo(List.of("Alpha", "A/B", "Beta"));
        assertThat(subject.mainlandReleaseDate()).isEqualTo(LocalDate.of(2018, 3, 4));
        assertThat(subject.voteCount()).isEqualTo(12_345L);
    }

    @Test
    void ignoresForeignRereleaseAndInvalidDatesAndRejectsMalformedOrOverflowVoteCounts() {
        String html = page("42", "42", "Title").replace("</body>", """
                <div id="info"><span class="pl">上映日期:</span>
                2020-01-01(美国) / 2022-01-01(中国大陆)（重映） / 2021-02-30(中国大陆)<br>
                <span class="pl">首播:</span> 2023-05-06（中国大陆）</div>
                <strong property="v:votes">9223372036854775808</strong></body>
                """);

        var subject = DoubanSubjectPageParser.read(html, "42");

        assertThat(subject.mainlandReleaseDate()).isEqualTo(LocalDate.of(2023, 5, 6));
        assertThat(subject.voteCount()).isNull();

        String malformedVotes = page("43", "43", "Title").replace("</body>",
                "<strong property='v:votes'>1,23</strong></body>");
        assertThat(DoubanSubjectPageParser.read(malformedVotes, "43").voteCount()).isNull();
    }

    @Test
    void leavesDateAndVoteCountNullWhenEvidenceIsAbsentOrNotMainland() {
        String html = page("44", "44", "Title").replace("</body>", """
                <div id="info"><span class="pl">上映日期:</span> 2024-01-02(中国台湾)</div>
                <strong property="v:votes">-1</strong></body>
                """);

        var subject = DoubanSubjectPageParser.read(html, "44");

        assertThat(subject.mainlandReleaseDate()).isNull();
        assertThat(subject.voteCount()).isNull();
    }

    @Test
    void readsMovieTitleTypeAndRoleSeparatedPeopleFromDoubanMarkup() {
        String html = """
                <html><head>
                  <link rel="canonical" href="https://movie.douban.com/subject/100/">
                  <meta property="og:url" content="https://movie.douban.com/subject/100/">
                  <meta property="og:title" content="寄生虫 - 豆瓣电影">
                </head><body>
                  <div typeof="v:Movie"><h1><span property="v:itemreviewed">寄生虫</span><span class="year">(2019)</span></h1></div>
                  <div id="info">
                    <span class="pl">导演</span>: <a href="/celebrity/1/">奉俊昊</a> / <a href="/celebrity/1/">奉俊昊</a><br>
                    <span class="pl">主演</span>: <a href="/celebrity/2/">宋康昊</a> / <a href="/celebrity/3/">李善均</a><br>
                    <span class="pl">上映日期</span>: 2019-05-30(韩国)<br>
                  </div>
                </body></html>
                """;

        var subject = DoubanSubjectPageParser.read(html, "100");

        assertThat(subject.localizedTitle()).isEqualTo("寄生虫");
        assertThat(subject.mediaType()).isEqualTo("movie");
        assertThat(subject.directors()).isEqualTo(List.of("奉俊昊"));
        assertThat(subject.actors()).isEqualTo(List.of("宋康昊", "李善均"));
        assertThat(subject.episodeCount()).isNull();
        assertThat(subject.year()).isEqualTo("2019");
    }

    @Test
    void readsTvSignalsAndPositiveEpisodeCountWithoutMixingPeopleRoles() {
        String html = page("101", "101", "剧集标题").replace("</body>", """
                <meta property="og:title" content="剧集标题 (豆瓣)">
                <div id="info">
                  <span class="pl">首播</span>: 2020-01-02(美国)<br>
                  <span class="pl">集数</span>: 12集<br>
                  <span class="pl">单集片长</span>: 45分钟<br>
                  <span class="pl">导演</span>: <a href="/celebrity/4/">导演甲</a><br>
                  <span class="pl">主演</span>: <a href="/celebrity/5/">演员乙</a><br>
                </div></body>
                """);

        var subject = DoubanSubjectPageParser.read(html, "101");

        assertThat(subject.localizedTitle()).isEqualTo("剧集标题");
        assertThat(subject.mediaType()).isEqualTo("tv");
        assertThat(subject.episodeCount()).isEqualTo(12);
        assertThat(subject.directors()).isEqualTo(List.of("导演甲"));
        assertThat(subject.actors()).isEqualTo(List.of("演员乙"));
    }

    @Test
    void leavesMediaTypeNullForMissingOrConflictingSignalsAndEpisodeCountForInvalidValues() {
        var missing = DoubanSubjectPageParser.read(page("102", "102", "作品"), "102");
        assertThat(missing.mediaType()).isNull();
        assertThat(missing.localizedTitle()).isEqualTo("作品");

        String conflicting = page("103", "103", "作品").replace("</body>", """
                <div id="info"><span class="pl">上映日期</span>: 2019-03-01<br>
                <span class="pl">首播</span>: 2020-03-01<br>
                <span class="pl">集数</span>: 0<br></div></body>
                """);
        var subject = DoubanSubjectPageParser.read(conflicting, "103");
        assertThat(subject.mediaType()).isNull();
        assertThat(subject.episodeCount()).isNull();

        String overflow = page("104", "104", "作品").replace("</body>", """
                <div id="info"><span class="pl">集数</span>: 999999999999999999999<br></div></body>
                """);
        assertThat(DoubanSubjectPageParser.read(overflow, "104").episodeCount()).isNull();
    }

    private static String page(String canonicalId, String ogId, String title) {
        return "<html><head><link rel='canonical' href='https://movie.douban.com/subject/" + canonicalId
                + "/'><meta property='og:url' content='https://movie.douban.com/subject/" + ogId
                + "/'></head><body><h1><span property='v:itemreviewed'>" + title + "</span></h1></body></html>";
    }

    private static void assertError(String html, String requestedId, String code) {
        assertThatThrownBy(() -> DoubanSubjectPageParser.read(html, requestedId))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage(code);
    }
}
