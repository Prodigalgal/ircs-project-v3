package com.prodigalgal.ircs.metadata.provider.domain;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identity clues from the original source, which survive display normalization and authority writeback. */
public record MetadataSourceEvidence(Integer titleYear, boolean episodic) {
    public static final MetadataSourceEvidence NONE = new MetadataSourceEvidence(null, false);

    private static final Pattern TITLE_YEAR = Pattern.compile(
            "(?:[\\[(【]((?:18|19|20)[0-9]{2})[\\])】]|\\h+((?:18|19|20)[0-9]{2}))\\h*$");
    private static final Pattern EPISODE_COUNT = Pattern.compile(
            "(?:全|共)?\\h*([0-9]{1,4})\\h*(?:集|话)?");

    public static MetadataSourceEvidence from(String originalTitle, String normalizedTitle, String totalEpisodes) {
        return new MetadataSourceEvidence(extractTitleYear(originalTitle, normalizedTitle), hasMultipleEpisodes(totalEpisodes));
    }

    public boolean matchesTitleYear(String yearOrDate) {
        return titleYear == null || yearOrDate != null && yearOrDate.length() >= 4
                && Integer.toString(titleYear).equals(yearOrDate.substring(0, 4));
    }

    private static Integer extractTitleYear(String originalTitle, String normalizedTitle) {
        if (originalTitle == null || normalizedTitle == null || normalizedTitle.isBlank()) {
            return null;
        }
        String original = Normalizer.normalize(originalTitle, Normalizer.Form.NFKC);
        Matcher matcher = TITLE_YEAR.matcher(original);
        if (!matcher.find()
                || !MetadataCandidateTitles.normalize(original.substring(0, matcher.start()))
                        .equals(MetadataCandidateTitles.normalize(normalizedTitle))) {
            return null;
        }
        return Integer.valueOf(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
    }

    private static boolean hasMultipleEpisodes(String value) {
        if (value == null) {
            return false;
        }
        Matcher matcher = EPISODE_COUNT.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).strip());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) > 1;
    }
}
