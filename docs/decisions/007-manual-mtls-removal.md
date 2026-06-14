# 007 Manual mTLS removal

Date: 2026-06-12

## Decision

IRCS V3 no longer keeps manual application-managed mTLS in Spring Boot, nginx, Prometheus, cert-manager per-service certificates, JVM keystore/truststore, or per-service Kubernetes Secrets.

Service-to-service traffic in `ircs-dev` returns to plain HTTP inside the cluster. Authorization remains in the application layer through service identity headers, scoped service tokens, RBAC/permission checks, data scopes, audit, and future NetworkPolicy hardening.

## Rationale

- Transport mTLS is an infrastructure concern, not a business-service configuration concern.
- Manual mTLS added duplicated operational state: `SERVER_SSL_*`, `JAVA_TOOL_OPTIONS`, keystore/truststore files, nginx `proxy_ssl_*`, Prometheus TLS scrape config, cert-manager `Certificate` resources, and per-service Secrets.
- Current dev resource quotas are tight; removing per-service cert Secrets immediately reduces namespace pressure.
- Service mesh mTLS is deferred. Istio ambient, Linkerd, and Cilium native mTLS all require separate maturity, resource, and compatibility validation before becoming a production target.

## Consequences

- Keep application-level `service identity + scopes`; do not reuse shared admin tokens.
- Keep internal APIs ClusterIP-only and no-public-route.
- Do not add new app-managed `SERVER_SSL_*`, `javax.net.ssl.*` keystore/truststore wiring, or per-service `*-mtls` Secrets.
- If mTLS is revisited later, it must be owned by the infrastructure layer and introduced behind a separate rollout plan.

## Verification

```text
ircs-dev deployments Ready: 25/25
manual mTLS resources: certificates=0, issuers=0, secrets=0, HTTPS ServiceMonitors=0
Deployment manual mTLS env/volume scan: NO_MANUAL_MTLS_IN_DEPLOYMENTS
HTTP readiness smoke: MARK 1/20..20/20 passed
Prometheus ircs-dev active targets: 20
Prometheus ircs-dev UP targets: 20
Prometheus HTTPS targets: 0
Prometheus mTLS targets: 0
ResourceQuota secrets: 5/30
```
