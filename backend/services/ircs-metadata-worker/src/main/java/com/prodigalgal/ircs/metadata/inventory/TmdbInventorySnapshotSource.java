package com.prodigalgal.ircs.metadata.inventory;

import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.Limits;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.Validators;
import com.prodigalgal.ircs.metadata.inventory.TmdbInventorySnapshotReader.Entry;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class TmdbInventorySnapshotSource {
    private static final String EXPORT_ROOT = "https://files.tmdb.org/p/exports/";
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("MM_dd_uuuu", Locale.ROOT);
    private static final Limits LIMITS = new Limits(Duration.ofMinutes(8), 128L * 1024 * 1024);

    private final CatalogSnapshotHttpClient http;
    private final TmdbInventorySnapshotReader reader;

    TmdbInventorySnapshotSource(CatalogSnapshotHttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.reader = new TmdbInventorySnapshotReader(mapper);
    }

    int download(TmdbInventoryMediaType mediaType, LocalDate date, Consumer<List<Entry>> store)
            throws IOException, InterruptedException {
        String url = EXPORT_ROOT + mediaType.filePrefix + "_" + date.format(EXPORT_DATE) + ".json.gz";
        return http.download(url, "IRCS-TMDB-Inventory/1.0", Validators.NONE, LIMITS,
                body -> reader.read(body, mediaType, store)).rows();
    }
}
