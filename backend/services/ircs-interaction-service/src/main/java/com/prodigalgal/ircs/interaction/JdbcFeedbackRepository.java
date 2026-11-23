package com.prodigalgal.ircs.interaction;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserMessageResponse insertMessage(UUID id, UUID memberId, String content, Instant now) {
        return jdbcTemplate.queryForObject(
                """
                with inserted as (
                    insert into member_feedback_posts (
                        id,
                        created_at,
                        updated_at,
                        version,
                        member_id,
                        content,
                        status,
                        is_public
                    ) values (?, ?, ?, 0, ?, ?, 'PENDING', false)
                    returning id, member_id, content, status, is_public, created_at, updated_at
                )
                select inserted.id,
                       inserted.member_id,
                       coalesce(nullif(m.nickname, ''), '画外用户') as member_nickname,
                       m.email as member_email,
                       m.avatar_url as member_avatar_url,
                       inserted.content,
                       cast(null as text) as reply,
                       inserted.status,
                       inserted.is_public,
                       inserted.created_at,
                       inserted.updated_at
                from inserted
                join member_accounts m on m.id = inserted.member_id
                """,
                userMessageMapper(),
                id,
                Timestamp.from(now),
                Timestamp.from(now),
                memberId,
                content);
    }

    public PageResponse<UserMessageResponse> findMemberMessages(UUID memberId, PageBounds bounds) {
        long total = count("select count(*) from member_feedback_posts where member_id = ? and deleted_at is null", memberId);
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
                where um.member_id = ? and um.deleted_at is null
                order by um.created_at desc, um.id asc
                limit ? offset ?
                """,
                userMessageMapper(),
                memberId,
                bounds.size(),
                bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    public long countMemberMessagesCreatedBetween(UUID memberId, Instant startInclusive, Instant endExclusive) {
        return count(
                "select count(*) from member_feedback_posts where member_id = ? and created_at >= ? and created_at < ?",
                memberId,
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive));
    }

    public int deleteMemberMessage(UUID memberId, UUID messageId) {
        return jdbcTemplate.update(
                "update member_feedback_posts set deleted_at = now(), updated_at = now(), version = coalesce(version, 0) + 1 "
                        + "where member_id = ? and id = ? and deleted_at is null",
                memberId,
                messageId);
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
                rs.getString("member_email"),
                rs.getString("member_avatar_url"),
                rs.getString("content"),
                rs.getString("reply"),
                rs.getString("status"),
                rs.getBoolean("is_public"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
