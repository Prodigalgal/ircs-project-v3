package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RawVideoEnrichmentRepository {

    static final String SELECT_BY_ID = """
            select v.id, v.title, v.alias_title, v.subtitle, v.season, v.year,
                   c.slug as category_slug,
                   v.douban_id, v.tmdb_id, v.imdb_id, v.rotten_tomatoes_id,
                   v.enrichment_status, v.enrichment_retry_count, v.data_hash
            from raw_videos v
            left join raw_category rc on v.data_source_category_id = rc.id
            left join standard_category c on rc.category_id = c.id
            where v.id = ?
            """;

    private static final String UPDATE_STATUS = """
            update raw_videos
            set enrichment_status = ?,
                enrichment_retry_count = ?,
                updated_at = now()
            where id = ?
            """;

    public static final int MAX_ENRICHMENT_RETRIES = 5;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public Optional<RawVideoEnrichmentRecord> findById(UUID id) {
        return jdbcTemplate.query(SELECT_BY_ID, ps -> ps.setObject(1, id), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        });
    }

    public int updateStatus(UUID id, String status, Integer retryCount) {
        return jdbcTemplate.update(UPDATE_STATUS, status, retryCount, id);
    }

    public List<PipelineCommand> claimDueEnrichmentRecoveryCommands(Instant staleBefore, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return namedJdbcTemplate.query(
                """
                with due as (
                    select id
                    from raw_videos
                    where normalization_status = 'READY'
                      and enrichment_status in ('PENDING', 'FAILED')
                      and updated_at <= :staleBefore
                    order by updated_at asc, id asc
                    limit :limit
                    for update skip locked
                )
                update raw_videos rv
                set enrichment_status = 'PENDING',
                    updated_at = now()
                from due
                where rv.id = due.id
                returning rv.id, rv.data_hash
                """,
                new MapSqlParameterSource()
                        .addValue("staleBefore", Timestamp.from(staleBefore))
                        .addValue("limit", limit),
                (rs, rowNum) -> PipelineCommand.enrich(
                        rs.getObject("id", UUID.class),
                        rs.getString("data_hash")));
    }

    public int releaseRecoveryPublishFailures(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return namedJdbcTemplate.update(
                """
                update raw_videos
                set enrichment_status = 'FAILED',
                    updated_at = now()
                where id in (:ids)
                  and enrichment_status = 'PENDING'
                """,
                Map.of("ids", ids));
    }

    private RawVideoEnrichmentRecord mapRow(ResultSet rs) throws SQLException {
        return new RawVideoEnrichmentRecord(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("alias_title"),
                rs.getString("subtitle"),
                (Integer) rs.getObject("season"),
                rs.getString("year"),
                rs.getString("category_slug"),
                rs.getString("douban_id"),
                rs.getString("tmdb_id"),
                rs.getString("imdb_id"),
                rs.getString("rotten_tomatoes_id"),
                rs.getString("enrichment_status"),
                (Integer) rs.getObject("enrichment_retry_count"),
                rs.getString("data_hash"));
    }
}
