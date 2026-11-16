package com.prodigalgal.ircs.magnet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identity decisions use returned provider evidence, never the echoed search query. */
final class MagnetCandidateIdentityPolicy {

    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(18\\d{2}|19\\d{2}|20\\d{2})(?!\\d)");
    private static final Pattern SEASON = Pattern.compile(
            "(?i)(?:\\bs[ ._-]?(\\d{1,3})(?:[ ._-]?e\\d{1,4})?\\b|\\bseason[ ._-]*(\\d{1,3})\\b|第([一二三四五六七八九十百零〇两\\d]{1,4})季)");
    private static final Pattern EPISODE = Pattern.compile("(?i)\\be[ ._-]?\\d{1,4}\\b|第[一二三四五六七八九十百零〇两\\d]+[集话]");
    private static final Pattern RELEASE = Pattern.compile(
            "(?i)\\b(?:2160p|1080[pi]|720p|480p|4k|8k|uhd|bluray|blu-ray|bdrip|brrip|webrip|web-dl|webdl|hdtv|dvdrip|remux|x26[45]|h[ .]?26[45]|hevc|avc|aac|ac3|dts|ddp(?:\\d[ .]\\d)?|proper|repack|10bit|8bit|hdr10?|dv|atmos)\\b.*$");
    private static final Pattern PART = Pattern.compile("(?i)(?:\\bpart[ ._-]*(\\d+|iv|iii|ii|i)\\b|第([一二三四五六七八九十百零〇两\\d]+)[部篇])");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}[ ._/-]?\\d{2}[ ._/-]?\\d{2}(?!\\d)");

    private MagnetCandidateIdentityPolicy() {
    }

    record Target(String title, String subtitle, String aliasTitle, Integer year, Integer season,
                  String categoryCode, String imdbId, String tmdbId, String doubanId) {
    }

    enum Disposition { REJECT, CANDIDATE, AUTO_APPROVE }

    record Decision(Disposition disposition, int score, String reason) {
    }

    static Decision evaluate(Target target, MagnetProviderCandidate candidate) {
        if (target == null || candidate == null) {
            return reject("MISSING_IDENTITY_INPUT");
        }
        Map<String, Object> evidence = candidate.providerEvidence() == null ? Map.of() : candidate.providerEvidence();
        IdEvidence ids = ids(target, evidence);
        if (ids.conflict()) {
            return reject("RETURNED_EXTERNAL_ID_CONFLICT");
        }
        String source = value(evidence.get("titleSource")).toUpperCase(Locale.ROOT);
        boolean actualTitle = source.equals("CANDIDATE") && !value(candidate.title()).isBlank();
        if (value(candidate.title()).isBlank()) { return pending(20, "MISSING_INDEPENDENT_CANDIDATE_TITLE"); }

        List<String> names = targetNames(target);
        if (names.isEmpty()) {
            return pending(20, "MISSING_TARGET_TITLE");
        }
        String returnedTitle = candidate.title();
        Set<Integer> targetSeasons = seasons(String.join(" ", names));
        if (target.season() != null && target.season() > 0) {
            targetSeasons.add(target.season());
        }
        if (targetSeasons.size() > 1) {
            return pending(35, "TARGET_SEASON_AMBIGUOUS");
        }
        Set<Integer> candidateSeasons = seasons(returnedTitle);
        if (!targetSeasons.isEmpty() && !candidateSeasons.isEmpty() && !targetSeasons.containsAll(candidateSeasons)) {
            return reject("SEASON_CONFLICT");
        }
        Set<String> targetParts = parts(String.join(" ", names));
        Set<String> candidateParts = parts(returnedTitle);
        if (!targetParts.isEmpty() && !candidateParts.isEmpty() && !targetParts.equals(candidateParts)) {
            return reject("PART_CONFLICT");
        }
        Set<Integer> candidateYears = years(returnedTitle, names);
        Integer returnedYear = validYear(value(evidence.get("returnedYear")));
        if (returnedYear != null) {
            candidateYears.add(returnedYear);
        }
        boolean movie = isMovie(target.categoryCode());
        if (movie && !candidateSeasons.isEmpty()) {
            return reject("MOVIE_SERIES_CONFLICT");
        }
        if (movie && target.year() != null && !candidateYears.isEmpty()
                && (candidateYears.size() != 1 || !candidateYears.contains(target.year()))) {
            return reject("MOVIE_YEAR_CONFLICT");
        }
        Set<String> targetVersions = versions(String.join(" ", names));
        Set<String> candidateVersions = versions(returnedTitle);
        if (!targetVersions.isEmpty() && !candidateVersions.isEmpty() && !targetVersions.equals(candidateVersions)) {
            return reject("EDITION_CONFLICT");
        }

        Set<Integer> titleYears = new HashSet<>(candidateYears);
        if (target.year() != null) {
            titleYears.add(target.year());
        }
        String candidateName = cleanTitle(returnedTitle, titleYears);
        List<String> normalizedNames = names.stream().map(name -> cleanTitle(name, target.year() == null
                ? Set.of() : Set.of(target.year()))).filter(name -> !name.isBlank()).distinct().toList();
        if (candidateName.isBlank() || normalizedNames.isEmpty()) {
            return pending(20, "MISSING_SUBSTANTIVE_TITLE");
        }
        boolean exact = normalizedNames.contains(candidateName);
        // Preserve all digits in names: a sequel number is identity, not release noise.
        boolean numericConflict = !numbers(candidateName).isEmpty() && normalizedNames.stream()
                .filter(name -> !numbers(name).isEmpty())
                .allMatch(name -> !numbers(name).equals(numbers(candidateName)));
        boolean targetHasNumbers = normalizedNames.stream().allMatch(name -> !numbers(name).isEmpty());
        if (!exact && (targetHasNumbers && numericConflict
                || normalizedNames.stream().anyMatch(name -> sequelConflict(name, candidateName)))) {
            return reject("TITLE_NUMBER_CONFLICT");
        }
        double similarity = normalizedNames.stream().mapToDouble(name -> similarity(name, candidateName)).max().orElse(0);
        boolean sameScript = normalizedNames.stream().anyMatch(name -> sameScript(name, candidateName));
        boolean partial = !candidateName.isBlank() && normalizedNames.stream()
                .anyMatch(name -> name.contains(candidateName) || candidateName.contains(name));
        if (!exact && !partial && sameScript && similarity < 0.35) {
            return reject("TITLE_CONFLICT");
        }
        if (!actualTitle) {
            return pending(ids.match() ? 70 : 20, "MISSING_INDEPENDENT_CANDIDATE_TITLE");
        }
        if (!targetVersions.equals(candidateVersions)) {
            return pending(70, "EDITION_NOT_CONFIRMED");
        }
        if (!targetSeasons.isEmpty() && candidateSeasons.isEmpty()) {
            return pending(70, "SEASON_NOT_CONFIRMED");
        }
        if (targetSeasons.isEmpty() && !candidateSeasons.isEmpty()) {
            return pending(65, "TARGET_SEASON_NOT_CONFIRMED");
        }
        if (targetParts.isEmpty() != candidateParts.isEmpty()) {
            return pending(65, "PART_NOT_CONFIRMED");
        }
        if (isSpecificCategory(target.categoryCode())) {
            String fullName = cleanTitle(value(target.title()) + " " + value(target.subtitle()),
                    target.year() == null ? Set.of() : Set.of(target.year()));
            Set<String> targetDates = dates(value(target.title()) + " " + value(target.subtitle()));
            Set<String> candidateDates = dates(returnedTitle);
            if (!targetDates.isEmpty() && !candidateDates.isEmpty() && !targetDates.equals(candidateDates)) {
                return reject("EVENT_DATE_CONFLICT");
            }
            boolean explicitNumber = value(target.title()).matches("(?i).*[a-z]{2,}[-_ ]+[0-9]{2,}.*");
            if (!candidateName.equals(fullName)
                    || targetDates.isEmpty() && value(target.subtitle()).isBlank() && !explicitNumber) {
                return pending(65, "SPECIFIC_PROGRAM_IDENTITY_NOT_CONFIRMED");
            }
            return approve("FULL_PROGRAM_TITLE_CONFIRMED", 94);
        }
        if (ids.match() && (exact || !sameScript)) {
            return approve("RETURNED_EXTERNAL_ID_CONFIRMED", 99);
        }
        boolean yearConfirmed = target.year() != null && candidateYears.size() == 1 && candidateYears.contains(target.year());
        boolean seasonConfirmed = !targetSeasons.isEmpty() && targetSeasons.equals(candidateSeasons);
        if (exact && (movie ? yearConfirmed : yearConfirmed || seasonConfirmed)) {
            return approve("TITLE_AND_YEAR_OR_SEASON_CONFIRMED", 95);
        }
        return pending(exact ? 78 : similarity >= 0.65 ? 60 : 35,
                exact ? "TITLE_WITHOUT_ENOUGH_CORROBORATION" : "TITLE_NOT_CONFIRMED");
    }

    private static IdEvidence ids(Target target, Map<String, Object> evidence) {
        String[] expected = {id(target.imdbId(), true), id(target.tmdbId(), false), id(target.doubanId(), false)};
        String[] returned = {id(value(evidence.get("returnedImdbId")), true),
                id(value(evidence.get("returnedTmdbId")), false), id(value(evidence.get("returnedDoubanId")), false)};
        boolean match = false;
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isEmpty() && !returned[i].isEmpty()) {
                if (!expected[i].equals(returned[i])) {
                    return new IdEvidence(false, true);
                }
                match = true;
            }
        }
        return new IdEvidence(match, false);
    }

    private static String id(String raw, boolean imdb) {
        String normalized = value(raw).toLowerCase(Locale.ROOT);
        if (imdb) {
            return normalized.matches("tt[0-9]{7,10}") && !normalized.substring(2).matches("0+") ? normalized : "";
        }
        return normalized.matches("[1-9][0-9]{0,11}") ? normalized : "";
    }

    private static List<String> targetNames(Target target) {
        List<String> names = new ArrayList<>();
        if (!value(target.title()).isBlank()) {
            names.add(target.title());
            if (!value(target.subtitle()).isBlank()) {
                names.add(target.title() + " " + target.subtitle());
            }
        }
        for (String alias : value(target.aliasTitle()).split("[|/;；\\n]")) {
            if (!alias.isBlank()) {
                names.add(alias);
            }
        }
        return names;
    }

    private static String cleanTitle(String raw, Set<Integer> knownYears) {
        String title = Normalizer.normalize(value(raw), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        title = title.replaceAll("(?i)\\.(?:mkv|mp4|avi|torrent)$", "");
        title = RELEASE.matcher(title.replace('.', ' ').replace('_', ' ')).replaceAll("");
        title = SEASON.matcher(title).replaceAll(" ");
        title = EPISODE.matcher(title).replaceAll(" ");
        for (Integer year : knownYears) {
            String stripped = title.replaceAll("(?<!\\d)" + year + "(?!\\d)", " ");
            if (!stripped.replaceAll("[^\\p{L}\\p{N}]", "").isBlank()) {
                title = stripped;
            }
        }
        return title.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Set<Integer> years(String title, List<String> targetNames) {
        Set<Integer> years = new HashSet<>();
        Matcher matcher = YEAR.matcher(title);
        while (matcher.find()) {
            String found = matcher.group();
            // 1917, 2001 and similar digits may be the work's title.
            if (targetNames.stream().noneMatch(name -> YEAR.matcher(name).results().anyMatch(part -> part.group().equals(found)))) {
                years.add(Integer.parseInt(found));
            }
        }
        return years;
    }

    private static Integer validYear(String value) {
        return value.matches("(?:18|19|20)[0-9]{2}") ? Integer.valueOf(value) : null;
    }

    private static Set<Integer> seasons(String title) {
        Set<Integer> result = new HashSet<>();
        Matcher matcher = SEASON.matcher(title.replace('_', ' '));
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    int number = ordinal(matcher.group(i));
                    if (number > 0) result.add(number);
                }
            }
        }
        return result;
    }

    private static Set<String> parts(String title) {
        Set<String> result = new HashSet<>();
        Matcher matcher = PART.matcher(title.replace('_', ' '));
        while (matcher.find()) {
            String part = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            result.add(Integer.toString(ordinal(part)));
        }
        return result;
    }

    private static int ordinal(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        if (text.matches("[0-9]{1,4}")) return Integer.parseInt(text);
        if (text.matches("i{1,3}|iv")) return text.equals("iv") ? 4 : text.length();
        int total = 0;
        int digit = 0;
        for (char character : text.toCharArray()) {
            int index = "零一二三四五六七八九".indexOf(character);
            if (index >= 0) digit = index;
            else if (character == '两') digit = 2;
            else if (character == '十' || character == '百') {
                total += (digit == 0 ? 1 : digit) * (character == '十' ? 10 : 100);
                digit = 0;
            }
        }
        return total + digit;
    }

    private static Set<String> versions(String title) {
        Set<String> result = new HashSet<>();
        String text = value(title).toLowerCase(Locale.ROOT);
        if (text.matches(".*(?:director.?s?[ ._-]*cut|导演剪辑).*")) result.add("DIRECTORS_CUT");
        if (text.matches(".*(?:extended|加长|延长版).*")) result.add("EXTENDED");
        if (text.matches(".*(?:theatrical|院线版|公映版).*")) result.add("THEATRICAL");
        if (text.matches(".*(?:uncut|unrated|未删减|未分级).*")) result.add("UNCUT");
        return result;
    }

    private static boolean sequelConflict(String expected, String returned) {
        String expectedBase = expected.replaceAll("\\d+", "");
        String returnedBase = returned.replaceAll("\\d+", "");
        return !expectedBase.isBlank() && expectedBase.equals(returnedBase) && !expected.equals(returned);
    }

    private static Set<String> numbers(String title) {
        Set<String> result = new HashSet<>();
        Pattern.compile("\\d+").matcher(title).results().forEach(match -> result.add(match.group()));
        return result;
    }

    private static double similarity(String left, String right) {
        if (left.equals(right)) return 1;
        Set<String> a = bigrams(left);
        Set<String> b = bigrams(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> overlap = new HashSet<>(a);
        overlap.retainAll(b);
        return 2.0 * overlap.size() / (a.size() + b.size());
    }

    private static Set<String> bigrams(String title) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < title.length(); i++) result.add(title.substring(i, i + 2));
        return result;
    }

    private static boolean sameScript(String left, String right) {
        String leftScript = titleScript(left);
        return !leftScript.equals("UNKNOWN") && leftScript.equals(titleScript(right));
    }

    private static String titleScript(String title) {
        Set<Character.UnicodeScript> scripts = new HashSet<>();
        title.codePoints().filter(Character::isLetter).forEach(code -> scripts.add(Character.UnicodeScript.of(code)));
        if (scripts.contains(Character.UnicodeScript.HIRAGANA) || scripts.contains(Character.UnicodeScript.KATAKANA)) return "JAPANESE";
        if (scripts.contains(Character.UnicodeScript.HANGUL)) return "KOREAN";
        if (scripts.contains(Character.UnicodeScript.HAN)) return "HAN";
        if (scripts.contains(Character.UnicodeScript.LATIN)) return "LATIN";
        return scripts.isEmpty() ? "UNKNOWN" : scripts.stream().map(Character.UnicodeScript::name).sorted().toList().toString();
    }

    private static Set<String> dates(String title) {
        Set<String> result = new HashSet<>();
        DATE.matcher(title).results().forEach(match -> result.add(match.group().replaceAll("\\D", "")));
        return result;
    }

    private static boolean isMovie(String category) {
        return value(category).toUpperCase(Locale.ROOT).matches(".*(?:MOVIE|FILM|电影).*");
    }

    private static boolean isSpecificCategory(String category) {
        return value(category).isBlank() || value(category).toUpperCase(Locale.ROOT)
                .matches(".*(?:SPORT|NEWS|SHORT|EDUCATION|MUSIC|ADULT|OTHER|UNKNOWN|赛事|体育|新闻|短剧|教育|音乐|成人|其他|未知).*");
    }

    private static String value(Object value) { return value == null ? "" : value.toString().trim(); }
    private static Decision reject(String reason) { return new Decision(Disposition.REJECT, 0, reason); }
    private static Decision pending(int score, String reason) { return new Decision(Disposition.CANDIDATE, score, reason); }
    private static Decision approve(String reason, int score) { return new Decision(Disposition.AUTO_APPROVE, score, reason); }
    private record IdEvidence(boolean match, boolean conflict) { }
}
