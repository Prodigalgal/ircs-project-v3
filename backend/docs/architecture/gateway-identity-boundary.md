# Gateway identity boundary

The API gateway is the sole holder of the Ed25519 private signing key. Java backend APIs under `/api/v1` and `/api/portal` require a gateway signature even for public auth and anonymous portal reads. The signature proves the gateway's authorization decision; backend services never trust bare `X-IRCS-Auth-*` headers. The identity service additionally binds admin principals to the configured admin username (or a gateway-authenticated `api-token:` PAT identity) and rechecks member status.

`/media` remains directly accessible through the storage HTTPRoute. `/internal` keeps its service-token and scope policy and is not a gateway API. Missing internal service tokens now return 503 regardless of the historical `require-token=false` setting. Health probes and Prometheus endpoints retain their existing behavior.

## Runtime configuration

- Gateway only: `IRCS_GATEWAY_IDENTITY_SIGNING_KEY`, Ed25519 PKCS#8 private key as base64 DER or PEM.
- Java backend apps: `IRCS_GATEWAY_IDENTITY_VERIFICATION_KEY`, corresponding X.509 public key as base64 DER or PEM. Missing verification configuration fails API requests with 503. Malformed configured keys fail startup.
- Gateway: `IRCS_GATEWAY_IDENTITY_TRUSTED_PROXY_CIDRS` defaults to empty. Without this explicit trust boundary it uses the transport peer address. Production permits the cluster Pod CIDR only together with an ingress NetworkPolicy restricting gateway traffic to actual Envoy data-plane pods, the existing frontend nginx/SSR callers and Prometheus. The last Envoy-appended XFF entry is used; earlier client-supplied values, X-Real-IP and Forwarded are stripped. Servlet forwarded-header rewriting must remain disabled.
- Identity CORS: `app.identity.cors.allowed-origins` is a comma-separated exact origin allowlist, defaulting to the production admin and portal origins. Wildcards are discarded and browser ambient credentials are disabled.

## Signature wire format

Headers: `X-IRCS-Gateway-Timestamp` is epoch seconds; `X-IRCS-Client-IP` is the trusted address; `X-IRCS-Gateway-Signature` is the Ed25519 signature encoded base64url without padding. The three headers must occur exactly once. Identity headers may occur at most once. Accepted timestamp window is 30 seconds old through 5 seconds ahead.

Canonical bytes begin with the ASCII line `IRCS-GATEWAY-IDENTITY-V1` followed by LF. Each following field is UTF-8 encoded, then independently base64url encoded without padding, followed by LF (including the last field). An absent identity header is an empty line. Field order:

1. Uppercase HTTP method.
2. Exact upstream raw path plus `?` and raw query if present. Sign the routed target after query sanitization.
3. `X-Authenticated-User`
4. `X-IRCS-Auth-Subject`
5. `X-IRCS-Auth-Role`
6. `X-IRCS-Auth-Permissions`
7. `X-IRCS-Auth-Scopes`
8. `X-IRCS-Data-Categories`
9. `X-IRCS-Data-Genres`
10. `X-IRCS-Data-Tags`
11. `X-IRCS-Content-Visibility`
12. `X-IRCS-Client-IP`
13. `X-IRCS-Gateway-Timestamp`

This envelope is short-lived gateway identity attestation, not a single-use transaction token. Captured signed requests can be replayed for the same method/target/identity within the time window; payload bytes are not signed. Keep the private key gateway-only and preserve the ingress policy. Gateway compromise is outside this boundary. Existing operation idempotency and service-token scopes remain necessary.

## Authentication abuse controls

PoW and rate limiting share canonical matching for trailing slash, matrix parameter and encoded path aliases, without assuming those aliases are routed by MVC. Rate buckets use only the gateway-verified client address (or the transport peer in isolated filter tests), never raw XFF or X-Real-IP.

The old optional `PoWVerification(id, nonce)` body dialect cannot prove scope, server nonce or login captcha and is rejected with `pow.legacy.unsupported`. Existing frontend source uses scoped PoW headers and is unchanged. Historical clients sending the optional body must migrate to the header protocol. Header challenges are consumed atomically by Redis Lua, reject expired state and mismatched scopes, and cannot be reused.

## Deployment and rollback

Provision the private key only via the existing secret, replace the public-key placeholder in the app ConfigMap, and deploy the signing gateway (sync wave -5) before signature-enforcing backends (waves 0 and later). Migrator and classifier retain earlier waves. Rolling back only the gateway to a version that does not sign while keeping enforcing backends will cause 401 responses; roll back backends before the gateway, preserving their immutable images.

Validation is through GitHub Actions only. Native smoke must exercise one protected backend directly with forged identity headers (401), then the same authorized operation via the gateway, plus anonymous portal/auth traffic, media and internal token/scopes. Unit tests cover signature field tampering, missing configuration, stale/future signatures, duplicate headers, path aliases, CORS, forwarded-IP spoofing and PoW scope/replay.
