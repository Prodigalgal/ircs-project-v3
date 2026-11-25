package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
        byte[] valid = gzip("tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\n"
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

    private byte[] gzip(String text) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) { gzip.write(text.getBytes(StandardCharsets.UTF_8)); }
        return out.toByteArray();
    }
}
