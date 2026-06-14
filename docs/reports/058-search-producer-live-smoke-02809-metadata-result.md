# 058 search producer live smoke 02809 metadata result 报告

状态：已完成（metadata result -> raw search sync live smoke）

更新时间：2026-06-07

关联任务：

- `tasks/in-progress/028-09-search-producer-live-smoke-parity.md`

上游来源：

- `docs/migrations/006-metadata-worker-result-extraction.md`
- `docs/reports/055-search-producer-live-smoke-02809-normalization.md`
- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/migrations/050-search-producer-matrix-parity.md`

## 背景

`028-09` 第一刀已验证 normalization 成功后发布 legacy UUID raw search sync，并由
`ircs-search-service` 刷新 `ircs_raw_video`。本轮下一刀聚焦 metadata result consumer：

```text
x.process / metadata.result
  -> q.enrich.metadata.result
  -> ircs-metadata-worker
  -> raw_videos enrichment metadata/status update
  -> x.search / search.sync.raw legacy UUID payload
  -> q.search.sync.raw
  -> ircs-search-service
  -> ircs_raw_video ES document refresh
```

## 范围

包含：

- 直接投递 `EnrichmentResultMsg` JSON 的 live smoke 脚本。
- PostgreSQL `raw_videos` fixture seed/cleanup。
- RabbitMQ `q.enrich.metadata.result`、`q.search.sync.raw` 与 DLQ 检查。
- Elasticsearch `ircs_raw_video` document 检查与 cleanup。
- HTTPRoute 只读检查。

不包含：

- 真实 provider upstream、credential lease 或 `q.fetch.metadata.*`。
- aggregation -> unified search sync。
- old unified dirty lifecycle。
- 服务代码、K8S manifest、HTTPRoute 或 rollout 修改。

## MARK 设计

```text
MARK 1/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 2/6 metadata result fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 metadata result consumer updates raw_videos to SUCCESS
MARK 4/6 metadata result publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 5/6 metadata/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

## 脚本设计

新增脚本：

```text
scripts/smoke/search-producer-metadata-result-02809-smoke.ps1
```

关键设计：

- 使用唯一 `stamp` 和 UUID fixture 隔离并行 smoke。
- fixture 初始化为 `enrichment_status='PENDING'`、`normalization_status='PENDING'`、`aggregation_status='BOUND'`。
- metadata result 成功后会把 `aggregation_status` 推到 `PENDING`；本脚本保留
  `normalization_status='PENDING'`，用于隔离 aggregation scheduler。
- 直接向 `x.process` / `metadata.result` 投递 success `EnrichmentResultMsg`，不依赖真实 provider。
- 断言 `raw_videos` 写入 `tmdb_id`、`imdb_id`、`description`、`year`、`score`、`alias_title`，并进入 `SUCCESS`。
- 断言 `ircs_raw_video` 包含新的 enrichment 投影，例如 `enrichmentStatus=SUCCESS`、`hasTmdbId=true`、`externalIds` 包含 fixture ID。
- cleanup 覆盖 raw video、潜在 raw/unified 绑定和 ES raw document；默认不 purge 共享队列。

## 验证结果

语法检查：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-metadata-result-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

live smoke：

```text
.\scripts\smoke\search-producer-metadata-result-02809-smoke.ps1

MARK 1/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 2/6 metadata result fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 metadata result consumer updates raw_videos to SUCCESS
MARK 4/6 metadata result publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 5/6 metadata/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

## 当前结论

metadata result -> raw search sync live smoke 已通过，证明：

- `q.enrich.metadata.result` consumer 可处理直接投递的 provider result。
- final metadata result 会更新 `raw_videos` enrichment 字段并发布 legacy UUID raw search sync。
- `ircs-search-service` 可消费该 raw sync 并刷新 `ircs_raw_video`。
- `q.enrich.metadata.result`、`q.enrich.metadata.result.dlq`、`q.search.sync.raw`、`q.search.sync.raw.dlq`
  在本轮执行窗口结束时均为空。
- cleanup 后 DB/ES fixture 无残留。
- HTTPRoute 检查只读执行，未发现公共 HTTPRoute。

本轮未修改服务代码、K8S manifest、HTTPRoute、`docs/index.md` 或 `tasks/index.md`。
