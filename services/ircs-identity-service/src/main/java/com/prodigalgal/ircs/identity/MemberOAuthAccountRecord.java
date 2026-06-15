package com.prodigalgal.ircs.identity;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import java.time.Instant;
import java.util.UUID;

record MemberOAuthAccountRecord(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        long version,
        UUID memberId,
        String provider,
        String providerUserId,
        String providerEmail,
        boolean providerEmailVerified,
        String providerNickname,
        String providerAvatarUrl,
        String accessTokenHash,
        Instant lastLoginAt) {

    static MemberOAuthAccountRecord newAccount(
            UUID memberId,
            OAuthUserProfile profile,
            String accessTokenHash) {
        Instant now = Instant.now();
        return new MemberOAuthAccountRecord(
                IrcsUuidGenerators.nextId(),
                now,
                now,
                0L,
                memberId,
                profile.provider(),
                profile.subject(),
                profile.email(),
                profile.emailVerified(),
                profile.nickname(),
                profile.avatarUrl(),
                accessTokenHash,
                now);
    }

    MemberOAuthAccountRecord withProfile(OAuthUserProfile profile, String nextAccessTokenHash) {
        return new MemberOAuthAccountRecord(
                id,
                createdAt,
                Instant.now(),
                version,
                memberId,
                provider,
                providerUserId,
                profile.email(),
                profile.emailVerified(),
                profile.nickname(),
                profile.avatarUrl(),
                nextAccessTokenHash,
                Instant.now());
    }
}
