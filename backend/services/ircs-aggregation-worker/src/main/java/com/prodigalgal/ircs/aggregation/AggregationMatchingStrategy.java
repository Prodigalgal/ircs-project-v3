package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.contentmetadata.PersonNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AggregationMatchingStrategy {

    private static final double MATCH_THRESHOLD = 0.80;
    private static final double TITLE_BLOCK_THRESHOLD = 0.80;
    private static final double TITLE_WEIGHT = 0.70;
    private static final double METADATA_WEIGHT = 0.30;
    private static final double ACTOR_EVIDENCE_WEIGHT = 0.60;
    private static final double DIRECTOR_EVIDENCE_WEIGHT = 0.40;
    private static final double TITLE_ONLY_CONFIDENCE = 0.95;
    private static final double EPISODE_PENALTY = 0.15;
    private static final double AREA_PENALTY = 0.10;
    // Heuristic ranking floor, not a probability or ID verification. Validate on independently
    // annotated cross-source examples before changing it; sampled dirty IDs do not prove separation.
    private static final double SAME_ID_TITLE_FLOOR = 0.50;
    private static final List<String> EXTERNAL_ID_PROVIDERS = List.of("douban", "tmdb", "imdb", "rt");

    private final JaroWinklerSimilarity stringSimilarity = new JaroWinklerSimilarity();
    private final AggregationSafetyPolicy safetyPolicy;

    public Optional<UUID> findBestMatch(
            RawVideoAggregationRecord rawVideo,
            List<UnifiedVideoAggregationCandidate> candidates) {
        AggregationMatchPlan plan = findMatchPlan(rawVideo, candidates);
        return plan.hasRoot() ? Optional.of(plan.rootUnifiedVideoId()) : Optional.empty();
    }

    public AggregationMatchPlan findMatchPlan(
            RawVideoAggregationRecord rawVideo,
            List<UnifiedVideoAggregationCandidate> candidates) {
        List<UUID> matches = findMergeableMatches(rawVideo, candidates);
        if (matches.isEmpty()) {
            return AggregationMatchPlan.none();
        }
        if (matches.size() == 1) {
            return AggregationMatchPlan.rootOnly(matches.getFirst());
        }
        List<UnifiedVideoAggregationCandidate> matchedCandidates = candidates.stream()
                .filter(candidate -> matches.contains(candidate.id()))
                .toList();
        if (isSafeUnifiedMerge(matchedCandidates, Set.copyOf(matches))) {
            return AggregationMatchPlan.rootWithVictims(matches.getFirst(), matches.subList(1, matches.size()));
        }
        // A single positively supported candidate can own the raw without destroying alternatives.
        // A score tie or several ID-supported alternatives is an unresolved identity decision.
        List<UnifiedVideoAggregationCandidate> supported = matchedCandidates.stream()
                .filter(candidate -> checkExternalIds(rawVideo, candidate) > 0)
                .toList();
        return supported.size() == 1
                ? AggregationMatchPlan.rootOnly(supported.getFirst().id())
                : AggregationMatchPlan.ambiguous();
    }

    /**
     * Validates the complete proposed set, including victims against one another. Usable IDs are
     * not verified identities; each pair needs shared provider evidence and independent title/name
     * corroboration (or two shared providers). A raw carrying unrelated IDs cannot prove this link.
     */
    boolean isSafeUnifiedMerge(
            List<UnifiedVideoAggregationCandidate> candidates, Set<UUID> expectedUnifiedIds) {
        if (candidates == null || expectedUnifiedIds == null || expectedUnifiedIds.isEmpty()
                || expectedUnifiedIds.stream().anyMatch(java.util.Objects::isNull)
                || candidates.size() != expectedUnifiedIds.size()) {
            return false;
        }
        Set<UUID> actualIds = new HashSet<>();
        for (UnifiedVideoAggregationCandidate candidate : candidates) {
            if (candidate == null || candidate.id() == null || !actualIds.add(candidate.id())) {
                return false;
            }
        }
        if (!actualIds.equals(expectedUnifiedIds)) {
            return false;
        }
        Map<UUID, VideoFingerprint> fingerprints = new HashMap<>();
        candidates.forEach(candidate -> fingerprints.put(candidate.id(), fingerprint(candidate)));
        for (int leftIndex = 0; leftIndex < candidates.size(); leftIndex++) {
            UnifiedVideoAggregationCandidate left = candidates.get(leftIndex);
            VideoFingerprint leftFingerprint = fingerprints.get(left.id());
            for (int rightIndex = leftIndex + 1; rightIndex < candidates.size(); rightIndex++) {
                UnifiedVideoAggregationCandidate right = candidates.get(rightIndex);
                VideoFingerprint rightFingerprint = fingerprints.get(right.id());
                if (!isHardIdentityCompatible(left, right, leftFingerprint, rightFingerprint)
                        || hasConflictingEventIdentity(leftFingerprint, rightFingerprint)
                        || !hasUnifiedMergeEvidence(leftFingerprint, rightFingerprint)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasUnifiedMergeEvidence(VideoFingerprint left, VideoFingerprint right) {
        int sharedIdCount = sharedExternalIdCount(left, right);
        if (sharedIdCount == 0) {
            return false;
        }
        // Destroying an existing work needs stronger corroboration than ordinary raw matching.
        if (sharedIdCount > 1 || sharesPeopleInSameRole(left, right)) {
            return true;
        }
        return left.titles().stream()
                .filter(title -> AggregationTitleNormalizer.isDistinctiveSecondaryTitle(title, title))
                .anyMatch(right.titles()::contains);
    }

    private List<UUID> findMergeableMatches(
            RawVideoAggregationRecord rawVideo,
            List<UnifiedVideoAggregationCandidate> candidates) {
        VideoFingerprint rawFingerprint = fingerprint(rawVideo);
        return candidates.stream()
                .map(candidate -> new ScoredCandidate(candidate.id(),
                        calculateSimilarity(rawVideo, candidate, rawFingerprint, fingerprint(candidate))))
                .filter(candidate -> candidate.score() >= MATCH_THRESHOLD)
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(candidate -> candidate.id().toString()))
                .map(ScoredCandidate::id)
                .toList();
    }

    double calculateSimilarity(RawVideoAggregationRecord rawVideo, UnifiedVideoAggregationCandidate candidate) {
        return calculateSimilarity(rawVideo, candidate, fingerprint(rawVideo), fingerprint(candidate));
    }

    private double calculateSimilarity(RawVideoAggregationRecord rawVideo, UnifiedVideoAggregationCandidate candidate,
            VideoFingerprint rawFingerprint, VideoFingerprint candidateFingerprint) {
        if (!isHardIdentityCompatible(rawVideo, candidate, rawFingerprint, candidateFingerprint)) {
            return 0.0;
        }
        if (hasConflictingEventIdentity(rawFingerprint, candidateFingerprint)) {
            return 0.0;
        }
        if (usesSportsProfile(rawFingerprint, candidateFingerprint)) {
            return sameSportsEvent(rawFingerprint, candidateFingerprint) ? TITLE_ONLY_CONFIDENCE : 0.0;
        }

        int externalIdCheck = checkExternalIds(rawFingerprint, candidateFingerprint);
        if (externalIdCheck < 0) {
            return 0.0;
        }

        double titleScore = titleSimilarity(rawFingerprint, candidateFingerprint);
        if (externalIdCheck > 0) {
            return corroboratesSharedIdentity(rawFingerprint, candidateFingerprint,
                    sharedExternalIdCount(rawFingerprint, candidateFingerprint)) ? 1.0 : 0.0;
        }
        if (titleScore < TITLE_BLOCK_THRESHOLD) {
            return 0.0;
        }

        return scoreWithMetadataAndPenalties(
                titleScore,
                rawFingerprint,
                candidateFingerprint,
                rawFingerprint.episodeCount(),
                candidateFingerprint.episodeCount(),
                rawFingerprint.areas(),
                candidateFingerprint.areas());
    }

    double calculateSimilarity(RawVideoAggregationRecord left, RawVideoAggregationRecord right) {
        return calculateSimilarity(left, right, fingerprint(left), fingerprint(right));
    }

    private double calculateSimilarity(RawVideoAggregationRecord left, RawVideoAggregationRecord right,
            VideoFingerprint leftFingerprint, VideoFingerprint rightFingerprint) {
        if (!isHardIdentityCompatible(left, right, leftFingerprint, rightFingerprint)) {
            return 0.0;
        }
        if (hasConflictingEventIdentity(leftFingerprint, rightFingerprint)) {
            return 0.0;
        }
        if (usesSportsProfile(leftFingerprint, rightFingerprint)) {
            return sameSportsEvent(leftFingerprint, rightFingerprint) ? TITLE_ONLY_CONFIDENCE : 0.0;
        }

        int externalIdCheck = checkExternalIds(leftFingerprint, rightFingerprint);
        if (externalIdCheck < 0) {
            return 0.0;
        }

        double titleScore = titleSimilarity(leftFingerprint, rightFingerprint);
        if (externalIdCheck > 0) {
            return corroboratesSharedIdentity(leftFingerprint, rightFingerprint,
                    sharedExternalIdCount(leftFingerprint, rightFingerprint)) ? 1.0 : 0.0;
        }
        if (titleScore < TITLE_BLOCK_THRESHOLD) {
            return 0.0;
        }

        return scoreWithMetadataAndPenalties(
                titleScore,
                leftFingerprint,
                rightFingerprint,
                leftFingerprint.episodeCount(),
                rightFingerprint.episodeCount(),
                leftFingerprint.areas(),
                rightFingerprint.areas());
    }

    double calculateSimilarity(AggregationGraphCandidate left, AggregationGraphCandidate right) {
        return calculateSimilarity(left, right, fingerprint(left), fingerprint(right));
    }

    private VideoFingerprint fingerprint(AggregationGraphCandidate candidate) {
        if (candidate instanceof AggregationGraphCandidate.RawNode raw) {
            return fingerprint(raw.rawVideo());
        }
        if (candidate instanceof AggregationGraphCandidate.UnifiedNode unified) {
            return fingerprint(unified.unifiedVideo());
        }
        throw new IllegalArgumentException("Unsupported aggregation graph candidate");
    }

    private double calculateSimilarity(AggregationGraphCandidate left, AggregationGraphCandidate right,
            VideoFingerprint leftFingerprint, VideoFingerprint rightFingerprint) {
        if (left instanceof AggregationGraphCandidate.RawNode leftRaw
                && right instanceof AggregationGraphCandidate.RawNode rightRaw) {
            return calculateSimilarity(leftRaw.rawVideo(), rightRaw.rawVideo(), leftFingerprint, rightFingerprint);
        }
        if (left instanceof AggregationGraphCandidate.RawNode leftRaw
                && right instanceof AggregationGraphCandidate.UnifiedNode rightUnified) {
            return calculateSimilarity(leftRaw.rawVideo(), rightUnified.unifiedVideo(), leftFingerprint, rightFingerprint);
        }
        if (left instanceof AggregationGraphCandidate.UnifiedNode leftUnified
                && right instanceof AggregationGraphCandidate.RawNode rightRaw) {
            return calculateSimilarity(rightRaw.rawVideo(), leftUnified.unifiedVideo(), rightFingerprint, leftFingerprint);
        }
        return 0.0;
    }

    private boolean corroboratesSharedIdentity(VideoFingerprint left, VideoFingerprint right,
            int sharedIdCount) {
        if (sharedIdCount == 0) {
            return false;
        }
        if (sharedIdCount > 1 || titleSimilarity(left, right) >= SAME_ID_TITLE_FLOOR) {
            return true;
        }
        return sharesPeopleInSameRole(left, right);
    }

    SimilaritySession prepareSimilaritySession(List<AggregationGraphCandidate> candidates) {
        return new SimilaritySession(candidates);
    }

    /** Fixed operation-local cache. It cannot grow after construction or retain later requests. */
    final class SimilaritySession {
        private final Map<AggregationGraphCandidate, VideoFingerprint> fingerprints;

        private SimilaritySession(List<AggregationGraphCandidate> candidates) {
            Map<AggregationGraphCandidate, VideoFingerprint> prepared = new HashMap<>();
            for (AggregationGraphCandidate candidate : candidates) {
                prepared.computeIfAbsent(candidate, AggregationMatchingStrategy.this::fingerprint);
            }
            fingerprints = Map.copyOf(prepared);
        }

        double calculateSimilarity(AggregationGraphCandidate left, AggregationGraphCandidate right) {
            VideoFingerprint leftFingerprint = fingerprints.get(left);
            VideoFingerprint rightFingerprint = fingerprints.get(right);
            if (leftFingerprint == null || rightFingerprint == null) {
                throw new IllegalArgumentException("Candidate was not prepared for this similarity session");
            }
            return AggregationMatchingStrategy.this.calculateSimilarity(left, right, leftFingerprint, rightFingerprint);
        }

        int preparedCandidateCount() {
            return fingerprints.size();
        }
    }

    private String[] externalIds(RawVideoAggregationRecord raw) {
        return new String[] {raw.doubanId(), raw.tmdbId(), raw.imdbId(), raw.rottenTomatoesId()};
    }

    private String[] externalIds(UnifiedVideoAggregationCandidate unified) {
        return new String[] {unified.doubanId(), unified.tmdbId(), unified.imdbId(), unified.rottenTomatoesId()};
    }

    private Map<String, String> normalizedExternalIds(String[] values) {
        Map<String, String> usable = new HashMap<>();
        for (int index = 0; index < EXTERNAL_ID_PROVIDERS.size(); index++) {
            String provider = EXTERNAL_ID_PROVIDERS.get(index);
            String normalized = safetyPolicy.normalizeExternalId(provider, values[index]);
            if (normalized != null) { usable.put(provider, normalized); }
        }
        return Map.copyOf(usable);
    }

    private int checkExternalIds(VideoFingerprint left, VideoFingerprint right) {
        int result = 0;
        for (Map.Entry<String, String> identity : left.externalIds().entrySet()) {
            String other = right.externalIds().get(identity.getKey());
            if (other != null) {
                if (!identity.getValue().equals(other)) { return -1; }
                result = 1;
            }
        }
        return result;
    }

    private int sharedExternalIdCount(VideoFingerprint left, VideoFingerprint right) {
        return (int) left.externalIds().entrySet().stream()
                .filter(identity -> identity.getValue().equals(right.externalIds().get(identity.getKey())))
                .count();
    }

    /** Basic match eligibility; graph membership applies its stricter consistency threshold. */
    boolean isMergeable(AggregationGraphCandidate left, AggregationGraphCandidate right) {
        return calculateSimilarity(left, right) >= MATCH_THRESHOLD;
    }

    private VideoFingerprint fingerprint(RawVideoAggregationRecord rawVideo) {
        return fingerprint(rawVideo.title(), rawVideo.aliasTitle(), rawVideo.subtitle(), rawVideo.remarks(), rawVideo.season(),
                normalizedNames(rawVideo.actorNames()), normalizedNames(rawVideo.directorNames()), parseYear(rawVideo.year()),
                parseEpisodeCount(rawVideo.totalEpisodes()), normalizedAreas(rawVideo.areaNames()),
                normalizedExternalIds(externalIds(rawVideo)), AggregationCategoryProfile.category(rawVideo));
    }

    private VideoFingerprint fingerprint(UnifiedVideoAggregationCandidate candidate) {
        return fingerprint(
                candidate.title(),
                candidate.aliasTitle(),
                candidate.subtitle(),
                candidate.remarks(),
                candidate.season(), normalizedNames(candidate.actorNames()), normalizedNames(candidate.directorNames()), parseYear(candidate.year()),
                parseEpisodeCount(candidate.totalEpisodes()), normalizedAreas(candidate.areaNames()),
                normalizedExternalIds(externalIds(candidate)), candidate.categoryName());
    }

    private VideoFingerprint fingerprint(String title, String aliasTitle, String subtitle, String remarks, Integer season,
            Set<String> actors, Set<String> directors, Integer year, Integer episodeCount,
            Set<String> areas, Map<String, String> externalIds, String category) {
        String completeTitle = AggregationTitleNormalizer.completeIdentityTitle(title, subtitle);
        AggregationCategoryProfile profile = AggregationCategoryProfile.from(category);
        Optional<AggregationSportsIdentity> event = profile == AggregationCategoryProfile.SPORTS
                || AggregationSportsIdentity.looksLikeEvent(completeTitle)
                ? Optional.of(AggregationSportsIdentity.parse(completeTitle, year)) : Optional.empty();
        if (profile == AggregationCategoryProfile.UNKNOWN && event.isPresent()) {
            profile = AggregationCategoryProfile.SPORTS;
        }
        List<String> rawTitles = new ArrayList<>();
        addIfText(rawTitles, completeTitle);
        addIfText(rawTitles, title);
        rawTitles.addAll(AggregationTitleNormalizer.splitAliases(aliasTitle));

        // Sports season labels describe a competition, not the season of a screen work.
        Integer parsedSeason = profile.usesScreenSeason() ? season : null;
        VideoVersion version = VideoVersion.NORMAL;
        List<String> normalizedTitles = new ArrayList<>();
        for (String rawTitle : rawTitles) {
            if (AggregationTitleNormalizer.VERSION_REAL.matcher(rawTitle).find()) {
                version = VideoVersion.REAL_PERSON;
            } else if (AggregationTitleNormalizer.VERSION_ANIME.matcher(rawTitle).find()) {
                version = VideoVersion.ANIME;
            }
            if (parsedSeason == null && profile.usesScreenSeason()) {
                parsedSeason = AggregationTitleNormalizer.parseSeason(rawTitle);
            }

            String normalized = profile.normalizeTitle(rawTitle);
            if (StringUtils.hasText(normalized) && (rawTitle.equals(title)
                    || AggregationTitleNormalizer.isDistinctiveSecondaryTitle(rawTitle, normalized))) {
                normalizedTitles.add(normalized);
            }
        }
        if (version == VideoVersion.NORMAL && StringUtils.hasText(remarks)) {
            if (AggregationTitleNormalizer.VERSION_REAL.matcher(remarks).find()) {
                version = VideoVersion.REAL_PERSON;
            } else if (AggregationTitleNormalizer.VERSION_ANIME.matcher(remarks).find()) {
                version = VideoVersion.ANIME;
            }
        }
        if (normalizedTitles.isEmpty() && StringUtils.hasText(title)) {
            normalizedTitles.add(title.trim().toLowerCase(Locale.ROOT));
        }
        return new VideoFingerprint(List.copyOf(normalizedTitles), parsedSeason, version, actors, directors,
                event,
                year, episodeCount, areas, externalIds,
                StringUtils.hasText(title) ? profile.normalizeTitle(title) : "",
                AggregationTitleNormalizer.isDistinctiveSecondaryTitle(subtitle)
                        ? profile.normalizeTitle(subtitle) : "",
                profile, profile.normalizeTitle(completeTitle),
                AggregationCategoryProfile.installments(completeTitle, profile), completeTitle);
    }

    private double titleSimilarity(VideoFingerprint left, VideoFingerprint right) {
        double score = maxTitleSimilarity(left.titles(), right.titles());
        if ((usesProgrammeProfile(left) || usesProgrammeProfile(right))
                && (!left.subtitle().isEmpty() || !right.subtitle().isEmpty())) {
            score = stringSimilarity.apply(left.completeTitle(), right.completeTitle());
        }
        // A common heading cannot make different complete titles identical. Keep secondary
        // identity in the comparison; e.g. two short dramas with the same comma prefix.
        if (!left.primaryTitle().isEmpty() && left.primaryTitle().equals(right.primaryTitle())
                && !left.subtitle().isEmpty() && !right.subtitle().isEmpty()) {
            score = Math.min(score, stringSimilarity.apply(left.subtitle(), right.subtitle()));
        }
        return score;
    }

    private boolean usesProgrammeProfile(VideoFingerprint fingerprint) {
        return fingerprint.profile() == AggregationCategoryProfile.PROGRAMME
                || fingerprint.profile() == AggregationCategoryProfile.DOCUMENTARY;
    }

    private boolean sharesPeopleInSameRole(VideoFingerprint left, VideoFingerprint right) {
        return left.actors().stream().anyMatch(right.actors()::contains)
                || left.directors().stream().anyMatch(right.directors()::contains);
    }

    private double maxTitleSimilarity(List<String> leftTitles, List<String> rightTitles) {
        double max = 0.0;
        for (String leftTitle : leftTitles) {
            for (String rightTitle : rightTitles) {
                max = Math.max(max, stringSimilarity.apply(leftTitle, rightTitle));
            }
        }
        return max;
    }

    private double scoreWithMetadataAndPenalties(
            double titleScore,
            VideoFingerprint left,
            VideoFingerprint right,
            Integer leftTotalEpisodes,
            Integer rightTotalEpisodes,
            Set<String> leftAreas,
            Set<String> rightAreas) {
        double score;
        boolean actorsComparable = !left.actors().isEmpty() && !right.actors().isEmpty();
        boolean directorsComparable = !left.directors().isEmpty() && !right.directors().isEmpty();
        if (!actorsComparable && !directorsComparable) {
            score = titleScore * TITLE_ONLY_CONFIDENCE;
        } else {
            double weightedEvidence = 0.0;
            double availableWeight = 0.0;
            if (actorsComparable) {
                weightedEvidence += ACTOR_EVIDENCE_WEIGHT * metadataJaccard(left.actors(), right.actors());
                availableWeight += ACTOR_EVIDENCE_WEIGHT;
            }
            if (directorsComparable) {
                weightedEvidence += DIRECTOR_EVIDENCE_WEIGHT * metadataJaccard(left.directors(), right.directors());
                availableWeight += DIRECTOR_EVIDENCE_WEIGHT;
            }
            score = titleScore * TITLE_WEIGHT + (weightedEvidence / availableWeight) * METADATA_WEIGHT;
        }
        if (!isEpisodeCountCompatible(leftTotalEpisodes, rightTotalEpisodes)) {
            score -= EPISODE_PENALTY;
        }
        if (!areaMatch(leftAreas, rightAreas)) {
            score -= AREA_PENALTY;
        }
        return clamp(score);
    }

    private double metadataJaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.5;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.5 : (double) intersection.size() / union.size();
    }

    private Set<String> normalizedNames(Set<String> values) {
        return PersonNames.normalize(values).stream().map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean isEpisodeCountCompatible(Integer leftCount, Integer rightCount) {
        if (leftCount == null || rightCount == null) {
            return true;
        }
        return (leftCount > 1 || rightCount <= 5) && (rightCount > 1 || leftCount <= 5);
    }

    private Integer parseEpisodeCount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean areaMatch(Set<String> leftAreas, Set<String> rightAreas) {
        if (leftAreas == null || leftAreas.isEmpty() || rightAreas == null || rightAreas.isEmpty()) {
            return true;
        }
        for (String left : leftAreas) {
            for (String right : rightAreas) {
                if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
                    continue;
                }
                if (left.contains(right) || right.contains(left)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> normalizedAreas(Set<String> values) {
        if (values == null) { return Set.of(); }
        return values.stream().filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private int checkExternalIds(RawVideoAggregationRecord rawVideo, UnifiedVideoAggregationCandidate candidate) {
        return checkExternalIds(
                rawVideo.doubanId(),
                rawVideo.tmdbId(),
                rawVideo.imdbId(),
                rawVideo.rottenTomatoesId(),
                candidate.doubanId(),
                candidate.tmdbId(),
                candidate.imdbId(),
                candidate.rottenTomatoesId());
    }

    private int checkExternalIds(
            String leftDoubanId,
            String leftTmdbId,
            String leftImdbId,
            String leftRottenTomatoesId,
            String rightDoubanId,
            String rightTmdbId,
            String rightImdbId,
            String rightRottenTomatoesId) {
        int douban = checkExternalId("douban", leftDoubanId, rightDoubanId);
        int tmdb = checkExternalId("tmdb", leftTmdbId, rightTmdbId);
        int imdb = checkExternalId("imdb", leftImdbId, rightImdbId);
        int rottenTomatoes = checkExternalId("rt", leftRottenTomatoesId, rightRottenTomatoesId);
        if (douban < 0 || tmdb < 0 || imdb < 0 || rottenTomatoes < 0) {
            return -1;
        }
        if (douban > 0 || tmdb > 0 || imdb > 0 || rottenTomatoes > 0) {
            return 1;
        }
        return 0;
    }

    private int checkExternalId(String provider, String left, String right) {
        String normalizedLeft = safetyPolicy.normalizeExternalId(provider, left);
        String normalizedRight = safetyPolicy.normalizeExternalId(provider, right);
        if (normalizedLeft == null || normalizedRight == null) {
            return 0;
        }
        return normalizedLeft.equals(normalizedRight) ? 1 : -1;
    }

    boolean isUsableExternalId(String provider, String value) {
        return safetyPolicy.isUsableExternalId(provider, value);
    }

    Set<String> rejectedExternalIdValues(String provider) {
        return safetyPolicy.rejectedExternalIdValues(provider);
    }

    String normalizeExternalId(String provider, String value) {
        return safetyPolicy.normalizeExternalId(provider, value);
    }

    boolean isHardIdentityCompatible(RawVideoAggregationRecord left, RawVideoAggregationRecord right) {
        return isHardIdentityCompatible(left, right, fingerprint(left), fingerprint(right));
    }

    private boolean isHardIdentityCompatible(RawVideoAggregationRecord left, RawVideoAggregationRecord right,
            VideoFingerprint leftFingerprint, VideoFingerprint rightFingerprint) {
        return AggregationSafetyPolicy.categoriesCompatible(AggregationCategoryProfile.category(left), AggregationCategoryProfile.category(right))
                && fingerprintsCompatible(leftFingerprint, rightFingerprint)
                && checkExternalIds(leftFingerprint, rightFingerprint) >= 0;
    }

    boolean isHardIdentityCompatible(RawVideoAggregationRecord raw, UnifiedVideoAggregationCandidate unified) {
        return isHardIdentityCompatible(raw, unified, fingerprint(raw), fingerprint(unified));
    }

    private boolean isHardIdentityCompatible(RawVideoAggregationRecord raw, UnifiedVideoAggregationCandidate unified,
            VideoFingerprint rawFingerprint, VideoFingerprint unifiedFingerprint) {
        return AggregationSafetyPolicy.categoriesCompatible(AggregationCategoryProfile.category(raw), unified.categoryName())
                && fingerprintsCompatible(rawFingerprint, unifiedFingerprint)
                && checkExternalIds(rawFingerprint, unifiedFingerprint) >= 0;
    }

    boolean isCompatibleBinding(RawVideoAggregationRecord raw, UnifiedVideoAggregationCandidate unified) {
        return bindingConflictReason(raw, unified) == null;
    }

    String bindingConflictReason(RawVideoAggregationRecord raw, UnifiedVideoAggregationCandidate unified) {
        if (raw == null || unified == null) { return "MISSING_IDENTITY"; }
        VideoFingerprint left = fingerprint(raw);
        VideoFingerprint right = fingerprint(unified);
        if (!AggregationSafetyPolicy.categoriesCompatible(AggregationCategoryProfile.category(raw), unified.categoryName())) {
            return "CATEGORY_CONFLICT";
        }
        if (!profileYearsCompatible(left, right)) { return "YEAR_CONFLICT"; }
        // Refresh preserves an existing relationship when a field is simply missing. New
        // matching still uses strict season evidence and does not assume that unknown is S1.
        if (left.season() != null && right.season() != null && !left.season().equals(right.season())) {
            return "SEASON_CONFLICT";
        }
        if (!isVersionCompatible(left.version(), right.version())) { return "VERSION_CONFLICT"; }
        if (AggregationCategoryProfile.installmentsConflict(left.installments(), right.installments())) {
            return "INSTALLMENT_CONFLICT";
        }
        if (filmSequenceConflict(left, right)) { return "FILM_SEQUENCE_CONFLICT"; }
        if (programmeEditionConflict(left, right)) { return "EDITION_CONFLICT"; }
        if (usesShortDramaProfile(left, right) && !left.completeTitle().equals(right.completeTitle())
                && !left.completeTitle().startsWith(right.completeTitle())
                && !right.completeTitle().startsWith(left.completeTitle())) {
            return "SHORT_DRAMA_TITLE_CONFLICT";
        }
        for (String provider : EXTERNAL_ID_PROVIDERS) {
            String leftId = left.externalIds().get(provider);
            String rightId = right.externalIds().get(provider);
            if (leftId != null && rightId != null && !leftId.equals(rightId)) {
                return "EXTERNAL_ID_CONFLICT_" + provider.toUpperCase(Locale.ROOT);
            }
        }
        return hasConflictingEventIdentity(left, right) ? "EVENT_CONFLICT" : null;
    }

    boolean isHardIdentityCompatible(UnifiedVideoAggregationCandidate left, UnifiedVideoAggregationCandidate right) {
        return isHardIdentityCompatible(left, right, fingerprint(left), fingerprint(right));
    }

    private boolean isHardIdentityCompatible(UnifiedVideoAggregationCandidate left, UnifiedVideoAggregationCandidate right,
            VideoFingerprint leftFingerprint, VideoFingerprint rightFingerprint) {
        return AggregationSafetyPolicy.categoriesCompatible(left.categoryName(), right.categoryName())
                && fingerprintsCompatible(leftFingerprint, rightFingerprint)
                && checkExternalIds(leftFingerprint, rightFingerprint) >= 0;
    }

    private boolean fingerprintsCompatible(VideoFingerprint left, VideoFingerprint right) {
        return isSeasonCompatible(left.season(), right.season()) && isVersionCompatible(left.version(), right.version())
                && profileYearsCompatible(left, right)
                && !AggregationCategoryProfile.installmentsConflict(left.installments(), right.installments())
                && (!left.profile().requiresCompleteTitle() && !right.profile().requiresCompleteTitle()
                    || left.completeTitle().equals(right.completeTitle()))
                && !filmSequenceConflict(left, right)
                && !programmeEditionConflict(left, right)
                && animationFormatCompatible(left, right)
                && sourceOnlyEvidenceCompatible(left, right)
                && (!usesSportsProfile(left, right) || sameSportsEvent(left, right))
                && !hasConflictingEventIdentity(left, right);
    }

    private boolean usesSportsProfile(VideoFingerprint left, VideoFingerprint right) {
        return left.profile() == AggregationCategoryProfile.SPORTS || right.profile() == AggregationCategoryProfile.SPORTS;
    }

    private boolean usesShortDramaProfile(VideoFingerprint left, VideoFingerprint right) {
        return left.profile() == AggregationCategoryProfile.SHORT_DRAMA || right.profile() == AggregationCategoryProfile.SHORT_DRAMA;
    }

    private boolean filmSequenceConflict(VideoFingerprint left, VideoFingerprint right) {
        return (left.profile() == AggregationCategoryProfile.MOVIE || right.profile() == AggregationCategoryProfile.MOVIE)
                && AggregationCategoryProfile.filmSequenceConflict(left.originalCompleteTitle(), right.originalCompleteTitle());
    }

    private boolean programmeEditionConflict(VideoFingerprint left, VideoFingerprint right) {
        if (left.profile() != AggregationCategoryProfile.PROGRAMME && right.profile() != AggregationCategoryProfile.PROGRAMME) {
            return false;
        }
        String a = AggregationCategoryProfile.editionYear(left.completeTitle());
        String b = AggregationCategoryProfile.editionYear(right.completeTitle());
        return a != null && b != null && !a.equals(b);
    }

    private boolean animationFormatCompatible(VideoFingerprint left, VideoFingerprint right) {
        if (left.profile() != AggregationCategoryProfile.ANIMATION && right.profile() != AggregationCategoryProfile.ANIMATION) {
            return true;
        }
        String a = left.installments().get("format"), b = right.installments().get("format");
        if (a == null && b == null) { return true; }
        if (a != null && a.equals(b)) { return true; }
        return Integer.valueOf(0).equals(left.season()) && Integer.valueOf(0).equals(right.season());
    }

    private boolean sourceOnlyEvidenceCompatible(VideoFingerprint left, VideoFingerprint right) {
        if (left.profile() == AggregationCategoryProfile.NEWS || right.profile() == AggregationCategoryProfile.NEWS) {
            return left.installments().containsKey("record-date") && right.installments().containsKey("record-date");
        }
        if (left.profile() == AggregationCategoryProfile.MUSIC || right.profile() == AggregationCategoryProfile.MUSIC) {
            return left.actors().stream().anyMatch(right.actors()::contains)
                    || left.installments().containsKey("record-date") && right.installments().containsKey("record-date");
        }
        if (left.profile() == AggregationCategoryProfile.EDUCATION || right.profile() == AggregationCategoryProfile.EDUCATION) {
            return left.actors().stream().anyMatch(right.actors()::contains);
        }
        return true;
    }

    private boolean profileYearsCompatible(VideoFingerprint left, VideoFingerprint right) {
        // An annual sports fixture cannot use the film-release +/- one-year tolerance.
        Integer leftYear = left.eventKey().map(AggregationSportsIdentity::year).orElse(null);
        Integer rightYear = right.eventKey().map(AggregationSportsIdentity::year).orElse(null);
        return usesSportsProfile(left, right) ? leftYear == null || rightYear == null || leftYear.equals(rightYear)
                : yearsCompatible(left.year(), right.year());
    }

    private boolean sameSportsEvent(VideoFingerprint left, VideoFingerprint right) {
        return left.eventKey().isPresent() && right.eventKey().isPresent()
                && left.eventKey().get().supportsSameEvent(right.eventKey().get());
    }

    private Integer parseYear(String value) {
        return StringUtils.hasText(value) && value.trim().matches("[1-9][0-9]{3}")
                ? Integer.valueOf(value.trim()) : null;
    }

    // Release years may differ by one between sources. Unknown values are absence of evidence,
    // and complete pairwise checks prevent them from bridging incompatible known years.
    boolean isYearCompatible(String left, String right) {
        return yearsCompatible(parseYear(left), parseYear(right));
    }

    private boolean yearsCompatible(Integer leftYear, Integer rightYear) {
        return leftYear == null || rightYear == null || Math.abs(leftYear - rightYear) <= 1;
    }

    private boolean isSeasonCompatible(Integer left, Integer right) {
        return java.util.Objects.equals(left, right);
    }

    private boolean isVersionCompatible(VideoVersion left, VideoVersion right) {
        return left == right || left == VideoVersion.NORMAL || right == VideoVersion.NORMAL;
    }

    private void addIfText(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    private boolean hasConflictingEventIdentity(VideoFingerprint left, VideoFingerprint right) {
        return left.eventKey().isPresent() && right.eventKey().isPresent()
                && left.eventKey().get().conflictsWith(right.eventKey().get());
    }

    private record VideoFingerprint(List<String> titles, Integer season, VideoVersion version,
            Set<String> actors, Set<String> directors, Optional<AggregationSportsIdentity> eventKey, Integer year, Integer episodeCount,
            Set<String> areas, Map<String, String> externalIds, String primaryTitle, String subtitle,
            AggregationCategoryProfile profile, String completeTitle, Map<String, String> installments, String originalCompleteTitle) {
    }

    private enum VideoVersion {
        NORMAL,
        REAL_PERSON,
        ANIME
    }

    private record ScoredCandidate(UUID id, double score) {
    }
}
