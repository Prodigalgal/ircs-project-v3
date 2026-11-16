package com.prodigalgal.ircs.magnet;

import static com.prodigalgal.ircs.magnet.MagnetCandidateIdentityPolicy.Disposition.AUTO_APPROVE;
import static com.prodigalgal.ircs.magnet.MagnetCandidateIdentityPolicy.Disposition.CANDIDATE;
import static com.prodigalgal.ircs.magnet.MagnetCandidateIdentityPolicy.Disposition.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MagnetCandidateIdentityPolicyTest {

    @Test
    void acceptsActualMovieTitleAndYearWithoutReturnedId() {
        assertDecision(AUTO_APPROVE, movie("Arrival", 2016), "Arrival.2016.1080p.WEB-DL.x265", actual());
    }

    @Test
    void ignoresEchoedQueryAndHighProviderScore() {
        assertDecision(CANDIDATE, movie("Arrival", 2016), "Arrival", actual());
        assertDecision(CANDIDATE, movie("Arrival", 2016), "Arrival 2016", Map.of("titleSource", "QUERY"));
    }

    @Test
    void rejectsKnownMovieYearConflictEvenWithMatchingReturnedId() {
        assertDecision(REJECT, movie("Arrival", 2016), "Arrival 1996", imdb());
        assertDecision(REJECT, movie("Arrival", 2016), "Arrival", Map.of(
                "titleSource", "CANDIDATE", "returnedYear", 1996, "returnedImdbId", "tt2543164"));
    }

    @Test
    void usesReturnedYearAsCorroboration() {
        assertDecision(AUTO_APPROVE, movie("Arrival", 2016), "Arrival 1080p", Map.of(
                "titleSource", "CANDIDATE", "returnedYear", 2016));
    }

    @Test
    void rejectsIndependentExternalIdConflictAcrossAnyKnownNamespace() {
        assertDecision(REJECT, movie("Arrival", 2016), "Arrival 2016", Map.of(
                "titleSource", "CANDIDATE", "returnedImdbId", "tt0133093"));
        var target = new MagnetCandidateIdentityPolicy.Target("Arrival", null, null, 2016, null,
                "MOVIE", "tt2543164", "329865", "21324900");
        assertDecision(REJECT, target, "Arrival 2016", Map.of(
                "titleSource", "CANDIDATE", "returnedImdbId", "tt2543164", "returnedTmdbId", "603"));
    }

    @Test
    void rejectsSameLanguageTitleConflictEvenWithMatchingId() {
        assertDecision(REJECT, movie("Arrival", 2016), "Titanic 2016", imdb());
        assertDecision(REJECT, movie("降临", 2016), "盗梦空间 2016", imdb());
    }

    @Test
    void permitsCrossLanguageTitleOnlyWhenIndependentIdConfirmsIdentity() {
        assertDecision(AUTO_APPROVE, movie("降临", 2016), "Arrival 2016 1080p", imdb());
        assertDecision(AUTO_APPROVE, movie("千与千寻", 2001), "千と千尋の神隠し 2001 1080p", imdb());
        assertDecision(CANDIDATE, movie("降临", 2016), "Arrival 2016 1080p", actual());
    }

    @Test
    void acceptsKnownAliasInsteadOfRejectingTranslatedTitle() {
        var target = new MagnetCandidateIdentityPolicy.Target("降临", null, "Arrival|异星入境", 2016,
                null, "MOVIE", null, null, null);
        assertDecision(AUTO_APPROVE, target, "Arrival 2016 1080p", actual());
    }

    @Test
    void documentTitleOrMissingTitleCannotAutoApproveEvenWithReturnedId() {
        assertDecision(CANDIDATE, movie("Arrival", 2016), "Arrival 2016", Map.of(
                "titleSource", "DOCUMENT_TITLE", "returnedImdbId", "tt2543164"));
        assertDecision(CANDIDATE, movie("Arrival", 2016), null, imdb());
        assertDecision(CANDIDATE, movie("Arrival", 2016), "Arrival 2016", Map.of());
        assertDecision(CANDIDATE, movie("Arrival", 2016), "1080p WEB-DL", imdb());
    }

    @Test
    void preservesNumericFilmTitlesAndSequelNumbers() {
        assertDecision(AUTO_APPROVE, movie("1917", 2019), "1917.2019.1080p", actual());
        assertDecision(AUTO_APPROVE, movie("Apollo 13", 1995), "Apollo.13.1995.1080p", actual());
        assertDecision(AUTO_APPROVE, movie("钢铁侠3", 2013), "钢铁侠3 2013 1080p", actual());
        assertDecision(REJECT, movie("钢铁侠3", 2013), "钢铁侠2 2013 1080p", imdb());
        assertDecision(REJECT, movie("钢铁侠3", 2013), "Iron Man 2 2013 1080p", imdb());
        assertDecision(REJECT, movie("Iron Man", 2008), "Iron Man 2 2008", imdb());
    }

    @Test
    void seasonAndEpisodeMarkersAllowSameSeasonButRejectOtherSeasonsAndBundles() {
        var target = series("Example", 1);
        assertDecision(AUTO_APPROVE, target, "Example.S01E02.1080p", actual());
        assertDecision(AUTO_APPROVE, target, "Example_S01E02_1080p", actual());
        assertDecision(AUTO_APPROVE, target, "Example Season 1 1080p", actual());
        assertDecision(REJECT, target, "Example.S02E02.1080p", imdb());
        assertDecision(REJECT, target, "Example S01 S02 Complete", imdb());
        assertDecision(CANDIDATE, target, "Example", imdb());
    }

    @Test
    void readsChineseSeasonWithoutLosingSequelIdentity() {
        var target = series("长安", 2);
        assertDecision(AUTO_APPROVE, target, "长安 第二季 第三集 1080p", actual());
        assertDecision(REJECT, target, "长安 第三季 1080p", imdb());
    }

    @Test
    void explicitSeasonCannotBeSilentlyAssignedToUnknownSeasonOrMovie() {
        assertDecision(CANDIDATE, series("Example", null), "Example S01E02", imdb());
        assertDecision(REJECT, movie("Example", 2016), "Example 2016 S01E02", imdb());
    }

    @Test
    void explicitPartsAndEditionsMustNotConflict() {
        assertDecision(REJECT, movie("Example Part 1", 2016), "Example Part 2 2016", imdb());
        assertDecision(REJECT, movie("Example Director's Cut", 2016), "Example Theatrical 2016", imdb());
        assertDecision(CANDIDATE, movie("Example", 2016), "Example Extended 2016", imdb());
    }

    @Test
    void shortProgramTitleNeedsFullSubtitleOrDateEvenWithMatchingId() {
        var target = new MagnetCandidateIdentityPolicy.Target("新闻", "2026-11-16 午间版", null, 2026,
                null, "NEWS", "tt2543164", null, null);
        assertDecision(CANDIDATE, target, "新闻", imdb());
        assertDecision(AUTO_APPROVE, target, "新闻 2026-11-16 午间版 1080p", actual());
        assertDecision(REJECT, target, "新闻 2026-11-15 午间版", imdb());
        assertDecision(CANDIDATE, specific("音乐", null, "MUSIC"), "音乐", imdb());
    }

    @Test
    void otherAndAdultCategoriesPreserveSpecificCatalogNumbers() {
        for (String category : List.of("ADULT", "OTHER", "UNKNOWN")) {
            assertDecision(REJECT, specific("ABCD-619", null, category), "ABCD-620 1080p", imdb());
            assertDecision(AUTO_APPROVE, specific("ABCD-619", null, category), "ABCD-619 1080p", actual());
            assertDecision(CANDIDATE, specific("家", null, category), "家", imdb());
            assertDecision(AUTO_APPROVE, specific("家", "具体节目", category), "家 具体节目", actual());
        }
    }

    @Test
    void rejectsNullInputsAndDoesNotTreatMalformedIdsAsEvidence() {
        assertEquals(REJECT, MagnetCandidateIdentityPolicy.evaluate(null, candidate("Arrival", actual())).disposition());
        assertEquals(REJECT, MagnetCandidateIdentityPolicy.evaluate(movie("Arrival", 2016), null).disposition());
        assertDecision(CANDIDATE, movie("Arrival", 2016), "Arrival", Map.of(
                "titleSource", "CANDIDATE", "returnedImdbId", "https://imdb.com/title/tt2543164"));
    }

    private static MagnetCandidateIdentityPolicy.Target movie(String title, Integer year) {
        return new MagnetCandidateIdentityPolicy.Target(title, null, null, year, null,
                "MOVIE", "tt2543164", null, null);
    }

    private static MagnetCandidateIdentityPolicy.Target series(String title, Integer season) {
        return new MagnetCandidateIdentityPolicy.Target(title, null, null, null, season,
                "TV_SERIES", "tt2543164", null, null);
    }

    private static MagnetCandidateIdentityPolicy.Target specific(String title, String subtitle, String category) {
        return new MagnetCandidateIdentityPolicy.Target(title, subtitle, null, null, null,
                category, "tt2543164", null, null);
    }

    private static Map<String, Object> actual() { return Map.of("titleSource", "CANDIDATE"); }
    private static Map<String, Object> imdb() {
        return Map.of("titleSource", "CANDIDATE", "returnedImdbId", "tt2543164");
    }

    private static void assertDecision(MagnetCandidateIdentityPolicy.Disposition expected,
                                       MagnetCandidateIdentityPolicy.Target target, String title,
                                       Map<String, Object> evidence) {
        var decision = MagnetCandidateIdentityPolicy.evaluate(target, candidate(title, evidence));
        assertEquals(expected, decision.disposition(), () -> title + ": " + decision.reason());
    }

    private static MagnetProviderCandidate candidate(String title, Map<String, Object> evidence) {
        return new MagnetProviderCandidate("ABCDEF1234567890ABCDEF1234567890ABCDEF12", "magnet:?xt=urn:btih:test",
                title, null, null, null, null, null, null, null,
                "IMDB", "tt2543164", 100, null, List.of(), evidence);
    }
}
