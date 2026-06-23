package com.prodigalgal.ircs.ops.dashboard.infrastructure;

import com.prodigalgal.ircs.ops.dashboard.dto.ChartDataPoint;
import com.prodigalgal.ircs.ops.dashboard.dto.DashboardStatsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.EfficiencyStatsResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.SourceQualityResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcDashboardRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardStatsResponse loadStats() {
        return new DashboardStatsResponse(
                count("raw_videos"),
                0,
                count("unified_videos"),
                0,
                count("collection_tasks"),
                countRawByStatus("normalization_status", "PENDING"),
                countRawByStatus("enrichment_status", "PENDING"),
                countRawByStatus("normalization_status", "FAILED"),
                countMissingRawId("douban_id"),
                countMissingRawId("tmdb_id"),
                countCoverByStatus("FAILED"),
                countCoverByStatus("DEAD_LINK")
        );
    }

    public List<ChartDataPoint> weeklyTrend(int days) {
        return jdbcTemplate.query(
                """
                SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS label,
                       count(*) AS value
                  FROM raw_videos
                 WHERE created_at >= :since
                 GROUP BY date_trunc('day', created_at)
                 ORDER BY label ASC
                """,
                Map.of("since", Timestamp.from(Instant.now().minusSeconds(Math.max(1, days) * 86_400L))),
                chartMapper());
    }

    public List<ChartDataPoint> categoryDistribution() {
        return jdbcTemplate.query(
                """
                SELECT coalesce(sc.name, '未分类') AS label,
                       count(uv.id) AS value
                  FROM unified_videos uv
                  LEFT JOIN standard_category sc ON sc.slug = uv.category_code
                 GROUP BY coalesce(sc.name, '未分类')
                 ORDER BY value DESC, label ASC
                 LIMIT 20
                """,
                Map.of(),
                chartMapper());
    }

    public List<ChartDataPoint> sourceDistribution() {
        return jdbcTemplate.query(
                """
                SELECT coalesce(ds.name, '未知数据源') AS label,
                       count(rv.id) AS value
                  FROM raw_videos rv
                  LEFT JOIN data_sources ds ON rv.data_source_id = ds.id
                 GROUP BY coalesce(ds.name, '未知数据源')
                 ORDER BY value DESC, label ASC
                 LIMIT 20
                """,
                Map.of(),
                chartMapper());
    }

    public List<ChartDataPoint> enrichmentStatusDistribution() {
        return jdbcTemplate.query(
                """
                SELECT coalesce(enrichment_status, 'UNKNOWN') AS label,
                       count(*) AS value
                  FROM raw_videos
                 GROUP BY coalesce(enrichment_status, 'UNKNOWN')
                 ORDER BY value DESC, label ASC
                """,
                Map.of(),
                chartMapper());
    }

    public Map<String, Long> idCoverage() {
        return jdbcTemplate.queryForMap(
                """
                SELECT count(*) FILTER (
                           WHERE nullif(trim(coalesce(douban_id, '')), '') IS NOT NULL
                             AND nullif(trim(coalesce(tmdb_id, '')), '') IS NOT NULL
                       ) AS "Both",
                       count(*) FILTER (
                           WHERE nullif(trim(coalesce(douban_id, '')), '') IS NOT NULL
                             AND nullif(trim(coalesce(tmdb_id, '')), '') IS NULL
                       ) AS "DoubanOnly",
                       count(*) FILTER (
                           WHERE nullif(trim(coalesce(douban_id, '')), '') IS NULL
                             AND nullif(trim(coalesce(tmdb_id, '')), '') IS NOT NULL
                       ) AS "TmdbOnly",
                       count(*) FILTER (
                           WHERE nullif(trim(coalesce(douban_id, '')), '') IS NULL
                             AND nullif(trim(coalesce(tmdb_id, '')), '') IS NULL
                       ) AS "None"
                  FROM raw_videos
                """,
                Map.of()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ((Number) entry.getValue()).longValue(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    public List<EfficiencyStatsResponse> sourceEfficiency() {
        return jdbcTemplate.query(
                """
                SELECT coalesce(ds.name, '未知数据源') AS name,
                       count(rv.id) AS total,
                       count(rv.id) FILTER (
                           WHERE rv.enrichment_status = 'SUCCESS'
                              OR nullif(trim(coalesce(rv.douban_id, '')), '') IS NOT NULL
                              OR nullif(trim(coalesce(rv.tmdb_id, '')), '') IS NOT NULL
                       ) AS success_count
                  FROM raw_videos rv
                  LEFT JOIN data_sources ds ON rv.data_source_id = ds.id
                 GROUP BY coalesce(ds.name, '未知数据源')
                 ORDER BY total DESC, name ASC
                 LIMIT 10
                """,
                Map.of(),
                efficiencyMapper());
    }

    public List<EfficiencyStatsResponse> categoryEfficiency() {
        return jdbcTemplate.query(
                """
                SELECT coalesce(sc.name, nullif(trim(rc.source_name), ''), rv.source_category_code, rc.source_code, '未分类') AS name,
                       count(rv.id) AS total,
                       count(rv.id) FILTER (
                           WHERE rv.enrichment_status = 'SUCCESS'
                              OR nullif(trim(coalesce(rv.douban_id, '')), '') IS NOT NULL
                              OR nullif(trim(coalesce(rv.tmdb_id, '')), '') IS NOT NULL
                       ) AS success_count
                  FROM raw_videos rv
                  LEFT JOIN standard_category sc ON sc.slug = rv.category_code
                  LEFT JOIN raw_category rc ON rv.data_source_category_id = rc.id
                 GROUP BY coalesce(sc.name, nullif(trim(rc.source_name), ''), rv.source_category_code, rc.source_code, '未分类')
                 ORDER BY total DESC, name ASC
                 LIMIT 10
                """,
                Map.of(),
                efficiencyMapper());
    }

    public List<SourceQualityResponse> sourceQuality() {
        return jdbcTemplate.query(
                """
                SELECT ds.id AS data_source_id,
                       ds.name AS data_source_name,
                       count(rv.id) AS total_count,
                       count(rv.id) FILTER (
                           WHERE nullif(trim(coalesce(rv.douban_id, '')), '') IS NOT NULL
                             AND nullif(trim(coalesce(rv.tmdb_id, '')), '') IS NOT NULL
                             AND rv.normalization_status <> 'FAILED'
                             AND coalesce(ci.status, 'READY') <> 'FAILED'
                       ) AS perfect_count,
                       count(rv.id) FILTER (
                           WHERE nullif(trim(coalesce(rv.douban_id, '')), '') IS NULL
                       ) AS missing_douban_count,
                       count(rv.id) FILTER (
                           WHERE nullif(trim(coalesce(rv.tmdb_id, '')), '') IS NULL
                       ) AS missing_tmdb_count,
                       count(rv.id) FILTER (
                           WHERE rv.normalization_status = 'FAILED'
                       ) AS normalize_failed_count,
                       count(rv.id) FILTER (
                           WHERE ci.status IN ('FAILED', 'DEAD_LINK')
                       ) AS image_error_count
                  FROM data_sources ds
                  LEFT JOIN raw_videos rv ON rv.data_source_id = ds.id
                  LEFT JOIN cover_images ci ON rv.cover_image_id = ci.id
                 GROUP BY ds.id, ds.name
                 ORDER BY total_count DESC, ds.name ASC
                """,
                Map.of(),
                sourceQualityMapper());
    }

    private long count(String table) {
        Long value = jdbcTemplate.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private long countRawByStatus(String column, String status) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM raw_videos WHERE " + column + " = :status",
                Map.of("status", status),
                Long.class);
        return value == null ? 0 : value;
    }

    private long countMissingRawId(String column) {
        Long value = jdbcTemplate.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM raw_videos WHERE nullif(trim(coalesce(" + column + ", '')), '') IS NULL",
                        Long.class);
        return value == null ? 0 : value;
    }

    private long countCoverByStatus(String status) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cover_images WHERE status = :status",
                Map.of("status", status),
                Long.class);
        return value == null ? 0 : value;
    }

    private RowMapper<ChartDataPoint> chartMapper() {
        return (rs, rowNum) -> new ChartDataPoint(rs.getString("label"), rs.getLong("value"));
    }

    private RowMapper<EfficiencyStatsResponse> efficiencyMapper() {
        return (rs, rowNum) -> {
            long total = rs.getLong("total");
            long success = rs.getLong("success_count");
            double rate = total == 0 ? 0.0 : (success * 100.0) / total;
            return new EfficiencyStatsResponse(rs.getString("name"), total, success, rate);
        };
    }

    private RowMapper<SourceQualityResponse> sourceQualityMapper() {
        return (rs, rowNum) -> SourceQualityResponse.of(
                getUuid(rs, "data_source_id"),
                rs.getString("data_source_name"),
                rs.getLong("total_count"),
                rs.getLong("perfect_count"),
                rs.getLong("missing_douban_count"),
                rs.getLong("missing_tmdb_count"),
                rs.getLong("normalize_failed_count"),
                rs.getLong("image_error_count"));
    }

    private UUID getUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
