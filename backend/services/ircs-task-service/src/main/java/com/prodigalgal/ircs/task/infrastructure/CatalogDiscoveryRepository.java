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

    public int recoverFailedSubmissions(int limit) {
        return jdbc.update("""
                WITH failed AS (
                    SELECT r.unified_video_id, r.target_season, r.target_episode
                      FROM catalog_discovery_requests r
                     WHERE r.status = 'SUBMITTED' AND r.submitted_at <= now() - interval '5 minutes'
                       AND EXISTS (
                           SELECT 1 FROM collection_tasks task
                            WHERE task.headers -> 'ircsCatalogDiscovery' ->> 'requestKey' =
                                  r.unified_video_id::text || ':' || r.target_season || ':' || r.target_episode
                              AND task.status = 'FAILED' AND task.enabled = true
                       )
                       AND NOT EXISTS (
                           SELECT 1 FROM collection_tasks task
                            WHERE task.headers -> 'ircsCatalogDiscovery' ->> 'requestKey' =
                                  r.unified_video_id::text || ':' || r.target_season || ':' || r.target_episode
                              AND task.status <> 'FAILED'
                       )
                     ORDER BY r.submitted_at, r.unified_video_id
                     LIMIT :limit FOR UPDATE OF r SKIP LOCKED
                )
                UPDATE catalog_discovery_requests r
                   SET status = 'RETRY', next_attempt_at = now() + interval '5 minutes', updated_at = now(),
                       last_error = 'DISPATCH_FAILED_AFTER_ACCEPT', lease_token = NULL, lease_until = NULL
                  FROM failed
                 WHERE r.unified_video_id = failed.unified_video_id
                   AND r.target_season = failed.target_season AND r.target_episode = failed.target_episode
                """, Map.of("limit", Math.max(1, Math.min(5, limit))));
    }

    public List<CatalogDiscoveryCandidate> claimDue(int limit, Duration lease) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                WITH due AS (
                    SELECT r.unified_video_id, r.target_season, r.target_episode
                      FROM catalog_discovery_requests r
                      JOIN unified_videos uv ON uv.id = r.unified_video_id
                     WHERE ((r.status IN ('PENDING', 'RETRY') AND r.next_attempt_at <= now())
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
                       updated_at = now()
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
        return jdbc.update("""
                UPDATE catalog_discovery_requests
                   SET status = 'SUBMITTED', submitted_at = now(), updated_at = now(),
                       lease_token = NULL, lease_until = NULL, last_error = NULL
                 WHERE
                """ + OWNED_REQUEST, params(candidate)) == 1;
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
