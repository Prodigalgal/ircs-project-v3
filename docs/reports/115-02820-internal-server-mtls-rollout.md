# 115 - 028-20 internal server mTLS rollout

日期：2026-06-12

## 结论

`ircs-dev` 中所有有 Service DNS、可被内部调用的 Spring/BFF server 已切到 HTTPS + mTLS client cert：

```text
ircs-admin-bff
ircs-catalog-service
ircs-config-service
ircs-content-service
ircs-credential-service
ircs-identity-service
ircs-interaction-service
ircs-magnet-service
ircs-normalization-worker
ircs-ops-service
ircs-portal-bff
ircs-portal-service
ircs-scraper-service
ircs-search-service
ircs-storage-service
ircs-task-service
```

worker-only Pod (`aggregation-worker`, `ingestion-worker`, `metadata-worker`, `notification-worker`) 暂不切 server mTLS；它们没有稳定 Service DNS，Prometheus 仍通过 PodMonitor 走 HTTP Pod IP scrape。它们作为 client 的 mTLS 按需保留。

## 上线内容

- `deploy/k8s/dev/mtls-certificates.yaml`
  - 新增 catalog/config/content/identity/interaction/magnet/portal/scraper/storage/task/frontend-gateway 证书。
  - admin-bff、portal-bff、normalization-worker、ops-service 证书升级为 `server auth` + `client auth` 并补 Service DNS SAN。
- `scripts/k8s/apply-dev-mtls-first-slice.ps1`
  - 扩展为 internal-server mTLS rollout：16 个 Spring/BFF server 开启 `SERVER_SSL_CLIENT_AUTH=need`。
  - BFF 到所有后端 target base URL 切为 HTTPS。
  - content/task/identity 到 scraper/storage 的 owner call 切为 HTTPS。
- `scripts/k8s/apply-dev-mtls-internal-servers.ps1`
  - 新增语义化入口，转发到兼容脚本。
- `deploy/k8s/dev/frontend-portal-admin-dev.yaml`
  - frontend-gateway 挂载 `ircs-frontend-gateway-mtls` client cert。
  - nginx `proxy_pass` 到 admin/portal BFF 改为 HTTPS，并启用 server cert verify。
  - portal frontend SSR `BACKEND_URL` 改为走 frontend-gateway，由 gateway 持证转发到 portal-bff。
- `deploy/k8s/dev/observability-monitoring-dev.yaml`
  - 16 个 Service-backed Spring/BFF target 改为 HTTPS+mTLS ServiceMonitor。
  - 4 个 worker-only target 保持 HTTP PodMonitor。

## 验证证据

```text
script parser passed:
scripts/k8s/apply-dev-mtls-first-slice.ps1
scripts/k8s/apply-dev-mtls-internal-servers.ps1
scripts/smoke/mtls-internal-servers-live-smoke.ps1

server dry-run passed:
deploy/k8s/dev/mtls-certificates.yaml
deploy/k8s/dev/frontend-portal-admin-dev.yaml
deploy/k8s/dev/observability-monitoring-dev.yaml

live rollout:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-internal-servers.ps1 -Execute
all affected deployments successfully rolled out

live smoke:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\mtls-internal-servers-live-smoke.ps1
MARK 1/20..16/20 all Service-backed Spring/BFF mTLS readiness passed
MARK 17/20 catalog-service rejected no-client-cert request
MARK 18/20 portal-bff rejected no-client-cert request
MARK 19/20 gateway portal->portal-bff->portal-service passed
MARK 20/20 gateway admin->admin-bff->identity-service passed

cluster:
ircs-dev deployments Ready 25/25
mTLS certificates Ready 22/22
ResourceQuota: secrets 28/30, pods 28/30, services 25/26

Prometheus:
ircs-dev targets 20/20 UP
16 Service-backed targets scrape via https://.../actuator/prometheus
4 worker-only targets scrape via http://pod-ip:8080/actuator/prometheus
```

## 遗留风险

- `GET /api/v1/auth/pow?scope=admin.login` 经 gateway/admin-bff/identity-service 当前返回 500；本轮已用 admin login 空体 400 验证 mTLS 传输链路，PoW 业务错误需后续单独排查。
- 当前 dev namespace Secret 配额使用已到 `28/30`；后续再增加 mTLS secret 前需要先清理或提高 quota。
- 生产环境仍需替换为生产 CA、证书轮换告警和正式 trust domain 策略。
