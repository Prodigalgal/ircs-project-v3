package com.prodigalgal.ircs.interaction;

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
public class JdbcMediaRequestRepository {

    private static final String SELECT_SUBMISSION = """
            select s.id, s.demand_id, s.member_id, s.submitted_title, d.release_year, s.extra_info,
                   d.status, d.request_count, s.created_at, d.updated_at,
                   d.last_requested_at, d.scheduled_at, d.completed_at, d.last_error_message,
                   d.existing_video_id, d.existing_video_source, s.spent_points
            """;

    private final JdbcTemplate jdbcTemplate;

    public MediaRequestResponse submit(UUID submissionId, UUID proposedDemandId, UUID memberId, String title,
            String normalizedTitle, Integer releaseYear, String extraInfo, UUID clientRequestId,
            Instant now, int spentPoints) {
        Timestamp submittedAt = Timestamp.from(now);
        return jdbcTemplate.queryForObject(
                """
                with demand as (
                    insert into media_request_demands (
                        id, created_at, updated_at, version, title, normalized_title, release_year,
                        status, request_count, last_requested_at
                    ) values (?, ?, ?, 0, ?, ?, ?, 'PENDING', 1, ?)
                    on conflict (normalized_title, release_year) do update
                       set updated_at = greatest(media_request_demands.updated_at, excluded.updated_at),
                           version = media_request_demands.version + 1,
                           status = case
                               when media_request_demands.status in ('COMPLETED', 'FAILED', 'CANCELLED') then 'PENDING'
                               else media_request_demands.status
                           end,
                           request_count = media_request_demands.request_count + 1,
                           last_requested_at = greatest(media_request_demands.last_requested_at, excluded.last_requested_at),
                           scheduled_at = case when media_request_demands.status in ('COMPLETED', 'FAILED', 'CANCELLED')
                               then null else media_request_demands.scheduled_at end,
                           completed_at = case when media_request_demands.status in ('COMPLETED', 'FAILED', 'CANCELLED')
                               then null else media_request_demands.completed_at end,
                           current_batch_id = case when media_request_demands.status in ('COMPLETED', 'FAILED', 'CANCELLED')
                               then null else media_request_demands.current_batch_id end,
                           existing_video_id = case when media_request_demands.status = 'SKIPPED_EXISTS'
                               then media_request_demands.existing_video_id end,
                           existing_video_source = case when media_request_demands.status = 'SKIPPED_EXISTS'
                               then media_request_demands.existing_video_source end,
                           last_error_message = case when media_request_demands.status = 'SKIPPED_EXISTS'
                               then media_request_demands.last_error_message end
                    returning *
                ), submission as (
                    insert into media_request_submissions (
                        id, demand_id, member_id, submitted_title, extra_info, spent_points, client_request_id, created_at
                    ) select ?, id, ?, ?, ?, ?, ?, ? from demand
                    returning *
                )
                """ + SELECT_SUBMISSION + " from submission s join demand d on d.id = s.demand_id",
                mediaRequestMapper(),
                proposedDemandId, submittedAt, submittedAt, title, normalizedTitle, releaseYear, submittedAt,
                submissionId, memberId, title, extraInfo, spentPoints, clientRequestId, submittedAt);
    }

    public PageResponse<MediaRequestResponse> findMemberRequests(UUID memberId, PageBounds bounds) {
        long total = count("select count(*) from media_request_submissions where member_id = ?", memberId);
        List<MediaRequestResponse> content = jdbcTemplate.query(
                SELECT_SUBMISSION + """
                from media_request_submissions s
                join media_request_demands d on d.id = s.demand_id
                where s.member_id = ?
                order by s.created_at desc, s.id desc
                limit ? offset ?
                """,
                mediaRequestMapper(), memberId, bounds.size(), bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    public long countMemberRequestsCreatedBetween(UUID memberId, Instant startInclusive, Instant endExclusive) {
        return count("""
                select count(*) from media_request_submissions where member_id = ? and created_at >= ? and created_at < ?
                """, memberId, Timestamp.from(startInclusive), Timestamp.from(endExclusive));
    }

    public Optional<MediaRequestResponse> findMemberSubmission(UUID memberId, UUID clientRequestId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT_SUBMISSION + """
                    from media_request_submissions s
                    join media_request_demands d on d.id = s.demand_id
                    where s.member_id = ? and s.client_request_id = ?
                    """,
                    mediaRequestMapper(), memberId, clientRequestId));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private RowMapper<MediaRequestResponse> mediaRequestMapper() {
        return (rs, rowNum) -> new MediaRequestResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("demand_id", UUID.class),
                rs.getObject("member_id", UUID.class),
                rs.getString("submitted_title"),
                rs.getObject("release_year", Integer.class),
                rs.getString("extra_info"),
                rs.getString("status"),
                rs.getInt("request_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("last_requested_at")),
                instant(rs.getTimestamp("scheduled_at")),
                instant(rs.getTimestamp("completed_at")),
                rs.getString("last_error_message"),
                rs.getObject("existing_video_id", UUID.class),
                rs.getString("existing_video_source"),
                rs.getInt("spent_points"));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
