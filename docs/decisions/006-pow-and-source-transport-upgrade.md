# 006 PoW and source transport upgrade

Date: 2026-06-11

## Decision

IRCS V3 uses scoped, one-time PoW challenges for high-risk identity entrypoints and a pluggable outbound transport model for source-site fetches.

## Identity security

- Admin PoW applies only to `/api/v1/auth/login`.
- Authenticated admin APIs do not require additional PoW.
- Portal PoW applies to register, login, activate, resend-code, forgot-password, and reset-password.
- Math captcha is required only for admin and portal login challenges.
- PoW verification is transported by headers:
  - `X-IRCS-POW-Challenge`
  - `X-IRCS-POW-Counter`
  - `X-IRCS-POW-Digest`
  - `X-IRCS-POW-Captcha-Answer` for login scopes
- The portal/admin frontends show an explicit verification/loading state while calculating PoW, and show the math captcha on login forms.
- The portal frontend sends scoped PoW headers for every high-risk portal auth route.
- The admin frontend sends scoped PoW headers only for admin login.

## Source transport

`data_sources.transport_mode` controls source fetch transport:

- `AUTO`
- `JDK`
- `APACHE_HC5`
- `CURL_NATIVE`

`AUTO` keeps normal requests on JDK HttpClient and routes requests to Apache HC5 when the source requires HTTP/1.0, non-system DNS, or explicit IPv4/IPv6 policy.

`CURL_NATIVE` is accepted in the data contract but currently fails closed at runtime until a confirmed source-site live test requires it.

admin data-source wizard exposes transport mode, HTTP protocol, IP version policy, DNS resolver type/endpoint, source timeouts, and User-Agent. The same options are used for create/update and fetch-sample requests.

## Rationale

JDK HttpClient cannot provide request-level DNS resolver override and does not support true HTTP/1.0. Apache HC5 gives IRCS a Java-native path for request-scoped DNS and legacy HTTP version selection without moving every outbound request onto a heavier stack.

`CURL_NATIVE` remains the final fallback for source sites that need libcurl-specific behavior such as exact HTTP/1.0 edge cases, `--resolve`, or curl DoH semantics.

## Verification boundary

This decision is implementation-first. Build-level backend and frontend guards passed; behavior and live source-site verification are deferred to the unified test phase.
