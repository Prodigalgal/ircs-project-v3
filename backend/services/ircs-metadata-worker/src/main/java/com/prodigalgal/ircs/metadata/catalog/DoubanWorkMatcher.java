package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.PersonNames;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.DoubanTarget;
import com.prodigalgal.ircs.metadata.catalog.DoubanSubjectPageParser.Subject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DoubanWorkMatcher {
    private DoubanWorkMatcher() { }

    static boolean matches(DoubanTarget target, Subject subject, TmdbWorkEvidence evidence) {
        if (!target.mediaType().equals(subject.mediaType()) || target.releaseYear() == null
                || !target.releaseYear().toString().equals(subject.year())) return false;
        if ("tv".equals(target.mediaType()) && (target.regularSeasonCount() != 1 || evidence.episodeCount() <= 0
                || subject.episodeCount() == null || evidence.episodeCount() != subject.episodeCount())) return false;
        Set<String> titles = titleKeys(Stream.of(target.title(), target.originalTitle()));
        Set<String> subjectTitles = titleKeys(Stream.concat(
                Stream.of(subject.localizedTitle(), subject.title(), subject.originalTitle()), subject.aliases().stream()));
        if (titles.stream().noneMatch(subjectTitles::contains)) return false;

        Set<String> subjectDirectors = nameKeys(subject.directors());
        Set<String> subjectActors = nameKeys(subject.actors());
        var sharedDirectors = new HashSet<UUID>();
        var sharedActors = new HashSet<UUID>();
        var sharedNames = new HashSet<String>();
        boolean hasTmdbDirectors = false;
        for (TmdbCredit credit : evidence.credits()) {
            String matchedName = matchingName(credit, credit.director() ? subjectDirectors : subjectActors);
            if (credit.director()) {
                hasTmdbDirectors = true;
            }
            if (matchedName == null) continue;
            sharedNames.add(matchedName);
            (credit.director() ? sharedDirectors : sharedActors).add(credit.personId());
        }
        if (hasTmdbDirectors && !subjectDirectors.isEmpty() && sharedDirectors.isEmpty()) return false;
        if (sharedActors.isEmpty()) return false;
        sharedActors.addAll(sharedDirectors);
        return sharedActors.size() >= 2 && sharedNames.size() >= 2;
    }

    private static String matchingName(TmdbCredit credit, Set<String> subjectNames) {
        String credited = CatalogTitleKey.of(credit.creditedName());
        if (subjectNames.contains(credited)) return credited;
        String original = CatalogTitleKey.of(credit.originalName());
        return subjectNames.contains(original) ? original : null;
    }

    private static Set<String> nameKeys(List<String> names) {
        return titleKeys(PersonNames.normalize(names).stream());
    }

    private static Set<String> titleKeys(Stream<String> names) {
        return names.map(CatalogTitleKey::of).filter(key -> !key.isBlank()).collect(Collectors.toCollection(HashSet::new));
    }

    record TmdbWorkEvidence(List<TmdbCredit> credits, int episodeCount) { }
    record TmdbCredit(UUID personId, boolean director, String creditedName, String originalName) { }
}
