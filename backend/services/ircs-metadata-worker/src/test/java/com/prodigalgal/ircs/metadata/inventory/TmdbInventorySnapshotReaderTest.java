package com.prodigalgal.ircs.metadata.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.metadata.inventory.TmdbInventorySnapshotReader.Entry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TmdbInventorySnapshotReaderTest {
    private final TmdbInventorySnapshotReader reader = new TmdbInventorySnapshotReader(JsonMapper.builder().build());

    @Test
    void readsMoviesAndSkipsExplicitAdultRecords() throws IOException {
        byte[] bytes = compress("""
                {"adult":false,"id":42,"original_title":"电影","popularity":5.7,"video":true}
                {"adult":true,"id":43,"original_title":"Restricted","popularity":8.0,"video":false}
                """);
        var entries = new ArrayList<Entry>();
        assertThat(reader.read(new ByteArrayInputStream(bytes), TmdbInventoryMediaType.MOVIE, entries::addAll))
                .isEqualTo(1);
        assertThat(entries).containsExactly(new Entry(42, "电影", 5.7, true));
    }

    @Test
    void acceptsTvWithThePublishedSchemaAndNoTrailingNewline() throws IOException {
        byte[] bytes = compress("{\"id\":42,\"original_name\":\"剧集\",\"popularity\":0}");
        var entries = new ArrayList<Entry>();
        assertThat(reader.read(new ByteArrayInputStream(bytes), TmdbInventoryMediaType.TV, entries::addAll))
                .isEqualTo(1);
        assertThat(entries).containsExactly(new Entry(42, "剧集", 0, false));
    }

    @Test
    void acceptsNullOptionalMovieVideoFlagFromTheOfficialExport() throws IOException {
        byte[] bytes = compress("""
                {"adult":false,"id":1763675,"original_title":"电影","popularity":0,"video":null}
                """);
        var entries = new ArrayList<Entry>();
        assertThat(reader.read(new ByteArrayInputStream(bytes), TmdbInventoryMediaType.MOVIE, entries::addAll))
                .isEqualTo(1);
        assertThat(entries).containsExactly(new Entry(1763675, "电影", 0, false));
    }

    @Test
    void rejectsInvalidMovieFlagsAndOutOfRangeIdentifiers() throws IOException {
        byte[] withoutFlag = compress("{\"id\":42,\"original_title\":\"Title\",\"popularity\":3}");
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(withoutFlag), TmdbInventoryMediaType.MOVIE, rows -> { }))
                .isInstanceOf(IOException.class).hasMessage("SNAPSHOT_INVALID_ADULT_FLAG");

        byte[] outOfRange = compress("{\"id\":2147483648,\"original_name\":\"Series\",\"popularity\":1}");
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(outOfRange), TmdbInventoryMediaType.TV, rows -> { }))
                .isInstanceOf(IOException.class).hasMessage("SNAPSHOT_INVALID_ROW");
    }

    @Test
    void detectsAnIncompleteGzipBeforeReportingSuccess() throws IOException {
        byte[] content = compress("{\"id\":42,\"original_name\":\"Series\",\"popularity\":1}\n");
        byte[] truncated = Arrays.copyOf(content, content.length - 4);
        var entries = new ArrayList<Entry>();
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(truncated), TmdbInventoryMediaType.TV, entries::addAll))
                .isInstanceOf(IOException.class);
        assertThat(entries).isEmpty();
    }

    @Test
    void selectsSnapshotDateUsingTheUtcPublicationWindow() {
        assertThat(TmdbInventoryScheduler.latestPublishedDate(Instant.parse("2026-11-23T08:14:59Z")))
                .isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(TmdbInventoryScheduler.latestPublishedDate(Instant.parse("2026-11-23T08:15:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 23));
    }

    private byte[] compress(String json) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new GZIPOutputStream(buffer)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return buffer.toByteArray();
    }
}
