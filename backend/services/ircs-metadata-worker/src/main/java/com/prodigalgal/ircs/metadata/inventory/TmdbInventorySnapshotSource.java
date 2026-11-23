package com.prodigalgal.ircs.metadata.inventory;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundStreamingTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import com.prodigalgal.ircs.metadata.inventory.TmdbInventorySnapshotReader.Entry;
import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
class TmdbInventorySnapshotSource {
    private static final String EXPORT_ROOT = "https://files.tmdb.org/p/exports/";
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("MM_dd_uuuu", Locale.ROOT);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEADER_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BODY_TIMEOUT = Duration.ofMinutes(8);
    private static final long MAX_COMPRESSED_BYTES = 128L * 1024 * 1024;

    private final OutboundStreamingHttpClient http;
    private final TmdbInventorySnapshotReader reader;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("tmdb-inventory-body-timeout").factory());

    TmdbInventorySnapshotSource(PublicOutboundProxyPool proxyPool, ObjectMapper mapper) {
        this.http = new OutboundStreamingHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundStreamingTransport(CONNECT_TIMEOUT), proxyPool);
        this.reader = new TmdbInventorySnapshotReader(mapper);
    }

    int download(TmdbInventoryMediaType mediaType, LocalDate date, Consumer<List<Entry>> store)
            throws IOException, InterruptedException {
        String url = EXPORT_ROOT + mediaType.filePrefix + "_" + date.format(EXPORT_DATE) + ".json.gz";
        var request = OutboundHttpRequest.get(url, OutboundHttpPolicy.publicFetch(HEADER_TIMEOUT))
                .withHeader("Accept-Encoding", "identity")
                .withHeader("User-Agent", "IRCS-TMDB-Inventory/1.0");
        try (var response = http.execute(request)) {
            if (response.statusCode() != 200) {
                throw new SnapshotStatusException(response.statusCode());
            }
            return readResponse(response, mediaType, store);
        }
    }

    private int readResponse(OutboundStreamingHttpResponse response, TmdbInventoryMediaType mediaType,
                             Consumer<List<Entry>> store) throws IOException {
        long declaredLength = contentLength(response);
        if (declaredLength > MAX_COMPRESSED_BYTES) {
            throw new IOException("SNAPSHOT_COMPRESSED_SIZE_LIMIT");
        }
        AtomicBoolean expired = new AtomicBoolean();
        // The JDK request deadline can end when headers arrive because the body is an InputStream.
        var timeout = watchdog.schedule(() -> {
            expired.set(true);
            try {
                response.close();
            } catch (IOException exception) {
                log.debug("TMDB inventory timeout could not close body: {}", exception.getClass().getSimpleName());
            }
        }, BODY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        var body = new LimitedCompressedInput(response.body());
        try {
            int rows = reader.read(body, mediaType, store);
            if (declaredLength >= 0 && declaredLength != body.bytes) {
                throw new IOException("SNAPSHOT_CONTENT_LENGTH_MISMATCH");
            }
            if (expired.get()) {
                throw new IOException("SNAPSHOT_BODY_TIMEOUT");
            }
            return rows;
        } catch (IOException exception) {
            if (expired.get()) {
                throw new IOException("SNAPSHOT_BODY_TIMEOUT", exception);
            }
            throw exception;
        } finally {
            timeout.cancel(false);
        }
    }

    private long contentLength(OutboundStreamingHttpResponse response) throws IOException {
        var lengths = response.headers().get("content-length");
        if (lengths == null || lengths.isEmpty()) {
            return -1;
        }
        try {
            long parsed = Long.parseLong(lengths.getFirst());
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("SNAPSHOT_CONTENT_LENGTH_INVALID", exception);
        }
    }

    @PreDestroy
    void close() {
        watchdog.shutdownNow();
    }

    static final class SnapshotStatusException extends IOException {
        private final int status;

        SnapshotStatusException(int status) {
            super("SNAPSHOT_HTTP_" + status);
            this.status = status;
        }

        boolean notAvailable() {
            // The official host responds 403 for a not-yet-published export.
            return status == 403 || status == 404;
        }
    }

    private static final class LimitedCompressedInput extends FilterInputStream {
        private long bytes;

        private LimitedCompressedInput(InputStream source) {
            super(source);
        }

        @Override
        public int read() throws IOException {
            int next = in.read();
            if (next >= 0) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            int count = in.read(target, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int received) throws IOException {
            bytes += received;
            if (bytes > MAX_COMPRESSED_BYTES) {
                throw new IOException("SNAPSHOT_COMPRESSED_SIZE_LIMIT");
            }
        }
    }
}
