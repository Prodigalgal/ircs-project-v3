package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/** Streaming reader for IMDb's official gzip TSV snapshots. */
final class ImdbCatalogSnapshotReader {
    private static final int MAX_LINE_CHARS = 1_048_576;
    private static final int GZIP_BUFFER_BYTES = 64 * 1024;
    private static final int BATCH_SIZE = 500;
    private static final String BASICS_HEADER = "tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres";
    private static final String RATINGS_HEADER = "tconst\taverageRating\tnumVotes";
    private static final String ALIASES_HEADER = "titleId\tordering\ttitle\tregion\tlanguage\ttypes\tattributes\tisOriginalTitle";
    private static final String MISSING = "\\N";
    private static final int MAX_ALIASES_PER_TITLE = 32;
    private static final int MAX_ALIAS_TITLE_CHARS = 500;
    private static final int MAX_ALIAS_LOCALE_CHARS = 32;

    private ImdbCatalogSnapshotReader() { }

    record Title(String imdbId, String mediaType, String title, String originalTitle,
                 Integer year, Integer runtimeMinutes, List<String> genres) { }
    record Rating(String imdbId, BigDecimal rating, long votes) { }
    record Alias(String title, String region, String language, Boolean originalTitle) { }

    private static final Comparator<Alias> ALIAS_ORDER = Comparator
            .comparingInt(ImdbCatalogSnapshotReader::aliasPriority)
            .thenComparing(Alias::title)
            .thenComparing(Alias::region, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Alias::language, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Alias::originalTitle, Comparator.nullsFirst(Comparator.naturalOrder()));

    static int readTitles(InputStream compressed, Predicate<String> tracked, Consumer<List<Title>> store) throws IOException {
        try (var reader = open(compressed)) {
            requireHeader(reader, BASICS_HEADER);
            int count = 0;
            boolean hadRow = false;
            var batch = new ArrayList<Title>(BATCH_SIZE);
            String line;
            while ((line = reader.next()) != null) {
                hadRow = true;
                String id = trackedId(line, tracked);
                if (id == null) continue;
                String[] f = fields(line, 9);
                String type = f[1];
                String media = switch (type) {
                    case "movie", "tvMovie" -> "movie";
                    case "tvSeries", "tvMiniSeries" -> "tv";
                    default -> null;
                };
                if (media == null || !"0".equals(f[4])) continue;
                batch.add(new Title(id, media, value(f[2]), value(f[3]), optionalInt(f[5]), optionalInt(f[7]), genres(f[8])));
                count++;
                if (batch.size() == BATCH_SIZE) flush(batch, store);
            }
            flush(batch, store);
            if (!hadRow) throw invalid();
            return count;
        }
    }

    static int readRatings(InputStream compressed, Predicate<String> tracked, Consumer<List<Rating>> store) throws IOException {
        try (var reader = open(compressed)) {
            requireHeader(reader, RATINGS_HEADER);
            int count = 0;
            boolean hadRow = false;
            var batch = new ArrayList<Rating>(BATCH_SIZE);
            String line;
            while ((line = reader.next()) != null) {
                hadRow = true;
                String id = trackedId(line, tracked);
                if (id == null) continue;
                String[] f = fields(line, 3);
                try {
                    BigDecimal rating = new BigDecimal(f[1]);
                    long votes = Long.parseLong(f[2]);
                    if (rating.scale() < 0 || rating.scale() > 2 || rating.signum() < 0
                            || rating.compareTo(BigDecimal.TEN) > 0 || votes < 0) throw invalid();
                    batch.add(new Rating(id, rating, votes));
                } catch (NumberFormatException ex) {
                    throw invalid();
                }
                count++;
                if (batch.size() == BATCH_SIZE) flush(batch, store);
            }
            flush(batch, store);
            if (!hadRow) throw invalid();
            return count;
        }
    }

    static int readAliases(InputStream compressed, Predicate<String> tracked,
                           BiConsumer<String, List<Alias>> accept) throws IOException {
        Map<String, TreeSet<Alias>> retained = new HashMap<>();
        try (var reader = open(compressed)) {
            requireHeader(reader, ALIASES_HEADER);
            boolean hadRow = false;
            String line;
            while ((line = reader.next()) != null) {
                hadRow = true;
                String id = trackedId(line, tracked);
                if (id == null) continue;
                String[] f = fields(line, 8);
                String title = value(f[2]);
                if (title == null || title.length() > MAX_ALIAS_TITLE_CHARS) continue;
                Alias alias = new Alias(title, locale(f[3]), locale(f[4]), originalTitle(f[7]));
                TreeSet<Alias> aliases = retained.computeIfAbsent(id, ignored -> new TreeSet<>(ALIAS_ORDER));
                aliases.add(alias);
                if (aliases.size() > MAX_ALIASES_PER_TITLE) aliases.pollLast();
            }
            if (!hadRow) throw invalid();
        }
        int count = 0;
        for (var entry : retained.entrySet()) {
            List<Alias> aliases = List.copyOf(entry.getValue());
            accept.accept(entry.getKey(), aliases);
            count += aliases.size();
        }
        return count;
    }

    private static int aliasPriority(Alias alias) {
        int original = Boolean.TRUE.equals(alias.originalTitle()) ? 0 : 1;
        String region = alias.region() == null ? "" : alias.region();
        String language = alias.language() == null ? "" : alias.language();
        boolean preferred = switch (region) {
            case "CN", "HK", "TW", "MO", "SG" -> true;
            default -> false;
        } || switch (language) {
            case "zh", "cmn", "yue" -> true;
            default -> false;
        };
        return original * 2 + (preferred ? 0 : 1);
    }

    private static String locale(String text) {
        String value = value(text);
        return value != null && value.length() <= MAX_ALIAS_LOCALE_CHARS ? value : null;
    }

    private static Boolean originalTitle(String text) throws IOException {
        if (MISSING.equals(text)) return null;
        return switch (text) {
            case "0" -> false;
            case "1" -> true;
            default -> throw invalid();
        };
    }

    private static Lines open(InputStream in) throws IOException {
        return new Lines(new InputStreamReader(new GZIPInputStream(in, GZIP_BUFFER_BYTES), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }

    private static void requireHeader(Lines lines, String expected) throws IOException {
        if (!expected.equals(lines.next())) throw invalid();
    }

    private static String[] fields(String line, int expected) throws IOException {
        String[] fields = line.split("\t", -1);
        if (fields.length != expected) throw invalid();
        return fields;
    }

    private static String trackedId(String line, Predicate<String> tracked) throws IOException {
        int separator = line.indexOf('\t');
        if (separator < 0) throw invalid();
        String id = line.substring(0, separator);
        return tracked.test(id) ? AuthoritativeIds.normalizeImdb(id) : null;
    }

    private static String value(String text) {
        return text.isBlank() || MISSING.equals(text) ? null : text;
    }

    private static Integer optionalInt(String text) {
        if (MISSING.equals(text)) return null;
        try {
            int number = Integer.parseInt(text);
            return number > 0 ? number : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> genres(String s) {
        if (MISSING.equals(s) || s.isEmpty()) return List.of();
        return List.of(s.split(",", -1));
    }
    private static <T> void flush(ArrayList<T> batch, Consumer<List<T>> store) {
        if (!batch.isEmpty()) {
            store.accept(List.copyOf(batch));
            batch.clear();
        }
    }

    private static IOException invalid() {
        return new IOException("IMDB_SNAPSHOT_INVALID");
    }

    private static final class Lines implements AutoCloseable {
        private final Reader reader;
        private final char[] buffer = new char[16 * 1024];
        private int offset;
        private int available;

        private Lines(Reader reader) {
            this.reader = reader;
        }

        String next() throws IOException {
            StringBuilder prefix = null;
            for (;;) {
                if (offset == available) {
                    available = reader.read(buffer);
                    offset = 0;
                    if (available < 0) return prefix == null ? null : stripCr(prefix.toString());
                }
                if (available < 0) return null;
                int end = offset;
                while (end < available && buffer[end] != '\n') end++;
                int length = end - offset;
                if (length + (prefix == null ? 0 : prefix.length()) > MAX_LINE_CHARS) throw invalid();
                if (end < available) {
                    String line = prefix == null ? new String(buffer, offset, length)
                            : prefix.append(buffer, offset, length).toString();
                    offset = end + 1;
                    return stripCr(line);
                }
                if (prefix == null) prefix = new StringBuilder(length);
                prefix.append(buffer, offset, length);
                offset = available;
            }
        }

        private static String stripCr(String line) {
            return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
