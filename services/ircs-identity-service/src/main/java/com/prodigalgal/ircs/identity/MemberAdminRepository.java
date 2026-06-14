package com.prodigalgal.ircs.identity;

import java.util.UUID;

public interface MemberAdminRepository {

    PageResponse<MemberRecord> findMembers(String keyword, MemberStatus status, PageBounds bounds, String sort);

    boolean existsByEmailExcludingId(String email, UUID excludedId);

    PageResponse<HistoryRecordResponse> findFavorites(UUID memberId, PageBounds bounds);

    PageResponse<HistoryRecordResponse> findHistory(UUID memberId, PageBounds bounds);

    void deleteMemberRelations(UUID memberId);

    int deleteMember(UUID memberId);
}
