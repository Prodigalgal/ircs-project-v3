package com.prodigalgal.ircs.identity;

import com.prodigalgal.ircs.identity.IdentityDtos.MemberProfileResponse;
import com.prodigalgal.ircs.identity.IdentityDtos.MemberTokenResponse;
import com.prodigalgal.ircs.identity.IdentityDtos.MemberAdminResponse;
import org.springframework.stereotype.Component;

@Component
public class MemberResponseMapper {

    public MemberTokenResponse toToken(MemberRecord member, String token) {
        return new MemberTokenResponse(
                token,
                member.id().toString(),
                member.email(),
                member.nickname(),
                member.avatarUrl(),
                member.role(),
                member.status().name(),
                member.experience(),
                member.points(),
                member.level(),
                member.nextLevelXp(),
                member.checkInStreak(),
                member.title(),
                member.lastCheckInDate());
    }

    public MemberProfileResponse toProfile(MemberRecord member) {
        return new MemberProfileResponse(
                member.id(),
                member.email(),
                member.nickname(),
                member.avatarUrl(),
                member.role(),
                member.status(),
                member.experience(),
                member.points(),
                member.level(),
                member.nextLevelXp(),
                member.lastCheckInDate(),
                member.checkInStreak(),
                member.title(),
                member.createdAt(),
                member.updatedAt());
    }

    public MemberAdminResponse toAdmin(MemberRecord member) {
        return new MemberAdminResponse(
                member.id(),
                member.email(),
                member.nickname(),
                member.avatarUrl(),
                member.role(),
                member.status(),
                member.experience(),
                member.points(),
                member.level(),
                member.nextLevelXp(),
                member.lastCheckInDate(),
                member.checkInStreak(),
                member.title(),
                member.createdAt(),
                member.updatedAt());
    }
}
