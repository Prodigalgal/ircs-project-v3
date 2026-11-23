package com.prodigalgal.ircs.identity.repository;


import com.prodigalgal.ircs.identity.domain.MemberRecord;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {

    Optional<MemberRecord> findByEmail(String email);

    Optional<MemberRecord> findById(UUID id);

    Optional<MemberRecord> findByIdForUpdate(UUID id);

    boolean existsByEmail(String email);

    MemberRecord insert(MemberRecord member);

    MemberRecord update(MemberRecord member);

    int grantDailyCheckInPoints(UUID memberId, LocalDate date, int earnedPoints);

    void setPointBalance(UUID memberId, int balance);
}
