# 028-06-H RT provider first slice + 真实外网最小 smoke

状态：完成 first slice

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

关联报告：

- `docs/reports/097-02806h-rt-provider-first-slice.md`
- `docs/migrations/091-rt-provider-first-slice.md`

## 目标

- 对照 V1 RT provider 行为，补齐 V3 RT provider first slice。
- 增加 credential-service `ROTTEN_TOMATOES` catalog 最小支持。
- 保持 dev gate 默认关闭。
- 增加 focused tests。
- 执行真实 Rotten Tomatoes 只读最小 smoke。
- 如 full queue 条件不足，记录精确边界。

## 实现结果

- `metadata-worker` 已新增 `RottenTomatoesMetadataProvider`、HTTP client、可选 credential repository 和配置。
- `credential-service` 已支持 `ROTTEN_TOMATOES` template/create/fingerprint/lease。
- RT search page 解析使用 `jsoup`，selector 对齐 V1：`search-page-media-row a[slot=title]`，并保留 legacy list fallback。
- dev manifest 继续保持 `APP_METADATA_RT_ENABLED=false`。
- 新增 `scripts/smoke/metadata-provider-rt-live-02806-smoke.ps1`。

## 验证结果

通过：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

通过：

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1')); Write-Output 'parse-ok'
```

结果：`parse-ok`。

真实外网 smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1
```

结果：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
URL=https://www.rottentomatoes.com/search?search=The%20Matrix
HTTP=200
externalRequestCount=1
firstTitle=The Matrix
firstExtractedId=matrix
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
APP_METADATA_RT_ENABLED=false
credentialLease HTTP 200, leaseCount=0
```

Queue 只读检查：

```text
q.fetch.metadata.rt       messages=0 consumers=1
q.fetch.metadata.rt.dlq   messages=0 consumers=0
q.enrich.metadata.result  messages=0 consumers=1
q.search.sync.raw         messages=0 consumers=1
```

## MARK

```text
MARK 1/6 V1 RT provider behavior audited
MARK 2/6 provider implementation registered for ROTTEN_TOMATOES
MARK 3/6 credential catalog supports ROTTEN_TOMATOES template/create/fingerprint/lease
MARK 4/6 request/response/error mapping covered by focused tests
MARK 5/6 real Rotten Tomatoes readonly smoke returns HTTP 200 and parses The Matrix -> matrix
MARK 6/6 no write/no queue/no K8S env mutation/no runtime residue; queue full parity was deferred here and later closed by 028-06-I
```

## 剩余边界

历史中间态：当时 live metadata-worker 镜像尚未包含本地 RT provider 代码，本轮未构建/推送/rollout
新镜像，也未临时开启 `APP_METADATA_RT_ENABLED=true`。当前该剩余边界已由 `028-06-I` 关闭：
发布包含本切片代码的 ARM64 metadata-worker dev 镜像后，完成单条 queue smoke、raw metadata/search
sync 和完整 cleanup 证据，最终 `MARK 6/6`。
