# 028-06-F Douban provider live queue parity

状态：完成

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

上游来源：

- `docs/reports/090-02806-douban-provider-first-slice.md`
- `tasks/done/028-06-e-douban-provider-implementation-first-slice.md`

## 背景

`028-06-E` 已证明 Douban provider implementation、credential catalog 与最小只读 public suggest
请求可用，但 dev metadata-worker 仍保持 `APP_METADATA_DOUBAN_ENABLED=false`。本任务承接剩余的
`DEV_PROVIDER_GATE_DISABLED`，在受控窗口内临时开启 gate，完成一条真实队列链路 parity smoke，
并恢复原状态。

## 目标

- 新增 Douban live queue smoke 脚本。
- 短期、显式、可恢复地开启 `ircs-dev` metadata-worker Douban provider gate。
- 投递一条最小 `q.fetch.metadata.douban` provider task。
- 验证 provider result、raw metadata、search sync、queue/DLQ、DB/ES cleanup。
- 记录原 env、patch/restore 命令、外网请求次数和最终无残留证据。

## 非目标

- 不常驻开启 Douban provider。
- 不修改 config-service Rabbit publisher、content-service listener、notification/ops、storage-service、
  portal/admin/BFF。
- 不批量访问 Douban，不新增 crawler。
- 不验证 Douban credential/cookie 登录态质量。

## 上游来源

- `docs/reports/090-02806-douban-provider-first-slice.md`
- `tasks/done/028-06-e-douban-provider-implementation-first-slice.md`
- `tasks/in-progress/028-06-real-provider-live-parity.md`

## 输入

- `deploy/k8s/dev/metadata-worker-dev.yaml`
- `services/ircs-metadata-worker/**`
- `q.fetch.metadata.douban`
- `q.enrich.metadata.result`
- `q.search.sync.raw`
- dev PostgreSQL `raw_videos`
- dev RabbitMQ management/CLI
- dev Elasticsearch `ircs_raw_video`

## 输出

- `scripts/smoke/metadata-provider-douban-live-queue-02806-smoke.ps1`
- `docs/reports/091-02806-f-douban-provider-live-queue-parity.md`
- 本任务和父任务回写。

## 影响范围

```text
scripts/smoke/metadata-provider-douban-live-queue-02806-smoke.ps1
deploy/k8s/dev/metadata-worker-dev.yaml
services/ircs-metadata-worker/**
docs/reports/091-02806-f-douban-provider-live-queue-parity.md
tasks/in-progress/028-06-real-provider-live-parity.md
tasks/done/028-06-f-douban-provider-live-queue-parity.md
tasks/done/**
```

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- `postgres-0`、`rabbitmq-0`、`svc/rabbitmq-svc`、`svc/ircs-credential-service`、
  `svc/elasticsearch-svc` 可用。
- metadata-worker 当前镜像已包含 `028-06-E` Douban provider 实现。
- Douban public suggest endpoint 可低频访问。

## 风险

- Douban upstream 可能限速、403、返回空结果或字段漂移。
- metadata-worker rollout 可能与并行 worker 的 smoke 冲突。
- 共享队列存在残留时不能强行判断，应跳过或只在明确授权下清理 smoke queue。

## 验收标准

- 默认 gate disabled 时输出 `SKIPPED/DOUBAN_PROVIDER_DISABLED`，不修改运行态。
- 显式允许时记录原 env，patch 后 rollout 成功。
- 成功路径输出 `MARK 1/6` 到 `MARK 6/6`。
- 外网 Douban 请求数量为 1 条 provider 请求；脚本无额外 direct Douban 请求。
- 完成后 `APP_METADATA_DOUBAN_ENABLED`、`APP_METADATA_TMDB_WORKER_ENABLED`、
  `APP_METADATA_LISTENER_ENABLED` 恢复原值。
- smoke fixture 在 DB/ES 中无残留，相关 queue/DLQ 为空。

## 测试边界

- 本地脚本解析：

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1')); Write-Output 'parse-ok'
```

- 默认 diagnostic：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1
```

- 受控 live queue：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1 -EnableDoubanProviderForSmoke
```

## 关联文档

- `docs/reports/091-02806-f-douban-provider-live-queue-parity.md`

## 关联任务

- 父任务：`tasks/in-progress/028-06-real-provider-live-parity.md`
- 本任务：`tasks/done/028-06-f-douban-provider-live-queue-parity.md`

## 当前状态

完成。smoke 脚本已新增并通过解析；默认 diagnostic 正确识别
`SKIPPED/DOUBAN_PROVIDER_DISABLED` 且 `externalRequestCount=0`。显式短期开启 dev gate 后，
早期阻塞于 `q.fetch.metadata.douban consumers=0`；后续 `028-06-G` 发布修复后的
metadata-worker live 镜像，复验 `q.fetch.metadata.douban consumers=1` 并完成 full queue smoke。

## 实现结果

- 已新增 `scripts/smoke/metadata-provider-douban-live-queue-02806-smoke.ps1`。
- 默认不修改运行态；只有传入 `-EnableDoubanProviderForSmoke` 才 patch metadata-worker env。
- 脚本会捕获并恢复：
  - `APP_METADATA_DOUBAN_ENABLED`
  - `APP_METADATA_TMDB_WORKER_ENABLED`
  - `APP_METADATA_LISTENER_ENABLED`
- 脚本已修正 `SKIPPED`/`FAILED` 控制流：先进入 `finally` 做 cleanup/restore，再输出 JSON 并退出。

## 验证结果

解析检查：

```text
parse-ok
```

默认 diagnostic：

```text
status=SKIPPED
reason=DOUBAN_PROVIDER_DISABLED
externalRequestCount=0
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_LISTENER_ENABLED=true
```

受控启用：

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
status=SKIPPED
reason=CONSUMER_UNAVAILABLE
externalRequestCount=0
queues=q.fetch.metadata.douban consumers=0
```

恢复/清理：

```text
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_LISTENER_ENABLED=true
deployment "ircs-metadata-worker" successfully rolled out
q.fetch.metadata.douban/q.fetch.metadata.douban.dlq/q.enrich.metadata.result/q.search.sync.raw and DLQ messages=0
DB smoke residue=0
本脚本 port-forward residue=0
```

## 阻塞项

当前 `ircs-dev` metadata-worker 运行态未注册 Douban provider fetch consumer：

```text
q.fetch.metadata.douban consumers=0
q.fetch.metadata.tmdb consumers=1
q.enrich.metadata.result consumers=1
q.search.sync.raw consumers=1
```

本地代码已有 `MetadataProviderWorker.onDoubanTask(...)`，所以需要先发布/确认包含 Douban listener/provider
实现的 metadata-worker dev 镜像，或定位 listener 注册条件为什么只注册 TMDB queue。

## MARK

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 Douban provider queue task is published and q.fetch.metadata.douban drains
MARK 4/6 provider result updates raw metadata to SUCCESS with douban_id
MARK 5/6 raw search sync refreshes ES document with Douban enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
```

## 完成补充

`028-06-G` 已发布 metadata-worker dev 镜像并复跑本任务 smoke：

```text
metadata-worker imageDigest=sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
q.fetch.metadata.douban consumers=1
status=SUCCESS
reason=DOUBAN_LIVE_QUEUE_PARITY_OK
externalRequestCount=1
expectedDoubanId=1291843
APP_METADATA_DOUBAN_ENABLED=false restored
queue/DLQ messages=0
DB smoke residue=0
port-forward residue=0
```

关联报告：

- `docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md`
