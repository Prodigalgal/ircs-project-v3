package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class ImdbCatalogSnapshotReaderTest {
    @Test
    void readsTrackedEligibleTitlesAndMapsMissingFields() throws Exception {
        var batches = new ArrayList<ImdbCatalogSnapshotReader.Title>();
        String data = "tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\n"
                + "tt0000001\tmovie\tFilm\t\\N\t0\t2001\t\\N\t90\tDrama,Comedy\n"
                + "tt0000002\tepisode\tEpisode\tEpisode\t0\t2001\t\\N\t20\tDrama\n"
                + "tt0000003\ttvSeries\tSeries\tOriginal\t0\t\\N\t\\N\t\\N\t\\N\n"
                + "tt0000004\tmovie\tAdult\tAdult\t1\t2000\t\\N\t90\tDrama\n";
        int count = ImdbCatalogSnapshotReader.readTitles(gzip(data), "tt0000001"::equals, batches::addAll);
        assertThat(count).isEqualTo(1);
        assertThat(batches).containsExactly(new ImdbCatalogSnapshotReader.Title("tt0000001", "movie", "Film", null,
                2001, 90, java.util.List.of("Drama", "Comedy")));
    }

    @Test
    void parsesRatingsAndRejectsNegativeOrMalformedTrackedValues() throws Exception {
        String header = "tconst\taverageRating\tnumVotes\n";
        var batches = new ArrayList<ImdbCatalogSnapshotReader.Rating>();
        assertThat(ImdbCatalogSnapshotReader.readRatings(gzip(header + "tt0000001\t8.25\t123\n"), id -> true, batches::addAll)).isEqualTo(1);
        assertThat(batches.getFirst().rating()).isEqualByComparingTo("8.25");
        assertThatThrownBy(() -> ImdbCatalogSnapshotReader.readRatings(gzip(header + "tt0000001\t11\t4\n"), id -> true, rows -> { }))
                .isInstanceOf(IOException.class).hasMessage("IMDB_SNAPSHOT_INVALID");
    }

    @Test
    void rejectsWrongHeaderHeaderOnlyAndTruncatedGzip() throws Exception {
        byte[] valid = gzipBytes("tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\n"
                + "tt0000001\tmovie\tFilm\tFilm\t0\t2000\t\\N\t90\tDrama\n");
        assertThatThrownBy(() -> ImdbCatalogSnapshotReader.readTitles(gzip("wrong\nrow\n"), id -> true, rows -> { })).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImdbCatalogSnapshotReader.readTitles(gzip("tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\n"), id -> true, rows -> { }))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImdbCatalogSnapshotReader.readTitles(new ByteArrayInputStream(Arrays.copyOf(valid, valid.length - 4)), id -> true, rows -> { }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void flushesInBoundedBatchesAndAllowsZeroMatchesInNonemptySnapshot() throws Exception {
        var sizes = new ArrayList<Integer>();
        StringBuilder data = new StringBuilder("tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\n");
        for (int i = 1; i <= 1001; i++) data.append(String.format("tt%07d\tmovie\tT\tT\t0\t2000\t\\N\t90\tDrama\n", i));
        assertThat(ImdbCatalogSnapshotReader.readTitles(gzip(data.toString()), id -> true, batch -> sizes.add(batch.size()))).isEqualTo(1001);
        assertThat(sizes).containsExactly(500, 500, 1);
        assertThat(ImdbCatalogSnapshotReader.readTitles(gzip(data.toString()), id -> false, batch -> { })).isZero();
    }

    @Test
    void readsBoundedDistinctAliasesAndPreservesMissingMarkersAcrossInputOrder() throws Exception {
        String header = "titleId\tordering\ttitle\tregion\tlanguage\ttypes\tattributes\tisOriginalTitle\n";
        String rows = "tt0000002\t2\tSecond\t\\N\t\\N\t\\N\t\\N\t\\N\n"
                + "tt0000001\t1\tA\tCN\tzh\t\\N\t\\N\t1\n"
                + "tt0000001\t3\tA\tCN\tzh\t\\N\t\\N\t1\n"
                + "tt0000001\t4\tOther\tUS\ten\t\\N\t\\N\t0\n"
                + "tt0000001\t5\tLocal\tHK\tyue\t\\N\t\\N\t1\n"
                + "tt0000001\t6\t" + "x".repeat(501) + "\tCN\tzh\t\\N\t\\N\t0\n"
                + "tt0000001\t7\t\\N\tCN\tzh\t\\N\t\\N\t0\n"
                + "tt0000003\t1\tUntracked\tCN\tzh\t\\N\t\\N\t1\n";
        Map<String, List<ImdbCatalogSnapshotReader.Alias>> found = new HashMap<>();
        int count = ImdbCatalogSnapshotReader.readAliases(gzip(header + rows), id -> !id.equals("tt0000003"), found::put);
        assertThat(count).isEqualTo(4);
        assertThat(found.get("tt0000001")).containsExactly(
                new ImdbCatalogSnapshotReader.Alias("A", "CN", "zh", true),
                new ImdbCatalogSnapshotReader.Alias("Local", "HK", "yue", true),
                new ImdbCatalogSnapshotReader.Alias("Other", "US", "en", false));
        assertThat(found.get("tt0000002")).containsExactly(
                new ImdbCatalogSnapshotReader.Alias("Second", null, null, null));

        List<String> lines = new ArrayList<>(Arrays.asList(rows.split("\n")));
        Collections.reverse(lines);
        Map<String, List<ImdbCatalogSnapshotReader.Alias>> reversed = new HashMap<>();
        ImdbCatalogSnapshotReader.readAliases(gzip(header + String.join("\n", lines) + "\n"),
                id -> !id.equals("tt0000003"), reversed::put);
        assertThat(reversed).isEqualTo(found);
    }

    @Test
    void retainsTopThirtyTwoAliasesDeterministicallyAndEmitsOnlyAfterGzipValidation() throws Exception {
        String header = "titleId\tordering\ttitle\tregion\tlanguage\ttypes\tattributes\tisOriginalTitle\n";
        StringBuilder rows = new StringBuilder(header);
        for (int i = 39; i >= 0; i--) {
            rows.append("tt0000001\t").append(i).append("\tAlias").append(String.format("%02d", i))
                    .append("\tUS\ten\t\\N\t\\N\t0\n");
        }
        var first = new HashMap<String, List<ImdbCatalogSnapshotReader.Alias>>();
        assertThat(ImdbCatalogSnapshotReader.readAliases(gzip(rows.toString()), id -> true, first::put)).isEqualTo(32);
        assertThat(first.get("tt0000001")).hasSize(32);
        assertThat(first.get("tt0000001").getFirst().title()).isEqualTo("Alias00");

        StringBuilder reordered = new StringBuilder(header);
        for (int i = 0; i < 40; i++) {
            reordered.append("tt0000001\t").append(i).append("\tAlias").append(String.format("%02d", i))
                    .append("\tUS\ten\t\\N\t\\N\t0\n");
        }
        var second = new HashMap<String, List<ImdbCatalogSnapshotReader.Alias>>();
        ImdbCatalogSnapshotReader.readAliases(gzip(reordered.toString()), id -> true, second::put);
        assertThat(second).isEqualTo(first);

        byte[] valid = gzipBytes(header + "tt0000001\t1\tA\tUS\ten\t\\N\t\\N\t0\n");
        var emitted = new ArrayList<String>();
        assertThatThrownBy(() -> ImdbCatalogSnapshotReader.readAliases(
                new ByteArrayInputStream(Arrays.copyOf(valid, valid.length - 4)), id -> true,
                (id, aliases) -> emitted.add(id))).isInstanceOf(IOException.class);
        assertThat(emitted).isEmpty();
    }

    private ByteArrayInputStream gzip(String text) throws IOException {
        return new ByteArrayInputStream(gzipBytes(text));
    }

    private byte[] gzipBytes(String text) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
