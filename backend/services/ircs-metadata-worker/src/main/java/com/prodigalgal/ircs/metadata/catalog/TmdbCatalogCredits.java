package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contentmetadata.PersonNames;
import com.prodigalgal.ircs.metadata.catalog.AuthorityCatalogEvidence.Credit;
import com.prodigalgal.ircs.metadata.catalog.AuthorityCatalogEvidence.CreditKind;
import com.prodigalgal.ircs.metadata.catalog.AuthorityCatalogEvidence.CreditSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class TmdbCatalogCredits {
    private TmdbCatalogCredits() { }

    static List<CreditSet> movie(JsonNode work) {
        var result = new ArrayList<CreditSet>();
        JsonNode credits = work.path("credits");
        collect(result, credits.path("cast"), null, null, CreditKind.CAST, false);
        collect(result, credits.path("crew"), null, null, CreditKind.CREW, false);
        return List.copyOf(result);
    }

    static List<CreditSet> television(JsonNode work, JsonNode season, int seasonNumber) {
        var result = new ArrayList<CreditSet>();
        JsonNode seriesCredits = work.path("aggregate_credits");
        collect(result, seriesCredits.path("cast"), null, null, CreditKind.CAST, true);
        collect(result, seriesCredits.path("crew"), null, null, CreditKind.CREW, true);
        JsonNode seasonCredits = season.path("credits");
        collect(result, seasonCredits.path("cast"), seasonNumber, null, CreditKind.CAST, false);
        collect(result, seasonCredits.path("crew"), seasonNumber, null, CreditKind.CREW, false);
        for (JsonNode episode : season.path("episodes")) {
            // The caller already validated the season and every episode identity.
            int episodeNumber = episode.path("episode_number").asInt();
            collect(result, episode.path("guest_stars"), seasonNumber, episodeNumber, CreditKind.GUEST_CAST, false);
            collect(result, episode.path("crew"), seasonNumber, episodeNumber, CreditKind.CREW, false);
        }
        return List.copyOf(result);
    }

    private static void collect(List<CreditSet> result, JsonNode people, Integer season, Integer episode,
            CreditKind kind, boolean aggregate) {
        if (!people.isArray() || people.isEmpty()) return;
        Map<String, Credit> credits = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        boolean complete = true;
        int sourceOrder = 0;
        for (JsonNode person : people) {
            JsonNode roles = aggregate ? person.path(kind == CreditKind.CREW ? "jobs" : "roles") : null;
            if (roles == null) {
                complete &= accept(credit(person, person, kind, sourceOrder++), credits, ambiguous);
            } else if (!roles.isArray() || roles.isEmpty()) {
                complete = false;
            } else {
                for (JsonNode role : roles) {
                    complete &= accept(credit(person, role, kind, sourceOrder++), credits, ambiguous);
                }
            }
        }
        ambiguous.forEach(credits::remove);
        if (!credits.isEmpty()) result.add(new CreditSet(season, episode, kind, complete, List.copyOf(credits.values())));
    }

    private static boolean accept(Credit credit, Map<String, Credit> credits, Set<String> ambiguous) {
        if (credit == null) return false;
        if (credits.putIfAbsent(credit.tmdbCreditId(), credit) == null) return true;
        ambiguous.add(credit.tmdbCreditId());
        return false;
    }

    private static Credit credit(JsonNode person, JsonNode role, CreditKind kind, int sourceOrder) {
        String personId = AuthoritativeIds.normalizeTmdb(person.path("id").asText(null));
        String creditId = boundedText(role.path("credit_id"), 128);
        String originalName = name(person.path("original_name"));
        String name = name(person.path("name"));
        if (name == null) name = originalName;
        boolean crew = kind == CreditKind.CREW;
        String department = crew ? boundedText(person.path("department"), 128) : "Acting";
        String job = crew ? boundedText(role.path("job"), 128) : "Actor";
        if (personId == null || creditId == null || name == null || department == null || job == null) return null;
        Integer billingOrder = nonnegativeInt(person.path("order"));
        return new Credit(personId, creditId, name, originalName, department, job,
                crew ? null : boundedText(role.path("character"), 500), billingOrder, sourceOrder);
    }

    private static String name(JsonNode node) {
        if (!node.isTextual()) return null;
        String name = PersonNames.normalizeText(node.asText());
        return name.length() > 255 || PersonNames.normalize(List.of(name)).isEmpty() ? null : name;
    }

    private static String boundedText(JsonNode node, int max) {
        if (!node.isTextual()) return null;
        String value = node.asText().strip();
        return value.isEmpty() || value.length() > max || "null".equalsIgnoreCase(value) || value.indexOf('\0') >= 0
                ? null : value;
    }

    private static Integer nonnegativeInt(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.asInt() >= 0 ? node.asInt() : null;
    }
}
