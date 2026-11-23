package com.prodigalgal.ircs.interaction;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
public class JdbcAdminMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PageResponse<UserMessageResponse> findMessages(
            PageBounds bounds,
            Optional<String> keyword,
            Optional<String> status,
            Optional<Boolean> publicMessage) {
        QueryParts query = buildWhere(keyword, status, publicMessage);
        long total = count("select count(*) from member_feedback_posts um join member_accounts m on m.id = um.member_id" + query.where(),
                query.params());
        List<Object> params = new ArrayList<>(query.params());
        params.add(bounds.size());
        params.add(bounds.offset());
        List<UserMessageResponse> content = jdbcTemplate.query(
                """
                select um.id,
                       um.member_id,
                       coalesce(nullif(m.nickname, ''), '画外用户') as member_nickname,
                       m.email as member_email,
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
                """ + query.where() + """

                order by um.created_at desc, um.id asc
                limit ? offset ?
                """,
                userMessageMapper(),
                params.toArray());
        return PageResponse.of(content, total, bounds);
    }

    public Optional<UserMessageResponse> reply(UUID messageId, String reply, Instant now) {
        return queryOptional(
                """
                with updated as (
                    update member_feedback_posts
                    set status = 'REPLIED',
                        updated_at = ?,
                        version = coalesce(version, 0) + 1
                    where id = ? and deleted_at is null
                    returning id, member_id, content, status, is_public, created_at, updated_at
                ), inserted_reply as (
                    insert into member_feedback_replies (feedback_post_id, content, created_at)
                    select id, ?, clock_timestamp() from updated
                    returning feedback_post_id, content
                )
                select updated.id,
                       updated.member_id,
                       coalesce(nullif(m.nickname, ''), '画外用户') as member_nickname,
                       m.email as member_email,
                       m.avatar_url as member_avatar_url,
                       updated.content,
                       inserted_reply.content as reply,
                       updated.status,
                       updated.is_public,
                       updated.created_at,
                       updated.updated_at
                from updated
                join member_accounts m on m.id = updated.member_id
                join inserted_reply on inserted_reply.feedback_post_id = updated.id
                """,
                Timestamp.from(now),
                messageId,
                reply);
    }

    public Optional<UserMessageResponse> toggleVisibility(UUID messageId, boolean isPublic, Instant now) {
        return queryOptional(
                """
                with updated as (
                    update member_feedback_posts
                    set is_public = ?,
                        updated_at = ?,
                        version = coalesce(version, 0) + 1
                    where id = ? and deleted_at is null
                    returning id, member_id, content, status, is_public, created_at, updated_at
                )
                select updated.id,
                       updated.member_id,
                       coalesce(nullif(m.nickname, ''), '画外用户') as member_nickname,
                       m.email as member_email,
                       m.avatar_url as member_avatar_url,
                       updated.content,
                       reply.content as reply,
                       updated.status,
                       updated.is_public,
                       updated.created_at,
                       updated.updated_at
                from updated
                join member_accounts m on m.id = updated.member_id
                left join lateral (
                    select content from member_feedback_replies where feedback_post_id = updated.id
                    order by created_at desc, id desc limit 1
                ) reply on true
                """,
                isPublic,
                Timestamp.from(now),
                messageId);
    }

    public int delete(UUID messageId) {
        return jdbcTemplate.update(
                "update member_feedback_posts set deleted_at = now(), updated_at = now(), version = coalesce(version, 0) + 1 "
                        + "where id = ? and deleted_at is null",
                messageId);
    }

    private QueryParts buildWhere(Optional<String> keyword, Optional<String> status, Optional<Boolean> publicMessage) {
        List<String> conditions = new ArrayList<>();
        conditions.add("um.deleted_at is null");
        List<Object> params = new ArrayList<>();
        keyword.ifPresent(value -> {
            String pattern = "%" + value.toLowerCase() + "%";
            conditions.add("""
                    (
                        lower(um.content) like ?
                        or lower(coalesce(m.nickname, '')) like ?
                        or lower(coalesce(m.email, '')) like ?
                    )
                    """);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        });
        status.ifPresent(value -> {
            conditions.add("um.status = ?");
            params.add(value);
        });
        publicMessage.ifPresent(value -> {
            conditions.add("um.is_public = ?");
            params.add(value);
        });
        return new QueryParts(" where " + String.join(" and ", conditions), params);
    }

    private Optional<UserMessageResponse> queryOptional(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, userMessageMapper(), args));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private long count(String sql, List<Object> args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    private RowMapper<UserMessageResponse> userMessageMapper() {
        return (rs, rowNum) -> new UserMessageResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("member_id", UUID.class),
                rs.getString("member_nickname"),
                rs.getString("member_email"),
                rs.getString("member_avatar_url"),
                rs.getString("content"),
                rs.getString("reply"),
                rs.getString("status"),
                rs.getBoolean("is_public"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private record QueryParts(String where, List<Object> params) {
    }
}
