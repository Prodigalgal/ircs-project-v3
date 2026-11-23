package com.prodigalgal.ircs.interaction;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcInteractionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CoverImageUrlResolver coverImageUrlResolver;

    public PageResponse<UserMessageResponse> findPublicMessages(PageBounds bounds) {
        long total = count("select count(*) from member_feedback_posts where is_public and deleted_at is null");
        List<UserMessageResponse> content = jdbcTemplate.query(
                """
                select um.id,
                       um.member_id,
                       coalesce(nullif(m.nickname, ''), '画外用户') as member_nickname,
                       m.avatar_url as member_avatar_url,
                       um.content,
                       reply.content as reply,
                       um.status,
                       um.is_public,
                       um.created_at,
                       um.updated_at
                from member_feedback_posts um
                join member_accounts m on m.id = um.member_id
                left join lateral (
                    select content from member_feedback_replies where feedback_post_id = um.id
                    order by created_at desc, id desc limit 1
                ) reply on true
                where um.is_public and um.deleted_at is null
                order by um.created_at desc, um.id asc
                limit ? offset ?
                """,
                userMessageMapper(),
                bounds.size(),
                bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    public PageResponse<InteractionRecordResponse> findHistory(UUID memberId, PageBounds bounds) {
        long total = count("select count(*) from member_playback_progress where member_id = ?", memberId);
        List<InteractionRecordResponse> content = jdbcTemplate.query(
                """
                select mwh.id,
                       mwh.catalog_entry_id,
                       uv.title,
                       uv.score,
                       ci.storage_type as cover_storage_type,
                       ci.original_url as cover_original_url,
                       ci.storage_path as cover_storage_path,
                       sd.domain_value as cover_source_domain,
                       mwh.last_source_record_id,
                       mwh.last_source_playback_item_id,
                       mwh.catalog_episode_id,
                       mwh.episode_name,
                       mwh.progress_seconds,
                       mwh.duration_seconds,
                       mwh.last_watched_at,
                       exists (
                           select 1
                           from member_catalog_favorites mf
                           where mf.member_id = mwh.member_id
                             and mf.catalog_entry_id = mwh.catalog_entry_id
                       ) as is_favorite
                from member_playback_progress mwh
                join catalog_entries uv on uv.id = mwh.catalog_entry_id
                left join media_image_sources ci on ci.id = uv.media_image_source_id
                left join source_origins sd on sd.id = ci.source_origin_id
                where mwh.member_id = ?
                order by mwh.last_watched_at desc, mwh.id asc
                limit ? offset ?
                """,
                interactionRecordMapper(),
                memberId,
                bounds.size(),
                bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    public PageResponse<InteractionRecordResponse> findFavorites(UUID memberId, PageBounds bounds) {
        long total = count("select count(*) from member_catalog_favorites where member_id = ?", memberId);
        List<InteractionRecordResponse> content = jdbcTemplate.query(
                """
                select mf.id,
                       mf.catalog_entry_id,
                       uv.title,
                       uv.score,
                       ci.storage_type as cover_storage_type,
                       ci.original_url as cover_original_url,
                       ci.storage_path as cover_storage_path,
                       sd.domain_value as cover_source_domain,
                       cast(null as uuid) as last_source_record_id,
                       cast(null as uuid) as last_source_playback_item_id,
                       cast(null as uuid) as catalog_episode_id,
                       cast(null as text) as episode_name,
                       0 as progress_seconds,
                       0 as duration_seconds,
                       mf.created_at as last_watched_at,
                       true as is_favorite
                from member_catalog_favorites mf
                join catalog_entries uv on uv.id = mf.catalog_entry_id
                left join media_image_sources ci on ci.id = uv.media_image_source_id
                left join source_origins sd on sd.id = ci.source_origin_id
                where mf.member_id = ?
                order by mf.created_at desc, mf.id asc
                limit ? offset ?
                """,
                interactionRecordMapper(),
                memberId,
                bounds.size(),
                bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    public Optional<String> findMemberStatus(UUID memberId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select status from member_accounts where id = ?",
                    String.class,
                    memberId));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public boolean catalogEntryExists(UUID catalogEntryId) {
        return count("select count(*) from catalog_entries where id = ?", catalogEntryId) > 0;
    }

    public boolean favoriteExists(UUID memberId, UUID catalogEntryId) {
        return count(
                "select count(*) from member_catalog_favorites where member_id = ? and catalog_entry_id = ?",
                memberId,
                catalogEntryId) > 0;
    }

    public void insertFavorite(UUID id, UUID memberId, UUID catalogEntryId, Instant now) {
        jdbcTemplate.update(
                """
                insert into member_catalog_favorites (
                    id, member_id, catalog_entry_id, created_at, updated_at, version
                ) values (?, ?, ?, ?, ?, 0)
                """,
                id,
                memberId,
                catalogEntryId,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public int deleteFavorite(UUID memberId, UUID catalogEntryId) {
        return jdbcTemplate.update(
                "delete from member_catalog_favorites where member_id = ? and catalog_entry_id = ?",
                memberId,
                catalogEntryId);
    }

    public boolean upsertWatchHistory(
            UUID id,
            UUID memberId,
            UUID catalogEntryId,
            UUID sourceRecordId,
            UUID sourcePlaybackItemId,
            String episodeName,
            int progress,
            int duration,
            Instant now) {
        return savePlaybackProgress(id, memberId, catalogEntryId, sourceRecordId, sourcePlaybackItemId,
                episodeName, progress, duration, now, false) > 0;
    }

    public void upsertWatchHistoryIfNewer(
            UUID id,
            UUID memberId,
            UUID catalogEntryId,
            UUID sourceRecordId,
            UUID sourcePlaybackItemId,
            String episodeName,
            int progress,
            int duration,
            Instant now) {
        savePlaybackProgress(id, memberId, catalogEntryId, sourceRecordId, sourcePlaybackItemId,
                episodeName, progress, duration, now, true);
    }

    private int savePlaybackProgress(
            UUID id, UUID memberId, UUID catalogEntryId, UUID sourceRecordId, UUID sourcePlaybackItemId,
            String episodeName, int progress, int duration, Instant now, boolean onlyIfNewer) {
        Timestamp observedAt = Timestamp.from(now);
        return jdbcTemplate.update(
                """
                with input as (
                    select cast(? as uuid) as source_record_id, cast(? as uuid) as source_playback_item_id
                )
                insert into member_playback_progress (
                    id, member_id, catalog_entry_id, work_id, season_id, catalog_episode_id,
                    last_source_record_id, last_source_playback_item_id, episode_name, progress_seconds,
                    duration_seconds, last_watched_at, created_at, updated_at, version
                )
                select ?, account.id, entry.id, entry.work_id,
                       case when input.source_playback_item_id is not null
                         then coalesce(playable.catalog_season_id,entry.season_id) else entry.season_id end,
                       case when input.source_playback_item_id is not null then playable.catalog_episode_id end,
                       playable.source_id,
                       case when input.source_playback_item_id is not null then playable.episode_id end,
                       left(coalesce(case when input.source_playback_item_id is not null then playable.episode_name end, ?), 255),
                       ?, ?, ?, ?, ?, 0
                  from catalog_entries entry
                  join member_accounts account on account.id = ?
                  cross join input
                  left join lateral (
                      select item.source_id, item.episode_id, item.episode_name, item.catalog_episode_id,
                             binding.season_id as catalog_season_id
                        from catalog_list_playable_episodes(entry.id) item
                        join catalog_playback_bindings binding on binding.source_playback_item_id=item.episode_id
                       where (input.source_playback_item_id is not null or input.source_record_id is not null)
                         and (input.source_playback_item_id is null or item.episode_id = input.source_playback_item_id)
                         and (input.source_record_id is null or item.source_id = input.source_record_id)
                       limit 1
                  ) playable on true
                 where entry.id = ?
                   and (playable.source_id is not null
                        or (input.source_playback_item_id is null and input.source_record_id is null))
                on conflict (member_id, catalog_entry_id)
                do update set
                    work_id = excluded.work_id,
                    season_id = excluded.season_id,
                    catalog_episode_id = excluded.catalog_episode_id,
                    last_source_record_id = excluded.last_source_record_id,
                    last_source_playback_item_id = excluded.last_source_playback_item_id,
                    episode_name = excluded.episode_name,
                    progress_seconds = excluded.progress_seconds,
                    duration_seconds = excluded.duration_seconds,
                    last_watched_at = excluded.last_watched_at,
                    updated_at = excluded.updated_at,
                    version = coalesce(member_playback_progress.version, 0) + 1
                where not ? or excluded.last_watched_at >= member_playback_progress.last_watched_at
                """,
                sourceRecordId, sourcePlaybackItemId, id, episodeName, progress, duration,
                observedAt, observedAt, observedAt, memberId, catalogEntryId, onlyIfNewer);
    }

    public int deleteHistory(UUID memberId) {
        return jdbcTemplate.update("delete from member_playback_progress where member_id = ?", memberId);
    }

    public int deleteHistoryRecord(UUID memberId, UUID historyId) {
        return jdbcTemplate.update(
                "delete from member_playback_progress where member_id = ? and id = ?",
                memberId,
                historyId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private RowMapper<UserMessageResponse> userMessageMapper() {
        return (rs, rowNum) -> new UserMessageResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("member_id", UUID.class),
                rs.getString("member_nickname"),
                null,
                rs.getString("member_avatar_url"),
                rs.getString("content"),
                rs.getString("reply"),
                rs.getString("status"),
                rs.getBoolean("is_public"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private RowMapper<InteractionRecordResponse> interactionRecordMapper() {
        return (rs, rowNum) -> new InteractionRecordResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("catalog_entry_id", UUID.class),
                rs.getString("title"),
                coverImageUrl(rs),
                rs.getObject("score", BigDecimal.class),
                getUuid(rs, "last_source_record_id"),
                getUuid(rs, "last_source_playback_item_id"),
                getUuid(rs, "catalog_episode_id"),
                rs.getString("episode_name"),
                getInt(rs, "progress_seconds"),
                getInt(rs, "duration_seconds"),
                toInstant(rs.getTimestamp("last_watched_at")),
                rs.getBoolean("is_favorite"));
    }

    private String coverImageUrl(ResultSet rs) throws SQLException {
        return coverImageUrlResolver.resolve(
                rs.getString("cover_storage_type"),
                rs.getString("cover_original_url"),
                rs.getString("cover_storage_path"),
                rs.getString("cover_source_domain"));
    }

    private UUID getUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private int getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? 0 : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
