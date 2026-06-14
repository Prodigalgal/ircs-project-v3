# 093 Security and Source Network Hardening

## Summary

This migration slice hardens V3 service security and adds data-source level network behavior fields needed for later V1 source live testing.

## Security Changes

- JWT secrets no longer have source-level development fallback values.
- JWT HMAC secrets must be configured and at least 32 bytes.
- JWTs are no longer accepted from URL query parameters.
- Auth routes have Redis-backed IP/path counters.
- Password reset has per-email request limits and per-token invalid attempt limits.
- ops-service internal APIs require service identity in dev K8S.
- storage-service avatar internal upload requires service identity in dev K8S.
- BFFs strip inbound internal service identity headers; admin-bff injects them only for ops routes.
- actuator public access is narrowed to health/info/prometheus in identity-service.
- maintenance gate writes now validate payloads.
- cover image upload has an explicit configurable max size.
- High-risk identity routes use header-based PoW enforcement.
- Admin PoW is limited to login; authenticated admin operations do not add extra PoW.
- Portal auth PoW covers register/login/activate/resend-code/forgot-password/reset-password.
- Admin and portal login challenges include a math captcha prompt so the UI can show an explicit verification/loading state.
- portal frontend now sends PoW through `X-IRCS-POW-*` headers for login/register/activate/resend-code/forgot-password/reset-password.
- admin frontend sends PoW headers only for login; authenticated admin APIs remain bearer-token protected without extra PoW.
- portal/admin login forms display the math captcha prompt and visible security verification progress.

## Source Network Changes

Added to `data_sources`:

- `transport_mode`
- `http_protocol`
- `ip_version_policy`
- `dns_resolver_type`
- `dns_resolver_endpoint`
- `connect_timeout_ms`
- `read_timeout_ms`
- `user_agent`

The fields are exposed through catalog admin data-source APIs and fetch-sample payloads. scraper-service reads them and applies source timeout, source user-agent, HTTP version, transport mode, DNS resolver, and IPv4/IPv6 policy during real scraping.

admin frontend exposes the same fields in the data-source wizard and sends them for create/update plus list/detail fetch-sample requests, so source-specific behavior can be tuned before the unified live source test pass.

Shared outbound now routes source fetches through `OutboundTransportRouter`:

- `JDK`: existing JDK HttpClient path.
- `APACHE_HC5`: Apache HttpClient 5 classic transport with request-level DNS resolver and HTTP version selection.
- `AUTO`: JDK for normal requests; Apache HC5 when HTTP/1.0, DoH/DoT, or IPv4/IPv6 policy requires non-JDK behavior.
- `CURL_NATIVE`: stored as a future explicit fallback; currently fails closed if selected.

`PolicyAwareApacheDnsResolver` supports system DNS with IPv4/IPv6 filtering, JSON DoH A/AAAA lookup, and DNS-over-TLS A/AAAA lookup.

## Known Boundary

DoH/DoT and HTTP/1.0 no longer rely on JDK HttpClient when the request is routed to Apache HC5. The unified live source test pass must still identify whether any V1 source requires `CURL_NATIVE` or a dedicated source-fetcher service.

## Verification

`.\gradlew.bat --no-daemon compileJava -x test`

Result: `BUILD SUCCESSFUL`

`.\gradlew.bat --no-daemon :shared:ircs-common:compileTestJava :services:ircs-identity-service:compileTestJava :services:ircs-catalog-service:compileTestJava :services:ircs-scraper-service:compileTestJava -x test`

Result: `BUILD SUCCESSFUL`

`npm run build` in `D:\WorkSpace\Project\ircs\ircs-project-v1\huawai`

Result: `next build` successful

`npm run build` in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`

Result: `vite build` successful; rerun after data-source network strategy UI
