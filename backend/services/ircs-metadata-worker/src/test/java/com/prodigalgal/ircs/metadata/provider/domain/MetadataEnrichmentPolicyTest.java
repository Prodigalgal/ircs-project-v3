package com.prodigalgal.ircs.metadata.provider.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MetadataEnrichmentPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"sports", "news", "education", "music", "adult", "other", " SPORTS "})
    void sourceOnlyCategoriesDoNotUseFilmAuthorities(String category) {
        for (ProviderType provider : ProviderType.values()) {
            assertFalse(MetadataEnrichmentPolicy.supports(context(category, null), provider));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"movie", "series", "anime", "variety", "documentary"})
    void filmAndTelevisionCategoriesSupportConfiguredAuthorities(String category) {
        for (ProviderType provider : new ProviderType[]{ProviderType.DOUBAN, ProviderType.TMDB, ProviderType.ROTTEN_TOMATOES}) {
            assertTrue(MetadataEnrichmentPolicy.supports(context(category, null), provider));
        }
    }

    @Test
    void shortDramaOnlyUsesDoubanAndTmdb() {
        assertTrue(MetadataEnrichmentPolicy.supports(context("short-drama", null), ProviderType.DOUBAN));
        assertTrue(MetadataEnrichmentPolicy.supports(context("short-drama", null), ProviderType.TMDB));
        assertFalse(MetadataEnrichmentPolicy.supports(context("short-drama", null), ProviderType.ROTTEN_TOMATOES));
        assertFalse(MetadataEnrichmentPolicy.supports(context("short-drama", null), ProviderType.IMDB));
    }

    @ParameterizedTest
    @CsvSource({"movie,movie", "series,tv", "variety,tv", "short-drama,tv", "anime,multi", "documentary,multi"})
    void categoryChoosesSearchScopeWithoutGuessingAnimationOrDocumentaryFormat(String category, String type) {
        assertEquals(type, MetadataEnrichmentPolicy.searchType(context(category, null)));
    }

    @Test
    void explicitMovieCategoryTakesPrecedenceOverSuspiciousSeasonMetadata() {
        assertEquals("movie", MetadataEnrichmentPolicy.searchType(context("movie", 2)));
        assertEquals("tv", MetadataEnrichmentPolicy.searchType(context("series", 2)));
        assertEquals("tv", MetadataEnrichmentPolicy.searchType(context("anime", 0)));
    }

    @Test
    void rejectsMissingTitleOrProvider() {
        assertFalse(MetadataEnrichmentPolicy.supports(null, ProviderType.TMDB));
        assertFalse(MetadataEnrichmentPolicy.supports(MetadataSearchContext.builder().title(" ").build(), ProviderType.TMDB));
        assertFalse(MetadataEnrichmentPolicy.supports(context("movie", null), null));
    }

    private MetadataSearchContext context(String category, Integer season) {
        return MetadataSearchContext.builder().title("作品完整标题").categorySlug(category).season(season).build();
    }

    @ParameterizedTest
    @CsvSource({"movie,MOVIE_AUTHORITY", "series,SERIES_AUTHORITY", "anime,ANIMATION_AUTHORITY",
            "short-drama,SHORT_DRAMA_AUTHORITY", "sports,SPORTS_SOURCE", "variety,PROGRAMME_AUTHORITY",
            "documentary,DOCUMENTARY_AUTHORITY", "news,NEWS_SOURCE", "education,EDUCATION_SOURCE",
            "music,MUSIC_SOURCE", "adult,ADULT_SOURCE", "other,OTHER_SOURCE"})
    void eachStableCategorySelectsAnExplicitEnrichmentStrategy(String category, String strategy) {
        assertEquals(strategy, MetadataEnrichmentPolicy.strategy(context(category, null)));
    }
}
