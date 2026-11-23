package com.prodigalgal.ircs.metadata.inventory;

import com.prodigalgal.ircs.metadata.inventory.TmdbInventorySnapshotReader.Entry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(timeout = 15)
class TmdbInventoryRepository {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(15);
    private static final int CLEANUP_BATCH_SIZE = 10_000;
    private static final String OWNED = """
            media_type = :mediaType AND lease_token = :token AND lease_until > now()
               AND attempt_date = :snapshotDate AND active_slot <> :slot
            """;
    private static final String RENEW = """
            WITH lease AS (
                UPDATE authority_tmdb_inventory_state
                   SET lease_until = now() + interval '5 minutes'
                 WHERE %s
                RETURNING media_type
            )
            """.formatted(OWNED);

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<Claim> claim(TmdbInventoryMediaType mediaType, LocalDate date) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                UPDATE authority_tmdb_inventory_state
                   SET lease_token = :token, lease_until = now() + interval '5 minutes',
                       attempt_date = :snapshotDate, last_attempt_at = now()
                 WHERE media_type = :mediaType
                   AND (snapshot_date IS NULL OR snapshot_date < :snapshotDate)
                   AND (lease_until IS NULL OR lease_until <= now())
                   AND (attempt_date IS DISTINCT FROM :snapshotDate OR retry_at <= now())
                RETURNING 1 - active_slot
                """, Map.of("mediaType", mediaType.catalogType, "token", token, "snapshotDate", date),
                (rs, row) -> new Claim(mediaType, date, rs.getInt(1), token)).stream().findFirst();
    }

    public int clearInactiveBatch(Claim claim) {
        int removed = jdbc.queryForObject(RENEW + """
                , obsolete AS MATERIALIZED (
                    SELECT e.ctid
                      FROM authority_tmdb_inventory_entries e JOIN lease l USING (media_type)
                     WHERE e.slot = :slot
                     ORDER BY e.tmdb_id
                     LIMIT :batchSize
                ), removed AS (
                    DELETE FROM authority_tmdb_inventory_entries
                     WHERE ctid = ANY (ARRAY(SELECT ctid FROM obsolete))
                    RETURNING 1
                )
                SELECT CASE WHEN EXISTS (SELECT 1 FROM lease)
                       THEN (SELECT count(*)::int FROM removed) ELSE -1 END
                """, owned(claim).addValue("batchSize", CLEANUP_BATCH_SIZE), Integer.class);
        if (removed < 0) {
            throw new LeaseLost();
        }
        return removed;
    }

    public void append(Claim claim, List<Entry> entries) {
        var params = owned(claim)
                .addValue("ids", new SqlArrayValue("int8", entries.stream().map(Entry::tmdbId).toArray()))
                .addValue("titles", new SqlArrayValue("text", entries.stream().map(Entry::title).toArray()))
                .addValue("popularity", new SqlArrayValue("float8", entries.stream().map(Entry::popularity).toArray()))
                .addValue("video", new SqlArrayValue("bool", entries.stream().map(Entry::video).toArray()));
        int written = jdbc.update(RENEW + """
                INSERT INTO authority_tmdb_inventory_entries (media_type, slot, tmdb_id, title, popularity, video)
                SELECT lease.media_type, :slot, incoming.tmdb_id, incoming.title, incoming.popularity, incoming.video
                  FROM lease
                  CROSS JOIN unnest(cast(:ids as bigint[]), cast(:titles as text[]),
                                   cast(:popularity as double precision[]), cast(:video as boolean[]))
                             AS incoming(tmdb_id, title, popularity, video)
                """, params);
        if (written != entries.size()) {
            throw new LeaseLost();
        }
    }

    public boolean publish(Claim claim, int rowCount) {
        return jdbc.update("""
                UPDATE authority_tmdb_inventory_state
                   SET active_slot = :slot, snapshot_date = :snapshotDate, row_count = :rowCount,
                       last_success_at = now(), lease_token = NULL, lease_until = NULL,
                       last_error_code = NULL, retry_at = '-infinity'
                 WHERE
                """ + OWNED + """
                   AND cast(:rowCount as integer) >= greatest(cast(:minimum as integer), row_count * 0.8)
                """, owned(claim).addValue("rowCount", rowCount)
                .addValue("minimum", claim.mediaType().minimumRowCount)) == 1;
    }

    public void fail(Claim claim, String reasonCode) {
        jdbc.update("""
                UPDATE authority_tmdb_inventory_state
                   SET lease_token = NULL, lease_until = NULL, last_error_code = :reasonCode,
                       retry_at = now() + cast(:retrySeconds as integer) * interval '1 second'
                 WHERE
                """ + OWNED, owned(claim).addValue("reasonCode", reasonCode).addValue("retrySeconds", RETRY_DELAY.toSeconds()));
    }

    private static MapSqlParameterSource owned(Claim claim) {
        return new MapSqlParameterSource("mediaType", claim.mediaType().catalogType)
                .addValue("snapshotDate", claim.date()).addValue("slot", claim.slot()).addValue("token", claim.token());
    }

    record Claim(TmdbInventoryMediaType mediaType, LocalDate date, int slot, UUID token) { }

    static final class LeaseLost extends RuntimeException { }
}
