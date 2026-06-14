# 096 028-06-G retry Douban provider full queue parity 报告

状态：完成

关联任务：

- `tasks/done/028-06-g-retry-douban-provider-full-queue-parity.md`
- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `tasks/done/028-06-f-douban-provider-live-queue-parity.md`
- `tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md`

## 背景

本次 retry worker 不直接采信上一轮 `028-06-G` 的完成记录，重新检查 `ircs-dev`
metadata-worker live 镜像、manifest、Douban listener/provider consumer 和 full queue smoke。

## 目标

- 检查当前工作目录和 `ircs-dev` metadata-worker live 状态。
- 确认 live 镜像/manifest 是否包含并启用 Douban listener/provider。
- 如缺失，补最小实现或 manifest 并构建/推送 ARM64 metadata-worker dev 镜像后 rollout。
- 复验 `q.fetch.metadata.douban consumers=1`。
- 短期开启 Douban provider gate，低频执行一次 full queue smoke。
- 测试后恢复 `APP_METADATA_DOUBAN_ENABLED=false` 等原 gate，清理 DB/queue/port-forward 残留。

## 非目标

- 不常驻开启 Douban provider。
- 不修改 config-service/content-service C2、notification、storage、portal/admin/BFF。
- 不批量访问 Douban，不绕过访问策略。
- 不在缺少真实证据时标记完成。

## 验收标准

- 记录 changed files、测试命令、live imageID/digest 和是否需要 build/rollout。
- 记录 `q.fetch.metadata.douban consumers=1` 或精确阻塞证据。
- 记录 full queue smoke 的 MARK、DB/ES/queue 结果、错误语义和外网请求数。
- 记录恢复/清理证据：gate/env、queue/DLQ、DB fixture、port-forward。
- 回写本报告、任务文件和总账。

## 执行记录

### 工作目录状态

当前目录没有 `.git`，无法通过 Git 取 clean/dirty 状态：

```text
Test-Path .git => False
git status => fatal: not a git repository (or any of the parent directories): .git
```

本次实际改动仅为 docs/tasks 回写；未修改业务代码、manifest 或 smoke 脚本。

### 本地测试

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
```

结果：`BUILD SUCCESSFUL in 3s`。

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1')); Write-Output 'parse-ok'
```

结果：`parse-ok`。

### live manifest / image / env

K8S context：

```text
kubernetes-admin@kubernetes
```

Deployment image：

```text
harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev
```

初始 pod：

```text
pod=ircs-metadata-worker-56c9646bc6-wzb7c
ready=true
restartCount=0
imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
```

初始 env：

```text
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_RT_ENABLED=false
```

结论：live 镜像已是 `028-06-G` 记录的 digest，且 gate 默认关闭；不需要构建/推送新的
ARM64 metadata-worker 镜像。

### consumer 复验

```text
q.fetch.metadata.douban       0 1
q.fetch.metadata.tmdb         0 1
q.fetch.metadata.rt           0 1
q.enrich.metadata.result      0 1
q.search.sync.raw             0 1
q.fetch.metadata.douban.dlq   0 0
q.enrich.metadata.result.dlq  0 0
q.search.sync.raw.dlq         0 0
```

`q.fetch.metadata.douban consumers=1` 已满足。

### full queue smoke

命令：

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
credential lease HTTP 200, leaseCount=1
externalRequestCount=1
```

外网请求口径：脚本没有 direct Douban 请求；metadata-worker 由一条 provider queue task 触发
一次 Douban suggest GET。

### 恢复与清理

后置 env：

```text
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
deployment "ircs-metadata-worker" successfully rolled out
```

后置 pod：

```text
pod=ircs-metadata-worker-56c9646bc6-z5ngt
ready=true
restartCount=0
imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
```

后置 queue/DLQ：

```text
q.fetch.metadata.douban       0 1
q.enrich.metadata.result      0 1
q.search.sync.raw             0 1
q.fetch.metadata.douban.dlq   0 0
q.enrich.metadata.result.dlq  0 0
q.search.sync.raw.dlq         0 0
```

DB fixture：

```sql
select count(*)
from raw_videos
where id = 'ae066ce8-2992-4aae-9296-03c3ae8b4d76'
   or source_hash like 'codex02806douban%'
   or data_hash like 'codex02806doubandata%';
```

结果：`0`。

Port-forward：未发现匹配 `19077|18091|39202|02806-douban` 的 kubectl port-forward 进程。

HTTPRoute：

```text
No resources found in ircs-dev namespace.
```

## MARK

```text
MARK 1/6 live image/manifest/env inspected; no new build/rollout required
MARK 2/6 q.fetch.metadata.douban consumers=1
MARK 3/6 Douban provider queue task published and drained
MARK 4/6 raw metadata reached SUCCESS with douban_id=1291843
MARK 5/6 ES raw document projection refreshed with Douban enrichment fields
MARK 6/6 restore and cleanup complete
```

## 当前状态

完成。`028-06-G retry` 重新证明当前 live metadata-worker 已包含 Douban listener/provider；
full queue smoke 成功，真实外网请求数为 `1`，gate/env/queue/DB/port-forward 均已恢复或清理。
