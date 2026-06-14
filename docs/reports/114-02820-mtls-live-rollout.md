# 114 - 028-20 mTLS live rollout

日期：2026-06-12

## 结论

`ircs-dev` 已完成真实 mTLS first slice 上线。

- `credential-service` 与 `search-service` 已启用 Spring Boot HTTPS + `SERVER_SSL_CLIENT_AUTH=need`。
- metadata-worker、notification-worker、normalization-worker、admin-bff、ops-service、portal-bff、aggregation-worker 已挂载独立 client cert，并通过 JVM keyStore/trustStore 使用 mTLS。
- `search-service` internal sync API 已同步开启 `APP_SEARCH_SERVICE_REQUIRE_TOKEN=true`，并使用新建的 `SERVICE_SEARCH_TOKEN` Secret key，不再依赖旧共享 internal/admin token。
- K8S probe 使用 TCP probe，业务 mTLS 由 live smoke 验证，避免 kubelet 因无 client cert 误判服务不可用。
- Prometheus 已作为受控 mTLS client 接入 credential/search metrics，IRCS targets 恢复 20/20 UP。

## 影响范围

- `deploy/k8s/dev/mtls-certificates.yaml`
- `deploy/k8s/dev/observability-monitoring-dev.yaml`
- `scripts/k8s/apply-dev-mtls-first-slice.ps1`
- `scripts/smoke/mtls-first-slice-live-smoke.ps1`
- `tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`
- `tasks/done/028-20-25-mtls-live-rollout.md`
- `tasks/index.md`

## 上线动作

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-first-slice.ps1 -Execute
kubectl apply -f deploy\k8s\dev\mtls-certificates.yaml -f deploy\k8s\dev\observability-monitoring-dev.yaml
kubectl -n ircs-dev wait certificate/ircs-prometheus-scraper-mtls --for=condition=Ready --timeout=180s
```

脚本执行时如果 `ircs-dev-secrets` 缺少 `SERVICE_SEARCH_TOKEN`，会生成随机 token 并只写入 K8S Secret，不写入 repo，不打印 secret value。

## 验证证据

```text
mTLS smoke:
MARK 1/5 credential-service mTLS readiness passed
MARK 2/5 search-service mTLS readiness passed
MARK 3/5 no-client-cert request rejected by mTLS
MARK 4/5 credential-service token gate passed with status 200
MARK 5/5 search-service token gate passed without outbox write

rollout:
ircs-credential-service successfully rolled out
ircs-search-service successfully rolled out
ircs-metadata-worker successfully rolled out
ircs-notification-worker successfully rolled out
ircs-normalization-worker successfully rolled out
ircs-admin-bff successfully rolled out
ircs-ops-service successfully rolled out
ircs-portal-bff successfully rolled out
ircs-aggregation-worker successfully rolled out

cluster:
ircs-dev deployments Ready 25/25
mTLS certificates Ready 11/11
Prometheus ircs-dev targets 20/20 UP
credential-service scrape URL: https://10.244.0.172:8080/actuator/prometheus
search-service scrape URL: https://10.244.0.11:8080/actuator/prometheus

BFF path:
portal-bff -> search-service suggest returned HTTP 200 with []
```

## 设计补充

Prometheus 不放宽 mTLS。新增 `ircs-prometheus-scraper-mtls` client certificate，并将 ServiceMonitor 拆分：

- 普通 HTTP ServiceMonitor 排除 credential/search。
- credential-service 独立 HTTPS ServiceMonitor，指定 `serverName=ircs-credential-service.ircs-dev.svc.cluster.local`。
- search-service 独立 HTTPS ServiceMonitor，指定 `serverName=ircs-search-service.ircs-dev.svc.cluster.local`。

## 遗留风险

- 当前 mTLS first slice 只强制 credential-service/search-service server 端；其余服务作为 client 已接入，但还未全服务 server 端强制 mTLS。
- metadata/notification/normalization/admin-bff/ops/aggregation 的所有业务调用场景没有逐个触发完整数据路径；本轮已验证基础 TLS、token gate、portal-bff -> search 和 Prometheus scrape。
- 生产环境需要换成生产 CA/证书生命周期策略，并明确证书轮换窗口与告警。

