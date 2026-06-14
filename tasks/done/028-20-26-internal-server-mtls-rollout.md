# 028-20-26 internal server mTLS rollout

状态：done

日期：2026-06-12

## 目标

把 `ircs-dev` 中需要或可以切 mTLS 的 internal server 从 first slice 扩展到所有 Service-backed Spring/BFF 服务，并保持 gateway、Prometheus、BFF 到后端调用链路可用。

## 范围

- 16 个 Service-backed Spring/BFF server 启用 HTTPS + `SERVER_SSL_CLIENT_AUTH=need`。
- frontend-gateway 作为 nginx mTLS client 访问 admin-bff/portal-bff。
- admin-bff/portal-bff 到后端服务全部切 HTTPS。
- content/task/identity 到 scraper/storage owner call 切 HTTPS。
- Prometheus ServiceMonitor 改为 HTTPS+mTLS scrape。

## 非目标

- 不把 worker-only Pod 的 actuator server 切 mTLS；它们没有稳定 Service DNS。
- 不在本切片排查 PoW 业务 500。
- 不替换生产 CA 或引入生产证书轮换策略。

## 验收

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-internal-servers.ps1 -Execute
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\mtls-internal-servers-live-smoke.ps1

MARK 1/20..16/20 all Service-backed Spring/BFF mTLS readiness passed
MARK 17/20 catalog-service rejected no-client-cert request
MARK 18/20 portal-bff rejected no-client-cert request
MARK 19/20 gateway portal->portal-bff->portal-service passed
MARK 20/20 gateway admin->admin-bff->identity-service passed

ircs-dev deployments Ready 25/25
mTLS certificates Ready 22/22
Prometheus ircs-dev targets 20/20 UP
ResourceQuota: secrets 28/30, pods 28/30, services 25/26
```

## 输出

- `docs/reports/115-02820-internal-server-mtls-rollout.md`
- `scripts/k8s/apply-dev-mtls-internal-servers.ps1`
- `scripts/k8s/apply-dev-mtls-first-slice.ps1`
- `scripts/smoke/mtls-internal-servers-live-smoke.ps1`
- `deploy/k8s/dev/mtls-certificates.yaml`
- `deploy/k8s/dev/frontend-portal-admin-dev.yaml`
- `deploy/k8s/dev/observability-monitoring-dev.yaml`
