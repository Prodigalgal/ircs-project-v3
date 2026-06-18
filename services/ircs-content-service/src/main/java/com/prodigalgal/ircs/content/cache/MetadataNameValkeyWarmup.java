package com.prodigalgal.ircs.content.cache;

import com.prodigalgal.ircs.common.metadata.MetadataNameOwnerType;
import com.prodigalgal.ircs.common.metadata.MetadataNameRelation;
import com.prodigalgal.ircs.common.metadata.MetadataNameValkeyCache;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
class MetadataNameValkeyWarmup {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectProvider<MetadataNameValkeyCache> metadataNameCacheProvider;

    @EventListener(ApplicationReadyEvent.class)
    void warmup() {
        Thread warmupThread = new Thread(this::warmupInBackground, "metadata-name-valkey-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    private void warmupInBackground() {
        MetadataNameValkeyCache metadataNameCache = metadataNameCacheProvider.getIfAvailable();
        if (metadataNameCache == null) {
            log.warn("Metadata name Valkey cache bean is unavailable; warmup skipped");
            return;
        }
        long started = System.nanoTime();
        try {
            Map<UUID, Set<String>> rawActors = queryNameSetById(
                    """
                    select va.video_id as owner_id, a.name
                    from video_actors va
                    join actors a on a.id = va.actor_id
                    """);
            Map<UUID, Set<String>> rawDirectors = queryNameSetById(
                    """
                    select vd.video_id as owner_id, d.name
                    from video_directors vd
                    join directors d on d.id = vd.director_id
                    """);
            Map<UUID, Set<String>> rawAreas = queryNameSetById(
                    """
                    select vra.video_id as owner_id, sa.name
                    from video_raw_areas vra
                    join raw_areas ra on ra.id = vra.raw_area_id
                    join standard_areas sa on sa.id = ra.standard_area_id
                    """);
            Map<UUID, Set<String>> unifiedActors = queryNameSetById(
                    """
                    select uva.unified_video_id as owner_id, a.name
                    from unified_video_actors uva
                    join actors a on a.id = uva.actor_id
                    """);
            Map<UUID, Set<String>> unifiedDirectors = queryNameSetById(
                    """
                    select uvd.unified_video_id as owner_id, d.name
                    from unified_video_directors uvd
                    join directors d on d.id = uvd.director_id
                    """);
            Map<UUID, Set<String>> unifiedAreas = queryNameSetById(
                    """
                    select uvsa.unified_video_id as owner_id, sa.name
                    from unified_video_standard_areas uvsa
                    join standard_areas sa on sa.id = uvsa.standard_area_id
                    """);

            metadataNameCache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.ACTORS, rawActors);
            metadataNameCache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.DIRECTORS, rawDirectors);
            metadataNameCache.replaceNames(MetadataNameOwnerType.RAW, MetadataNameRelation.AREAS, rawAreas);
            metadataNameCache.replaceNames(MetadataNameOwnerType.UNIFIED, MetadataNameRelation.ACTORS, unifiedActors);
            metadataNameCache.replaceNames(MetadataNameOwnerType.UNIFIED, MetadataNameRelation.DIRECTORS, unifiedDirectors);
            metadataNameCache.replaceNames(MetadataNameOwnerType.UNIFIED, MetadataNameRelation.AREAS, unifiedAreas);

            log.info(
                    "Metadata name Valkey cache warmed: rawActors={}, rawDirectors={}, rawAreas={}, "
                            + "unifiedActors={}, unifiedDirectors={}, unifiedAreas={}, elapsedMs={}",
                    rawActors.size(),
                    rawDirectors.size(),
                    rawAreas.size(),
                    unifiedActors.size(),
                    unifiedDirectors.size(),
                    unifiedAreas.size(),
                    Math.max(0L, (System.nanoTime() - started) / 1_000_000L));
        } catch (RuntimeException ex) {
            log.warn("Metadata name Valkey cache warmup failed; DB fallback remains available", ex);
        }
    }

    private Map<UUID, Set<String>> queryNameSetById(String sql) {
        Map<UUID, Set<String>> result = new HashMap<>();
        jdbcTemplate.query(
                sql,
                new MapSqlParameterSource(),
                rs -> {
                    UUID ownerId = rs.getObject("owner_id", UUID.class);
                    String name = rs.getString("name");
                    if (ownerId != null && StringUtils.hasText(name)) {
                        result.computeIfAbsent(ownerId, ignored -> new HashSet<>()).add(name);
                    }
                });
        result.replaceAll((ignored, names) -> Set.copyOf(names));
        return Map.copyOf(result);
    }
}
