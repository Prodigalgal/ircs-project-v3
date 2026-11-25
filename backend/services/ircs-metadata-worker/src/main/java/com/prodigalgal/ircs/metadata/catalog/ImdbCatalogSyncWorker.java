package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.ImdbTarget;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.Lease;
import com.prodigalgal.ircs.metadata.catalog.ImdbCatalogSnapshotReader.Alias;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.Limits;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.SnapshotStatusException;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.Validators;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
class ImdbCatalogSyncWorker {
    static final String PROVIDER = "IMDb";
    private static final Limits LIMITS = new Limits(Duration.ofMinutes(15), 1024L * 1024 * 1024);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(25);
    private static final Duration NEXT_SNAPSHOT = Duration.ofHours(22);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(30);
    private static final int ALIAS_WRITE_BATCH = 250;

    private final CatalogReferenceSyncRepository repository;
    private final CatalogSnapshotHttpClient snapshots;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("catalog-imdb-sync-vt-");

    @Value("${app.catalog.reference.imdb.enabled:false}")
    private boolean enabled;
    @Value("${app.catalog.reference.imdb.title-basics-url:}")
    private String titleBasicsUrl;
    @Value("${app.catalog.reference.imdb.title-ratings-url:}")
    private String titleRatingsUrl;
    @Value("${app.catalog.reference.imdb.title-akas-url:}")
    private String titleAkasUrl;

    @Scheduled(initialDelayString = "${app.catalog.reference.imdb.initial-delay-ms:90000}",
            fixedDelayString = "${app.catalog.reference.imdb.fixed-delay-ms:900000}")
    void scheduled() {
        if (enabled) ScheduledTriggers.submit(executor, this::run, log, "catalog.imdb.sync");
    }

    private void run() {
        if (!running.compareAndSet(false, true)) return;
        try {
            if (!StringUtils.hasText(titleBasicsUrl) && !StringUtils.hasText(titleRatingsUrl)
                    && !StringUtils.hasText(titleAkasUrl)) {
                log.warn("IMDb catalog sync has no configured dataset URLs");
                return;
            }
            sync(Feed.TITLES, titleBasicsUrl);
            if (!Thread.currentThread().isInterrupted()) sync(Feed.RATINGS, titleRatingsUrl);
            if (!Thread.currentThread().isInterrupted()) sync(Feed.ALIASES, titleAkasUrl);
        } finally {
            running.set(false);
        }
    }

    private void sync(Feed feed, String url) {
        if (!StringUtils.hasText(url)) return;
        var claimed = repository.claim(PROVIDER, feed.key, LEASE_DURATION);
        if (claimed.isEmpty()) return;
        Lease lease = claimed.orElseThrow();
        try {
            Map<String, ImdbTarget> targets = repository.imdbTargets();
            if (targets.isEmpty()) {
                repository.complete(lease, RETRY_DELAY, lease.fingerprint(), lease.etag(), lease.lastModified());
                return;
            }
            String endpoint = datasetUrl(url);
            String fingerprint = fingerprint(endpoint, targets);
            var validators = fingerprint.equals(lease.fingerprint())
                    ? new Validators(lease.etag(), lease.lastModified()) : Validators.NONE;
            var aliasesById = new HashMap<String, List<Alias>>();
            var snapshot = snapshots.download(endpoint, "IRCS-IMDb-Catalog/1.0", validators, LIMITS,
                    body -> switch (feed) {
                        case TITLES -> ImdbCatalogSnapshotReader.readTitles(body, targets::containsKey,
                                batch -> repository.storeImdbTitles(lease, targets, batch));
                        case RATINGS -> ImdbCatalogSnapshotReader.readRatings(body, targets::containsKey,
                                batch -> repository.storeImdbRatings(lease, targets, batch));
                        case ALIASES -> ImdbCatalogSnapshotReader.readAliases(body, targets::containsKey, aliasesById::put);
                    });
            if (feed == Feed.ALIASES && !snapshot.notModified()) {
                storeAliases(lease, targets, aliasesById);
            }
            if (repository.complete(lease, NEXT_SNAPSHOT, fingerprint,
                    snapshot.validators().etag(), snapshot.validators().lastModified())) {
                log.info("IMDb catalog snapshot processed, feed={}, notModified={}, matchingRows={}",
                        feed.key, snapshot.notModified(), snapshot.rows());
            }
        } catch (InterruptedException ex) {
            repository.fail(lease, RETRY_DELAY, "INTERRUPTED");
            Thread.currentThread().interrupt();
        } catch (SnapshotStatusException ex) {
            fail(lease, "HTTP_" + ex.status());
        } catch (IOException | RuntimeException ex) {
            String reason = ex.getMessage();
            fail(lease, reason != null && reason.matches("(SNAPSHOT|IMDB)_[A-Z0-9_]{1,48}")
                    ? reason : "SNAPSHOT_READ_OR_WRITE_FAILED");
        }
    }

    private void storeAliases(Lease lease, Map<String, ImdbTarget> targets, Map<String, List<Alias>> aliasesById)
            throws InterruptedException {
        var batch = new LinkedHashMap<String, List<Alias>>();
        for (String imdbId : targets.keySet()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            batch.put(imdbId, aliasesById.getOrDefault(imdbId, List.of()));
            if (batch.size() == ALIAS_WRITE_BATCH) {
                repository.storeImdbAliases(lease, targets, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) repository.storeImdbAliases(lease, targets, batch);
    }

    private void fail(Lease lease, String reason) {
        repository.fail(lease, RETRY_DELAY, reason);
        log.warn("IMDb catalog sync deferred, feed={}, reason={}", lease.feed(), reason);
    }

    private static String datasetUrl(String url) {
        URI uri = URI.create(url.strip());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("IMDB_INVALID_DATASET_URL");
        }
        return uri.toASCIIString();
    }

    private static String fingerprint(String endpoint, Map<String, ImdbTarget> targets) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(endpoint.getBytes(StandardCharsets.UTF_8));
            targets.forEach((id, target) -> digest.update(
                    ("\n" + id + ":" + target.workId() + ":" + target.mediaType()).getBytes(StandardCharsets.US_ASCII)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private enum Feed {
        TITLES("title-basics"),
        RATINGS("title-ratings"),
        ALIASES("title-akas");

        private final String key;

        Feed(String key) {
            this.key = key;
        }
    }
}
