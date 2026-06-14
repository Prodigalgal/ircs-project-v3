# 028-20-27 manual mTLS removal

Status: done

Decision: `docs/decisions/007-manual-mtls-removal.md`

Report: `docs/reports/116-02820-manual-mtls-removal.md`

## Goal

Remove manual application-managed mTLS from IRCS V3 dev runtime and repo deployment sources.

## Scope

- Restore internal service traffic to HTTP.
- Remove Spring Boot server mTLS env and JVM keystore/truststore client env.
- Remove nginx gateway mTLS client config.
- Remove Prometheus HTTPS/mTLS scrape config.
- Delete manual mTLS cert-manager resources and Secrets.
- Keep app-layer service identity, scoped tokens, RBAC, data scope, audit, and internal API authorization.

## Non-goals

- Do not migrate to Istio ambient, Linkerd, or Cilium mTLS in this slice.
- Do not remove application-level service identity/scopes.
- Do not open public routes.

## Verification

```text
kubectl apply --dry-run=server -n ircs-dev -f deploy\k8s\dev\frontend-portal-admin-dev.yaml
kubectl apply --dry-run=server -n ircs-dev -f deploy\k8s\dev\observability-monitoring-dev.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\k8s\remove-dev-manual-mtls.ps1 -Execute

ircs-dev deployments Ready: 25/25
NO_MANUAL_MTLS_IN_DEPLOYMENTS
HTTP readiness smoke: MARK 1/20..20/20 passed
Prometheus ircs-dev targets: 20/20 UP
Prometheus HTTPS targets: 0
Prometheus mTLS targets: 0
ResourceQuota secrets: 5/30
```
