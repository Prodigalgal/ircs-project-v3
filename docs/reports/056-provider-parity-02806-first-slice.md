# 056 provider parity 02806 第一刀报告

状态：完成第一刀，`028-06` 总任务仍进行中

任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

## 背景

`028-06` 需要把 metadata/magnet 从 dev-safe provider 行为推进到真实 provider live parity。
当前可低风险推进的真实切片是 TMDB：metadata worker 已有 `TmdbMetadataProvider`、
credential-service lease、rate limiter 和 HTTP client，且可以在缺少真实 credential 时输出
明确 skip/diagnostic。Douban/RT 真实 provider 与 magnet 真实 provider runner 仍需要后续切片
设计，不在本次第一刀中直接接入公网 crawler。

## 当前实现观察

- metadata dispatcher 已用 `app.metadata.providers.douban-enabled`、
  `app.metadata.providers.tmdb-enabled` 和 `app.metadata.providers.rotten-tomatoes-enabled`
  控制 fan-out。
- metadata provider worker 当前由 `app.metadata.provider-workers.tmdb-enabled` 控制 bean 是否启用；
  启用后同一个 worker 会监听 TMDB、Douban 和 RT fetch queue。
- TMDB provider 已通过 credential-service 获取 `api_key` lease；无 enabled credential 时会抛出
  `POOL_EXHAUSTED` retryable failure。
- magnet service 当前 provider search runner 是 dev-safe fixture，后续真实 runner 需要独立 provider
  enable gate、credential/endpoint gate 和失败分类。

## 第一刀方案

- 在 metadata worker 执行 provider task 时增加 provider enable gate。
- disabled provider task 统一发布终止失败：
  - `errorCode=PROVIDER_DISABLED`
  - `retryable=false`
  - 不调用 provider implementation。
- 保持 enabled TMDB 现有路径不变：
  - 成功时发布 success。
  - credential pool exhausted 仍发布 retryable failure。
  - unsupported context 仍发布 terminal failure。
- 新增 `02806` smoke 脚本，只做真实 diagnostic：
  - 先探测 credential-service lease。
  - 无 credential 或 credential-service 不可用时输出 skip/diagnostic JSON。
  - 有 credential 时调用 TMDB search endpoint，输出真实成功或失败分类。

## 范围

包含：

- `services/ircs-metadata-worker/**`
- `scripts/smoke/*02806*.ps1`
- `docs/reports/*provider*02806*.md`
- `docs/migrations/*provider*parity*.md`
- `tasks/in-progress/028-06-real-provider-live-parity.md`

不包含：

- 新公共 `HTTPRoute`、controller 或 BFF endpoint。
- Douban/RT 真实 provider crawler。
- magnet 真实 provider runner 替换。
- normalization、storage、shared observability、per-service audit、文档索引、K8S 总快照。

## 风险

- disabled provider 历史消息会变成明确终止失败，需要由上游清理误投或旧队列残留。
- credential-service 不可用不能代表 provider 成功，只能作为 diagnostic skip。
- TMDB upstream 可能限速或网络失败，smoke 必须保留真实 HTTP/network 分类。

## 验收标准

- `MetadataProviderWorker` disabled provider task 不调用 provider implementation，并发布
  `PROVIDER_DISABLED` terminal failure。
- TMDB provider enabled 测试继续覆盖 success 与 `POOL_EXHAUSTED` retryable failure。
- `02806` smoke 在无 credential 时输出 skip 证据，在有 credential 时执行真实 TMDB search。

## 验证计划

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-parity-02806-smoke.ps1
```

## 实现结果

- `MetadataProviderWorker` 注入 `MetadataProviderRoutingProperties`，执行 provider task 前先检查
  `app.metadata.providers.*`。
- disabled provider task 现在发布 `PROVIDER_DISABLED` terminal failure，且不会调用 provider
  implementation。
- TMDB enabled 路径保持原有 success、`POOL_EXHAUSTED` retryable failure 和 unsupported context
  terminal failure 语义。
- 新增 `scripts/smoke/metadata-provider-live-parity-02806-smoke.ps1`：
  - 默认探测 `APP_METADATA_CREDENTIAL_SERVICE_BASE_URL` 或 `http://127.0.0.1:18088`。
  - 支持 `-StartCredentialPortForward -UseKubernetesSecretToken` 连接 `ircs-dev` credential-service。
  - credential-service 不可用、HTTP 非 2xx、无 enabled credential 或 credential 无 `api_key` 时输出
    `SKIPPED`/diagnostic JSON。
  - 有 TMDB credential 时调用真实 TMDB search endpoint，输出真实 `SUCCESS` 或 HTTP/network 失败分类。

## 验证结果

- 通过：`.\gradlew.bat :services:ircs-metadata-worker:test`
- 通过：`.\gradlew.bat :services:ircs-magnet-service:test`
- 通过 diagnostic smoke：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-parity-02806-smoke.ps1
```

本机默认端口未启动 credential-service，smoke 输出：

```json
{
  "status": "SKIPPED",
  "reason": "CREDENTIAL_SERVICE_UNAVAILABLE",
  "credentialBaseUrl": "http://127.0.0.1:18088",
  "tmdbBaseUrl": "https://api.themoviedb.org/3",
  "details": {
    "curlExitCode": 7
  }
}
```

该结果是预期 diagnostic，不代表 TMDB provider 成功；有 dev credential 时需使用同一脚本重新获取
`SUCCESS` 或真实 upstream 失败分类。

## 后续 backlog

- Douban 真实 provider task spec、credential/endpoint gate 和 live smoke。
- Rotten Tomatoes 真实 provider task spec、credential/endpoint gate 和 live smoke。
- TMDB queue ack/DLQ live smoke 与 raw metadata/search sync 副作用联动验收。
- magnet provider 真实 runner 替换策略、provider endpoint gate 和失败分类。
- provider run/result 与 DLQ 清理证据归档。
