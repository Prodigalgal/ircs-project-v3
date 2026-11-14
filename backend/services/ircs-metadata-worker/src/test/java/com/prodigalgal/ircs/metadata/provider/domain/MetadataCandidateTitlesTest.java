package com.prodigalgal.ircs.metadata.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import org.junit.jupiter.api.Test;

class MetadataCandidateTitlesTest {

    @Test
    void matchesCombinedSubtitleAndOriginalLanguageAlias() {
        var context = MetadataSearchContext.builder().title("Alien").subtitle("Covenant")
                .aliasTitle("异形：契约").build();

        assertThat(MetadataCandidateTitles.matches(context, "Alien: Covenant")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "异形：契约")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "Alien", "Alien")).isFalse();
    }

    @Test
    void rejectsSequelAndShortPrefixEvenWhenProviderRanksThemFirst() {
        var context = MetadataSearchContext.builder().title("Alien").build();

        assertThat(MetadataCandidateTitles.matches(context, "Alien: Covenant")).isFalse();
        assertThat(MetadataCandidateTitles.matches(context, "Ali")).isFalse();
    }

    @Test
    void rejectsConflictingSeasonEvenWhenOriginalNameMatches() {
        var context = MetadataSearchContext.builder().title("Example").season(2).build();

        assertThat(MetadataCandidateTitles.matches(context, "Example 第三季", "Example")).isFalse();
        assertThat(MetadataCandidateTitles.matches(context, "Example 第二季", "Example")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "Example Season 3")).isFalse();
        assertThat(MetadataCandidateTitles.matches(context, "Example S02")).isTrue();
    }

    @Test
    void retainsUnicodePunctuationAndWidthVariantsWithoutDiscardingNumbers() {
        var context = MetadataSearchContext.builder().title("ＨＥＲＯ：２").build();

        assertThat(MetadataCandidateTitles.matches(context, "Hero 2")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "Hero 3")).isFalse();
    }

    @Test
    void preservesSeasonZeroAndRejectsOtherSeasons() {
        var context = MetadataSearchContext.builder().title("Example").season(0).build();

        assertThat(MetadataCandidateTitles.matches(context, "Example S0")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "Example 第零季")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "Example Season 1", "Example")).isFalse();
        var titleSeason = MetadataSearchContext.builder().title("Example").subtitle("第零季").build();
        assertThat(MetadataCandidateTitles.matches(titleSeason, "Example 第一季")).isFalse();
    }

    @Test
    void releaseLabelsDoNotHideExactWorkTitle() {
        for (String label : new String[] {"正片", "高清", "HD", "1080P高清", "国语中字"}) {
            var context = MetadataSearchContext.builder().title("Alien").subtitle(label).build();
            assertThat(MetadataCandidateTitles.matches(context, "Alien")).as(label).isTrue();
        }
    }

    @Test
    void comparesExplicitAliasListsWithoutSplittingStoryCommas() {
        var context = MetadataSearchContext.builder().title("Alien").subtitle("Covenant")
                .aliasTitle("异形：契约 / Alien Covenant|Prometheus 2;异形前传；异形续章").build();

        assertThat(MetadataCandidateTitles.matches(context, "Alien Covenant")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "异形续章")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, context.getAliasTitle())).isTrue();
        var story = MetadataSearchContext.builder().title("Different Work")
                .aliasTitle("离开你，我成了自己的光").build();
        assertThat(MetadataCandidateTitles.matches(story, "离开你")).isFalse();
        assertThat(MetadataCandidateTitles.matches(story, "离开你，我成了自己的光")).isTrue();
    }

    @Test
    void subtitleAlreadyPresentInOriginalTitleDoesNotRequireDuplication() {
        var context = MetadataSearchContext.builder().title("Alien Covenant").subtitle("Covenant").build();

        assertThat(MetadataCandidateTitles.matches(context, "Alien: Covenant")).isTrue();
        assertThat(MetadataCandidateTitles.matches(context, "Alien")).isFalse();
    }
}
