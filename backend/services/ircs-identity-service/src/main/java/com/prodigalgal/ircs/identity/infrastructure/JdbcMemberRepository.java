package com.prodigalgal.ircs.identity.infrastructure;




import com.prodigalgal.ircs.identity.repository.MemberRepository;
import com.prodigalgal.ircs.identity.domain.MemberRecord;
import com.prodigalgal.ircs.identity.domain.MemberStatus;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcMemberRepository implements MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<MemberRecord> findByEmail(String email) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    selectSql() + " where lower(email) = lower(?)",
                    mapper(),
                    email));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MemberRecord> findById(UUID id) {
        return findById(id, "");
    }

    @Override
    public Optional<MemberRecord> findByIdForUpdate(UUID id) {
        return findById(id, " for no key update of account");
    }

    private Optional<MemberRecord> findById(UUID id, String lock) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    selectSql() + " where account.id = ?" + lock,
                    mapper(),
                    id));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from member_accounts where lower(email) = lower(?))",
                Boolean.class,
                email);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public MemberRecord insert(MemberRecord member) {
        jdbcTemplate.update(
                """
                insert into member_accounts (
                    id, created_at, updated_at, version, email, password_hash, nickname, avatar_url, role, status,
                    adult_content_allowed, experience, last_check_in_date, check_in_streak
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                member.id(),
                timestamp(member.createdAt()),
                timestamp(member.updatedAt()),
                member.version(),
                member.email(),
                member.passwordHash(),
                member.nickname(),
                member.avatarUrl(),
                member.role(),
                member.status().name(),
                member.adultContentAllowed(),
                member.experience(),
                date(member.lastCheckInDate()),
                member.checkInStreak());
        if (member.points() > 0) {
            applyPointTransaction(member.id(), "account-opening", "ACCOUNT_OPENING", member.points(), false);
        }
        return member;
    }

    @Override
    public MemberRecord update(MemberRecord member) {
        int updatedRows = jdbcTemplate.update(
                """
                update member_accounts
                   set updated_at = ?,
                       version = coalesce(version, 0) + 1,
                       email = ?,
                       password_hash = ?,
                       nickname = ?,
                       avatar_url = ?,
                       role = ?,
                       status = ?,
                       adult_content_allowed = ?,
                       experience = ?,
                       last_check_in_date = ?,
                       check_in_streak = ?
                 where id = ? and coalesce(version, 0) = coalesce(cast(? as bigint), 0)
                """,
                timestamp(member.updatedAt()),
                member.email(),
                member.passwordHash(),
                member.nickname(),
                member.avatarUrl(),
                member.role(),
                member.status().name(),
                member.adultContentAllowed(),
                member.experience(),
                date(member.lastCheckInDate()),
                member.checkInStreak(),
                member.id(),
                member.version());
        if (updatedRows == 0) {
            throw new OptimisticLockingFailureException("Member account changed during update");
        }
        return member;
    }

    @Override
    public int grantDailyCheckInPoints(UUID memberId, LocalDate date, int earnedPoints) {
        return applyPointTransaction(memberId, "daily-check-in:" + date, "DAILY_CHECK_IN", earnedPoints, false);
    }

    @Override
    public void setPointBalance(UUID memberId, int balance) {
        applyPointTransaction(memberId, "admin-adjustment:" + UUID.randomUUID(), "ADMIN_ADJUSTMENT", balance, true);
    }

    private int applyPointTransaction(UUID memberId, String key, String reason, int points, boolean setBalance) {
        return jdbcTemplate.queryForObject(
                "select apply_member_point_transaction(?, ?, ?, ?, ?, null)",
                Integer.class, memberId, key, reason, points, setBalance);
    }

    private String selectSql() {
        return """
        select account.id, account.created_at, account.updated_at, account.version, account.email,
               account.password_hash, account.nickname, account.avatar_url, account.role, account.status,
               coalesce(account.adult_content_allowed, false) as adult_content_allowed,
               account.experience, points.balance as points, account.last_check_in_date, account.check_in_streak
          from member_accounts account join member_point_balances points on points.member_id = account.id
        """;
    }

    private RowMapper<MemberRecord> mapper() {
        return (rs, rowNum) -> new MemberRecord(
                rs.getObject("id", UUID.class),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
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
                localDate(rs.getDate("last_check_in_date")),
                rs.getInt("check_in_streak"));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Date date(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
