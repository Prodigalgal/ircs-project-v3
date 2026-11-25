package com.prodigalgal.ircs.metadata.inventory;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundStreamingTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Shared public streaming transport for catalog exports. Dataset parsing belongs to each provider. */
@Component
@Slf4j
public class CatalogSnapshotHttpClient {
    private static final Duration HEADER_TIMEOUT = Duration.ofSeconds(30);
    private final OutboundStreamingHttpClient http;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("catalog-snapshot-body-timeout").factory());

    public CatalogSnapshotHttpClient(PublicOutboundProxyPool proxyPool) {
        http = new OutboundStreamingHttpClient(new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundStreamingTransport(Duration.ofSeconds(10)), proxyPool);
    }

    public Snapshot download(String url, String userAgent, Validators previous, Limits limits, BodyReader reader)
            throws IOException, InterruptedException {
        var request = OutboundHttpRequest.get(url, OutboundHttpPolicy.publicFetch(HEADER_TIMEOUT))
                .withHeader("Accept-Encoding", "identity").withHeader("User-Agent", userAgent);
        if (previous.etag() != null) request = request.withHeader("If-None-Match", previous.etag());
        if (previous.lastModified() != null) request = request.withHeader("If-Modified-Since", previous.lastModified());
        try (var response = http.execute(request)) {
            if (response.statusCode() == 304 && (previous.etag() != null || previous.lastModified() != null)) {
                return new Snapshot(true, 0, previous);
            }
            if (response.statusCode() != 200) throw new SnapshotStatusException(response.statusCode());
            int rows = readBody(response, limits, reader);
            return new Snapshot(false, rows, new Validators(
                    safeHeader(response, "etag", 512), safeHeader(response, "last-modified", 128)));
        }
    }

    private int readBody(OutboundStreamingHttpResponse response, Limits limits, BodyReader reader) throws IOException {
        long declaredLength = contentLength(response);
        if (declaredLength > limits.maxCompressedBytes()) throw new IOException("SNAPSHOT_COMPRESSED_SIZE_LIMIT");
        AtomicBoolean expired = new AtomicBoolean();
        // The transport timeout may stop at headers when the body is streamed.
        var timeout = watchdog.schedule(() -> {
            expired.set(true);
            try {
                response.close();
            } catch (IOException ex) {
                log.debug("Catalog snapshot timeout closing stream: {}", ex.getClass().getSimpleName());
            }
        }, limits.bodyTimeout().toMillis(), TimeUnit.MILLISECONDS);
        var body = new LimitedCompressedInput(response.body(), limits.maxCompressedBytes());
        try {
            int rows = reader.read(body);
            if (expired.get()) throw new IOException("SNAPSHOT_BODY_TIMEOUT");
            if (declaredLength >= 0 && declaredLength != body.bytes) throw new IOException("SNAPSHOT_CONTENT_LENGTH_MISMATCH");
            return rows;
        } catch (IOException ex) {
            if (expired.get()) throw new IOException("SNAPSHOT_BODY_TIMEOUT", ex);
            throw ex;
        } finally {
            timeout.cancel(false);
        }
    }

    private String safeHeader(OutboundStreamingHttpResponse response, String name, int maxChars) {
        var values = response.headers().get(name);
        if (values == null || values.isEmpty()) return null;
        String value = values.getFirst();
        return value.length() <= maxChars && value.chars().allMatch(ch -> ch >= 32 && ch < 127) ? value : null;
    }

    private long contentLength(OutboundStreamingHttpResponse response) throws IOException {
        var lengths = response.headers().get("content-length");
        if (lengths == null || lengths.isEmpty()) return -1;
        try {
            long length = Long.parseLong(lengths.getFirst());
            if (length >= 0) return length;
        } catch (NumberFormatException ignored) {
            throw new IOException("SNAPSHOT_CONTENT_LENGTH_INVALID");
        }
        throw new IOException("SNAPSHOT_CONTENT_LENGTH_INVALID");
    }

    @PreDestroy
    void close() {
        watchdog.shutdownNow();
    }

    public record Limits(Duration bodyTimeout, long maxCompressedBytes) { }
    public record Validators(String etag, String lastModified) {
        public static final Validators NONE = new Validators(null, null);
    }
    public record Snapshot(boolean notModified, int rows, Validators validators) { }

    @FunctionalInterface
    public interface BodyReader {
        int read(InputStream body) throws IOException;
    }

    public static final class SnapshotStatusException extends IOException {
        private final int status;

        SnapshotStatusException(int status) {
            super("SNAPSHOT_HTTP_" + status);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private static final class LimitedCompressedInput extends FilterInputStream {
        private final long limit;
        private long bytes;

        private LimitedCompressedInput(InputStream source, long limit) {
            super(source);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int next = in.read();
            if (next >= 0) count(1);
            return next;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            int count = in.read(target, offset, length);
            if (count > 0) count(count);
            return count;
        }

        private void count(int received) throws IOException {
            bytes += received;
            if (bytes > limit) throw new IOException("SNAPSHOT_COMPRESSED_SIZE_LIMIT");
        }
    }
}
