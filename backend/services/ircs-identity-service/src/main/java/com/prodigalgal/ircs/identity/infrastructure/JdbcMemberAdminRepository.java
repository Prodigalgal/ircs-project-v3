package com.prodigalgal.ircs.identity.infrastructure;








import com.prodigalgal.ircs.identity.dto.PageBounds;
import com.prodigalgal.ircs.identity.repository.MemberAdminRepository;
import com.prodigalgal.ircs.identity.application.CoverImageUrlResolver;
import com.prodigalgal.ircs.identity.dto.PageResponse;
import com.prodigalgal.ircs.identity.domain.MemberRecord;
import com.prodigalgal.ircs.identity.domain.MemberStatus;
import com.prodigalgal.ircs.identity.dto.HistoryRecordResponse;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class JdbcMemberAdminRepository implements MemberAdminRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CoverImageUrlResolver coverImageUrlResolver;

    @Override
    public PageResponse<MemberRecord> findMembers(
            String keyword,
            MemberStatus status,
            Boolean adultContentAllowed,
            Integer minPoints,
            Integer maxPoints,
            PageBounds bounds,
            String sort) {
        QueryParts query = memberQuery(keyword, status, adultContentAllowed, minPoints, maxPoints);
        String memberBalances = " from member_accounts account join member_point_balances points on points.member_id = account.id";
        long total = count("select count(*)" + memberBalances + query.where(), query.args());

        List<Object> args = new ArrayList<>(query.args());
        args.add(bounds.size());
        args.add(bounds.offset());
        List<MemberRecord> content = jdbcTemplate.query(
                """
                select account.id, account.created_at, account.updated_at, account.version, account.email,
                       account.password_hash, account.nickname, account.avatar_url, account.role, account.status,
                       coalesce(account.adult_content_allowed, false) as adult_content_allowed,
                       account.experience, points.balance as points, account.last_check_in_date, account.check_in_streak
                """ + memberBalances
                        + query.where()
                        + orderBy(sort)
                        + " limit ? offset ?",
                memberMapper(),
                args.toArray());
        return PageResponse.of(content, total, bounds);
    }

    @Override
    public boolean existsByEmailExcludingId(String email, UUID excludedId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from member_accounts where lower(email) = lower(?) and id <> ?)",
                Boolean.class,
                email,
                excludedId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public PageResponse<HistoryRecordResponse> findFavorites(UUID memberId, PageBounds bounds) {
        long total = count("select count(*) from member_catalog_favorites where member_id = ?", List.of(memberId));
        List<HistoryRecordResponse> content = jdbcTemplate.query(
                """
                select mf.id,
                       mf.catalog_entry_id,
                       uv.title,
                       uv.score,
                       ci.storage_type as cover_storage_type,
                       ci.original_url as cover_original_url,
                       ci.storage_path as cover_storage_path,
                       sd.domain_value as cover_source_domain,
                       cast(null as uuid) as last_video_id,
                       cast(null as uuid) as last_episode_id,
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
                historyMapper(),
                memberId,
                bounds.size(),
                bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    @Override
    public PageResponse<HistoryRecordResponse> findHistory(UUID memberId, PageBounds bounds) {
        long total = count("select count(*) from member_playback_progress where member_id = ?", List.of(memberId));
        List<HistoryRecordResponse> content = jdbcTemplate.query(
                """
                select mwh.id,
                       mwh.catalog_entry_id,
                       uv.title,
                       uv.score,
                       ci.storage_type as cover_storage_type,
                       ci.original_url as cover_original_url,
                       ci.storage_path as cover_storage_path,
                       sd.domain_value as cover_source_domain,
                       mwh.last_video_id,
                       mwh.last_episode_id,
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
                historyMapper(),
                memberId,
                bounds.size(),
                bounds.offset());
        return PageResponse.of(content, total, bounds);
    }

    @Override
    public void deleteMemberRelations(UUID memberId) {
        jdbcTemplate.update("delete from member_catalog_favorites where member_id = ?", memberId);
        jdbcTemplate.update("delete from member_playback_progress where member_id = ?", memberId);
    }

    @Override
    public int deleteMember(UUID memberId) {
        return jdbcTemplate.update("delete from member_accounts where id = ?", memberId);
    }

    private QueryParts memberQuery(
            String keyword,
            MemberStatus status,
            Boolean adultContentAllowed,
            Integer minPoints,
            Integer maxPoints) {
        List<String> predicates = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            predicates.add("(lower(email) like ? or lower(coalesce(nickname, '')) like ?)");
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null) {
            predicates.add("status = ?");
            args.add(status.name());
        }
        if (adultContentAllowed != null) {
            predicates.add("coalesce(adult_content_allowed, false) = ?");
            args.add(adultContentAllowed);
        }
        if (minPoints != null) {
            predicates.add("points.balance >= ?");
            args.add(Math.max(0, minPoints));
        }
        if (maxPoints != null) {
            predicates.add("points.balance <= ?");
            args.add(Math.max(0, maxPoints));
        }
        String where = predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);
        return new QueryParts(where, args);
    }

    private String orderBy(String sort) {
        SortSpec spec = parseSort(sort);
        return " order by " + spec.column() + " " + spec.direction() + ", id asc";
    }

    private SortSpec parseSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return new SortSpec("created_at", "desc");
        }
        String[] parts = sort.split(",");
        String column = switch (parts[0].trim()) {
            case "email" -> "email";
            case "nickname" -> "nickname";
            case "role" -> "role";
            case "status" -> "status";
            case "experience" -> "experience";
            case "points" -> "points";
            case "updatedAt" -> "updated_at";
            default -> "created_at";
        };
        String direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()) ? "asc" : "desc";
        return new SortSpec(column, direction);
    }

    private long count(String sql, List<Object> args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    private RowMapper<MemberRecord> memberMapper() {
        return (rs, rowNum) -> new MemberRecord(
                rs.getObject("id", UUID.class),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getObject("version", Long.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getString("role"),
                MemberStatus.valueOf(rs.getString("status")),
                rs.getBoolean("adult_content_allowed"),
                rs.getInt("experience"),
                rs.getInt("points"),
                toLocalDate(rs.getDate("last_check_in_date")),
                rs.getInt("check_in_streak"));
    }

    private RowMapper<HistoryRecordResponse> historyMapper() {
        return (rs, rowNum) -> new HistoryRecordResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("catalog_entry_id", UUID.class),
                rs.getString("title"),
                coverImageUrl(rs),
                rs.getObject("score", BigDecimal.class),
                rs.getObject("last_video_id", UUID.class),
                rs.getObject("last_episode_id", UUID.class),
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

    private int getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? 0 : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private record QueryParts(String where, List<Object> args) {
    }

    private record SortSpec(String column, String direction) {
    }
}
