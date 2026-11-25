package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.ImdbTarget;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.Lease;
import com.prodigalgal.ircs.metadata.catalog.ImdbCatalogSnapshotReader.Alias;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.BodyReader;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.Snapshot;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.Validators;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ImdbCatalogSyncWorkerTest {
    private static final String URL = "https://datasets.imdbws.com/title.akas.tsv.gz";
    private static final String TITLE_ID = "tt7605074";
    private final CatalogReferenceSyncRepository repository = mock(CatalogReferenceSyncRepository.class);
    private final CatalogSnapshotHttpClient http = mock(CatalogSnapshotHttpClient.class);
    private final ImdbCatalogSyncWorker worker = new ImdbCatalogSyncWorker(repository, http);
    private final Lease lease = new Lease("IMDb", "title-akas", UUID.randomUUID(), null, null, null);
    private final Map<String, ImdbTarget> targets = Map.of(TITLE_ID, new ImdbTarget(UUID.randomUUID(), "movie"));

    @BeforeEach
    void prepareAliasFeed() {
        ReflectionTestUtils.setField(worker, "titleAkasUrl", URL);
        when(repository.claim(eq("IMDb"), eq("title-akas"), any())).thenReturn(Optional.of(lease));
        when(repository.imdbTargets()).thenReturn(targets);
    }

    @AfterEach
    void stopWorker() {
        worker.close();
    }

    @Test
    void writesAliasSetsAfterTheDownloadThenAdvancesTheCheckpoint() throws Exception {
        when(http.download(eq(URL), any(), eq(Validators.NONE), any(), any())).thenAnswer(call -> {
            int matchedRows = call.<BodyReader>getArgument(4).read(aliasBody());
            assertThat(matchedRows).isOne();
            verify(repository, never()).storeImdbAliases(any(), any(), any());
            return new Snapshot(false, matchedRows, new Validators("\"alias-etag\"", null));
        });

        ReflectionTestUtils.invokeMethod(worker, "run");

        var events = inOrder(repository);
        events.verify(repository).storeImdbAliases(lease, targets,
                Map.of(TITLE_ID, List.of(new Alias("流浪地球", "CN", "zh", false))));
        events.verify(repository).complete(eq(lease), eq(Duration.ofHours(22)), any(), eq("\"alias-etag\""), eq(null));
    }

    @Test
    void preservesTheExistingStateWhenTheTransportRejectsTheParsedBody() throws Exception {
        when(http.download(eq(URL), any(), eq(Validators.NONE), any(), any())).thenAnswer(call -> {
            assertThat(call.<BodyReader>getArgument(4).read(aliasBody())).isOne();
            throw new IOException("SNAPSHOT_CONTENT_LENGTH_MISMATCH");
        });

        ReflectionTestUtils.invokeMethod(worker, "run");

        verify(repository, never()).storeImdbAliases(any(), any(), any());
        verify(repository, never()).complete(any(), any(), any(), any(), any());
        verify(repository).fail(lease, Duration.ofMinutes(30), "SNAPSHOT_CONTENT_LENGTH_MISMATCH");
    }

    private static ByteArrayInputStream aliasBody() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write("""
                    titleId\tordering\ttitle\tregion\tlanguage\ttypes\tattributes\tisOriginalTitle
                    tt7605074\t1\t流浪地球\tCN\tzh\t\\N\t\\N\t0
                    """.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
