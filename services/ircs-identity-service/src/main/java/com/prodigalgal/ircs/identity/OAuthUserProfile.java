package com.prodigalgal.ircs.identity;

record OAuthUserProfile(
        String provider,
        String subject,
        String email,
        boolean emailVerified,
        String nickname,
        String avatarUrl) {
}
