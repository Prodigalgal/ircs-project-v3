package com.prodigalgal.ircs.magnet;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class MagnetUriUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)^[a-f0-9]{40}$");
    private static final Pattern BASE32_PATTERN = Pattern.compile("(?i)^[a-z2-7]{32}$");
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Fallback announce list used when {@code app.magnet.tracker-list} is not configured.
     *
     * <p>Only {@code info_hash} is available from apibay and YTS, so those links would otherwise rely
     * on DHT alone: a torrent whose peers are reachable only through its announce list then looks dead
     * to the client. These are long-running public trackers; every host was resolution-checked and most
     * also appear in the announce lists of live releases. Site-specific trackers (for example a single
     * indexer's own tracker) are deliberately excluded because they do not accept foreign torrents.
     */
    static final List<String> DEFAULT_TRACKERS = List.of(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://tracker.qu.ax:6969/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker.ducks.party:1984/announce",
            "udp://tracker.auctor.tv:6969/announce",
            "udp://explodie.org:6969/announce",
            "udp://tracker.nyaa.vc:6969/announce",
            "udp://tracker.gmi.gd:6969/announce",
            "udp://bt1.archive.org:6969/announce");

    private MagnetUriUtils() {
    }

    static Optional<String> normalizeInfoHash(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.regionMatches(true, 0, "magnet:?", 0, "magnet:?".length())) {
            return magnetInfoHash(value);
        }
        if (value.regionMatches(true, 0, "urn:btih:", 0, "urn:btih:".length())) {
            value = value.substring("urn:btih:".length());
        } else if (value.regionMatches(true, 0, "btih:", 0, "btih:".length())) {
            value = value.substring("btih:".length());
        }
        return normalizeHashValue(value);
    }

    private static Optional<String> magnetInfoHash(String magnetUri) {
        int queryStart = magnetUri.indexOf('?');
        if (queryStart < 0) {
            return Optional.empty();
        }
        String query = magnetUri.substring(queryStart + 1);
        int fragmentStart = query.indexOf('#');
        if (fragmentStart >= 0) {
            query = query.substring(0, fragmentStart);
        }

        String selectedHash = null;
        boolean invalidBtih = false;
        for (String parameter : query.split("&", -1)) {
            int separator = parameter.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = decodeQueryPart(parameter.substring(0, separator)).orElse(null);
            if (!"xt".equalsIgnoreCase(key)) {
                continue;
            }
            String exactTopic = decodeQueryPart(parameter.substring(separator + 1)).orElse(null);
            if (exactTopic == null) { invalidBtih = true; continue; }
            if (!exactTopic.regionMatches(true, 0, "urn:btih:", 0, "urn:btih:".length())) {
                continue;
            }
            Optional<String> normalized = normalizeHashValue(exactTopic.substring("urn:btih:".length()));
            if (normalized.isEmpty()) {
                invalidBtih = true;
                continue;
            }
            if (selectedHash != null && !selectedHash.equals(normalized.get())) {
                return Optional.empty();
            }
            selectedHash = normalized.get();
        }
        return invalidBtih ? Optional.empty() : Optional.ofNullable(selectedHash);
    }

    private static Optional<String> decodeQueryPart(String value) {
        try {
            return Optional.of(URLDecoder.decode(value, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> normalizeHashValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (HEX_PATTERN.matcher(value).matches()) {
            return Optional.of(value.toUpperCase(Locale.ROOT));
        }
        if (!BASE32_PATTERN.matcher(value).matches()) {
            return Optional.empty();
        }
        return decodeBase32(value);
    }

    private static Optional<String> decodeBase32(String value) {
        byte[] bytes = new byte[20];
        int buffer = 0;
        int bitsInBuffer = 0;
        int byteIndex = 0;
        for (int index = 0; index < value.length(); index++) {
            int digit = BASE32_ALPHABET.indexOf(Character.toUpperCase(value.charAt(index)));
            if (digit < 0) {
                return Optional.empty();
            }
            buffer = (buffer << 5) | digit;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                if (byteIndex >= bytes.length) {
                    return Optional.empty();
                }
                bytes[byteIndex++] = (byte) (buffer >> bitsInBuffer);
                buffer &= (1 << bitsInBuffer) - 1;
            }
        }
        if (byteIndex != bytes.length || bitsInBuffer != 0) {
            return Optional.empty();
        }
        StringBuilder hex = new StringBuilder(40);
        for (byte hashByte : bytes) {
            hex.append(Character.forDigit((hashByte >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(hashByte & 0x0f, 16));
        }
        return Optional.of(hex.toString().toUpperCase(Locale.ROOT));
    }

    static String buildMagnetUri(String infoHash, String title) {
        String normalized = normalizeInfoHash(infoHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid info hash"));
        StringBuilder builder = new StringBuilder("magnet:?xt=urn:btih:").append(normalized);
        if (title != null && !title.isBlank()) {
            builder.append("&dn=").append(encodeQueryValue(title.trim()));
        }
        return builder.toString();
    }

    /** Distinct announce URLs a magnet already carries, in magnet order. */
    static List<String> trackersOf(String magnetUri) {
        if (magnetUri == null || magnetUri.isBlank()) {
            return List.of();
        }
        int queryStart = magnetUri.indexOf('?');
        if (queryStart < 0) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        String query = magnetUri.substring(queryStart + 1);
        int fragmentStart = query.indexOf('#');
        if (fragmentStart >= 0) {
            query = query.substring(0, fragmentStart);
        }
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator < 0 || !"tr".equalsIgnoreCase(
                    decodeQueryPart(parameter.substring(0, separator)).orElse(parameter.substring(0, separator)).trim())) {
                continue;
            }
            decodeQueryPart(parameter.substring(separator + 1))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * Adds configured trackers without rebuilding or normalizing the provider's query fields.
     *
     * <p>Order is preserved: the provider's own announce list stays first, because those trackers are
     * known to serve that specific torrent, and the configured defaults follow.
     */
    static String ensureTrackers(String magnetUri, List<String> trackers) {
        if (magnetUri == null || !magnetUri.regionMatches(true, 0, "magnet:?", 0, 8)
                || trackers == null || trackers.isEmpty()) {
            return magnetUri;
        }
        if (normalizeInfoHash(magnetUri).isEmpty()) {
            return magnetUri;
        }
        List<String> existing = trackersOf(magnetUri);
        List<String> missing = mergeTrackers(existing, trackers).stream()
                .filter(tracker -> !existing.contains(tracker))
                .toList();
        return appendTrackers(magnetUri, missing);
    }

    /** Union of two announce lists, existing entries first and duplicates removed. */
    static List<String> mergeTrackers(List<String> existing, List<String> additional) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            existing.stream().filter(MagnetUriUtils::hasText)
                    .forEach(value -> merged.add(value.trim()));
        }
        if (additional != null) {
            additional.stream().filter(MagnetUriUtils::hasText)
                    .forEach(value -> merged.add(value.trim()));
        }
        return List.copyOf(merged);
    }

    /**
     * Combines the stored magnet with an incoming one for the same info hash.
     *
     * <p>Providers differ in what they publish: therarbg hands back a magnet with a full announce list
     * while apibay and YTS return only the hash. Whichever provider was stored last used to win, which
     * silently downgraded a richer link to a bare one. Keeping the union means a link never loses
     * trackers it once had.
     *
     * <p>Public trackers are appended separately when delivering approved links.
     */
    static String mergeMagnets(String existing, String incoming, String infoHash, String fallbackTitle) {
        Optional<String> normalized = normalizeInfoHash(infoHash);
        if (normalized.isEmpty()) {
            return incoming == null ? existing : incoming;
        }
        String expectedHash = normalized.get();
        List<String> existingFields = validatedFields(existing, expectedHash);
        List<String> incomingFields = validatedFields(incoming, expectedHash);
        List<String> mergedFields = mergeQueryFields(existingFields, incomingFields);
        if (displayName(incomingFields).isEmpty() && displayName(existingFields).isEmpty()
                && hasText(fallbackTitle)) {
            mergedFields.add("dn=" + encodeQueryValue(fallbackTitle.trim()));
        }
        if (mergedFields.isEmpty()) {
            return buildMagnetUri(expectedHash, fallbackTitle);
        }
        return "magnet:?" + String.join("&", mergedFields);
    }

    static boolean hasTrackers(String magnetUri) {
        return !trackersOf(magnetUri).isEmpty();
    }

    private static List<String> validatedFields(String magnetUri, String expectedHash) {
        if (magnetUri == null || !magnetUri.regionMatches(true, 0, "magnet:?", 0, 8)) {
            return List.of();
        }
        Optional<String> uriHash = normalizeInfoHash(magnetUri);
        return uriHash.filter(expectedHash::equals).map(ignored -> queryFields(magnetUri)).orElseGet(List::of);
    }

    private static List<String> queryFields(String magnetUri) {
        int queryStart = magnetUri.indexOf('?');
        if (queryStart < 0) {
            return new ArrayList<>();
        }
        int fragmentStart = magnetUri.indexOf('#', queryStart + 1);
        String query = magnetUri.substring(queryStart + 1, fragmentStart < 0 ? magnetUri.length() : fragmentStart);
        return new ArrayList<>(List.of(query.split("&", -1)));
    }

    private static String appendTrackers(String magnetUri, List<String> trackers) {
        if (trackers.isEmpty()) {
            return magnetUri;
        }
        int fragmentStart = magnetUri.indexOf('#');
        String uri = fragmentStart < 0 ? magnetUri : magnetUri.substring(0, fragmentStart);
        String fragment = fragmentStart < 0 ? "" : magnetUri.substring(fragmentStart);
        StringBuilder builder = new StringBuilder(uri);
        for (String tracker : trackers) {
            builder.append("&tr=").append(encodeQueryValue(tracker));
        }
        return builder.append(fragment).toString();
    }

    private static List<String> mergeQueryFields(List<String> existing, List<String> incoming) {
        List<String> merged = new ArrayList<>(existing);
        List<QueryField> existingParsed = parseFields(existing);
        List<QueryField> incomingParsed = parseFields(incoming);

        boolean incomingHasDisplayName = incomingParsed.stream().anyMatch(field -> field.keyEquals("dn"));
        if (incomingHasDisplayName) {
            merged.removeIf(field -> parseField(field).keyEquals("dn"));
            existingParsed = parseFields(merged);
        }

        Map<String, Integer> existingCounts = countFields(existingParsed);
        Map<String, Integer> incomingCounts = countFields(incomingParsed);
        Map<String, Integer> appendedCounts = new LinkedHashMap<>();
        Set<String> knownTrackers = new LinkedHashSet<>();
        for (QueryField field : existingParsed) {
            if (field.keyEquals("tr") && field.decodedValue() != null) {
                knownTrackers.add(field.decodedValue().trim());
            }
        }
        for (String rawField : incoming) {
            QueryField field = parseField(rawField);
            if (field.keyEquals("dn") && incomingHasDisplayName) {
                merged.add(rawField);
                continue;
            }
            if (field.keyEquals("tr") && field.decodedValue() != null
                    && !knownTrackers.add(field.decodedValue().trim())) {
                continue;
            }
            String identity = field.identity();
            int incomingCount = incomingCounts.getOrDefault(identity, 0);
            int alreadyPresent = existingCounts.getOrDefault(identity, 0)
                    + appendedCounts.getOrDefault(identity, 0);
            if (alreadyPresent < incomingCount) {
                merged.add(rawField);
                appendedCounts.merge(identity, 1, Integer::sum);
            }
        }
        return merged;
    }

    private static List<QueryField> parseFields(List<String> rawFields) {
        return rawFields.stream().map(MagnetUriUtils::parseField).toList();
    }

    private static Map<String, Integer> countFields(List<QueryField> fields) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QueryField field : fields) {
            counts.merge(field.identity(), 1, Integer::sum);
        }
        return counts;
    }

    private static QueryField parseField(String rawField) {
        int separator = rawField.indexOf('=');
        String rawKey = separator < 0 ? rawField : rawField.substring(0, separator);
        String rawValue = separator < 0 ? "" : rawField.substring(separator + 1);
        String decodedKey = decodeQueryPart(rawKey).orElse(null);
        String decodedValue = decodeQueryPart(rawValue).orElse(null);
        return new QueryField(rawField, rawKey, rawValue, decodedKey, decodedValue);
    }

    private record QueryField(String rawField, String rawKey, String rawValue, String decodedKey, String decodedValue) {
        private boolean keyEquals(String key) {
            return key.equalsIgnoreCase(decodedKey == null ? rawKey : decodedKey);
        }

        private String identity() {
            String key = decodedKey == null ? rawKey : decodedKey;
            String value = decodedValue == null ? rawValue : decodedValue;
            if ("xt".equalsIgnoreCase(key) && value.regionMatches(true, 0, "urn:btih:", 0, 9)) {
                return "xt\u0000" + normalizeHashValue(value.substring(9))
                        .map(hash -> "urn:btih:" + hash).orElse(value);
            }
            if ("xt".equalsIgnoreCase(key)) {
                return "xt\u0000" + value;
            }
            return rawField;
        }
    }

    private static Optional<String> displayName(List<String> fields) {
        return fields.stream().map(MagnetUriUtils::parseField)
                .filter(field -> field.keyEquals("dn"))
                .map(QueryField::decodedValue)
                .filter(MagnetUriUtils::hasText)
                .findFirst();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
