package com.prodigalgal.ircs.identity;

record OAuthAccessToken(
        String accessToken,
        String tokenType,
        String openId) {
}
