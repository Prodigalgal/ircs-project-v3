package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
