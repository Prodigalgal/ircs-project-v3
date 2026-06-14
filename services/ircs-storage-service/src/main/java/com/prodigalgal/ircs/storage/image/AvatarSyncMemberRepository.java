package com.prodigalgal.ircs.storage.image;

import java.sql.Types;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class AvatarSyncMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<String> findAvatarUrl(UUID memberId) {
        return jdbcTemplate.query(
                "select avatar_url from members where id = ?",
                ps -> ps.setObject(1, memberId),
                rs -> rs.next() ? Optional.ofNullable(rs.getString("avatar_url")) : Optional.empty());
    }

    public int updateAvatarUrl(UUID memberId, String expectedCurrentUrl, String nextUrl) {
        return jdbcTemplate.update(
                """
                update members
                   set avatar_url = ?,
                       updated_at = now(),
                       version = coalesce(version, 0) + 1
                 where id = ?
                   and avatar_url = ?
                """,
                new Object[] {nextUrl, memberId, expectedCurrentUrl},
                new int[] {Types.VARCHAR, Types.OTHER, Types.VARCHAR});
    }

    public List<UUID> findLocalAvatarMemberIds(String localPublicPrefix, int limit) {
        if (!StringUtils.hasText(localPublicPrefix) || limit <= 0) {
            return List.of();
        }
        String prefix = localPublicPrefix.endsWith("/")
                ? localPublicPrefix.substring(0, localPublicPrefix.length() - 1)
                : localPublicPrefix;
        return jdbcTemplate.query(
                """
                select id
                  from members
                 where avatar_url like ?
                 order by updated_at asc, id asc
                 limit ?
                """,
                ps -> {
                    ps.setString(1, prefix + "/%");
                    ps.setInt(2, limit);
                },
                new SingleColumnRowMapper<>(UUID.class));
    }

    public List<UUID> claimLocalAvatarMemberIds(
            String localPublicPrefix,
            int limit,
            String workerId,
            Duration leaseDuration) {
        if (!StringUtils.hasText(localPublicPrefix) || limit <= 0 || !StringUtils.hasText(workerId)) {
            return List.of();
        }
        String prefix = localPublicPrefix.endsWith("/")
                ? localPublicPrefix.substring(0, localPublicPrefix.length() - 1)
                : localPublicPrefix;
        Instant claimedUntil = Instant.now().plus(leaseDuration == null || !leaseDuration.isPositive()
                ? Duration.ofMinutes(10)
                : leaseDuration);
        return jdbcTemplate.query(
                """
                with candidates as (
                    select id
                      from members
                     where avatar_url like ?
                       and (avatar_sync_claimed_until is null or avatar_sync_claimed_until <= now())
                     order by updated_at asc, id asc
                     limit ?
                     for update skip locked
                )
                update members m
                   set avatar_sync_claimed_by = ?,
                       avatar_sync_claimed_until = ?
                  from candidates
                 where m.id = candidates.id
                returning m.id
                """,
                ps -> {
                    ps.setString(1, prefix + "/%");
                    ps.setInt(2, limit);
                    ps.setString(3, workerId);
                    ps.setTimestamp(4, Timestamp.from(claimedUntil));
                },
                new SingleColumnRowMapper<>(UUID.class));
    }

    public void releaseAvatarSyncClaim(UUID memberId, String workerId) {
        if (memberId == null || !StringUtils.hasText(workerId)) {
            return;
        }
        jdbcTemplate.update(
                """
                update members
                   set avatar_sync_claimed_by = null,
                       avatar_sync_claimed_until = null
                 where id = ?
                   and avatar_sync_claimed_by = ?
                """,
                new Object[] {memberId, workerId},
                new int[] {Types.OTHER, Types.VARCHAR});
    }
}
