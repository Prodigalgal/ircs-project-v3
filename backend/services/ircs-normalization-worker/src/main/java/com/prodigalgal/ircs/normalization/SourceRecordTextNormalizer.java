package com.prodigalgal.ircs.normalization;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.github.houbb.opencc4j.util.ZhJpConverterUtil;
import com.hankcs.hanlp.HanLP;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contentmetadata.PersonNames;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SourceRecordTextNormalizer {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:18|19|20)\\d{2}");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern EDGE_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{M}\\p{N}]+|[^\\p{L}\\p{M}\\p{N}]+$");
    private static final Pattern TITLE_LEADING_EDGE_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{M}\\p{N}]+");
    private static final Pattern TITLE_TRAILING_EDGE_SYMBOLS = Pattern.compile("[^\\p{L}\\p{M}\\p{N})]+$");
    private static final Pattern BRACKET_YEAR_PATTERN = Pattern.compile("[\\[(]\\s*(?:18|19|20)\\d{2}\\s*[\\])]");
    private static final Pattern LEADING_RELEASE_GROUP_PATTERN = Pattern.compile(
            "(?i)^(?:\\[[a-z0-9][a-z0-9._ -]{1,31}]|\\([a-z0-9][a-z0-9._ -]{1,31}\\))\\s*");
    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("(?i)\\.(mp4|mkv|avi|rmvb|ts|mov)$");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(?:www\\.|https?://|ftp://)[a-z0-9.-]+\\.[a-z]{2,}(?:/[^\\s]*)?");
    private static final Pattern RELEASE_NOISE = Pattern.compile(
            "(?i)\\b(2160p|1440p|1080p|720p|480p|web-dl|webrip|bluray|blu-ray|hdtv|x264|x265|h264|h265|hevc|aac|ddp|hdr|4k|8k)\\b.*$");
    private static final String BRACKET_NOISE_TOKEN =
            "2160p|1440p|1080p|1080i|720p|720i|480p|uhd|fhd|hd|bd|tc|cam|dvd|web-dl|webrip|"
                    + "blu-ray|bluray|remux|bdrip|hdtv|dvdrip|hdr|hevc|x264|x265|h264|h265|aac|"
                    + "ac3|dts|ddp|webdl|mp4|mkv|avi|ts|高清|蓝光|超清|标清|枪版|正式版|修正版|"
                    + "收藏版|纯净版|网络版|tv版|短剧版|官采|国语|粤语|英语|日语|韩语|中英|中日|"
                    + "中韩|简繁|字幕|配音|普通话|汉语|双语|中字|双字|国粤|港台|台配|中配|"
                    + "日配|英配|韩配|chs|cht|eng|jpn|kr|big5|gb";
    private static final Pattern PURE_BRACKET_NOISE = Pattern.compile(
            "(?i)^[\\s\\p{Punct}_]*(?:\\d+|" + BRACKET_NOISE_TOKEN + ")(?:[\\s\\p{Punct}_]*(?:\\d+|"
                    + BRACKET_NOISE_TOKEN + "))*[\\s\\p{Punct}_]*$");
    private static final Pattern PARENTHESES_CONTENT = Pattern.compile("\\(([^)]*)(\\)|$)");
    private static final Pattern BRACKET_CONTENT = Pattern.compile("\\[([^]]*)(\\]|$)");
    private static final Pattern BILINGUAL_NUMBER_REPEAT = Pattern.compile(
            "^(.+?)(\\d+)\\s+([a-zA-Z0-9\\s\\p{Punct}]+)\\s+\\2$");
    private static final Pattern MONOLINGUAL_REPEAT = Pattern.compile("^(.+?)[\\s:：-]*\\1$");
    private static final Pattern CHINESE_ORDINAL = Pattern.compile("第([零一二三四五六七八九十百两]+)([季部])");
    private static final Pattern SEASON_PATTERN = Pattern.compile(
            "(?i)(?:第\\s*([0-9]{1,3})\\s*季|\\bseason\\s*[-_:]?\\s*([0-9]{1,3})\\b|\\bs\\s*([0-9]{1,3})\\b)");
    private static final Pattern SEASON_ONLY_PATTERN = Pattern.compile(
            "(?i)^(?:第\\s*[0-9]{1,3}\\s*季|season\\s*[-_:]?\\s*[0-9]{1,3}|s\\s*[0-9]{1,3})$");
    private static final Pattern COLON_TITLE_SPLIT = Pattern.compile("^(.+?)\\s*:\\s*(.+)$");
    private static final Pattern DASH_TITLE_SPLIT = Pattern.compile("^(.+?)\\s+[—–-]\\s+(.+)$");
    private static final Pattern HAN_SPACE_TITLE_SPLIT = Pattern.compile("^(\\S.+?)\\s+([^\\s].+)$");
    private static final Pattern BRACKET_SUBTITLE_SPLIT = Pattern.compile("^(.+?)\\s*\\(([^)]{1,80})\\)\\s*$");
    private static final Pattern COMPACT_CN_SPLIT = Pattern.compile("^([\\u4e00-\\u9fa5]{2,})(\\d+)([^\\d\\s].*)$");
    private static final Pattern TRAILING_NUM_PATTERN = Pattern.compile("^(.+?)\\s*(?<!\\d)(\\d{1,2})$");
    private static final Pattern UNICODE_ROMAN_PATTERN = Pattern.compile("[\\u2160-\\u216B]");
    private static final Pattern SPORTS_MATCHUP_PATTERN = Pattern.compile(
            "^(.+?)\\s+([^\\s]+\\s*(?i:vs|对阵)\\s*[^\\s]+(?:\\s.*)?)$");
    private static final Pattern SHORT_DRAMA_COMMA_SPLIT = Pattern.compile("^(.+?)[,，]\\s*(.+)$");
    private static final Pattern RELATION_SPLIT_PATTERN = Pattern.compile("[,，、/|;；]+");

    private final ObjectMapper objectMapper;
    private final DescriptionMetadataExtractor descriptionMetadataExtractor;
    private final RawRelationAliasPolicy rawRelationAliasPolicy;
    public SourceRecordTextNormalizer(
            ObjectMapper objectMapper,
            DescriptionMetadataExtractor descriptionMetadataExtractor,
            RawRelationAliasPolicy rawRelationAliasPolicy) {
        this.objectMapper = objectMapper;
        this.descriptionMetadataExtractor = descriptionMetadataExtractor;
        this.rawRelationAliasPolicy = rawRelationAliasPolicy;
    }

    @PostConstruct
    public void verifyRuntimeSupport() {
        String converted = standardize("繁體國語與音楽 ABC");
        if (!converted.contains("繁体国语") || !converted.contains("音乐") || !converted.contains("ABC")) {
            throw new IllegalStateException("Text conversion dictionaries did not produce the required normalized output");
        }
    }

    public SourceRecordPatch normalize(SourceRecordRecord record, JsonNode metadata) {
        Set<String> locked = parseLockedFields(record.lockedFields());
        NormalizationProfile profile = NormalizationProfile.select(record, metadata);
        String title = record.title();
        String aliasTitle = record.aliasTitle();
        Integer season = record.season();
        String subtitle = record.subtitle();
        String description = record.description();
        String year = record.year();
        String area = record.area();
        String language = record.rawLanguageStr();
        String remarks = record.remarks();
        BigDecimal score = record.score();
        String totalEpisodes = record.totalEpisodes();
        String duration = record.duration();
        String doubanId = record.doubanId();
        String tmdbId = record.tmdbId();
        String imdbId = record.imdbId();
        String rottenTomatoesId = record.rottenTomatoesId();
        UUID rawCategoryDataSourceId = null;
        String rawCategorySourceCode = null;
        String rawCategorySourceName = null;

        if (!locked.contains("year")) {
            year = normalizeYear(firstText(metadata, year, "year", "releaseYear"));
        }
        String sourceTitle = firstText(metadata, title, "title", "name");
        TitleStructure titleStructure = profile == NormalizationProfile.SPORTS
                ? extractSportsTitleStructure(sourceTitle)
                : extractTitleStructure(sourceTitle, year, profile);
        if (!locked.contains("title")) {
            title = StringUtils.hasText(titleStructure.mainTitle())
                    ? titleStructure.mainTitle()
                    : cleanTitle(sourceTitle, year);
        }
        if (!locked.contains("season") && profile.usesScreenSeasons()) {
            season = parseSeason(firstText(metadata, null, "season"));
            if (season == null) {
                season = titleStructure.season();
            }
            if (season == null) {
                season = record.season();
            }
        }
        if (!locked.contains("season") && !profile.usesScreenSeasons()) {
            season = null;
        }
        if (!isSubtitleLocked(locked)) {
            subtitle = profile.preserveIdentityPunctuation()
                    ? cleanIdentityTitle(firstText(metadata, subtitle, "subtitle", "sub_title"))
                    : cleanSubtitle(firstText(metadata, subtitle, "subtitle", "sub_title"), year);
            if (!StringUtils.hasText(subtitle)) {
                subtitle = titleStructure.subtitle();
            }
        }
        if (!locked.contains("aliasTitle")) {
            String sourceAliases = firstText(metadata, aliasTitle, "aliasTitle", "subTitle", "alias");
            String completeSourceTitle = cleanTitle(sourceTitle, year, false);
            if (StringUtils.hasText(completeSourceTitle)
                    && !completeSourceTitle.equals(removeRedundantBilingualTitle(completeSourceTitle))) {
                sourceAliases = StringUtils.hasText(sourceAliases)
                        ? sourceAliases + " / " + completeSourceTitle
                        : completeSourceTitle;
            }
            aliasTitle = cleanAliasTitle(sourceAliases, title, season, subtitle);
        }
        if (!locked.contains("description")) {
            description = cleanDescription(firstText(metadata, description, "description", "desc", "plot"));
        }
        if (!locked.contains("area")) {
            area = trimToLength(firstText(metadata, area, "area", "region"), 50);
        }
        if (!locked.contains("language")) {
            language = trimToLength(firstText(metadata, language, "language", "lang"), 255);
        }
        if (!locked.contains("remarks")) {
            remarks = trimToLength(firstText(metadata, remarks, "remarks", "status"), 255);
        }
        if (!locked.contains("score")) {
            score = parseScore(metadata, score);
        }
        if (!locked.contains("totalEpisodes")) {
            totalEpisodes = trimToLength(firstText(metadata, totalEpisodes, "totalEpisodes", "episodes"), 50);
        }
        if (!locked.contains("duration")) {
            duration = trimToLength(firstText(metadata, duration, "duration", "runtime"), 50);
        }
        if (!locked.contains("doubanId")) {
            doubanId = AuthoritativeIds.normalizeDouban(firstRawText(metadata, doubanId, "doubanId"));
        }
        if (!locked.contains("tmdbId")) {
            tmdbId = AuthoritativeIds.normalizeTmdb(firstRawText(metadata, tmdbId, "tmdbId"));
        }
        if (!locked.contains("imdbId")) {
            imdbId = AuthoritativeIds.normalizeImdb(firstRawText(metadata, imdbId, "imdbId"));
        }
        if (!locked.contains("rottenTomatoesId")) {
            rottenTomatoesId = AuthoritativeIds.normalizeRottenTomatoes(
                    firstRawText(metadata, rottenTomatoesId, "rottenTomatoesId", "rtId"));
        }
        if (!isCategoryLocked(locked)) {
            rawCategoryDataSourceId = firstUuid(metadata, record.dataSourceId(), "dataSourceId");
            rawCategorySourceCode = normalizeId(firstText(
                    metadata, null, "rawTypeId", "sourceCategoryCode", "categoryId", "categoryCode", "typeId"), 100);
            rawCategorySourceName = trimToLength(firstText(
                    metadata, null, "rawTypeName", "sourceCategoryName", "categoryName", "category", "type", "videoType"), 255);
            if (!StringUtils.hasText(rawCategorySourceCode) && StringUtils.hasText(rawCategorySourceName)) {
                rawCategorySourceCode = normalizeId(rawCategorySourceName, 100);
            }
        }
        Set<String> rawGenreValues = locked.contains("genres")
                ? Set.of()
                : rawRelationAliasPolicy.expandGenres(relationValues(metadata, "genreNames", "genres", "genre"));
        if (!locked.contains("genres") && StringUtils.hasText(rawCategorySourceName)) {
            Set<String> sourceCategoryGenreValues =
                    rawRelationAliasPolicy.sourceCategoryGenreValues(rawCategorySourceName);
            if (!sourceCategoryGenreValues.isEmpty()) {
                LinkedHashSet<String> mergedGenreValues = new LinkedHashSet<>(rawGenreValues);
                mergedGenreValues.addAll(sourceCategoryGenreValues);
                rawGenreValues = mergedGenreValues;
            }
        }
        Set<String> rawLanguageValues = locked.contains("language")
                ? Set.of()
                : rawRelationAliasPolicy.expandLanguages(relationValues(metadata, "language", "lang"));
        Set<String> rawAreaValues = locked.contains("area")
                ? Set.of()
                : rawRelationAliasPolicy.expandAreas(relationValues(metadata, "area", "region"));
        Set<String> actorValues = locked.contains("actors")
                ? Set.of()
                : personValues(metadata, "actorNames", "actors", "actor");
        Set<String> directorValues = locked.contains("directors")
                ? Set.of()
                : personValues(metadata, "directorNames", "directors", "director");
        if (!locked.contains("actors") && (profile == NormalizationProfile.MUSIC || profile == NormalizationProfile.EDUCATION)) {
            LinkedHashSet<String> performers = new LinkedHashSet<>(actorValues);
            performers.addAll(profile == NormalizationProfile.MUSIC
                    ? personValues(metadata, "artistNames", "artists", "singers", "performers")
                    : personValues(metadata, "lecturerNames", "instructors", "teachers"));
            actorValues = PersonNames.normalize(performers);
        }
        if (shouldExtractPeopleFromDescription(locked, actorValues, directorValues, description)) {
            DescriptionMetadataExtractor.ExtractedMetadata extracted =
                    descriptionMetadataExtractor.extract(PersonNames.normalizeText(firstRawText(
                            metadata, record.description(), "description", "desc", "plot")));
            if (!locked.contains("actors") && actorValues.isEmpty()) {
                actorValues = trimPersonValues(extracted.actors());
            }
            if (!locked.contains("directors") && directorValues.isEmpty()) {
                directorValues = trimPersonValues(extracted.directors());
            }
        }

        return new SourceRecordPatch(
                trimToLength(title, 255),
                trimToLength(aliasTitle, 255),
                season,
                trimToLength(subtitle, 255),
                description,
                trimToLength(year, 20),
                area,
                language,
                remarks,
                score,
                totalEpisodes,
                duration,
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                rawCategoryDataSourceId,
                rawCategorySourceCode,
                rawCategorySourceName,
                rawGenreValues,
                rawLanguageValues,
                rawAreaValues,
                actorValues,
                directorValues);
    }

    private boolean shouldExtractPeopleFromDescription(
            Set<String> locked,
            Set<String> actorValues,
            Set<String> directorValues,
            String description) {
        return StringUtils.hasText(description)
                && ((!locked.contains("actors") && actorValues.isEmpty())
                || (!locked.contains("directors") && directorValues.isEmpty()));
    }

    Set<String> parseLockedFields(String raw) {
        Set<String> locked = new HashSet<>();
        if (!StringUtils.hasText(raw)) {
            return locked;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                node.forEach(item -> {
                    if (item.isTextual() && StringUtils.hasText(item.asText())) {
                        locked.add(item.asText());
                    }
                });
            } else if (node.isObject()) {
                Iterator<String> names = node.propertyNames().iterator();
                while (names.hasNext()) {
                    String name = names.next();
                    JsonNode value = node.get(name);
                    if (value == null || value.asBoolean(true)) {
                        locked.add(name);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return locked;
    }

    private String firstRawText(JsonNode metadata, String fallback, String... names) {
        for (String name : names) {
            JsonNode value = metadata.get(name);
            if (value != null && !value.isNull()) {
                String text = value.isTextual() ? value.asText() : value.toString();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return fallback;
    }

    private String firstText(JsonNode metadata, String fallback, String... names) {
        for (String name : names) {
            JsonNode value = metadata.get(name);
            if (value != null && !value.isNull()) {
                String text = value.isTextual() ? value.asText() : value.toString();
                if (StringUtils.hasText(text)) {
                    return standardize(text.trim());
                }
            }
        }
        return fallback;
    }

    private UUID firstUuid(JsonNode metadata, UUID fallback, String... names) {
        for (String name : names) {
            JsonNode value = metadata.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.isTextual() ? value.asText() : value.asText(value.toString());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }

    private boolean isCategoryLocked(Set<String> locked) {
        return locked.contains("category")
                || locked.contains("dataSourceCategory");
    }

    private boolean isSubtitleLocked(Set<String> locked) {
        return locked.contains("subtitle") || locked.contains("sub_title");
    }

    private String cleanTitle(String value, String year) {
        return cleanTitle(value, year, true);
    }

    private String cleanTitle(String value, String year, boolean removeBilingualRepetition) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String title = standardize(value);
        title = normalizeTitleSymbols(title);
        title = repairBrackets(title);
        title = LEADING_RELEASE_GROUP_PATTERN.matcher(title).replaceAll("");
        title = decodeBasicHtmlEntities(title);
        title = URL_PATTERN.matcher(title).replaceAll("");
        title = FILE_EXTENSION_PATTERN.matcher(title).replaceAll("");
        title = BRACKET_YEAR_PATTERN.matcher(title).replaceAll(" ");
        title = normalizeChineseOrdinals(title);
        title = removeSelfRepetition(title);
        if (removeBilingualRepetition) {
            title = removeRedundantBilingualTitle(title);
        }
        title = RELEASE_NOISE.matcher(title).replaceAll("");
        title = processV1BracketContents(title);
        title = FILE_EXTENSION_PATTERN.matcher(title.trim()).replaceAll("");
        title = title.replaceAll("[/|\\\\&,·・　]+", " ");
        title = cleanTitleEdges(title);
        title = SPACE_PATTERN.matcher(title).replaceAll(" ").trim();
        String normalizedYear = normalizeYear(year);
        if (hasTrailingReleaseYear(title, normalizedYear)) {
            title = title.substring(0, title.length() - normalizedYear.length()).trim();
            title = cleanTitleEdges(title);
        }
        if (removeBilingualRepetition) {
            title = removeRedundantBilingualTitle(title);
        }
        title = SPACE_PATTERN.matcher(title).replaceAll(" ").trim();
        return title;
    }

    private TitleStructure extractTitleStructure(String sourceTitle, String year, NormalizationProfile profile) {
        String processing = profile.preserveIdentityPunctuation()
                ? cleanIdentityTitle(sourceTitle) : cleanTitle(sourceTitle, year);
        if (!StringUtils.hasText(processing)) {
            return new TitleStructure(null, null, null);
        }
        if (profile.retainsUnstructuredIdentity()) {
            return new TitleStructure(processing, null, null);
        }
        if (profile.usesScreenSeasons()) {
            processing = normalizeChineseOrdinals(processing);
        }
        String cleanedTitle = processing;
        SeasonMatch seasonMatch = findSeasonMatch(processing, profile);
        Integer season = seasonMatch == null ? null : seasonMatch.season();
        String subtitle = null;

        if (seasonMatch != null) {
            String pre = processing.substring(0, seasonMatch.start()).trim();
            String post = processing.substring(seasonMatch.end()).trim();
            post = cleanTitleEdges(post);
            if (StringUtils.hasText(pre)) {
                processing = cleanTitleEdges(pre);
                String cleanSub = cleanTitleEdges(removeSeasonPatterns(post));
                if (StringUtils.hasText(cleanSub)
                        && (profile == NormalizationProfile.SHORT_DRAMA || !isV1NoiseContent(cleanSub))) {
                    subtitle = cleanSub;
                }
            } else {
                processing = cleanTitleEdges(post);
            }
        }

        if (!StringUtils.hasText(subtitle)) {
            TitleSplit split = profile == NormalizationProfile.SHORT_DRAMA
                    ? splitShortDramaTitle(processing) : splitV1TitleStructure(processing, year);
            if (split != null) {
                processing = split.mainTitle();
                subtitle = split.subtitle();
            } else if (season == null && profile.inferBareSequence()) {
                Matcher compact = COMPACT_CN_SPLIT.matcher(processing);
                if (compact.find()) {
                    String number = compact.group(2);
                    if (!number.startsWith("0") && number.length() <= 2
                            && !continuesIdentityNumber(compact.group(3))) {
                        Integer parsed = parsePositiveSeason(number);
                        if (parsed != null) {
                            processing = compact.group(1).trim();
                            season = parsed;
                            subtitle = compact.group(3).trim();
                        }
                    }
                }
            }
        }
        if (!StringUtils.hasText(subtitle) && containsHan(processing)
                && profile != NormalizationProfile.SHORT_DRAMA) {
            TitleSplit split = splitHanSpaceTitle(processing, year);
            if (split != null) {
                processing = split.mainTitle();
                subtitle = split.subtitle();
            }
        }
        if (season == null && profile.inferBareSequence() && StringUtils.hasText(processing) && !processing.matches("^\\d+$")) {
            Matcher trailing = TRAILING_NUM_PATTERN.matcher(processing);
            if (trailing.find()) {
                String potentialTitle = trailing.group(1).trim();
                String number = trailing.group(2);
                Integer parsed = parsePositiveSeason(number);
                if (parsed != null
                        && !number.startsWith("0")
                        && potentialTitle.length() >= 2
                        && containsHan(potentialTitle)
                        && !potentialTitle.endsWith(".")
                        && !endsWithPartOrVolumeMarker(potentialTitle)) {
                    processing = potentialTitle;
                    season = parsed;
                }
            }
        }
        if (StringUtils.hasText(subtitle)) {
            subtitle = profile.preserveIdentityPunctuation()
                    ? cleanIdentityTitle(subtitle) : cleanSubtitle(subtitle, year);
            if (!StringUtils.hasText(subtitle)
                    || (!profile.preserveIdentityPunctuation() && isV1NoiseContent(subtitle))) {
                subtitle = null;
            }
        }
        if (profile.usesScreenSeasons()) {
            processing = removeSeasonPatterns(processing);
        }
        if (StringUtils.hasText(subtitle) && processing.endsWith(subtitle)) {
            processing = processing.substring(0, processing.length() - subtitle.length()).trim();
        }
        String mainTitle = cleanTitleEdges(processing);
        return new TitleStructure(StringUtils.hasText(mainTitle) ? mainTitle : cleanedTitle, season, subtitle);
    }

    private SeasonMatch findSeasonMatch(String value, NormalizationProfile profile) {
        if (!StringUtils.hasText(value) || !profile.usesScreenSeasons()) {
            return null;
        }
        Matcher matcher = SEASON_PATTERN.matcher(value);
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                Integer season = parseSeasonToken(matcher.group(i));
                if (season != null) {
                    return new SeasonMatch(season, matcher.start(), matcher.end());
                }
            }
        }
        if (!profile.inferBareSequence()) {
            return null;
        }
        matcher = UNICODE_ROMAN_PATTERN.matcher(value);
        while (matcher.find()) {
            if (endsWithPartOrVolumeMarker(value.substring(0, matcher.start()))) {
                continue;
            }
            Integer season = parseUnicodeRoman(matcher.group());
            if (season != null) {
                return new SeasonMatch(season, matcher.start(), matcher.end());
            }
        }
        return null;
    }

    private Integer parseSeason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = normalizeChineseOrdinals(normalizeTitleSymbols(standardize(value)));
        String compact = SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
        if (compact.matches("[0-9]{1,3}")) {
            return parseNonNegativeSeason(compact);
        }
        Matcher matcher = SEASON_PATTERN.matcher(compact);
        if (!matcher.find()) {
            return null;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            Integer season = parseSeasonToken(matcher.group(i));
            if (season != null) {
                return season;
            }
        }
        return null;
    }

    private Integer parseSeasonToken(String rawNumber) {
        if (!StringUtils.hasText(rawNumber)) {
            return null;
        }
        String normalized = rawNumber.trim();
        if (normalized.matches("[0-9]{1,3}")) {
            return parseNonNegativeSeason(normalized);
        }
        return parseRomanNumeral(normalized);
    }

    private Integer parseNonNegativeSeason(String rawNumber) {
        try {
            int parsed = Integer.parseInt(rawNumber);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parsePositiveSeason(String rawNumber) {
        try {
            int parsed = Integer.parseInt(rawNumber);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseRomanNumeral(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int total = 0;
        int previous = 0;
        String upper = raw.toUpperCase(java.util.Locale.ROOT);
        for (int i = upper.length() - 1; i >= 0; i--) {
            int value = switch (upper.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> -1;
            };
            if (value < 0) {
                return null;
            }
            if (value < previous) {
                total -= value;
            } else {
                total += value;
                previous = value;
            }
        }
        return total > 0 && total <= 100 ? total : null;
    }

    private Integer parseUnicodeRoman(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return switch (raw.charAt(0)) {
            case '\u2160' -> 1;
            case '\u2161' -> 2;
            case '\u2162' -> 3;
            case '\u2163' -> 4;
            case '\u2164' -> 5;
            case '\u2165' -> 6;
            case '\u2166' -> 7;
            case '\u2167' -> 8;
            case '\u2168' -> 9;
            case '\u2169' -> 10;
            case '\u216A' -> 11;
            case '\u216B' -> 12;
            default -> null;
        };
    }

    private TitleSplit splitV1TitleStructure(String title, String year) {
        TitleSplit split = splitByPattern(title, COLON_TITLE_SPLIT, year);
        if (split != null) {
            return split;
        }
        split = splitByPattern(title, DASH_TITLE_SPLIT, year);
        if (split != null) {
            return split;
        }
        split = splitByPattern(title, BRACKET_SUBTITLE_SPLIT, year);
        if (split != null) {
            return split;
        }
        if (containsHan(title)) {
            return splitHanSpaceTitle(title, year);
        }
        return null;
    }

    private TitleSplit splitHanSpaceTitle(String title, String year) {
        Matcher matcher = HAN_SPACE_TITLE_SPLIT.matcher(title);
        if (!matcher.find()) {
            return null;
        }
        String left = cleanTitleEdges(matcher.group(1).trim());
        String right = cleanSubtitle(matcher.group(2), year);
        if (left.length() < 2 || !containsHan(left) || !StringUtils.hasText(right)) {
            return null;
        }
        if (SEASON_ONLY_PATTERN.matcher(right).matches()) {
            return null;
        }
        // A numbered film part is part of the title, not a subtitle or TV season.
        if (right.matches("第?[0-9零一二三四五六七八九十百两]+[部篇卷]")) {
            return null;
        }
        return new TitleSplit(left, right);
    }

    private TitleSplit splitByPattern(String title, Pattern pattern, String year) {
        Matcher matcher = pattern.matcher(title);
        if (!matcher.find()) {
            return null;
        }
        String left = cleanTitleEdges(matcher.group(1).trim());
        String right = cleanSubtitle(matcher.group(2), year);
        if (left.length() < 2 || !StringUtils.hasText(right)) {
            return null;
        }
        if (SEASON_ONLY_PATTERN.matcher(right).matches()) {
            return null;
        }
        return new TitleSplit(left, right);
    }

    private String extractTrailingSeasonSubtitle(String cleanedTitle, String year) {
        Matcher matcher = SEASON_PATTERN.matcher(cleanedTitle);
        String trailingSubtitle = null;
        while (matcher.find()) {
            String post = cleanedTitle.substring(matcher.end()).trim();
            if (StringUtils.hasText(post)) {
                trailingSubtitle = cleanSubtitle(post, year);
            }
        }
        return trailingSubtitle;
    }

    private String removeSeasonPatterns(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return SEASON_PATTERN.matcher(value).replaceAll(" ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private boolean endsWithPartOrVolumeMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT).matches(".*\\b(?:part|vol|volume)\\.?$");
    }

    private String cleanSubtitle(String value, String year) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String subtitle = standardize(value);
        subtitle = normalizeTitleSymbols(subtitle);
        subtitle = repairBrackets(subtitle);
        subtitle = LEADING_RELEASE_GROUP_PATTERN.matcher(subtitle).replaceAll("");
        subtitle = decodeBasicHtmlEntities(subtitle);
        subtitle = URL_PATTERN.matcher(subtitle).replaceAll("");
        subtitle = FILE_EXTENSION_PATTERN.matcher(subtitle).replaceAll("");
        subtitle = BRACKET_YEAR_PATTERN.matcher(subtitle).replaceAll(" ");
        subtitle = normalizeChineseOrdinals(subtitle);
        subtitle = RELEASE_NOISE.matcher(subtitle).replaceAll("");
        subtitle = processV1BracketContents(subtitle);
        subtitle = FILE_EXTENSION_PATTERN.matcher(subtitle.trim()).replaceAll("");
        subtitle = cleanTitleEdges(subtitle);
        subtitle = SPACE_PATTERN.matcher(subtitle).replaceAll(" ").trim();
        String normalizedYear = normalizeYear(year);
        if (hasTrailingReleaseYear(subtitle, normalizedYear)) {
            subtitle = subtitle.substring(0, subtitle.length() - normalizedYear.length()).trim();
            subtitle = cleanTitleEdges(subtitle);
        }
        return StringUtils.hasText(subtitle) ? subtitle : null;
    }

    private String cleanDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Document document = Jsoup.parseBodyFragment(Parser.unescapeEntities(standardize(value), false));
        document.select("script, style, noscript").remove();
        String description = document.text();
        description = SPACE_PATTERN.matcher(description).replaceAll(" ").trim();
        return StringUtils.hasText(description) ? trimToLength(description, 4096) : null;
    }

    private String normalizeTitleSymbols(String input) {
        return input.replace('【', '[')
                .replace('】', ']')
                .replace('（', '(')
                .replace('）', ')')
                .replace('《', '(')
                .replace('》', ')')
                .replace('<', '(')
                .replace('>', ')')
                .replace('：', ':');
    }

    private String cleanTitleEdges(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        String cleaned = TITLE_LEADING_EDGE_SYMBOLS.matcher(input).replaceAll("");
        return TITLE_TRAILING_EDGE_SYMBOLS.matcher(cleaned).replaceAll("");
    }

    private String repairBrackets(String input) {
        int parens = 0;
        int brackets = 0;
        for (char c : input.toCharArray()) {
            if (c == '(') {
                parens++;
            } else if (c == ')') {
                parens--;
            } else if (c == '[') {
                brackets++;
            } else if (c == ']') {
                brackets--;
            }
        }
        StringBuilder repaired = new StringBuilder(input);
        while (parens > 0) {
            repaired.append(')');
            parens--;
        }
        while (brackets > 0) {
            repaired.append(']');
            brackets--;
        }
        return repaired.toString();
    }

    private String decodeBasicHtmlEntities(String input) {
        return input.replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&lt;", "(")
                .replace("&gt;", ")")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String processV1BracketContents(String input) {
        String withoutParenNoise = processBracketPattern(input, PARENTHESES_CONTENT);
        return processBracketPattern(withoutParenNoise, BRACKET_CONTENT);
    }

    private String processBracketPattern(String input, Pattern pattern) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        Matcher matcher = pattern.matcher(input);
        int lastAppendPosition = 0;
        while (matcher.find()) {
            result.append(input, lastAppendPosition, matcher.start());
            String content = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (!isV1NoiseContent(content)) {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
                result.append('(').append(content).append(')');
            }
            lastAppendPosition = matcher.end();
        }
        if (lastAppendPosition < input.length()) {
            result.append(input.substring(lastAppendPosition));
        }
        return result.toString();
    }

    private boolean isV1NoiseContent(String content) {
        if (!StringUtils.hasText(content)) {
            return true;
        }
        String trimmed = content.trim();
        if (trimmed.matches("^[\\d\\p{Punct}]+$") && trimmed.length() > 4) {
            return true;
        }
        return PURE_BRACKET_NOISE.matcher(trimmed).matches();
    }

    private String normalizeChineseOrdinals(String input) {
        Matcher matcher = CHINESE_ORDINAL.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Integer number = parseChineseNumber(matcher.group(1));
            if (number != null) {
                matcher.appendReplacement(result, "第" + number + matcher.group(2));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Integer parseChineseNumber(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        if ("十".equals(raw)) {
            return 10;
        }
        int result = 0;
        int current = 0;
        for (char c : raw.toCharArray()) {
            int digit = switch (c) {
                case '零' -> 0;
                case '一' -> 1;
                case '二', '两' -> 2;
                case '三' -> 3;
                case '四' -> 4;
                case '五' -> 5;
                case '六' -> 6;
                case '七' -> 7;
                case '八' -> 8;
                case '九' -> 9;
                default -> -1;
            };
            if (digit >= 0) {
                current = digit;
                continue;
            }
            if (c == '十' || c == '百') {
                int unit = c == '十' ? 10 : 100;
                if (current == 0 && result == 0 && unit == 10) {
                    current = 1;
                }
                result += current * unit;
                current = 0;
                continue;
            }
            return null;
        }
        result += current;
        return result > 0 ? result : null;
    }

    private String removeRedundantBilingualTitle(String input) {
        Matcher repeatedNumber = BILINGUAL_NUMBER_REPEAT.matcher(input);
        if (repeatedNumber.matches() && containsHan(repeatedNumber.group(1))) {
            return repeatedNumber.group(1).trim() + repeatedNumber.group(2);
        }
        // Adjacent scripts can be one identity (头文字D, DNA恋人), not redundant translations.
        return input;
    }

    private String removeSelfRepetition(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }
        Matcher matcher = MONOLINGUAL_REPEAT.matcher(input);
        if (matcher.matches() && matcher.group(1).trim().length() > 1) {
            return matcher.group(1).trim();
        }
        return input;
    }

    private boolean containsHan(String input) {
        return input != null && input.codePoints()
                .anyMatch(codepoint -> Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN);
    }

    private String cleanAliasTitle(String value, String title, Integer season, String subtitle) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        Set<String> titleKeys = aliasComparisonKeys(title, season, subtitle);
        Set<String> aliases = new LinkedHashSet<>();
        for (String part : RELATION_SPLIT_PATTERN.split(standardize(value).replace('\\', '/'))) {
            String alias = SPACE_PATTERN.matcher(part).replaceAll(" ").trim();
            alias = EDGE_SYMBOLS.matcher(alias).replaceAll("");
            if (!StringUtils.hasText(alias)) {
                continue;
            }
            if (titleKeys.contains(aliasComparisonKey(alias))) {
                continue;
            }
            aliases.add(alias);
        }
        return aliases.isEmpty() ? null : String.join(", ", aliases);
    }

    private Set<String> aliasComparisonKeys(String title, Integer season, String subtitle) {
        Set<String> keys = new LinkedHashSet<>();
        addAliasComparisonKey(keys, title);
        if (StringUtils.hasText(title) && season != null && season >= 0) {
            addAliasComparisonKey(keys, title + season);
            addAliasComparisonKey(keys, title + " " + season);
            addAliasComparisonKey(keys, title + " 第" + season + "季");
            addAliasComparisonKey(keys, title + "第" + season + "季");
        }
        if (StringUtils.hasText(title) && StringUtils.hasText(subtitle)) {
            addAliasComparisonKey(keys, title + " " + subtitle);
            addAliasComparisonKey(keys, title + subtitle);
            addAliasComparisonKey(keys, title + ": " + subtitle);
            addAliasComparisonKey(keys, title + " - " + subtitle);
        }
        if (StringUtils.hasText(title) && season != null && season >= 0 && StringUtils.hasText(subtitle)) {
            addAliasComparisonKey(keys, title + " 第" + season + "季 " + subtitle);
            addAliasComparisonKey(keys, title + "第" + season + "季" + subtitle);
        }
        return keys;
    }

    private void addAliasComparisonKey(Set<String> keys, String value) {
        String normalized = aliasComparisonKey(value);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        keys.add(normalized);
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            String mainTitle = normalized.substring(0, colon).trim();
            if (StringUtils.hasText(mainTitle)) {
                keys.add(mainTitle);
            }
        }
    }

    private String aliasComparisonKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeTitleSymbols(standardize(value));
        normalized = normalizeChineseOrdinals(normalized);
        return SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    private String normalizeYear(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        Matcher matcher = YEAR_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private BigDecimal parseScore(JsonNode metadata, BigDecimal fallback) {
        JsonNode value = metadata.get("score");
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            BigDecimal score = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
            if (score.compareTo(BigDecimal.ZERO) < 0) {
                return fallback;
            }
            if (score.compareTo(new BigDecimal("10.0")) > 0) {
                return new BigDecimal("10.0");
            }
            return score;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Set<String> relationValues(JsonNode metadata, String... names) {
        return relationValues(metadata, 100, names);
    }

    private Set<String> relationValues(JsonNode metadata, int maxLength, String... names) {
        Set<String> values = new LinkedHashSet<>();
        for (String name : names) {
            JsonNode value = metadata.get(name);
            if (value != null && !value.isNull()) {
                collectRelationValues(value, values, maxLength);
            }
        }
        return values;
    }

    private void collectRelationValues(JsonNode node, Set<String> values, int maxLength) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectRelationValues(item, values, maxLength));
            return;
        }
        String text = node.isTextual() ? node.asText() : node.asText(node.toString());
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String part : RELATION_SPLIT_PATTERN.split(text)) {
            String value = trimToLength(standardize(part), maxLength);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
    }

    private Set<String> personValues(JsonNode metadata, String... names) {
        Set<String> values = new LinkedHashSet<>();
        for (String name : names) {
            collectPersonValues(metadata.get(name), values);
        }
        return trimPersonValues(values);
    }

    private void collectPersonValues(JsonNode node, Set<String> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectPersonValues(item, values));
        } else if (node.isTextual()) {
            values.add(node.asText());
        }
    }

    private Set<String> trimPersonValues(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : PersonNames.normalize(values)) {
            normalized.add(trimToLength(value, 255));
        }
        return normalized;
    }

    private TitleStructure extractSportsTitleStructure(String value) {
        String title = cleanIdentityTitle(value);
        if (!StringUtils.hasText(title)) {
            return new TitleStructure(null, null, null);
        }
        // Split only an explicit matchup suffix. A score colon and a season range are identity.
        Matcher matchup = SPORTS_MATCHUP_PATTERN
                .matcher(title);
        if (matchup.matches()) {
            return new TitleStructure(matchup.group(1).trim(), null, matchup.group(2).trim());
        }
        return new TitleStructure(title, null, null);
    }

    private TitleSplit splitShortDramaTitle(String title) {
        for (Pattern separator : new Pattern[]{
                COLON_TITLE_SPLIT, DASH_TITLE_SPLIT, SHORT_DRAMA_COMMA_SPLIT,
                BRACKET_SUBTITLE_SPLIT, HAN_SPACE_TITLE_SPLIT}) {
            Matcher matcher = separator.matcher(title);
            if (!matcher.matches()) {
                continue;
            }
            if (separator == BRACKET_SUBTITLE_SPLIT && matcher.group(2).trim().matches("[0-9]+")) {
                continue;
            }
            String main = cleanIdentityTitle(matcher.group(1));
            String subtitle = cleanIdentityTitle(matcher.group(2));
            if (StringUtils.hasText(main) && StringUtils.hasText(subtitle)
                    && (separator != HAN_SPACE_TITLE_SPLIT || containsHan(main))) {
                return new TitleSplit(main, subtitle);
            }
        }
        return null;
    }

    // Sports dates, scores, rounds and short-drama clauses are work identity, not seasons.
    // Keep punctuation and numeric brackets; only remove technical release noise.
    private String cleanIdentityTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String title = standardize(Parser.unescapeEntities(value, false));
        title = normalizeTitleSymbols(title);
        title = URL_PATTERN.matcher(title).replaceAll("");
        title = FILE_EXTENSION_PATTERN.matcher(title).replaceAll("");
        title = RELEASE_NOISE.matcher(title).replaceAll("");
        title = SPACE_PATTERN.matcher(title).replaceAll(" ").trim();
        return StringUtils.hasText(title) ? title : null;
    }

    private String normalizeId(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String standardize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        // CharTable is intended for NLP matching and also folds distinct name characters.
        // Only fold full-width ASCII here; retain the existing Chinese/Japanese dictionaries.
        StringBuilder widthNormalized = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            if (character >= '\uFF01' && character <= '\uFF5E') {
                widthNormalized.append((char) (character - 0xFEE0));
            } else {
                widthNormalized.append(character == '\u3000' ? ' ' : character);
            }
        }
        String normalized = HanLP.convertToSimplifiedChinese(widthNormalized.toString());
        if (StringUtils.hasText(normalized)) {
            normalized = ZhJpConverterUtil.toSimple(normalized);
        }
        return normalized.trim();
    }

    private boolean hasTrailingReleaseYear(String title, String year) {
        if (!StringUtils.hasText(year) || !title.endsWith(year) || title.length() <= year.length()) {
            return false;
        }
        // A release year is a separate token. A title such as 1917 or 赛博朋克2077
        // must survive even when its identity number equals the metadata release year.
        int boundary = title.length() - year.length();
        return Character.isWhitespace(title.charAt(boundary - 1));
    }

    private boolean continuesIdentityNumber(String value) {
        // Compact title numbers such as 幸福36计 and 中国刑侦1号案 are identity,
        // whereas an explicit 第N季 remains handled by SEASON_PATTERN.
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return "号计路天年月日时分秒岁个位人米级次度万亿百千".indexOf(value.charAt(0)) >= 0
                || value.startsWith("小时")
                || (value.charAt(0) == '.' && value.length() > 1 && Character.isDigit(value.charAt(1)));
    }

    private record TitleSplit(String mainTitle, String subtitle) {
    }

    private record SeasonMatch(Integer season, int start, int end) {
    }

    private record TitleStructure(String mainTitle, Integer season, String subtitle) {
    }
}
