# 091 028-06-F Douban provider live queue parity 报告

状态：完成

关联任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `tasks/done/028-06-f-douban-provider-live-queue-parity.md`

上游来源：

- `docs/reports/090-02806-douban-provider-first-slice.md`
- `tasks/done/028-06-e-douban-provider-implementation-first-slice.md`

## 背景

`028-06-E` 已完成 Douban provider first slice，解除
`PROVIDER_IMPLEMENTATION_MISSING` 与 `CREDENTIAL_CATALOG_UNSUPPORTED`。剩余缺口是
`DEV_PROVIDER_GATE_DISABLED`：dev 环境默认关闭 `APP_METADATA_DOUBAN_ENABLED`，还没有证明真实
`q.fetch.metadata.douban -> metadata-worker -> provider -> q.enrich.metadata.result -> raw_videos -> q.search.sync.raw`
队列链路。

## 目标

- 设计并执行安全 live smoke：短期开启 dev metadata-worker 的 Douban provider gate。
- 投递一条最小 Douban metadata provider task，验证 queue drain、provider result、raw metadata、
  search sync 与 error/success 语义。
- 结束后恢复原 gate/env/deployment 状态。
- 清理 smoke fixture，并记录 queue/DB/ES 无残留或明确清理证据。
- 记录真实 Douban 外网请求次数，保持低频、单样本、不循环。

## 非目标

- 不常驻开启 Douban provider gate。
- 不绕过 Douban 访问策略或反爬。
- 不修改 config-service publisher、content-service listener、notification/ops、storage-service、
  portal/admin/BFF。
- 不做批量抓取或 credential/cookie 覆盖场景。

## 方案

- 新增 `scripts/smoke/metadata-provider-douban-live-queue-02806-smoke.ps1`。
- 复用 TMDB live queue smoke 的安全模式：preflight、capture env、显式 patch env、rollout、
  publish one task、wait queues/DB/ES、cleanup、restore。
- 默认不改变 dev gate；只有传入显式开关时才临时设置：
  `APP_METADATA_DOUBAN_ENABLED=true`、`APP_METADATA_TMDB_WORKER_ENABLED=true`、
  `APP_METADATA_LISTENER_ENABLED=true`。
- provider upstream 请求由 metadata-worker 在集群内执行；脚本本身不额外调用 Douban direct
  endpoint，避免重复外网请求。

## 风险

- Douban suggest endpoint 是公网非稳定接口，可能返回 403/429/5xx 或字段变化。
- metadata-worker env patch 会触发 rollout，可能与其他 worker 的 live smoke 冲突。
- 共享 queue 若已有消息，脚本必须默认跳过，避免误清理非本任务消息。
- ES/search-service 未就绪时，provider/DB 成功仍可能卡在 search sync 验证。

## 预期验收标准

- `MARK 1/6` preflight 完成，确认 credential lease diagnostic 与 gate 状态。
- `MARK 2/6` consumers ready，HTTPRoute 仍 absent。
- `MARK 3/6` Douban provider queue task routed and drained。
- `MARK 4/6` raw metadata 到达 `SUCCESS` 且写入 `douban_id`。
- `MARK 5/6` ES raw document 投影 `hasDoubanId=true` 与 externalIds 包含 Douban id。
- `MARK 6/6` queue/DLQ 清空，DB/ES fixture 清理，env 恢复。

## 当前执行记录

### 本地脚本解析

命令：

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1')); Write-Output 'parse-ok'
```

结果：

```text
parse-ok
```

### 默认 diagnostic

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1
```

结果：

```json
{
  "status": "SKIPPED",
  "reason": "DOUBAN_PROVIDER_DISABLED",
  "externalRequestCount": 0,
  "details": {
    "appMetadataDoubanEnabled": "false",
    "appMetadataListenerEnabled": "true",
    "appMetadataTmdbWorkerEnabled": "true"
  }
}
```

### 受控启用 live queue

第一次执行命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1 -EnableDoubanProviderForSmoke -PurgeSmokeQueuesOnCleanup
```

结果：外层 424 秒超时。恢复审计发现 `APP_METADATA_DOUBAN_ENABLED=true` 留存；已立即按原值恢复：

```powershell
kubectl -n ircs-dev set env deployment/ircs-metadata-worker APP_METADATA_DOUBAN_ENABLED=false APP_METADATA_TMDB_WORKER_ENABLED=true APP_METADATA_LISTENER_ENABLED=true
kubectl -n ircs-dev rollout status deployment/ircs-metadata-worker --timeout=240s
```

恢复结果：

```text
deployment "ircs-metadata-worker" successfully rolled out
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
```

脚本随后修正为统一走 `finally` 恢复后再输出结果，避免 `SKIPPED`/`FAILED` 路径直接 `exit`
绕过 restore。

第二次短等待验证命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1 -EnableDoubanProviderForSmoke -WaitAttempts 3 -WaitSeconds 2
```

结果：

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
status=SKIPPED
reason=CONSUMER_UNAVAILABLE
externalRequestCount=0
queues=q.fetch.metadata.douban consumers=0
```

## 阻塞证据

即使 `APP_METADATA_DOUBAN_ENABLED=true`、`APP_METADATA_TMDB_WORKER_ENABLED=true`、
`APP_METADATA_LISTENER_ENABLED=true`，`ircs-dev` RabbitMQ 当前仍只有 TMDB fetch queue 有 consumer：

```text
q.fetch.metadata.douban     0 messages  0 consumers
q.fetch.metadata.tmdb       0 messages  1 consumers
q.fetch.metadata.rt         0 messages  0 consumers
q.enrich.metadata.result    0 messages  1 consumers
q.search.sync.raw           0 messages  1 consumers
```

本地代码中 `MetadataProviderWorker` 已存在 `@RabbitListener(queues = QueueTopic.Names.FETCH_Q_DOUBAN)`，
因此当前阻塞点不是 dev gate，而是 `ircs-dev` metadata-worker 运行态未注册 Douban fetch consumer。
合理推断是当前 `harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev` 镜像尚未包含带 Douban listener/provider
的部署产物，或运行态 listener 注册条件未生效；需要先完成 metadata-worker 镜像发布/rollout 复验。

## 恢复与清理证据

最终 gate/env：

```text
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
deployment "ircs-metadata-worker" successfully rolled out
```

最终 queue/DLQ：

```text
q.fetch.metadata.douban       0 messages  0 consumers
q.fetch.metadata.douban.dlq   0 messages  0 consumers
q.enrich.metadata.result      0 messages  1 consumers
q.enrich.metadata.result.dlq  0 messages  0 consumers
q.search.sync.raw             0 messages  1 consumers
q.search.sync.raw.dlq         0 messages  0 consumers
```

最终 DB smoke residue：

```sql
select count(*) as smoke_residue
from raw_videos
where source_hash like 'codex02806douban%'
   or data_hash like 'codex02806doubandata%'
   or id = '8ded3c16-4a6e-47db-b054-9458c10eb9aa';
```

结果：

```text
0
```

最终 port-forward residue：未发现本脚本端口 `19077|18091|39202` 的 kubectl port-forward 进程。

## MARK

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
MARK 2/6 BLOCKED: q.fetch.metadata.douban consumers=0
MARK 3/6 not run: no Douban queue task published
MARK 4/6 not run: raw metadata success not reached
MARK 5/6 not run: ES raw document projection not reached
MARK 6/6 partial cleanup: env restored, queue/DLQ empty, DB residue 0
```

结论：`028-06-F` 不标记完成；full queue parity 阻塞于 dev metadata-worker 未消费
`q.fetch.metadata.douban`。

## 028-06-G 复跑结果

2026-06-09 后续 `028-06-G` 已解除本报告原阻塞：metadata-worker dev 镜像已发布到
`sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526`，并复验
`q.fetch.metadata.douban consumers=1`。

复跑命令：

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
externalRequestCount=1
```

最终恢复/清理：

```text
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_TMDB_WORKER_ENABLED=true
target queue/DLQ messages=0
DB smoke residue=0
port-forward residue=0
```

本任务完成；镜像发布、constructor 修复和最终证据详见
`docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md`。
