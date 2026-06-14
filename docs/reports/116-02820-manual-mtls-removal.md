# 116 028-20 manual mTLS removal

Date: 2026-06-12

## Summary

Manual mTLS has been removed from `ircs-dev` and from current dev deployment sources. The target state is now HTTP inside the cluster plus application-layer service identity/scopes and later network-policy hardening.

## Changes

- Removed `deploy/k8s/dev/mtls-certificates.yaml`.
- Removed old mTLS rollout and smoke scripts:
  - `scripts/k8s/apply-dev-mtls-first-slice.ps1`
  - `scripts/k8s/apply-dev-mtls-internal-servers.ps1`
  - `scripts/smoke/mtls-first-slice-live-smoke.ps1`
  - `scripts/smoke/mtls-internal-servers-live-smoke.ps1`
- Restored `deploy/k8s/dev/frontend-portal-admin-dev.yaml` gateway upstreams to HTTP and removed nginx mTLS volume mounts.
- Restored `deploy/k8s/dev/observability-monitoring-dev.yaml` to one HTTP ServiceMonitor plus one HTTP PodMonitor.
- Added `scripts/k8s/remove-dev-manual-mtls.ps1` for live removal of manual mTLS env, volumes, URLs, cert-manager resources, Secrets, and HTTPS ServiceMonitors.

## Live Commands

```powershell
kubectl apply --dry-run=server -n ircs-dev -f deploy\k8s\dev\frontend-portal-admin-dev.yaml
kubectl apply --dry-run=server -n ircs-dev -f deploy\k8s\dev\observability-monitoring-dev.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\k8s\remove-dev-manual-mtls.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\k8s\remove-dev-manual-mtls.ps1 -Execute
```

## Evidence

```text
ircs-dev deployments Ready: 25/25
NO_MANUAL_MTLS_IN_DEPLOYMENTS
HTTP readiness smoke: MARK 1/20..20/20 passed
Prometheus: IRCS_DEV_TARGETS=20, IRCS_DEV_UP=20, IRCS_DEV_HTTPS_TARGETS=0, IRCS_DEV_MTLS_TARGETS=0
ResourceQuota: secrets 5/30, pods 28/30, services 25/26
```

## Residual Risk

- In-cluster transport is no longer encrypted by mTLS. Current mitigation is namespace isolation, ClusterIP-only services, app-layer service identity/scopes, and existing authz checks.
- A future mTLS attempt must be infrastructure-owned. Do not reintroduce manual per-service mTLS wiring in application manifests.
