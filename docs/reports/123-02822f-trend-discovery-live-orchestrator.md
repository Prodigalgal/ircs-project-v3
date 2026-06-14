# 123 028-22-F trend discovery live orchestrator

日期：2026-06-13

## 目标

把 `028-22-E` 的 trend discovery / credential lease 从实现闭环推进到可重复 live 验证入口：

- 提供统一 smoke 脚本，默认不触发真实 Douban/TMDB 外呼。
- 将该脚本接入 `028-19` production-like smoke orchestrator。
- 推送 task-service / scraper-service / ops-service ARM64 dev 镜像并恢复 `ircs-dev` Ready。
- 验证 task-service discovery internal contract、DB residue cleanup 和 dev manifest 可 apply。

## 实现

- 新增 `scripts/smoke/trend-sync-discovery-02822f-smoke.ps1`。
  - 默认 no-external 模式检查 deployment rollout、task readiness、空 discovery request contract、
    DB data source candidates 和 smoke residue cleanup。
  - `-AllowProviderExternal` 模式预留真实 provider direct discovery 与 scraper trend-sync 调用。
  - 所有外部 provider 写侧行为都必须显式打开；默认只验证安全内部链路。
- `scripts/smoke/production-like-smoke-orchestrator-02819.ps1` 新增批次
  `TREND-02822-F`。
- scraper-service 为 `TmdbCredentialResolver` / `TrendDiscoveryTaskClient` 收敛到单构造器，
  保持生产代码 no `@Autowired` 约束。
- scraper-service 新增 `ScraperOutboundConfiguration`，提供 shared `OutboundHttpClient` bean。
- scraper-service credential token 支持 `APP_SCRAPER_CREDENTIAL_SERVICE_TOKEN` 优先，
  并兼容 `SERVICE_CREDENTIAL_TOKEN` secret key 兜底。
- `deploy/k8s/dev/*-service-dev.yaml` 对 task/scraper/ops 的 Actuator `httpGet` probe
  已与 live deployment 对齐，`kubectl apply` 不再因 `tcpSocket/httpGet` handler 漂移失败。

## Live evidence

Rollout 后运行中 digest：

```text
ircs-task-service    sha256:79476a020223e3c4f1e5e698ddcd99f5bd730ab4a6fe21dd8b58c01e84cd70dc
ircs-scraper-service sha256:44bf8e68e61988e27fce360d45a40c7731b301864ece1bc0a6f99a03f6d47506
ircs-ops-service     sha256:2d2e406a84111c28623faefcc1274ade39f8326e3e3ecaa08ca22231822a1be7
```

scraper credential env 仍来自 `ircs-dev-secrets/SERVICE_CREDENTIAL_TOKEN`，未写入明文。

Manifest apply 验证：

```powershell
kubectl apply -f deploy\k8s\dev\task-service-dev.yaml -f deploy\k8s\dev\scraper-service-dev.yaml -f deploy\k8s\dev\ops-service-dev.yaml
```

结果：task-service / scraper-service / ops-service 均 successfully rolled out。

No-external smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\trend-sync-discovery-02822f-smoke.ps1
```

结果：

```text
MARK 1/6 task/scraper/credential deployments are Ready
MARK 2/6 task-service readiness endpoint reachable
MARK 3/6 task-service trend-discovery internal contract accepts empty request
MARK 4/6 DB data source candidates available and smoke residue is clean
MARK 5/6 provider-gated direct discovery and scraper trend-sync skipped
MARK 6/6 smoke collection task residue cleaned
```

Focused regression：

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test --tests *TrendDiscoveryTaskServiceTest --tests *TrendDiscoveryInternalControllerTest :services:ircs-scraper-service:test --tests *TrendSyncServiceTest --tests *TrendDiscoveryTaskClientTest --tests *TmdbCredentialResolverTest
```

结果：`BUILD SUCCESSFUL in 9s`。

生产代码扫描：

```powershell
rg -n "@Autowired" services shared --glob "!**/src/test/**" --glob "!**/build/**"
```

结果：无输出。

## MARK

```text
MARK F1 PASS trend discovery live smoke script exists with no-external default and provider explicit gate
MARK F2 PASS 028-19 orchestrator exposes TREND-02822-F batch
MARK F3 PASS task/scraper/ops ARM64 images are rolled out and Ready in ircs-dev
MARK F4 PASS dev manifests apply cleanly after probe alignment
MARK F5 PASS no-external task discovery contract and DB residue smoke passes
MARK F6 DEFERRED provider-gated Douban/TMDB live smoke remains explicit external window
```

## 遗留

- 真实 Douban/TMDB provider direct discovery 与 scraper trend-sync 仍需在统一外部依赖窗口用
  `-AllowProviderExternal` 执行。
- 本轮验证了 discovery contract 与 residue cleanup；未把真实 provider 结果写入生产候选结论。
- dev deployment 仍保留低资源 requests/limits，未做生产容量压测。
