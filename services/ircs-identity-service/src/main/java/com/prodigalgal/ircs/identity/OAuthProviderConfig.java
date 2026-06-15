package com.prodigalgal.ircs.identity;

import java.net.URI;

record OAuthProviderConfig(
        OAuthProviderRegistry.Definition definition,
        String clientId,
        String clientSecret,
        String scope,
        URI redirectUri,
        String frontendBaseUrl) {

    String code() {
        return definition.code();
    }

    String label() {
        return definition.label();
    }
}
