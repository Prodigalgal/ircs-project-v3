package com.prodigalgal.ircs.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Ed25519 envelope. Only the API gateway is provisioned with the signing key. */
public final class GatewayIdentitySignature {
    public static final String SIGNATURE = "X-IRCS-Gateway-Signature";
    public static final String TIMESTAMP = "X-IRCS-Gateway-Timestamp";
    public static final String CLIENT_IP = "X-IRCS-Client-IP";
    static final String VERIFIED_ATTRIBUTE = GatewayIdentitySignature.class.getName() + ".verified";
    public static final String CLIENT_IP_ATTRIBUTE = GatewayIdentitySignature.class.getName() + ".clientIp";
    private static final List<String> IDENTITY_HEADERS = List.of(
            IrcsAuthHeaders.AUTHENTICATED_USER, IrcsAuthHeaders.AUTH_SUBJECT, IrcsAuthHeaders.AUTH_ROLE,
            IrcsAuthHeaders.AUTH_PERMISSIONS, IrcsAuthHeaders.AUTH_SCOPES, IrcsAuthHeaders.DATA_CATEGORIES,
            IrcsAuthHeaders.DATA_GENRES, IrcsAuthHeaders.DATA_TAGS, IrcsAuthHeaders.CONTENT_VISIBILITY);

    private GatewayIdentitySignature() { }

    public static PrivateKey privateKey(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(decodeKey(encoded)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid gateway identity signing key");
        }
    }

    public static PublicKey publicKey(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(decodeKey(encoded)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid gateway identity verification key");
        }
    }

    private static byte[] decodeKey(String encoded) {
        return Base64.getDecoder().decode(encoded.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
    }

    public static void sign(Map<String, String> headers, String method, String target, String clientIp, PrivateKey key) {
        headers.keySet().removeIf(GatewayIdentitySignature::isEnvelopeHeader);
        headers.put(CLIENT_IP, clientIp);
        headers.put(TIMESTAMP, Long.toString(Instant.now().getEpochSecond()));
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(canonical(method, target, name -> header(headers, name)));
            headers.put(SIGNATURE, Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        } catch (Exception ex) {
            throw new IllegalStateException("Gateway identity signing failed");
        }
    }

    public static boolean verify(HttpServletRequest request, PublicKey key) {
        try {
            for (String name : IDENTITY_HEADERS) {
                if (java.util.Collections.list(request.getHeaders(name)).size() > 1) return false;
            }
            for (String name : List.of(SIGNATURE, TIMESTAMP, CLIENT_IP)) {
                if (java.util.Collections.list(request.getHeaders(name)).size() != 1) return false;
            }
            long issuedAt = Long.parseLong(request.getHeader(TIMESTAMP));
            long now = Instant.now().getEpochSecond();
            if (issuedAt < now - 30 || issuedAt > now + 5) return false;
            if (request.getHeader(CLIENT_IP).isBlank()) return false;
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(canonical(request.getMethod(), target(request), request::getHeader));
            return verifier.verify(Base64.getUrlDecoder().decode(request.getHeader(SIGNATURE)));
        } catch (Exception ex) {
            return false;
        }
    }

    public static String target(HttpServletRequest request) {
        return request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    }

    // Each UTF-8 field is independently base64url encoded; one field per line, including the final newline.
    static byte[] canonical(String method, String target, Function<String, String> headers) {
        StringBuilder result = new StringBuilder("IRCS-GATEWAY-IDENTITY-V1\n");
        field(result, method.toUpperCase(Locale.ROOT));
        field(result, target);
        for (String name : IDENTITY_HEADERS) field(result, headers.apply(name));
        field(result, headers.apply(CLIENT_IP));
        field(result, headers.apply(TIMESTAMP));
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void field(StringBuilder result, String value) {
        result.append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8))).append('\n');
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream().filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }

    public static boolean isEnvelopeHeader(String name) {
        return SIGNATURE.equalsIgnoreCase(name) || TIMESTAMP.equalsIgnoreCase(name) || CLIENT_IP.equalsIgnoreCase(name);
    }
}
