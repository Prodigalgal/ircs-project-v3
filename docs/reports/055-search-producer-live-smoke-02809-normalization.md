# 055 search producer live smoke 02809 normalization 报告

状态：已完成（normalization -> raw search sync live smoke 第一刀）

更新时间：2026-06-07

关联任务：

- `tasks/in-progress/028-09-search-producer-live-smoke-parity.md`

上游来源：

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/migrations/050-search-producer-matrix-parity.md`
- `docs/migrations/052-normalization-raw-relation-parity.md`

## 背景

`049` producer matrix 已确认 normalization 成功后会发布 raw search sync，但当时只有本地
单元测试证据，live queue smoke 被留到后续。`028-09` 的第一刀只验证这条已有 producer
path，不修改服务代码：

```text
x.process / video.normalize
  -> q.normalize.video
  -> ircs-normalization-worker
  -> raw_videos READY + raw relation 写入
  -> x.search / search.sync.raw legacy UUID payload
  -> q.search.sync.raw
  -> ircs-search-service legacy UUID parser
  -> ircs_raw_video ES document refresh
```

## 范围

包含：

- normalization -> raw search sync live smoke 脚本候选。
- PostgreSQL fixture seed/cleanup。
- RabbitMQ publish、queue/DLQ 检查。
- Elasticsearch raw document 检查与 cleanup。

不包含：

- metadata result producer live smoke。
- aggregation unified producer live smoke。
- old unified dirty lifecycle live smoke。
- 服务代码、K8S manifest、HTTPRoute 或共享 observability 修改。

## MARK 设计

```text
MARK 1/5 normalization worker consumes q.normalize.video and marks raw video READY
MARK 2/5 normalization writes normalized raw fields from raw_metadata without triggering aggregation
MARK 3/5 normalization publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 4/5 normalize/search queues and DLQs are empty for the smoke window
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

## 脚本设计

新增脚本：

```text
scripts/smoke/search-producer-normalization-02809-smoke.ps1
```

关键设计：

- 通过 `x.process` / `video.normalize` 投递 raw video ID。
- 使用唯一 `stamp` 和 UUID fixture，避免与其他 agent 数据混淆。
- fixture 的 `aggregation_status` 使用 `BOUND`，避免 aggregation scheduler 抢占本轮
  normalization producer smoke。
- 默认不 purge 共享 RabbitMQ 队列；成功路径断言目标 queue/DLQ 为 0。若需要故障后强制清队，
  可显式传入 `-PurgeSmokeQueuesOnCleanup`。
- cleanup 覆盖 raw video、raw relation、可能被 aggregation 建出的 raw/unified 绑定、unified
  row，以及 raw/unified ES document。

## 验证结果

语法检查：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-normalization-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

K8S 前置条件：

```text
ircs-normalization-worker READY 1/1，APP_NORMALIZATION_LISTENER_ENABLED=true
ircs-search-service READY 1/1，APP_SEARCH_LISTENER_ENABLED=true
q.normalize.video messages=0 consumers=1
q.search.sync.raw messages=0 consumers=1
```

live smoke：

```text
.\scripts\smoke\search-producer-normalization-02809-smoke.ps1

MARK 1/5 normalization worker consumes q.normalize.video and marks raw video READY
MARK 2/5 normalization writes normalized raw fields from raw_metadata without triggering aggregation
MARK 3/5 normalization publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 4/5 normalize/search queues and DLQs are empty for the smoke window
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

## 发现与调整

首次 live smoke 使用 raw relation 作为 MARK 2，并让 fixture 保持
`aggregation_status='PENDING'`。执行结果：

```text
MARK 1/5 normalization worker consumes q.normalize.video and marks raw video READY
raw relation counts were not 2|1|1, actual=0|0|0
```

同时 aggregation-worker 日志显示该 fixture 被自动聚合：

```text
Raw video aggregated: rawVideoId=4a30717c-8dbc-4fdb-9121-a1a9c7694c5a,
unifiedVideoId=41e6c4b9-0493-4f5b-892c-7b489b295215
```

结论：

- 当前 dev 集群 normalization-worker 可消费 `q.normalize.video`，写 READY/基础字段，并发布 raw
  search sync。
- 当前 dev 集群未实证 `028-05-F` 的 raw relation 写入；如果要把 relation 也纳入 live MARK，
  需要先确认 dev 镜像已包含 `RawVideoNormalizationRepository.markReady()` 的 relation upsert。
- normalization producer live smoke 应隔离 aggregation，避免一个 producer path 的验证被另一个
  worker 的 scheduler 扩大影响面。

## 当前结论

本轮完成 `028-09` 的低风险第一刀：normalization producer path 已通过 live smoke，证明
legacy UUID raw search sync 可被 search-service 消费并刷新 `ircs_raw_video`。

后续仍需独立切片：

- metadata result -> raw search sync live smoke。
- aggregation -> unified search sync live smoke。
- old unified dirty lifecycle live smoke。
- normalization raw relation live smoke strict mode，前提是 dev 镜像已包含 `028-05-F` relation
  upsert。
