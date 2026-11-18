package com.prodigalgal.ircs.task.infrastructure;

import com.prodigalgal.ircs.task.domain.CatalogDiscoveryCandidate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CatalogDiscoveryRepository {

    private static final String OWNED_REQUEST = """
             unified_video_id = :id AND target_season = :season AND target_episode = :episode
             AND status = 'DISPATCHING' AND lease_token = :token AND lease_until > now()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public int reconcileSubmitted(int limit) {
        return jdbc.update("""
                WITH due AS MATERIALIZED (
                    SELECT r.unified_video_id, r.target_season, r.target_episode, r.attempts
                      FROM catalog_discovery_requests r
                     WHERE r.status = 'SUBMITTED' AND r.fulfilled_at IS NULL
                       AND r.next_attempt_at <= now()
                       AND coalesce(r.submitted_at, r.updated_at) <= now() - interval '5 minutes'
                     ORDER BY r.next_attempt_at, r.submitted_at, r.unified_video_id, r.target_season, r.target_episode
                     LIMIT :limit FOR UPDATE OF r SKIP LOCKED
                ), outcomes AS MATERIALIZED (
                    SELECT d.*, child.*,
                           coalesce(inventory.resource_available
                               AND (d.target_episode = 0 OR inventory.available_episode >= d.target_episode), false)
                               AS fulfilled
                      FROM due d
                      CROSS JOIN LATERAL catalog_resource_inventory(d.unified_video_id, d.target_season) inventory
                      CROSS JOIN LATERAL (
                          SELECT coalesce(bool_or(t.enabled AND t.status IN ('QUEUED', 'RUNNING')), false) AS active,
                                 coalesce(bool_or(NOT t.enabled OR t.status IN ('PAUSED', 'STOPPING')), false) AS paused,
                                 coalesce(bool_or(t.status = 'FAILED'), false) AS failed,
                                 coalesce(bool_or(t.status = 'COMPLETED'), false) AS completed,
                                 coalesce(max(coalesce(t.stat_end_time, t.updated_at))
                                     FILTER (WHERE t.status = 'COMPLETED')
                                         > timezone('UTC', now()) - interval '5 minutes', false) AS settling
                            FROM collection_tasks t
                           WHERE t.headers -> 'ircsCatalogDiscovery' ->> 'requestKey' =
                                 d.unified_video_id::text || ':' || d.target_season || ':' || d.target_episode
                      ) child
                )
                UPDATE catalog_discovery_requests r
                   SET status = CASE WHEN o.fulfilled OR o.paused OR o.active OR o.settling
                                          THEN 'SUBMITTED' ELSE 'RETRY' END,
                       fulfilled_at = CASE WHEN o.fulfilled THEN now() END,
                       next_attempt_at = CASE
                           WHEN o.fulfilled THEN now()
                           WHEN o.paused THEN now() + interval '6 hours'
                           WHEN o.active OR o.settling THEN now() + interval '2 minutes'
                           WHEN o.completed THEN now() + make_interval(mins => least(360, 15 * greatest(1, o.attempts)))
                           ELSE now() + interval '5 minutes'
                       END,
                       last_error = CASE
                           WHEN o.fulfilled THEN NULL
                           WHEN o.paused THEN 'OPERATOR_PAUSED'
                           WHEN o.active OR o.settling THEN NULL
                           WHEN o.completed THEN 'COLLECTION_NO_RESOURCE'
                           WHEN o.failed THEN 'DISPATCH_FAILED_AFTER_ACCEPT'
                           ELSE 'NO_COLLECTION_TASK_AFTER_ACCEPT'
                       END,
                       updated_at = now(), lease_token = NULL, lease_until = NULL
                  FROM outcomes o
                 WHERE r.unified_video_id = o.unified_video_id
                   AND r.target_season = o.target_season AND r.target_episode = o.target_episode
                """, Map.of("limit", Math.max(1, Math.min(50, limit))));
    }

    public List<CatalogDiscoveryCandidate> claimDue(int limit, Duration lease) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                WITH due AS (
                    SELECT r.unified_video_id, r.target_season, r.target_episode
                      FROM catalog_discovery_requests r
                      JOIN unified_videos uv ON uv.id = r.unified_video_id
                     WHERE ((r.status IN ('PENDING', 'RETRY') AND r.next_attempt_at <= now())
                         OR (r.status = 'RETRY' AND r.last_error = 'NO_ELIGIBLE_SOURCE'
                             AND r.updated_at <= now() - interval '5 minutes')
                         OR (r.status = 'DISPATCHING' AND r.lease_until <= now()))
                       AND coalesce(uv.adult_restricted, false) = false
                       AND coalesce((uv.adult_assessment ->> 'adultRestricted')::boolean, false) = false
                       AND upper(coalesce(uv.adult_assessment ->> 'level', 'SAFE')) <> 'ADULT'
                       AND uv.content_visibility = 'PUBLIC'
                     ORDER BY r.priority DESC, r.next_attempt_at, r.unified_video_id,
                              r.target_season, r.target_episode
                     LIMIT :limit
                     FOR UPDATE OF r SKIP LOCKED
                )
                UPDATE catalog_discovery_requests r
                   SET status = 'DISPATCHING', attempts = least(r.attempts + 1, 1000000),
                       lease_token = :token, lease_until = now() + (:leaseSeconds * interval '1 second'),
                       fulfilled_at = NULL, updated_at = now()
                  FROM due
                 WHERE r.unified_video_id = due.unified_video_id
                   AND r.target_season = due.target_season AND r.target_episode = due.target_episode
                RETURNING r.unified_video_id, r.target_season, r.target_episode, r.keyword, r.attempts, r.lease_token
                """, new MapSqlParameterSource()
                .addValue("limit", Math.max(1, Math.min(5, limit)))
                .addValue("leaseSeconds", lease.toSeconds())
                .addValue("token", token),
                (rs, row) -> new CatalogDiscoveryCandidate(rs.getObject("unified_video_id", UUID.class),
                        rs.getInt("target_season"), rs.getInt("target_episode"), rs.getString("keyword"),
                        rs.getInt("attempts"), rs.getObject("lease_token", UUID.class)));
    }

    public boolean renew(CatalogDiscoveryCandidate candidate, Duration lease) {
        return jdbc.update("""
                UPDATE catalog_discovery_requests SET lease_until = now() + (:leaseSeconds * interval '1 second'),
                       updated_at = now()
                 WHERE
                """ + OWNED_REQUEST, params(candidate).addValue("leaseSeconds", lease.toSeconds())) == 1;
    }

    public boolean markSubmitted(CatalogDiscoveryCandidate candidate) {
        return acknowledge(candidate, false);
    }

    public boolean markFulfilled(CatalogDiscoveryCandidate candidate) {
        return acknowledge(candidate, true);
    }

    private boolean acknowledge(CatalogDiscoveryCandidate candidate, boolean fulfilled) {
        return jdbc.update("""
                UPDATE catalog_discovery_requests
                   SET status = 'SUBMITTED', submitted_at = now(), updated_at = now(),
                       fulfilled_at = CASE WHEN :fulfilled THEN now() END,
                       next_attempt_at = now() + interval '5 minutes',
                       lease_token = NULL, lease_until = NULL, last_error = NULL
                 WHERE
                """ + OWNED_REQUEST, params(candidate).addValue("fulfilled", fulfilled)) == 1;
    }

    public boolean retry(CatalogDiscoveryCandidate candidate, Duration delay, String errorCode) {
        return jdbc.update("""
                UPDATE catalog_discovery_requests
                   SET status = 'RETRY', next_attempt_at = now() + (:delaySeconds * interval '1 second'),
                       updated_at = now(), lease_token = NULL, lease_until = NULL, last_error = :errorCode
                 WHERE
                """ + OWNED_REQUEST, params(candidate)
                .addValue("delaySeconds", Math.max(60, delay.toSeconds()))
                .addValue("errorCode", errorCode)) == 1;
    }

    public int activeTaskCount(int limit) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT id FROM collection_tasks WHERE status IN ('QUEUED', 'RUNNING', 'STOPPING') LIMIT :limit
                ) active
                """, Map.of("limit", Math.max(1, limit)), Integer.class);
        return count == null ? 0 : count;
    }

    public boolean hasActiveTasks(CatalogDiscoveryCandidate candidate) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM collection_tasks
                     WHERE enabled = true AND status IN ('QUEUED', 'RUNNING')
                       AND headers -> 'ircsCatalogDiscovery' ->> 'requestKey' = :requestKey
                )
                """, Map.of("requestKey", candidate.requestKey()), Boolean.class));
    }

    public boolean isPausedByOperator(CatalogDiscoveryCandidate candidate) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM collection_tasks
                     WHERE (enabled = false OR status IN ('PAUSED', 'STOPPING'))
                       AND headers -> 'ircsCatalogDiscovery' ->> 'requestKey' = :requestKey
                )
                """, Map.of("requestKey", candidate.requestKey()), Boolean.class));
    }

    public boolean hasRequestedResources(CatalogDiscoveryCandidate candidate) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT resource_available AND (:episode = 0 OR available_episode >= :episode)
                  FROM catalog_resource_inventory(:id, :season)
                """, params(candidate), Boolean.class));
    }

    private static MapSqlParameterSource params(CatalogDiscoveryCandidate candidate) {
        return new MapSqlParameterSource("id", candidate.unifiedVideoId())
                .addValue("season", candidate.targetSeason()).addValue("episode", candidate.targetEpisode())
                .addValue("token", candidate.leaseToken());
    }
}
