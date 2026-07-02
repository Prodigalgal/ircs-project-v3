package com.prodigalgal.ircs.common.adult;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdultContentAssessorTest {

    @Test
    void marksAdultWhenDataSourceIsRestricted() {
        AdultAssessment assessment = AdultContentAssessor.assess(new AdultAssessmentInput(
                "普通标题",
                null,
                null,
                null,
                null,
                "movie",
                "电影",
                List.of(),
                List.of(),
                List.of(),
                List.of(new AdultAssessmentInput.SourceEvidence(
                        "限制资源站",
                        true,
                        null,
                        null,
                        "https://example.invalid",
                        null))));

        assertThat(assessment.adultRestricted()).isTrue();
        assertThat(assessment.level()).isEqualTo(AdultAssessmentLevel.ADULT);
        assertThat(assessment.signals()).anyMatch(signal -> "dataSourceAdultRestricted".equals(signal.field()));
    }

    @Test
    void treatsStandaloneAvTokenAsAdultButAvoidsCommonFalsePositives() {
        AdultAssessment adult = AdultContentAssessor.assess(new AdultAssessmentInput(
                "JAV sample",
                null,
                null,
                null,
                null,
                "movie",
                "电影",
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        AdultAssessment avatar = AdultContentAssessor.assess(new AdultAssessmentInput(
                "Avatar",
                null,
                null,
                null,
                null,
                "movie",
                "电影",
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        assertThat(adult.adultRestricted()).isTrue();
        assertThat(avatar.adultRestricted()).isFalse();
        assertThat(avatar.signals()).isEmpty();
    }

    @Test
    void keepsAmbiguousEroticTermsAsSuspectEvidence() {
        AdultAssessment assessment = AdultContentAssessor.assess(new AdultAssessmentInput(
                "经典伦理片",
                null,
                null,
                null,
                null,
                "movie",
                "电影",
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        assertThat(assessment.adultRestricted()).isFalse();
        assertThat(assessment.level()).isEqualTo(AdultAssessmentLevel.SUSPECT);
        assertThat(assessment.signals()).isNotEmpty();
    }
}
