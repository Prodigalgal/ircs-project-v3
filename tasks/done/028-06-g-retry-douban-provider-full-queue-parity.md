# 028-06-G retry Douban provider full queue parity

状态：完成

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

上游来源：

- `docs/reports/091-02806-f-douban-provider-live-queue-parity.md`
- `docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md`
- `tasks/done/028-06-f-douban-provider-live-queue-parity.md`
- `tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md`

## 背景

`028-06-E` 已完成 Douban provider first slice；`028-06-F` 早期曾阻塞于
`q.fetch.metadata.douban consumers=0`。本地已有 `028-06-G` 完成记录，但本次 retry
按当前 `ircs-dev` 真实状态重新复验，不直接复用上一 worker 结论。

## 目标

- 检查当前工作目录状态和 `ircs-dev` metadata-worker live 镜像/manifest/env。
- 确认 Douban listener/provider 是否已在 live pod 注册。
- 如缺失，最小修复 metadata-worker 或 dev manifest，并构建/推送 ARM64 dev 镜像后 rollout。
- 复验 `q.fetch.metadata.douban consumers=1`。
- 显式短期开启 `APP_METADATA_DOUBAN_ENABLED=true`，执行一次 full queue smoke。
- 恢复原 gate/env/deployment 状态，清理 DB/queue/port-forward 残留。

## 非目标

- 不常驻开启 Douban provider gate。
- 不修改 config-service/content-service C2、notification、storage、portal/admin/BFF。
- 不批量访问 Douban，不新增 crawler。
- 不做无关重构。

## 影响范围

```text
docs/reports/096-02806g-retry-douban-provider-full-queue-parity.md
docs/reports/093-v1-v3-parity-progress-ledger.md
docs/index.md
tasks/index.md
tasks/in-progress/028-06-real-provider-live-parity.md
tasks/done/028-06-g-retry-douban-provider-full-queue-parity.md
```

## 实现结果

- 未修改业务代码、K8S manifest 或 smoke 脚本。
- live manifest 使用 `harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev`。
- live pod imageID 已是：

```text
harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
```

- 初检已满足 `q.fetch.metadata.douban consumers=1`，因此未构建、未推送新镜像、未执行额外 rollout。
- full queue smoke 期间脚本短期开启 Douban gate 并由 `finally` 恢复。

## 验证结果

当前目录没有 `.git`，`git status` 返回：

```text
fatal: not a git repository (or any of the parent directories): .git
```

本地测试：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
```

结果：`BUILD SUCCESSFUL in 3s`。

脚本解析：

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1')); Write-Output 'parse-ok'
```

结果：`parse-ok`。

初始 live env：

```text
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
```

初始 queue：

```text
q.fetch.metadata.douban 0 1
q.fetch.metadata.tmdb   0 1
q.fetch.metadata.rt     0 1
q.enrich.metadata.result 0 1
q.search.sync.raw       0 1
```

full queue smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1 -EnableDoubanProviderForSmoke -WaitAttempts 45 -WaitSeconds 2
```

结果：

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 Douban provider queue task is published and q.fetch.metadata.douban drains
MARK 4/6 provider result updates raw metadata to SUCCESS with douban_id
MARK 5/6 raw search sync refreshes ES document with Douban enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
status=SUCCESS
reason=DOUBAN_LIVE_QUEUE_PARITY_OK
rawVideoId=ae066ce8-2992-4aae-9296-03c3ae8b4d76
expectedDoubanId=1291843
externalRequestCount=1
```

恢复与清理：

```text
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
deployment "ircs-metadata-worker" successfully rolled out
q.fetch.metadata.douban messages=0 consumers=1
q.fetch.metadata.douban.dlq messages=0 consumers=0
q.enrich.metadata.result messages=0 consumers=1
q.enrich.metadata.result.dlq messages=0 consumers=0
q.search.sync.raw messages=0 consumers=1
q.search.sync.raw.dlq messages=0 consumers=0
DB smoke residue=0
port-forward residue=0
HTTPRoute=No resources found in ircs-dev namespace.
```

最终 pod：

```text
pod=ircs-metadata-worker-56c9646bc6-z5ngt
ready=true
restartCount=0
imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
```

## MARK

```text
MARK 1/6 live image/manifest/env inspected; no new build/rollout required
MARK 2/6 q.fetch.metadata.douban consumers=1
MARK 3/6 Douban provider queue task published and drained
MARK 4/6 raw metadata reached SUCCESS with douban_id=1291843
MARK 5/6 search sync refreshed ES projection with Douban enrichment fields
MARK 6/6 gate restored and DB/queue/port-forward cleanup verified
```

## 当前状态

完成。`028-06-G retry` 复验通过；`028-06` 父任务剩余范围仍是 RT provider 独立切片。
