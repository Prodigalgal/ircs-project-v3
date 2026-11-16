package com.prodigalgal.ircs.magnet;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

final class MagnetUriUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)^[a-f0-9]{40}$");
    private static final Pattern BASE32_PATTERN = Pattern.compile("(?i)^[a-z2-7]{32}$");
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

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

    static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
