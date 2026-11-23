package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;

import com.prodigalgal.ircs.contracts.ingestion.IngestionEpisodeDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionPlaylistDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionVideoDTO;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
@RequiredArgsConstructor
public class SourceRecordIngestionRepository {

    private static final int PLAYLIST_WRITE_BATCH_SIZE = 256;
    private static final int PLAYLIST_NAME_LENGTH = 255;
    private static final String INSERT_PLAYLIST = """
            insert into source_playlists
                (id, created_at, updated_at, version, name, source_record_id, source_record_version_id, managed_manually)
            values (:id, now(), now(), 0, :name, :sourceRecordId, :sourceRecordVersionId, false)
            on conflict (id) do update
              set source_record_version_id = excluded.source_record_version_id, updated_at = now()
            """;
    private static final String INSERT_EPISODE = """
            insert into source_playback_items
                (id, created_at, updated_at, version, name, url, playlist_id, source_origin_id, source_record_id)
            values (:id, now(), now(), 0, :name, :url, :playlistId, :sourceDomainId, :sourceRecordId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Optional<SourceRecordState> lockState(IngestionVideoDTO video) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceVid", video.sourceVid())
                .addValue("sourceHash", video.sourceHash())
                .addValue("collectionSourceId", video.dataSourceId());
        String identity = video.dataSourceId() == null ? video.sourceHash() : video.dataSourceId() + ":" + video.sourceVid();
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(:identity, 0))",
                params.addValue("identity", "ircs:source-record:" + identity), (rs, rowNum) -> null);
        String identityPredicate = video.dataSourceId() == null
                ? "source_hash = :sourceHash"
                : "collection_source_id = :collectionSourceId and source_vid = :sourceVid";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select id, data_hash, normalization_status, current_version_id, playback_version_id, current_observed_at
                    from source_records
                    where %s
                    for update
                    """.formatted(identityPredicate),
                    params,
                    (rs, rowNum) -> {
                        Timestamp observedAt = rs.getTimestamp("current_observed_at");
                        return new SourceRecordState(
                            rs.getObject("id", UUID.class),
                            rs.getString("data_hash"),
                            rs.getString("normalization_status"),
                            rs.getObject("current_version_id", UUID.class),
                            rs.getObject("playback_version_id", UUID.class),
                            observedAt == null ? null : observedAt.toInstant());
                    }));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public void recordVersion(UUID sourceRecordId, IngestionVideoDTO video, Instant observedAt, boolean makeCurrent) {
        ObjectNode payload = objectMapper.valueToTree(video);
        payload.remove(List.of("normalizationStatus", "ingestionRetryCount"));
        payload.set("rawMetadata", objectMapper.readTree(rawMetadata(video)));
        if (video.playlists() == null) {
            payload.putArray("playlists");
        }
        jdbcTemplate.update("""
                with inserted as (
                    insert into source_record_versions (id, source_record_id, data_hash, payload, observed_at)
                    values (:id, :sourceRecordId, :dataHash, cast(:payload as jsonb), :observedAt)
                    on conflict (source_record_id, data_hash) do nothing
                    returning id
                )
                update source_records
                   set current_version_id = coalesce(
                       (select id from inserted),
                       (select id from source_record_versions where source_record_id=:sourceRecordId and data_hash=:dataHash)),
                       current_observed_at = :observedAt
                 where id = :sourceRecordId and :makeCurrent
                """, new MapSqlParameterSource("sourceRecordId", sourceRecordId)
                .addValue("id", IrcsUuidGenerators.nextId())
                .addValue("dataHash", video.dataHash())
                .addValue("payload", objectMapper.writeValueAsString(payload))
                .addValue("makeCurrent", makeCurrent)
                .addValue("observedAt", Timestamp.from(observedAt)));
    }

    public void advanceObservationTime(UUID sourceRecordId, Instant observedAt) {
        jdbcTemplate.update("""
                update source_records set current_observed_at = :observedAt
                 where id = :sourceRecordId and current_observed_at < :observedAt
                """, new MapSqlParameterSource("sourceRecordId", sourceRecordId)
                .addValue("observedAt", Timestamp.from(observedAt)));
    }

    public Optional<PlaybackSyncState> lockPlaybackSyncState(UUID sourceRecordId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select r.data_hash, r.normalization_status, r.current_version_id, r.playback_version_id,
                           (v.payload -> 'playlists')::text as playlists
                      from source_records r
                      join source_record_versions v on v.source_record_id=r.id and v.id=r.current_version_id
                     where r.id = :id
                     for update of r
                    """,
                    new MapSqlParameterSource("id", sourceRecordId),
                    (rs, rowNum) -> new PlaybackSyncState(
                            rs.getString("data_hash"), rs.getString("normalization_status"),
                            rs.getObject("current_version_id", UUID.class), rs.getObject("playback_version_id", UUID.class),
                            rs.getString("playlists"))));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public UUID upsertSourceRecord(IngestionVideoDTO video, UUID existingId) {
        UUID id = existingId == null ? IrcsUuidGenerators.nextId() : existingId;
        UUID coverImageId = getOrCreateCoverImage(video.coverImageUrl(), video.dataSourceId());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("sourceVid", video.sourceVid())
                .addValue("sourceHash", trimToLength(video.sourceHash(), 64))
                .addValue("dataHash", trimToLength(video.dataHash(), 64))
                .addValue("title", trimToLength(video.title(), 255))
                .addValue("aliasTitle", trimToLength(video.aliasTitle(), 255))
                .addValue("description", video.description())
                .addValue("coverImageId", coverImageId)
                .addValue("year", trimToLength(video.year(), 20))
                .addValue("area", trimToLength(video.area(), 255))
                .addValue("language", trimToLength(video.language(), 255))
                .addValue("remarks", trimToLength(video.remarks(), 255))
                .addValue("score", video.score())
                .addValue("publishedAt", video.publishedAt() == null ? null : Date.valueOf(video.publishedAt()))
                .addValue("totalEpisodes", trimToLength(video.totalEpisodes(), 50))
                .addValue("duration", trimToLength(video.duration(), 50))
                .addValue("doubanId", AuthoritativeIds.normalizeDouban(video.doubanId()))
                .addValue("tmdbId", AuthoritativeIds.normalizeTmdb(video.tmdbId()))
                .addValue("imdbId", AuthoritativeIds.normalizeImdb(video.imdbId()))
                .addValue("rottenTomatoesId", AuthoritativeIds.normalizeRottenTomatoes(video.rottenTomatoesId()))
                .addValue("rawMetadata", rawMetadata(video))
                .addValue("dataSourceId", video.dataSourceId());

        String conflictTarget = video.dataSourceId() == null ? "source_hash" : "collection_source_id, source_vid";
        return jdbcTemplate.queryForObject(
                """
                insert into source_records (
                    id, created_at, updated_at, version,
                    source_vid, source_hash, data_hash, title, alias_title,
                    description, media_image_source_id, year, area, raw_language_str, remarks, score, published_at,
                    total_episodes, duration,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id,
                    locked_fields, enrichment_status, enrichment_retry_count,
                    normalization_status, normalization_retry_count,
                    aggregation_status, raw_metadata, collection_source_id
                ) values (
                    :id, now(), now(), 0,
                    :sourceVid, :sourceHash, :dataHash, :title, :aliasTitle,
                    :description, :coverImageId, :year, :area, :language, :remarks, :score, :publishedAt,
                    :totalEpisodes, :duration,
                    :doubanId, :tmdbId, :imdbId, :rottenTomatoesId,
                    '[]'::jsonb, 'PENDING', 0,
                    'PENDING', 0,
                    'PENDING', cast(:rawMetadata as jsonb), :dataSourceId
                )
                on conflict (%s) do update set
                    data_hash = excluded.data_hash,
                    title = excluded.title,
                    alias_title = excluded.alias_title,
                    description = excluded.description,
                    media_image_source_id = coalesce(excluded.media_image_source_id, source_records.media_image_source_id),
                    year = excluded.year,
                    area = excluded.area,
                    raw_language_str = excluded.raw_language_str,
                    remarks = excluded.remarks,
                    score = excluded.score,
                    published_at = excluded.published_at,
                    total_episodes = excluded.total_episodes,
                    duration = excluded.duration,
                    douban_id = case when coalesce(jsonb_exists(source_records.locked_fields, 'doubanId'), false)
                        then source_records.douban_id else excluded.douban_id end,
                    tmdb_id = case when coalesce(jsonb_exists(source_records.locked_fields, 'tmdbId'), false)
                        then source_records.tmdb_id else excluded.tmdb_id end,
                    imdb_id = case when coalesce(jsonb_exists(source_records.locked_fields, 'imdbId'), false)
                        then source_records.imdb_id else excluded.imdb_id end,
                    rotten_tomatoes_id = case when coalesce(jsonb_exists(source_records.locked_fields, 'rottenTomatoesId'), false)
                        then source_records.rotten_tomatoes_id else excluded.rotten_tomatoes_id end,
                    normalization_status = 'PENDING',
                    enrichment_status = 'PENDING',
                    aggregation_status = 'PENDING',
                    raw_metadata = excluded.raw_metadata,
                    updated_at = now()
                returning id
                """.formatted(conflictTarget),
                params,
                UUID.class);
    }

    private UUID getOrCreateCoverImage(String coverImageUrl, UUID dataSourceId) {
        if (!StringUtils.hasText(coverImageUrl)) {
            return null;
        }
        CoverImageReference reference = resolveCoverImageReference(coverImageUrl);
        UUID sourceDomainId = upsertSourceDomain(reference.domainValue(), "ingestion-cover");
        linkDataSourceDomain(dataSourceId, sourceDomainId);
        UUID imageId = jdbcTemplate.queryForObject(
                """
                insert into media_image_sources (
                    id, created_at, updated_at, version, storage_type, original_url, storage_path,
                    file_hash, file_size, mime_type, source_origin_id, status, retry_count,
                    next_retry_time, last_error
                ) values (
                    :id, now(), now(), 0, 'EXTERNAL', :originalUrl, null,
                    null, null, null, :sourceDomainId, 'UNPROCESSED', 0,
                    now(), null
                )
                on conflict (original_url, source_origin_id) do update
                set updated_at = media_image_sources.updated_at
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("originalUrl", trimToLength(reference.originalUrl(), 2048))
                        .addValue("sourceDomainId", sourceDomainId),
                UUID.class);
        jdbcTemplate.update(
                """
                update media_image_sources
                set status = 'UNPROCESSED',
                    retry_count = 0,
                    next_retry_time = now(),
                    last_error = null,
                    updated_at = now()
                where id = :id
                  and status in ('FAILED', 'DEAD')
                """,
                new MapSqlParameterSource("id", imageId));
        return imageId;
    }

    private CoverImageReference resolveCoverImageReference(String coverImageUrl) {
        String trimmed = coverImageUrl.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("http")) {
            try {
                URI uri = URI.create(trimmed);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (StringUtils.hasText(scheme) && StringUtils.hasText(host)) {
                    String domainValue = scheme + "://" + host + (uri.getPort() > -1 ? ":" + uri.getPort() : "");
                    String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
                    String relativeUrl = path
                            + (StringUtils.hasText(uri.getRawQuery()) ? "?" + uri.getRawQuery() : "")
                            + (StringUtils.hasText(uri.getRawFragment()) ? "#" + uri.getRawFragment() : "");
                    return new CoverImageReference(domainValue, relativeUrl);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new CoverImageReference("EXTERNAL_COVER", trimmed);
    }

    private UUID upsertSourceDomain(String domainValue, String remark) {
        return jdbcTemplate.queryForObject(
                """
                insert into source_origins (id, created_at, updated_at, version, domain_hash, domain_value, remark)
                values (:id, now(), now(), 0, :domainHash, :domainValue, :remark)
                on conflict (domain_hash) do update set
                    domain_value = excluded.domain_value,
                    updated_at = now()
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("domainHash", sha256(domainValue))
                        .addValue("domainValue", trimToLength(domainValue, 255))
                        .addValue("remark", remark),
                UUID.class);
    }

    private void linkDataSourceDomain(UUID dataSourceId, UUID sourceDomainId) {
        if (dataSourceId == null || sourceDomainId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                insert into collection_source_origins (collection_source_id, source_origin_id, first_seen_at)
                values (:dataSourceId, :sourceDomainId, now())
                on conflict (collection_source_id, source_origin_id) do nothing
                """,
                new MapSqlParameterSource()
                        .addValue("dataSourceId", dataSourceId)
                        .addValue("sourceDomainId", sourceDomainId));
    }

    public int replacePlaylists(UUID sourceRecordId, PlaybackSyncState state) {
        List<IngestionPlaylistDTO> playlists = state.playlists() == null ? List.of()
                : objectMapper.readValue(state.playlists(), new TypeReference<>() {});
        playlists = playlists.stream().filter(Objects::nonNull).toList();
        var recordParam = new MapSqlParameterSource("sourceRecordId", sourceRecordId);
        var existingPlaylists = new HashMap<String, StoredPlaylist>();
        jdbcTemplate.query("""
                select id, name, managed_manually from source_playlists where source_record_id = :sourceRecordId
                """, recordParam, rs -> {
                    existingPlaylists.put(rs.getString("name"),
                            new StoredPlaylist(rs.getObject("id", UUID.class), rs.getBoolean("managed_manually")));
                });
        var existingItems = new HashMap<StoredItemKey, ArrayDeque<UUID>>();
        jdbcTemplate.query("""
                select e.id, e.playlist_id, e.name, e.url, e.source_origin_id
                  from source_playback_items e join source_playlists p on p.id=e.playlist_id
                 where e.source_record_id = :sourceRecordId and not p.managed_manually
                 order by e.id
                """, recordParam, rs -> {
                    var key = new StoredItemKey(rs.getObject("playlist_id", UUID.class), rs.getString("name"),
                            rs.getString("url"), rs.getObject("source_origin_id", UUID.class));
                    existingItems.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(rs.getObject("id", UUID.class));
                });
        var requestedNames = playlists.stream()
                .map(playlist -> StringUtils.hasText(playlist.name()) ? playlist.name().trim() : "默认")
                .map(name -> limitPlaylistName(name, PLAYLIST_NAME_LENGTH))
                .toList();
        // Keep incoming distinct names available, even when a duplicate appears earlier in the message.
        var reservedNames = new HashSet<>(requestedNames);
        var nextSuffixByName = new HashMap<String, Integer>();
        var playlistRows = new ArrayList<MapSqlParameterSource>(playlists.size());
        var episodes = new ArrayList<PlaylistEpisode>();
        var domains = new TreeSet<String>();
        for (int playlistIndex = 0; playlistIndex < playlists.size(); playlistIndex++) {
            IngestionPlaylistDTO playlist = playlists.get(playlistIndex);
            String requestedName = requestedNames.get(playlistIndex);
            String playlistName = requestedName;
            Integer nextSuffix = nextSuffixByName.putIfAbsent(requestedName, 2);
            if (nextSuffix != null) {
                int suffixNumber = nextSuffix;
                do {
                    String suffix = " (" + suffixNumber + ")";
                    playlistName = limitPlaylistName(requestedName, PLAYLIST_NAME_LENGTH - suffix.length()) + suffix;
                    suffixNumber++;
                } while (!reservedNames.add(playlistName));
                nextSuffixByName.put(requestedName, suffixNumber);
            }
            StoredPlaylist existing = existingPlaylists.remove(playlistName);
            if (existing != null && existing.managedManually()) {
                continue;
            }
            UUID playlistId = existing == null ? IrcsUuidGenerators.nextId() : existing.id();
            playlistRows.add(new MapSqlParameterSource("id", playlistId)
                    .addValue("name", playlistName)
                    .addValue("sourceRecordId", sourceRecordId)
                    .addValue("sourceRecordVersionId", state.currentVersionId()));
            if (playlist.episodes() == null) continue;
            for (IngestionEpisodeDTO episode : playlist.episodes()) {
                if (episode == null) continue;
                EpisodeLocation location = resolveEpisodeLocation(episode);
                if (location.domainValue() != null) domains.add(location.domainValue());
                episodes.add(new PlaylistEpisode(playlistId, trimToLength(defaultEpisodeName(episode), 255), location));
            }
        }
        // Acquire any shared domain rows in the same order across concurrent consumers.
        var domainIds = new HashMap<String, UUID>();
        for (String domain : domains) domainIds.put(domain, upsertSourceDomain(domain, "ingestion-worker"));

        for (int offset = 0; offset < playlistRows.size(); offset += PLAYLIST_WRITE_BATCH_SIZE) {
            int end = Math.min(playlistRows.size(), offset + PLAYLIST_WRITE_BATCH_SIZE);
            jdbcTemplate.batchUpdate(INSERT_PLAYLIST, playlistRows.subList(offset, end).toArray(MapSqlParameterSource[]::new));
        }
        var observedDomainIds = new TreeSet<UUID>();
        for (int offset = 0; offset < episodes.size(); offset += PLAYLIST_WRITE_BATCH_SIZE) {
            int end = Math.min(episodes.size(), offset + PLAYLIST_WRITE_BATCH_SIZE);
            var rows = new ArrayList<MapSqlParameterSource>(end - offset);
            for (int index = offset; index < end; index++) {
                var episode = episodes.get(index);
                var location = episode.location();
                UUID domainId = location.domainValue() == null ? location.sourceDomainId() : domainIds.get(location.domainValue());
                if (domainId != null) observedDomainIds.add(domainId);
                String url = trimToLength(location.url(), 2048);
                var previous = existingItems.get(new StoredItemKey(episode.playlistId(), episode.name(), url, domainId));
                if (previous != null && !previous.isEmpty()) {
                    previous.removeFirst();
                    continue;
                }
                rows.add(new MapSqlParameterSource("id", IrcsUuidGenerators.nextId())
                        .addValue("name", episode.name()).addValue("url", url)
                        .addValue("playlistId", episode.playlistId()).addValue("sourceDomainId", domainId)
                        .addValue("sourceRecordId", sourceRecordId));
            }
            if (!rows.isEmpty()) {
                jdbcTemplate.batchUpdate(INSERT_EPISODE, rows.toArray(MapSqlParameterSource[]::new));
            }
        }
        deleteRemovedPlaybackItems(existingItems);
        var stalePlaylists = existingPlaylists.values().stream().filter(playlist -> !playlist.managedManually())
                .map(StoredPlaylist::id).sorted().toList();
        if (!stalePlaylists.isEmpty()) {
            jdbcTemplate.update("delete from source_playlists where id in (:ids)", new MapSqlParameterSource("ids", stalePlaylists));
        }
        if (!observedDomainIds.isEmpty()) {
            jdbcTemplate.update("""
                    insert into collection_source_origins (collection_source_id, source_origin_id, first_seen_at)
                    select video.collection_source_id, domain.id, now()
                      from source_records video
                      cross join source_origins domain
                     where video.id=:sourceRecordId and video.collection_source_id is not null and domain.id in (:domainIds)
                     order by domain.id
                    on conflict (collection_source_id, source_origin_id) do nothing
                    """, recordParam.addValue("domainIds", List.copyOf(observedDomainIds)));
        }
        jdbcTemplate.update("""
                update source_records set playback_version_id = :sourceRecordVersionId where id = :sourceRecordId
                """, recordParam.addValue("sourceRecordVersionId", state.currentVersionId()));
        jdbcTemplate.query("select reconcile_catalog_playback(:sourceRecordId)", recordParam, (rs, rowNum) -> null);
        return playlistRows.size();
    }

    private void deleteRemovedPlaybackItems(HashMap<StoredItemKey, ArrayDeque<UUID>> existingItems) {
        var removed = existingItems.values().stream().flatMap(java.util.Collection::stream).sorted().toList();
        for (int offset = 0; offset < removed.size(); offset += PLAYLIST_WRITE_BATCH_SIZE) {
            int end = Math.min(removed.size(), offset + PLAYLIST_WRITE_BATCH_SIZE);
            jdbcTemplate.update("delete from source_playback_items where id in (:ids)",
                    new MapSqlParameterSource("ids", removed.subList(offset, end)));
        }
    }

    private static String limitPlaylistName(String name, int maxLength) {
        if (name.codePointCount(0, name.length()) <= maxLength) return name;
        return name.substring(0, name.offsetByCodePoints(0, maxLength));
    }

    private EpisodeLocation resolveEpisodeLocation(IngestionEpisodeDTO episode) {
        if (episode.sourceDomainId() != null) {
            return new EpisodeLocation(episode.url(), episode.sourceDomainId(), null);
        }
        if (!StringUtils.hasText(episode.url())) {
            return new EpisodeLocation("", null, null);
        }

        try {
            URI uri = URI.create(episode.url().trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return new EpisodeLocation(episode.url().trim(), null, null);
            }
            String domainValue = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > -1 ? ":" + uri.getPort() : "");
            String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
            String relativePath = path + (StringUtils.hasText(uri.getRawQuery()) ? "?" + uri.getRawQuery() : "");
            return new EpisodeLocation(relativePath, null, domainValue);
        } catch (IllegalArgumentException ignored) {
            return new EpisodeLocation(episode.url().trim(), null, null);
        }
    }

    private String defaultEpisodeName(IngestionEpisodeDTO episode) {
        return StringUtils.hasText(episode.name()) ? episode.name().trim() : "默认";
    }

    private String rawMetadata(IngestionVideoDTO video) {
        return StringUtils.hasText(video.rawMetadata()) ? video.rawMetadata() : "{}";
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record EpisodeLocation(String url, UUID sourceDomainId, String domainValue) {
    }

    private record PlaylistEpisode(UUID playlistId, String name, EpisodeLocation location) {
    }

    record PlaybackSyncState(String dataHash, String normalizationStatus, UUID currentVersionId,
            UUID playbackVersionId, String playlists) {
        boolean playbackCurrent() {
            return currentVersionId.equals(playbackVersionId);
        }
    }

    private record StoredPlaylist(UUID id, boolean managedManually) {
    }

    private record StoredItemKey(UUID playlistId, String name, String url, UUID sourceDomainId) {
    }

    private record CoverImageReference(String domainValue, String originalUrl) {
    }
}
