# 097 028-06-H RT provider first slice 报告

状态：完成 first slice，full queue parity 后续另拆发布窗口

关联任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `tasks/done/028-06-h-rt-provider-first-slice.md`

## 背景

`028-06-E/F/G` 已完成 Douban provider first slice 与 full queue parity；`028-06` 剩余项收敛为
Rotten Tomatoes provider。V3 已有 `q.fetch.metadata.rt` topic、listener 和 provider gate，
但缺真实 RT provider implementation、credential catalog、错误语义和只读外网 smoke。

## V1 行为对照

V1 `RottenTomatoesMetadataProvider` 行为很窄：

- 请求：`https://www.rottentomatoes.com/search?search={title}`。
- 解析：优先 `search-page-media-row a[slot='title']`，fallback `ul[slot='list'] li a`。
- 匹配：标题 lower/trim 后做 equals/contains，V1 还可走 aggregation similarity。
- 字段：只回填 `rottenTomatoesId`，取 URL path 最后一段 slug。
- enable：受 `RT_ENABLED` 控制，执行前有 traffic delay。
- 异常：V1 捕获异常后返回 empty；V3 first slice 改为可观测的 retryable/terminal error code。

## 实现结果

- `metadata-worker` 新增 `RottenTomatoesMetadataProvider`，以 `ProviderType.ROTTEN_TOMATOES`
  注册到 worker provider list。
- 新增 `RestClientRottenTomatoesHttpClient`，使用 `OutboundHttpPolicy.publicFetch` 发起只读 GET。
- 新增 `RottenTomatoesCredentialRepository`，从 credential-service 读取可选 `ROTTEN_TOMATOES`
  credential；无 credential 或 credential-service 不可用时降级为无 credential public search。
- 新增 `app.metadata.rt.search-url/request-timeout/default-user-agent` 配置。
- `credential-service` catalog 支持 `ROTTEN_TOMATOES` template/create/fingerprint/lease。
- `metadata-worker` 增加 `jsoup` 依赖，与 scraper-service 已有版本 `1.21.2` 对齐，用于结构化 HTML 解析。

## 错误语义

- `2xx`：返回 HTML body；空 body 视为空结果。
- `404`：空结果。
- `429`：`RATE_LIMITED` retryable。
- `5xx`：`UPSTREAM_UNAVAILABLE` retryable。
- `403`：`RT_FORBIDDEN` terminal。
- public outbound policy/IO/interrupt：`RT_OUTBOUND_UNAVAILABLE` 或 `RT_OUTBOUND_INTERRUPTED` retryable。
- HTML parse failure：`PROVIDER_PARSE_ERROR` retryable。

## 测试证据

通过：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

结果：`BUILD SUCCESSFUL in 17s`。

覆盖点：

- RT provider 注册与 Spring production constructors。
- RT search URL 构造、`search-page-media-row` 解析、legacy list fallback、slug 提取。
- 不匹配时返回 empty，blank title unsupported。
- 可选 credential header 传递。
- HTTP `200/404/429/403/5xx`、private URL protection 和 outbound error mapping。
- credential-service `ROTTEN_TOMATOES` template/create/fingerprint/lease/empty payload reject。
- dev gate 仍为 `APP_METADATA_RT_ENABLED=false`。

PowerShell parse check：

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1')); Write-Output 'parse-ok'
```

结果：`parse-ok`。

## 真实外网 smoke

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1
```

最终结果：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
provider=ROTTEN_TOMATOES
externalRequestCount=1
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
credentialLease HTTP 200, leaseCount=0, secretPayloadOmitted=true
```

RT 只读请求：

```text
URL=https://www.rottentomatoes.com/search?search=The%20Matrix
HTTP=200
contentType=text/html; charset=utf-8
timeTotalSeconds=0.385472
sizeDownloadBytes=133641
requestCount=1
rateLimitMode=single readonly GET; no queue; no loop; no retry storm
```

解析结果：

```text
parseStatus=OK
firstTitle=The Matrix
firstHref=https://www.rottentomatoes.com/m/matrix
firstExtractedId=matrix
fullBodyOmitted=true
```

dev gate / runtime：

```text
APP_METADATA_RT_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_LISTENER_ENABLED=true
q.fetch.metadata.rt messages=0 consumers=1
q.fetch.metadata.rt.dlq messages=0 consumers=0
q.enrich.metadata.result messages=0 consumers=1
q.search.sync.raw messages=0 consumers=1
```

## Queue 边界

`q.fetch.metadata.rt` 当前有 consumer，但 live deployment 仍使用上一轮 Douban 镜像 digest；
本轮 RT provider implementation 只在本地代码中完成，尚未构建/推送/rollout 到 `ircs-dev`。
为避免把 first slice 与 live 镜像发布混在同一窗口，本轮不投递 RT provider queue，不 patch
`APP_METADATA_RT_ENABLED=true`。

后续 RT full queue parity 需要单独执行：

1. 构建并推送包含 RT provider 的 ARM64 metadata-worker dev 镜像。
2. rollout 后确认 `q.fetch.metadata.rt consumers=1` 且 provider implementation 存在于 live 镜像。
3. 显式短期开启 `APP_METADATA_RT_ENABLED=true`。
4. 投递单条 `q.fetch.metadata.rt`，验证 provider result、raw metadata、search sync、queue/DLQ、DB/ES cleanup。
5. 恢复 gate/env 并记录 port-forward residue=0。

## MARK

```text
MARK 1/6 V1 RT provider behavior audited
MARK 2/6 provider implementation registered for ROTTEN_TOMATOES
MARK 3/6 credential catalog supports ROTTEN_TOMATOES template/create/fingerprint/lease
MARK 4/6 request/response/error mapping covered by focused tests
MARK 5/6 real Rotten Tomatoes readonly smoke returns HTTP 200 and parses The Matrix -> matrix
MARK 6/6 no write/no queue/no K8S env mutation/no runtime residue; queue full parity deferred to live image rollout slice
```
