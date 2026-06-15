package com.prodigalgal.ircs.identity;

interface OAuthProviderClient {

    OAuthAccessToken exchangeCode(OAuthProviderConfig config, String code, String codeVerifier);

    OAuthUserProfile fetchUserProfile(OAuthProviderConfig config, OAuthAccessToken token);
}
