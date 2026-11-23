package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class MediaAssetCleanupScheduler {
    private final NamedParameterJdbcTemplate jdbc;
    private final LocalObjectStorage local;
    private final R2ObjectStorage remote;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("ircs-media-cleanup-vt-");

    @Scheduled(initialDelayString = "${app.storage.asset-cleanup.initial-delay-ms:90000}",
            fixedDelayString = "${app.storage.asset-cleanup.delay-ms:60000}")
    void scheduledCleanup() {
        ScheduledTriggers.submit(executor, this::drain, log, "storage.media-asset-cleanup");
    }

    private void drain() {
        if (!running.compareAndSet(false, true)) return;
        try {
            for (int count = 0; count < 10; count++) {
                UUID lease = UUID.randomUUID();
                UUID asset = jdbc.queryForObject("select claim_media_asset_cleanup(:lease)",
                        Map.of("lease", lease), UUID.class);
                if (asset == null) return;
                process(asset, lease);
            }
        } finally {
            running.set(false);
        }
    }

    private void process(UUID asset, UUID lease) {
        var params = Map.of("asset", asset, "lease", lease);
        try {
            var locations = jdbc.query("""
                    select storage_backend,storage_bucket,object_key from media_asset_locations
                    where media_asset_id=:asset order by storage_backend, id
                    """, params, (rs, row) -> new Location(rs.getString(1), rs.getString(2), rs.getString(3)));
            for (Location location : locations) {
                String filename = location.key().substring(location.key().lastIndexOf('/') + 1);
                if (!filename.startsWith(asset + "-")) throw new IllegalStateException("Invalid media generation key");
                if ("LOCAL".equals(location.backend())) local.deleteIfExists(location.key());
                else remote.deleteCover(location.bucket(), location.key());
            }
            jdbc.update("""
                    delete from media_assets asset using media_asset_cleanup_jobs job
                    where asset.id=:asset and job.media_asset_id=asset.id and job.lease_id=:lease
                      and asset.retired_at is not null
                      and not exists(select from media_image_sources image where image.media_asset_id=asset.id)
                    """, params);
        } catch (RuntimeException ex) {
            log.warn("Media asset cleanup deferred: id={}, errorType={}", asset, ex.getClass().getSimpleName());
            jdbc.update("""
                    update media_asset_cleanup_jobs set lease_id=null, lease_until=null,
                        next_attempt_at=now()+make_interval(mins=>least(60,attempts)*2),last_error='DELETE_FAILED'
                    where media_asset_id=:asset and lease_id=:lease
                    """, params);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private record Location(String backend, String bucket, String key) { }
}
