# 050 metadata provider worker and enrichment result parity 分析

状态：已完成（Douban/RT dev-safe listener 与 terminal failure result 本地测试已闭环）

更新时间：2026-06-07

## 背景

`028-05-C` 已证明 `ENRICH_METADATA` dispatcher 能 fan-out 到
`FETCH_METADATA_DOUBAN`、`FETCH_METADATA_TMDB`、`FETCH_METADATA_RT`。但 V3 当前
`MetadataProviderWorker` 只监听 TMDB fetch queue；如果后续在 dev 中启用 Douban/RT fan-out，
对应队列会有消息但没有 worker 消费，portal/admin 看到的内容 enrich 状态会停在 pending。

V1 单体中三类 provider fetch queue 都有 worker 入口。V3 当前不应直接接公网 Douban/RT；
但至少需要 dev-safe listener 能消费消息、生成可观察的 terminal failure result，并进入
`ENRICHMENT_RESULT` collector，避免“接口成功但异步链路静默堆积”。

## 上游来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/048-metadata-provider-fanout-parity-analysis.md`
- `docs/migrations/049-metadata-provider-fanout-parity.md`
- `tasks/done/028-05-c-metadata-provider-fanout-parity.md`
- V1 `MetadataProviderWorker`
- V3 `services/ircs-metadata-worker`

## 当前差异

| 项目 | V1 | V3 当前 | 本轮处理 |
| --- | --- | --- | --- |
| TMDB fetch worker | 有 listener/provider | 有 listener/provider | 保留 |
| Douban fetch worker | 有 listener/provider | 有 topic，无 listener/provider | 补 dev-safe listener |
| RT fetch worker | 有 listener/provider | 有 topic，无 listener/provider | 补 dev-safe listener |
| provider 缺实现语义 | 失败 result 可进入 result collector | `execute()` 已有 `PROVIDER_NOT_FOUND`，但无 queue listener | 接入 listener 后复用 |
| result collector | 成功/失败更新 raw enrichment status | 已存在，但缺 terminal failure 测试 | 补测试 |

## 本轮方案

最小 dev-safe parity：

```text
MetadataProviderWorker
  -> @RabbitListener q.fetch.metadata.tmdb -> execute(TMDB)
  -> @RabbitListener q.fetch.metadata.douban -> execute(DOUBAN)
  -> @RabbitListener q.fetch.metadata.rt -> execute(ROTTEN_TOMATOES)
  -> provider missing: publish ENRICHMENT_RESULT failure retryable=false errorCode=PROVIDER_NOT_FOUND

MetadataResultCollectorService
  -> non-retryable failure without authoritative ID
  -> enrichment_status = PERMANENT_FAILURE
  -> publish raw search INDEX
```

## 非目标

- 不实现真实 Douban/RT 公网 provider。
- 不开启 dev 中 metadata dispatcher/provider worker 环境变量。
- 不新增 Pod、Service、HTTPRoute、中间件或资源配额。
- 不修改 search-service consumer。
- 不重做 TMDB HTTP provider。

## 风险

- 将 Douban/RT 缺实现映射为 terminal failure 是 dev-safe 行为，生产级真实 provider 仍需后续
  单独实现。
- 如果未来启用三 provider fan-out，多个 terminal failure result 可能让 raw video 进入
  `PERMANENT_FAILURE`；这是当前缺 provider 时更诚实的状态，不再静默 pending。
- 真实 scatter/gather 的“所有 provider 完成后再最终判定”仍未完整实现，后续需要 pending set
  gather 任务继续收敛。

## 验收标准

```text
MARK 1/4 MetadataProviderWorker listens on Douban fetch queue
MARK 2/4 MetadataProviderWorker listens on RT fetch queue
MARK 3/4 missing Douban/RT provider publishes terminal ENRICHMENT_RESULT failure
MARK 4/4 metadata result collector records terminal failure and publishes raw search sync
```

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
```

本轮不做 K8S rollout。后续 live smoke 单独启用 dispatcher/provider worker 后验证：

```text
publish ENRICH_METADATA fixture
verify q.fetch.metadata.douban / q.fetch.metadata.rt are consumed
verify q.enrich.metadata.result and DLQ cleanup
verify raw_videos.enrichment_status reflects terminal result
```

## 实现结果

- `MetadataProviderWorker` 新增：
  - `onDoubanTask(Message)` 监听 `q.fetch.metadata.douban`。
  - `onRottenTomatoesTask(Message)` 监听 `q.fetch.metadata.rt`。
- 两个新 listener 复用现有 `execute(context, providerType)`：
  - 如果 provider 实现缺失，发布 `ENRICHMENT_RESULT` failure。
  - `retryable=false`。
  - `errorCode=PROVIDER_NOT_FOUND`。
- `MetadataResultCollectorServiceTest` 增加 terminal failure 断言，证明 non-retryable failure 会写
  `PERMANENT_FAILURE` 并发布 raw search sync。

## 验证结果

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s
12 actionable tasks: 12 executed
```

完成标记：

```text
MARK 1/4 MetadataProviderWorker listens on Douban fetch queue
MARK 2/4 MetadataProviderWorker listens on RT fetch queue
MARK 3/4 missing Douban/RT provider publishes terminal ENRICHMENT_RESULT failure
MARK 4/4 metadata result collector records terminal failure and publishes raw search sync
```

## 剩余边界

- 真实 Douban/RT provider、公网失败分类、rate limit 和 credential lease 仍未实现。
- 本轮未启用 dev live dispatcher/provider worker，因此 RabbitMQ ack/DLQ 和 pending set cleanup
  仍需后续 smoke。
- scatter/gather 的“所有 provider 完成后统一判定”仍未完整恢复，目前 terminal failure 是
  dev-safe 可观察兜底。
