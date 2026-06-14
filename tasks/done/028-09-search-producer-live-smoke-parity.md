# 028-09 search producer live smoke parity 后续子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/migrations/050-search-producer-matrix-parity.md`
- `docs/migrations/052-normalization-raw-relation-parity.md`

关联报告：

- `docs/reports/055-search-producer-live-smoke-02809-normalization.md`
- `docs/reports/058-search-producer-live-smoke-02809-metadata-result.md`
- `docs/reports/064-search-producer-aggregation-live-smoke-02809.md`
- `docs/reports/065-search-producer-old-unified-dirty-lifecycle-02809.md`
- `docs/migrations/056-search-producer-normalization-live-smoke.md`
- `docs/migrations/058-search-producer-metadata-result-live-smoke.md`
- `docs/migrations/064-search-producer-aggregation-live-smoke.md`
- `docs/migrations/065-search-producer-old-unified-dirty-lifecycle.md`

## 背景

`028-05-D` 已建立 raw/unified search producer matrix，并用 content image unlink live
smoke 证明 cover 清空链路。`028-09` 将 producer path 拆成业务场景 live smoke；当前
normalization、metadata result 与 aggregation 已完成，old unified dirty lifecycle 仍需
按业务场景做 live smoke，避免“producer 存在但副作用未实证”。

`028-05-F` 已完成 normalization raw relation 本地测试，明确将 live queue smoke 后置。本轮
低风险第一刀优先验证已有 `ircs-normalization-worker` producer path：消费
`q.normalize.video`，成功写入 raw relation/READY 后发布 legacy UUID raw search sync，并由
`ircs-search-service` 刷新 `ircs_raw_video`。

## 目标

- 为 normalization -> raw search sync 做一个最小 live smoke 脚本候选并执行。
- 验证 `q.normalize.video`、`q.search.sync.raw` 和 DLQ 清理边界。
- 验证 raw relation DB 副作用与 ES raw document 刷新。
- 明确 old unified dirty lifecycle 仍为后续切片。

## 非目标

- 不替换 search-service 索引模型。
- 不执行全量重建。
- 不做 ES 性能压测。
- 不修改服务代码、K8S 清单、HTTPRoute 或公共路由。
- 不覆盖 old unified dirty lifecycle 的 live smoke 实现。
- 不修改 normalization/metadata/magnet/storage/shared observability、`docs/index.md`、
  `tasks/index.md` 或 K8S 总快照。

## 输入

- `q.normalize.video`
- `q.search.sync.raw`
- `raw_videos.raw_metadata`
- `raw_genres`、`raw_languages`、`raw_areas`
- `video_raw_genres`、`video_raw_languages`、`video_raw_areas`
- Elasticsearch dev index `ircs_raw_video`

## 输出

- `scripts/smoke/search-producer-normalization-02809-smoke.ps1`
- `docs/reports/055-search-producer-live-smoke-02809-normalization.md`
- `docs/migrations/056-search-producer-normalization-live-smoke.md`
- 本任务状态回写。

## 影响范围

包含：

```text
scripts/smoke/*02809*.ps1
docs/reports/*search*02809*.md
tasks/done/028-09-search-producer-live-smoke-parity.md
```

不包含：

```text
services/**
deploy/**
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
normalization/metadata/magnet/storage/shared observability 代码
```

## 依赖

- `ircs-dev` namespace 可访问。
- PostgreSQL `postgres-0` 可执行 `psql`。
- RabbitMQ management 可通过 port-forward 访问。
- Elasticsearch 可通过 port-forward 访问。
- `ircs-normalization-worker` 和 `ircs-search-service` 对应 listener 已启用且 Ready。
- 当前 dev 镜像已包含 `028-05-F` 的 normalization raw relation 代码。

## 风险

- dev 集群镜像若未包含 `028-05-F`，脚本会停在 DB relation 或 ES 刷新验证。
- search producer 使用 legacy UUID payload，依赖 search-service legacy parser。
- fixture 写入共享 PostgreSQL raw relation 表，清理必须覆盖 join 表、raw relation 表、
  raw video 和 ES document。
- 如果其他 agent 正在消费同一队列，脚本只能通过唯一 `stamp` 和 fixture ID 做隔离，不能
  假设全局业务队列长期为空。

## 验收标准

- 脚本语法检查通过。
- live smoke 可输出以下 MARK：

```text
MARK 1/5 normalization worker consumes q.normalize.video and marks raw video READY
MARK 2/5 normalization writes normalized raw fields from raw_metadata without triggering aggregation
MARK 3/5 normalization publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 4/5 normalize/search queues and DLQs are empty for the smoke window
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

- 若未执行 live smoke，报告必须说明前置条件与未执行原因。
- 不新增公共 HTTPRoute，不修改服务代码。

## 测试边界

- 静态检查：

```text
powershell -NoProfile -ExecutionPolicy Bypass -Command "$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-normalization-02809-smoke.ps1' -Raw)); 'syntax ok'"
```

- live smoke 候选：

```text
.\scripts\smoke\search-producer-normalization-02809-smoke.ps1
```

## 当前状态

2026-06-07：

- 已读取 `AGENTS.md` 并确认本轮仅使用本地 docs/tasks，不使用线上 SaaS。
- 已读取 `049` producer matrix、`050` migration、`051/052` normalization parity、
  `028-05-D/F` 任务和 search-service consumer/parser。
- 已选定低风险第一刀：normalization -> raw search sync live smoke candidate。
- 已新增并执行 `scripts/smoke/search-producer-normalization-02809-smoke.ps1`。
- 已完成 live smoke：

```text
MARK 1/5 normalization worker consumes q.normalize.video and marks raw video READY
MARK 2/5 normalization writes normalized raw fields from raw_metadata without triggering aggregation
MARK 3/5 normalization publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 4/5 normalize/search queues and DLQs are empty for the smoke window
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

- 首次尝试暴露两个边界：
  - dev 集群中的 normalization-worker 已能写 READY/基础字段并发布 search sync，但 raw
    relation 计数为 `0|0|0`，说明当前 dev 镜像未实证 `028-05-F` relation 写入。
  - `aggregation_status='PENDING'` 的 fixture 会被 aggregation scheduler 抢走并创建
    unified 绑定；最终脚本改为 `aggregation_status='BOUND'`，避免依赖其他 worker。
- 服务代码保持只读，不新增 HTTPRoute。

## 剩余子任务

- metadata result -> raw search sync live smoke（已完成）。
- aggregation -> unified search sync live smoke（已完成）。
- old unified dirty lifecycle live smoke（已完成）。
- search-service legacy UUID payload parser 的 consumer 级结论已由本轮 normalization live smoke
  间接验证；如需 parser 独立 smoke，可后续拆为更小任务。

## 子任务 spec：old unified dirty lifecycle live smoke

状态：已完成

背景：

`RawVideoAdminService.update()` 对 raw critical 字段变化采用 dirty lifecycle：旧
`unified_videos` 先被标记为 `DIRTY`，raw 从旧 unified 解绑并回到
`aggregation_status='PENDING'`，raw 搜索文档立即刷新；旧 unified 搜索文档不在 raw update
阶段直接刷新，而是等待 recalculate/aggregation 关闭 dirty lifecycle。`028-09` 需要用
live smoke 验证这条当前 V3 语义。

目标：

- 使用 content-service HTTP endpoint 触发 raw critical update，不用 SQL 伪造业务动作。
- 验证 old unified `metadata_status` 到 `DIRTY`，并且 raw/unified 绑定被删除。
- 验证 raw search INDEX 刷新 `ircs_raw_video`。
- 验证 old unified 在 recalculate 前保持 dirty candidate。
- 通过 `POST /api/v1/unified-videos/{id}/recalculate` 验证 old unified 回到 `SYNCED` 并刷新
  `ircs_unified_video`。
- 验证队列/DLQ、HTTPRoute、DB/ES residue。

非目标：

- 不修改 raw update 后 old unified 是否立即发布 search sync 的当前设计。
- 不修改 content-service、search-service、shared contracts 或 K8S manifest。
- 不 rollout 镜像。
- 不覆盖 aggregation 新绑定链路，该链路已由 `064` 完成。

上游来源：

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/064-search-producer-aggregation-live-smoke-02809.md`
- `docs/reports/065-search-producer-old-unified-dirty-lifecycle-02809.md`
- `docs/migrations/065-search-producer-old-unified-dirty-lifecycle.md`

输入：

- `PUT /api/v1/raw-videos/{id}`
- `POST /api/v1/unified-videos/batch/sync-search`
- `POST /api/v1/unified-videos/{id}/recalculate`
- PostgreSQL `raw_videos`
- PostgreSQL `unified_videos`
- PostgreSQL `raw_video_unified_video`
- `q.search.sync.raw`
- `q.search.sync.unified`
- Elasticsearch `ircs_raw_video`
- Elasticsearch `ircs_unified_video`

输出：

- `scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1`
- `docs/reports/065-search-producer-old-unified-dirty-lifecycle-02809.md`
- `docs/migrations/065-search-producer-old-unified-dirty-lifecycle.md`
- 本任务状态回写。

影响范围：

包含：

```text
scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1
docs/reports/065-search-producer-old-unified-dirty-lifecycle-02809.md
docs/migrations/065-search-producer-old-unified-dirty-lifecycle.md
tasks/done/028-09-search-producer-live-smoke-parity.md
```

不包含：

```text
services/**
shared/**
deploy/**
```

依赖：

- `ircs-dev` namespace 可访问。
- PostgreSQL `postgres-0` 可执行 `psql`。
- content-service ClusterIP 可通过 port-forward 访问。
- Elasticsearch 可通过 port-forward 访问。
- `ircs-search-service` 的 `APP_SEARCH_LISTENER_ENABLED=true`，且
  `q.search.sync.raw`、`q.search.sync.unified` 有 consumer。

风险：

- raw critical update 后 raw 变为 `PENDING`，aggregation scheduler 可能随后处理 fixture；
  脚本必须使用唯一外部 ID 并在 cleanup 覆盖潜在新 unified。
- 如果目标队列或 DLQ 已有堆积，脚本必须 `SKIPPED`，不能误判其他任务消息为本轮结果。
- 本切片证明当前 dirty lifecycle，不代表立即刷新 old unified ES。

验收标准：

- PowerShell scriptblock parse check 通过。
- 条件满足时 live smoke 输出以下 MARK：

```text
MARK 1/6 old unified fixture is seeded and initial ES unified document has sourceCount=1
MARK 2/6 raw critical update marks old unified DIRTY and unbinds raw
MARK 3/6 raw critical update publishes raw search INDEX and refreshes ircs_raw_video
MARK 4/6 old unified remains a dirty lifecycle candidate until recalculate closes it
MARK 5/6 unified recalculate publishes q.search.sync.unified and refreshes ircs_unified_video
MARK 6/6 search queues/DLQs are clean and cleanup leaves no DB/ES fixture residue
```

- 条件不满足时输出结构化 `SKIPPED`，报告记录原因。
- 不修改服务代码，不新增 HTTPRoute，不 rollout。

测试边界：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
```

```text
.\scripts\smoke\search-producer-old-unified-dirty-02809-smoke.ps1
```

完成记录：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

```text
.\scripts\smoke\search-producer-old-unified-dirty-02809-smoke.ps1

MARK 1/6 old unified fixture is seeded and initial ES unified document has sourceCount=1
MARK 2/6 raw critical update marks old unified DIRTY and unbinds raw
MARK 3/6 raw critical update publishes raw search INDEX and refreshes ircs_raw_video
MARK 4/6 old unified remains a dirty lifecycle candidate until recalculate closes it
MARK 5/6 unified recalculate publishes q.search.sync.unified and refreshes ircs_unified_video
MARK 6/6 search queues/DLQs are clean and cleanup leaves no DB/ES fixture residue
```

后置检查：

```text
DB residue=0|0
q.search.sync.raw=0 messages, 1 consumer
q.search.sync.raw.dlq=0 messages
q.search.sync.unified=0 messages, 1 consumer
q.search.sync.unified.dlq=0 messages
HTTPRoute=none
ResourceQuota pods=28/30, services=24/26
port-forward residue=none
```

服务代码、`shared/**`、K8S manifest、HTTPRoute 均保持未修改。`028-09` 已完成
normalization、metadata result、aggregation 和 old unified dirty lifecycle 四条 producer
live smoke parity。

## 子任务 spec：metadata result -> raw search sync live smoke

状态：已完成

背景：

`006` migration 已证明 metadata result collector 能消费 `q.enrich.metadata.result` 并发布 raw
search sync；`028-09` 需要把这条 producer path 纳入统一 live smoke parity，使用当前 dev 集群和
search-service consumer 实证 `metadata.result -> q.search.sync.raw -> ircs_raw_video`。

目标：

- 新增可运行 smoke 脚本，直接投递 `EnrichmentResultMsg` JSON 到 `x.process/metadata.result`。
- 验证 `ircs-metadata-worker` 将 provider result 落到 `raw_videos`，并在 final result 后发布 raw search sync。
- 验证 `ircs-search-service` 消费 legacy UUID raw sync 后刷新 `ircs_raw_video`。
- 验证 DB/ES fixture cleanup、`q.enrich.metadata.result`/`q.search.sync.raw` 及 DLQ 状态。
- 做 HTTPRoute 只读检查，确认本轮未新增公共入口。

非目标：

- 不调用真实 TMDB/Douban/RT provider，不验证 provider upstream 和 credential lease。
- 不修改 `services/**`、`shared/**`、K8S manifest、HTTPRoute 或 rollout 状态。
- 不覆盖 aggregation -> unified search sync、old unified dirty lifecycle。
- 不重建 ES index，不做性能压测。

上游来源：

- `docs/migrations/006-metadata-worker-result-extraction.md`
- `docs/reports/055-search-producer-live-smoke-02809-normalization.md`
- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/migrations/050-search-producer-matrix-parity.md`

输入：

- `x.process` / routing key `metadata.result`
- `q.enrich.metadata.result`
- `q.search.sync.raw`
- PostgreSQL `raw_videos`
- Elasticsearch `ircs_raw_video`

输出：

- `scripts/smoke/search-producer-metadata-result-02809-smoke.ps1`
- `docs/reports/058-search-producer-live-smoke-02809-metadata-result.md`
- `docs/migrations/058-search-producer-metadata-result-live-smoke.md`
- 本任务状态回写

影响范围：

包含：

```text
scripts/smoke/*02809*metadata*.ps1
docs/reports/*02809*metadata*.md
docs/migrations/*search*metadata*.md
tasks/done/028-09-search-producer-live-smoke-parity.md
```

不包含：

```text
services/**
shared/**
deploy/**
docs/index.md
tasks/index.md
父任务
K8S 快照
```

依赖：

- `ircs-dev` namespace 可访问。
- PostgreSQL `postgres-0` 可执行 `psql`。
- RabbitMQ management 可通过 port-forward 访问。
- Elasticsearch 可通过 port-forward 访问。
- `ircs-metadata-worker` 的 `APP_METADATA_LISTENER_ENABLED=true` 且 `q.enrich.metadata.result` 有 consumer。
- `ircs-search-service` 的 `APP_SEARCH_LISTENER_ENABLED=true` 且 `q.search.sync.raw` 有 consumer。

风险：

- 如果 dev 集群 listener 未启用或队列已有堆积，脚本必须输出 `SKIPPED`/diagnostic，不能伪造 MARK。
- `metadataUpdated=true` 会把 fixture 的 `aggregation_status` 改为 `PENDING`；脚本使用
  `normalization_status='PENDING'` 隔离 aggregation scheduler，cleanup 仍覆盖潜在 raw/unified 绑定。
- 多 worker 并行时共享队列不保证全局长期为空；脚本只断言执行窗口内目标队列/DLQ最终为空。

验收标准：

- PowerShell scriptblock parse check 通过。
- 条件满足时 live smoke 输出以下 MARK：

```text
MARK 1/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 2/6 metadata result fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 metadata result consumer updates raw_videos to SUCCESS
MARK 4/6 metadata result publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 5/6 metadata/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

- 条件不满足时输出结构化 `SKIPPED`，报告记录原因。
- 不修改服务代码，不新增 HTTPRoute，不 rollout。

测试边界：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-metadata-result-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
```

```text
.\scripts\smoke\search-producer-metadata-result-02809-smoke.ps1
```

完成记录：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-metadata-result-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

```text
.\scripts\smoke\search-producer-metadata-result-02809-smoke.ps1

MARK 1/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 2/6 metadata result fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 metadata result consumer updates raw_videos to SUCCESS
MARK 4/6 metadata result publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 5/6 metadata/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

服务代码、`shared/**`、K8S manifest、HTTPRoute、`docs/index.md`、`tasks/index.md` 均保持未修改。

## 子任务 spec：aggregation -> unified search sync live smoke

状态：已完成

背景：

`049` producer matrix 已确认 `AggregationService.aggregateBatch()` 对 `BOUND` 结果发布
raw/unified 两路 legacy UUID search sync；但该结论仍需在 `ircs-dev` 中用真实
`ircs-aggregation-worker` scheduler、RabbitMQ 和 Elasticsearch 端到端实证。`028-09`
已完成 normalization 与 metadata result 两条 raw producer live smoke，本切片继续覆盖
aggregation 新建/绑定 unified 的 producer path。

目标：

- 新增可运行 smoke 脚本，seed 一个唯一 raw fixture，满足 aggregation scheduler eligibility。
- 验证 scheduler 将 raw 从 `PENDING/PROCESSING` 推进到 `BOUND`。
- 验证创建或绑定 `unified_videos` 与 `raw_video_unified_video`。
- 验证 legacy UUID raw/unified search sync 被 search-service 消费，并刷新
  `ircs_raw_video` 与 `ircs_unified_video`。
- 验证队列/DLQ、HTTPRoute、DB/ES residue。

非目标：

- 不修改 aggregation-worker、search-service、shared contracts 或 K8S manifest。
- 不 rollout 镜像。
- 不验证 old unified dirty lifecycle。
- 不重建 ES index，不做性能压测。

上游来源：

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/058-search-producer-live-smoke-02809-metadata-result.md`
- `docs/migrations/064-search-producer-aggregation-live-smoke.md`
- `docs/reports/064-search-producer-aggregation-live-smoke-02809.md`

输入：

- PostgreSQL `raw_videos`
- PostgreSQL `unified_videos`
- PostgreSQL `raw_video_unified_video`
- `q.search.sync.raw`
- `q.search.sync.unified`
- Elasticsearch `ircs_raw_video`
- Elasticsearch `ircs_unified_video`

输出：

- `scripts/smoke/search-producer-aggregation-02809-smoke.ps1`
- `docs/reports/064-search-producer-aggregation-live-smoke-02809.md`
- `docs/migrations/064-search-producer-aggregation-live-smoke.md`
- 本任务状态回写。

影响范围：

包含：

```text
scripts/smoke/search-producer-aggregation-02809-smoke.ps1
docs/reports/064-search-producer-aggregation-live-smoke-02809.md
docs/migrations/064-search-producer-aggregation-live-smoke.md
tasks/done/028-09-search-producer-live-smoke-parity.md
```

不包含：

```text
services/**
shared/**
deploy/**
old unified dirty lifecycle
```

依赖：

- `ircs-dev` namespace 可访问。
- PostgreSQL `postgres-0` 可执行 `psql`。
- RabbitMQ management 可通过 port-forward 访问。
- Elasticsearch 可通过 port-forward 访问。
- `ircs-aggregation-worker` scheduler enabled 且 Ready。
- `ircs-search-service` 的 `APP_SEARCH_LISTENER_ENABLED=true`，且
  `q.search.sync.raw`、`q.search.sync.unified` 有 consumer。

风险：

- aggregation scheduler 为轮询模型，脚本需要等待 `fixed-delay-ms` 窗口。
- 如果目标队列或 DLQ 已有堆积，脚本必须 `SKIPPED`，不能误判其他任务消息为本轮结果。
- fixture 会创建 unified video，cleanup 必须覆盖 raw/unified/join/关系表和 ES 两个 index。
- 本切片只覆盖新绑定链路，不代表 old unified dirty lifecycle 已完成。

验收标准：

- PowerShell scriptblock parse check 通过。
- 条件满足时 live smoke 输出以下 MARK：

```text
MARK 1/6 aggregation/search consumers are ready and HTTPRoute remains absent
MARK 2/6 aggregation fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 aggregation scheduler binds raw video to unified video and marks BOUND
MARK 4/6 aggregation publishes raw/unified legacy UUID search sync and ES documents are refreshed
MARK 5/6 aggregation/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

- 条件不满足时输出结构化 `SKIPPED`，报告记录原因。
- 不修改服务代码，不新增 HTTPRoute，不 rollout。

测试边界：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-aggregation-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
```

```text
.\scripts\smoke\search-producer-aggregation-02809-smoke.ps1
```

完成记录：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-aggregation-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

```text
.\scripts\smoke\search-producer-aggregation-02809-smoke.ps1

MARK 1/6 aggregation/search consumers are ready and HTTPRoute remains absent
MARK 2/6 aggregation fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 aggregation scheduler binds raw video to unified video and marks BOUND
MARK 4/6 aggregation publishes raw/unified legacy UUID search sync and ES documents are refreshed
MARK 5/6 aggregation/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

后置检查：

```text
DB residue=0|0
q.search.sync.raw=0 messages, 1 consumer
q.search.sync.raw.dlq=0 messages
q.search.sync.unified=0 messages, 1 consumer
q.search.sync.unified.dlq=0 messages
HTTPRoute=none
ResourceQuota pods=28/30, services=24/26
port-forward residue=none
```

服务代码、`shared/**`、K8S manifest、HTTPRoute 均保持未修改。old unified dirty
lifecycle 已在 `065` 子切片完成，`028-09` 整体已归档。
