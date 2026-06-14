# 028-20-25 mTLS live rollout

状态：done

日期：2026-06-12

## 目标

把 `028-20-10B` 的 K8S mTLS rollout 入口真实执行到 `ircs-dev`，并闭环 rollout、mTLS smoke、service token gate、Prometheus scrape 和门户 BFF 代表路径。

## 范围

- credential-service/search-service server 端启用 HTTPS + `SERVER_SSL_CLIENT_AUTH=need`。
- covered clients 挂载 mTLS Secret 并注入 JVM keyStore/trustStore。
- search-service internal sync API 开启 `APP_SEARCH_SERVICE_REQUIRE_TOKEN=true`。
- Prometheus 使用独立 client cert 抓取 credential/search metrics。
- 新增可重复执行的 mTLS live smoke 脚本。

## 非目标

- 不一次性把所有服务都改成 mTLS server。
- 不替换生产 CA 或正式证书轮换策略。
- 不执行全量业务数据写入 smoke。

## 验收

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-first-slice.ps1 -Execute
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\mtls-first-slice-live-smoke.ps1

MARK 1/5 credential-service mTLS readiness passed
MARK 2/5 search-service mTLS readiness passed
MARK 3/5 no-client-cert request rejected by mTLS
MARK 4/5 credential-service token gate passed with status 200
MARK 5/5 search-service token gate passed without outbox write

ircs-dev deployments Ready 25/25
mTLS certificates Ready 11/11
Prometheus ircs-dev targets 20/20 UP
portal-bff -> search-service suggest HTTP 200
```

## 输出

- `docs/reports/114-02820-mtls-live-rollout.md`
- `scripts/smoke/mtls-first-slice-live-smoke.ps1`
- `deploy/k8s/dev/mtls-certificates.yaml`
- `deploy/k8s/dev/observability-monitoring-dev.yaml`
- `scripts/k8s/apply-dev-mtls-first-slice.ps1`

