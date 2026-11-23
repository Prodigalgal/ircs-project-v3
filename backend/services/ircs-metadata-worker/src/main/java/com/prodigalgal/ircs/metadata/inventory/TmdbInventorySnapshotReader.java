package com.prodigalgal.ircs.metadata.inventory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class TmdbInventorySnapshotReader {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_LINE_BYTES = 16 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int BATCH_SIZE = 2_000;
    private final ObjectMapper mapper;

    TmdbInventorySnapshotReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    int read(InputStream source, TmdbInventoryMediaType mediaType, Consumer<List<Entry>> store) throws IOException {
        List<Entry> batch = new ArrayList<>(BATCH_SIZE);
        byte[] line = new byte[MAX_LINE_BYTES];
        int lineSize = 0;
        int rowCount = 0;
        long bytes = 0;
        try (var input = new BufferedInputStream(new GZIPInputStream(source, BUFFER_SIZE), BUFFER_SIZE)) {
            for (int next; (next = input.read()) != -1; ) {
                if (++bytes > MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("SNAPSHOT_EXPANDED_SIZE_LIMIT");
                }
                if (next == '\n') {
                    addEntry(batch, line, lineSize, mediaType);
                    lineSize = 0;
                    if (batch.size() == BATCH_SIZE) {
                        store.accept(batch);
                        rowCount += batch.size();
                        batch.clear();
                    }
                } else {
                    if (lineSize == MAX_LINE_BYTES) {
                        throw new IOException("SNAPSHOT_LINE_SIZE_LIMIT");
                    }
                    line[lineSize++] = (byte) next;
                }
            }
            addEntry(batch, line, lineSize, mediaType);
        }
        if (!batch.isEmpty()) {
            store.accept(batch);
        }
        return rowCount + batch.size();
    }

    private void addEntry(List<Entry> batch, byte[] line, int size, TmdbInventoryMediaType mediaType) throws IOException {
        if (size == 0) {
            return;
        }
        JsonNode node;
        try {
            node = mapper.readTree(new ByteArrayInputStream(line, 0, size));
        } catch (RuntimeException exception) {
            throw new IOException("SNAPSHOT_INVALID_JSON", exception);
        }
        if (node == null || !node.isObject()) {
            throw new IOException("SNAPSHOT_INVALID_ROW");
        }
        JsonNode adult = node.get("adult");
        // The public TV export currently omits this field. The movie export carries an explicit flag.
        if ((adult == null && mediaType == TmdbInventoryMediaType.MOVIE) || (adult != null && !adult.isBoolean())) {
            throw new IOException("SNAPSHOT_INVALID_ADULT_FLAG");
        }
        if (adult != null && adult.asBoolean()) {
            return;
        }
        JsonNode id = node.path("id");
        JsonNode title = node.path(mediaType.titleField);
        JsonNode popularity = node.path("popularity");
        JsonNode video = node.get("video");
        if (!id.isIntegralNumber() || !id.canConvertToInt() || id.asInt() <= 0 || !title.isTextual()
                || !popularity.isNumber() || (video != null && !video.isNull() && !video.isBoolean())) {
            throw new IOException("SNAPSHOT_INVALID_ROW");
        }
        double score = popularity.asDouble();
        if (!Double.isFinite(score) || score < 0) {
            throw new IOException("SNAPSHOT_INVALID_POPULARITY");
        }
        batch.add(new Entry(id.asLong(), title.asText().replace("\u0000", ""), score, video != null && video.asBoolean()));
    }

    record Entry(long tmdbId, String title, double popularity, boolean video) { }
}
