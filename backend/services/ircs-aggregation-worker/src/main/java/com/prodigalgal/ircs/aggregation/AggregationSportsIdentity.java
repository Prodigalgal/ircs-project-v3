package com.prodigalgal.ircs.aggregation;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Bounded in-memory fixture evidence. Scores and broadcast labels are never participant names. */
record AggregationSportsIdentity(Integer year, MonthDay date, String details, String identityDetails,
        String competition, String participants, Map<String, String> rounds, String group, CompetitionSeason competitionSeason,
        boolean fixture, boolean standaloneEvent) {

    private static final Pattern COMPACT_DATE = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])(?!\\d)");
    private static final Pattern FULL_DATE = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?");
    private static final Pattern MONTH_DAY = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern SEASON = Pattern.compile("(?<!\\d)((?:19|20)?\\d{2})\\s*[-/]\\s*((?:19|20)?\\d{2})\\s*赛季");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern SEASON_OR_STANDALONE_YEAR = Pattern.compile(
            "(" + SEASON.pattern() + ")|(?<![\\d/-])(?:19|20)\\d{2}(?![\\d/-])年?");
    private static final Pattern BROADCAST = Pattern.compile("(?i)(?:^|[\\s(（【\\[])(?:全场)?(?:回放|录像|录播|重播|直播|集锦|精华|高清|完整版|全场)(?:版)?[)）】\\]]?(?=$|[\\s)）】\\]]|世界杯|NBA|(?<=[)）】\\]]))|(?:全场回放|全场录像|集锦|录像|回放)\\s*$|(?:1080|2160)p|4k");
    private static final Pattern SCORED_VS = Pattern.compile("\\d{1,3}\\s*(?i:vs)\\s*\\d{1,3}");
    private static final Pattern SCORE = Pattern.compile("(?<=[\\p{L}])\\s*[(（]?\\s*(?:暂|比分)?\\s*\\d{1,3}\\s*[-:]\\s*\\d{1,3}\\s*[)）]?\\s*(?=[\\p{L}])(?!(?:赛季|比赛|开赛|开始))");
    private static final Pattern TRAILING_SCORE = Pattern.compile("(?:[(（]\\s*\\d{1,3}\\s*[-:]\\s*\\d{1,3}\\s*[)）]|\\s+\\d{1,3}\\s*[-:]\\s*\\d{1,3})\\s*$");
    private static final Pattern VS = Pattern.compile("(?i)vs\\.?|对阵|迎战");
    private static final Pattern ROUND = Pattern.compile(
            "(?:第[0-9零一二三四五六七八九十百两]+[轮日场局]|[0-9]+of[0-9]+决赛|半决赛|总决赛|(?<!半)(?<!总)决赛|小组赛|热身赛|常规赛|季后赛|资格赛|预选赛)");
    private static final Pattern GROUP = Pattern.compile("男子组|女子组|男单|女单|男双|女双|混双|男子|女子");
    private static final Pattern CONTEXT_END = Pattern.compile(
            "赛季|常规赛|季后赛|总决赛|半决赛|决赛|热身赛|小组赛|资格赛|预选赛|邀请赛|大师赛|锦标赛|冠军赛|公开赛|联赛|世界杯|世预赛|杯|浙BA|WNBA|NBA|(?:第[0-9零一二三四五六七八九十百两]+[轮日场局])|[男女](?:子组|单|双)|站",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_MARKER = Pattern.compile(
            "(?i)赛季|联赛|比赛|世界杯|世预赛|邀请赛|锦标赛|大师赛|杯(?:赛|小组赛|决赛|预选赛|资格赛|\\s|$)|WTA|ATP|NBA|大奖赛|巡游|(?<=\\p{IsHan})vs|vs(?=\\p{IsHan})|\\bvs\\b|对阵|迎战");
    private static final Pattern STANDALONE = Pattern.compile("巡游|大奖赛|排位赛|田径|游泳|跳水");

    static boolean looksLikeEvent(String title) {
        return StringUtils.hasText(title) && EVENT_MARKER.matcher(title).find();
    }

    static AggregationSportsIdentity parse(String title, Integer metadataYear) {
        String text = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        Integer parsedYear = null;
        MonthDay date = null;
        for (Pattern format : new Pattern[] {COMPACT_DATE, FULL_DATE, MONTH_DAY}) {
            Matcher matcher = format.matcher(text);
            if (matcher.find()) {
                int offset = format == MONTH_DAY ? 0 : 1;
                try {
                    MonthDay parsedDate = MonthDay.of(Integer.parseInt(matcher.group(1 + offset)), Integer.parseInt(matcher.group(2 + offset)));
                    Integer dateYear = offset == 1 ? Integer.valueOf(matcher.group(1)) : null;
                    if (dateYear != null && !parsedDate.isValidYear(dateYear)) { throw new DateTimeException("Invalid leap date"); }
                    date = parsedDate;
                    parsedYear = dateYear;
                    text = matcher.replaceFirst(" ");
                } catch (DateTimeException invalidDate) {
                    // Invalid dates are absent evidence, never an identity discriminator.
                    text = matcher.replaceFirst(" ");
                }
                break;
            }
        }
        if (parsedYear == null) {
            Matcher year = YEAR.matcher(SEASON.matcher(text).replaceAll(" "));
            if (year.find()) { parsedYear = Integer.valueOf(year.group(1)); }
        }
        // The edition year is already retained as a field; keep competition-season ranges intact.
        text = SEASON_OR_STANDALONE_YEAR.matcher(text).replaceAll(match -> match.group(1) == null ? " " : match.group());
        text = BROADCAST.matcher(text).replaceAll(" ");
        text = TRAILING_SCORE.matcher(text).replaceAll(" ");
        text = SCORED_VS.matcher(text).replaceAll("vs");
        text = SCORE.matcher(text).replaceAll("vs");
        text = VS.matcher(text).replaceAll("vs");
        text = text.replaceAll("([0-9]+)[/.之_]([0-9]+)决赛", "$1of$2决赛");
        Matcher season = SEASON.matcher(text);
        CompetitionSeason competitionSeason = season.find()
                ? new CompetitionSeason(Integer.parseInt(season.group(1)), Integer.parseInt(season.group(2))) : null;
        String details = key(text);
        // Keep the season label in the legacy event key. Compare it separately from the fixture.
        String identityDetails = key(SEASON.matcher(text).replaceAll(" "));
        int versus = identityDetails.indexOf("vs");
        String competition = identityDetails;
        String participants = "";
        boolean fixture = versus > 0 && versus + 2 < identityDetails.length();
        if (fixture) {
            String before = identityDetails.substring(0, versus);
            Matcher context = CONTEXT_END.matcher(before);
            int boundary = 0;
            while (context.find()) { boundary = context.end(); }
            competition = before.substring(0, boundary);
            String first = before.substring(boundary);
            String second = identityDetails.substring(versus + 2);
            if (!first.isEmpty() && !second.isEmpty()) { participants = first + "vs" + second; }
        }
        Map<String, String> rounds = rounds(details);
        String group = matches(GROUP, details);
        competition = GROUP.matcher(ROUND.matcher(competition).replaceAll("")).replaceAll("");
        Integer resolvedYear = parsedYear == null ? metadataYear : parsedYear;
        if (date != null && resolvedYear != null && !date.isValidYear(resolvedYear)) { date = null; }
        return new AggregationSportsIdentity(resolvedYear, date, details, identityDetails,
                competition, participants, rounds, group, competitionSeason, fixture, STANDALONE.matcher(details).find());
    }

    boolean conflictsWith(AggregationSportsIdentity other) {
        return knownConflict(year, other.year) || knownConflict(date, other.date)
                || (competitionSeason != null && other.competitionSeason != null
                    && !competitionSeason.matches(other.competitionSeason))
                || textConflict(participants, other.participants) || textConflict(competition, other.competition)
                || AggregationCategoryProfile.installmentsConflict(rounds, other.rounds) || textConflict(group, other.group);
    }

    boolean supportsSameEvent(AggregationSportsIdentity other) {
        // A league/day heading or an undated recurring fixture cannot prove one concrete event.
        boolean sharedYear = year != null && year.equals(other.year);
        boolean sharedSeason = competitionSeason != null && other.competitionSeason != null
                && competitionSeason.matches(other.competitionSeason);
        // If only one title carries a competition-season label, ignore it only with a shared
        // resolved year. A season label alone remains a conservative anchor and must agree.
        return !conflictsWith(other) && (sharedYear || sharedSeason) && date != null && date.equals(other.date)
                && !identityDetails.isEmpty() && identityDetails.equals(other.identityDetails)
                && ((fixture && other.fixture) || (standaloneEvent && other.standaloneEvent));
    }

    String key() {
        return "event:" + year + ":" + date + "|" + details;
    }

    private static String matches(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder found = new StringBuilder();
        while (matcher.find()) { found.append(matcher.group()).append('|'); }
        return found.toString();
    }

    private static Map<String, String> rounds(String value) {
        Map<String, String> rounds = new java.util.HashMap<>();
        Matcher matcher = ROUND.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("第")) {
                String axis = token.substring(token.length() - 1);
                Integer ordinal = AggregationTitleNormalizer.parseSeason("第" + token.substring(1, token.length() - 1) + "季");
                rounds.put(axis, String.valueOf(ordinal));
            } else {
                rounds.put("phase", token);
            }
        }
        return Map.copyOf(rounds);
    }

    private static boolean knownConflict(Object left, Object right) {
        return left != null && right != null && !Objects.equals(left, right);
    }

    private static boolean textConflict(String left, String right) {
        return !left.isEmpty() && !right.isEmpty() && !left.equals(right);
    }

    private static String key(String value) {
        return value.replaceAll("[\\p{P}\\p{Z}\\s]+", "").trim();
    }

    private record CompetitionSeason(int start, int end) {
        boolean matches(CompetitionSeason other) {
            return sameYearLabel(start, other.start) && sameYearLabel(end, other.end);
        }

        private static boolean sameYearLabel(int left, int right) {
            return left == right || ((left < 100 || right < 100) && left % 100 == right % 100);
        }
    }
}
