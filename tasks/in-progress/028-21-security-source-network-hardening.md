# 028-21 Security and Source Network Hardening

## Goal

Close the newly identified security gaps and add data-source level network options before the next unified live test pass.

## Scope

- Remove JWT URL token fallback and hardcoded JWT secret fallback from source configuration.
- Require non-empty JWT secret with at least 32 bytes before signing/parsing.
- Add Redis-backed auth endpoint rate limiting and password reset brute-force counters.
- Protect ops-service and storage-service internal APIs with service identity headers and scopes.
- Add validation to maintenance gate writes and explicit cover image upload size limit.
- Persist and expose data-source HTTP/DNS/IP options for admin payloads and scraper runtime.
- Upgrade high-risk identity routes to header-based PoW challenge enforcement and add login math captcha.
- Add source transport selection so legacy source sites can opt into non-JDK HTTP behavior.
- Keep behavior/live tests deferred to the unified test phase, per current direction.

## Implemented

- `JwtAuthenticationFilter`, `IrcsJwtRequestAuthenticator`, interaction portal/feedback controllers now accept member JWT only from `Authorization: Bearer`.
- `JwtTokenService` and `IrcsJwtAuthDecoder` reject missing or short JWT HMAC secrets.
- `IdentityAuthRateLimitFilter` adds IP/path Redis counters for login/reset/activation routes.
- `MemberAuthService` adds reset request and invalid reset token attempt counters.
- `OpsInternalAccessFilter` protects `/api/v1/ops`, `/api/v1/dashboard`, `/api/v1/debug`.
- `StorageInternalAccessPolicy` protects `/internal/storage/avatars`; identity-service now calls storage-service with `storage:avatar`.
- admin-bff injects `ops:read ops:run` service identity for ops routes; portal/admin BFF strip externally supplied internal service headers.
- `MaintenanceGateCreateRequest` and `MaintenanceGateCloseRequest` now use Bean Validation.
- `CoverImageAdminService` enforces `app.storage.image.max-upload-bytes`.
- `data_sources` gained `http_protocol`, `ip_version_policy`, `dns_resolver_type`, `dns_resolver_endpoint`, `connect_timeout_ms`, `read_timeout_ms`, `user_agent`.
- catalog data-source admin/fetch-sample payloads expose the new fields.
- identity challenge endpoints now return scoped PoW data (`scope`, `nonce`, `expiresAt`) and login-only math captcha prompts.
- `PoWEnforcementFilter` verifies `X-IRCS-POW-Challenge`, `X-IRCS-POW-Counter`, `X-IRCS-POW-Digest`, and login captcha answers before high-risk auth handlers.
- Admin PoW boundary is intentionally limited to `/api/v1/auth/login`; authenticated admin APIs are not given extra PoW.
- Portal PoW boundary covers register/login/activate/resend-code/forgot-password/reset-password; math captcha is login-only.
- `data_sources` now also has `transport_mode` with `AUTO`, `JDK`, `APACHE_HC5`, `CURL_NATIVE`.
- catalog data-source admin/fetch-sample payloads expose `transportMode`.
- scraper-service reads the new fields and applies source read timeout, source user-agent, HTTP version, transport mode, DNS resolver, and IPv4/IPv6 policy.
- shared outbound now has `OutboundTransportRouter`, `ApacheHttpClient5OutboundTransport`, and policy-aware Apache DNS resolver.
- `AUTO` transport stays on JDK for normal requests and switches to Apache HC5 for HTTP/1.0, non-system DNS, or explicit IPv4/IPv6 policy.
- DoH and DoT source DNS policies are implemented in the policy-aware resolver for A/AAAA lookup; `CURL_NATIVE` remains an explicit not-implemented runtime boundary.
- portal frontend (`huawai`) now solves scoped PoW into `X-IRCS-POW-*` headers for login/register/activate/resend-code/forgot-password/reset-password.
- portal login page now fetches a login challenge up front, displays the math captcha prompt, and shows visible challenge/PoW/login loading states.
- admin frontend (`ircs-frontend`) now limits PoW to admin login, sends PoW headers through the login mutation, displays the math captcha prompt, and keeps authenticated admin APIs unchanged.
- portal API error handling now recognizes the unified error envelope fields commonly returned by V3 services.
- admin data-source wizard now exposes source network strategy fields: transport mode, HTTP protocol, IPv4/IPv6 policy, DNS resolver type/endpoint, connect/read timeout, and User-Agent.
- admin data-source save and list/detail fetch-sample requests now send the same source network strategy payload.

## Validation

- Ran: `.\gradlew.bat --no-daemon compileJava -x test`
- Result: `BUILD SUCCESSFUL`
- Latest compile evidence after PoW + transport upgrade: `.\gradlew.bat --no-daemon compileJava -x test` -> `BUILD SUCCESSFUL`
- DTO/test source compile guard: `.\gradlew.bat --no-daemon :shared:ircs-common:compileTestJava :services:ircs-identity-service:compileTestJava :services:ircs-catalog-service:compileTestJava :services:ircs-scraper-service:compileTestJava -x test` -> `BUILD SUCCESSFUL`
- Portal frontend compile guard: `npm run build` in `D:\WorkSpace\Project\ircs\ircs-project-v1\huawai` -> `next build` successful
- Admin frontend compile guard: `npm run build` in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend` -> `vite build` successful, rerun after data-source network strategy UI

## Deferred Unified Test Items

- Full auth/security regression tests.
- K8S rollout smoke for ops/admin-bff/storage/identity after image rebuild.
- Portal/admin live login smoke against deployed V3 identity-service.
- V1 source-site live request matrix:
  - IPv4/IPv6 behavior.
  - DNS pollution behavior.
  - DoH/DoT endpoint behavior.
  - HTTP/1.0-only source behavior.
  - Source-specific user-agent and timeout behavior.
- Confirm whether any source requires `CURL_NATIVE` after the V1 source-site live request matrix.

## Risks

- Apache HC5 now enforces request-level DNS resolver selection for source fetches that route to it; live source testing still needs to confirm each real source's DNS/protocol behavior.
- HTTP/1.0 is implemented through Apache HC5 request version selection, but source-specific behavior still needs live verification.
- `CURL_NATIVE` is persisted as an explicit future mode but currently fails closed if selected.
- Test sources were not updated in this slice; unified test cleanup must update constructor signatures for new data-source fields.
