package com.prodigalgal.ircs.interaction;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberPointLedger {

    private final JdbcTemplate jdbcTemplate;

    public void lockMember(UUID memberId) {
        boolean memberExists = !jdbcTemplate.queryForList("""
                select id from member_accounts where id = ? and status = 'ACTIVE' for no key update
                """, UUID.class, memberId).isEmpty();
        if (!memberExists) {
            throw new ApiException(HttpStatus.FORBIDDEN, "账户当前不可用");
        }
    }

    public void spend(UUID memberId, int points, String reason, UUID subjectId) {
        if (points <= 0) {
            return;
        }
        Integer balance = jdbcTemplate.queryForObject(
                "select apply_member_point_transaction(?, ?, ?, ?, false, ?)",
                Integer.class,
                memberId,
                reason + ":" + subjectId,
                reason,
                -points,
                subjectId);
        if (balance == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "积分不足，请先签到获取积分");
        }
    }
}
