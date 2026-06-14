# 051 metadata provider worker and enrichment result parity

状态：已完成

更新时间：2026-06-07

## 来源

- `docs/reports/050-metadata-provider-worker-enrichment-parity-analysis.md`
- `tasks/done/028-05-e-metadata-provider-worker-enrichment-parity.md`
- `docs/migrations/049-metadata-provider-fanout-parity.md`

## 迁移目标

在不接真实公网 Douban/RT provider 的前提下，补齐 V3 provider fetch worker 的 dev-safe
消费入口，避免 fan-out 后出现有消息无消费者的内部功能缺失。

## 实现结果

```text
q.fetch.metadata.douban -> MetadataProviderWorker.onDoubanTask -> execute(DOUBAN)
q.fetch.metadata.tmdb   -> MetadataProviderWorker.onTmdbTask -> execute(TMDB)
q.fetch.metadata.rt     -> MetadataProviderWorker.onRottenTomatoesTask -> execute(ROTTEN_TOMATOES)
```

当 provider 实现缺失时：

```text
publish ENRICHMENT_RESULT failure
retryable = false
errorCode = PROVIDER_NOT_FOUND
```

result collector 对 non-retryable failure：

```text
raw_videos.enrichment_status = PERMANENT_FAILURE
publish SEARCH_SYNC_RAW INDEX
```

## 验证

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

- 真实 Douban/RT provider、credential、rate limit 和公网失败分类未实现。
- 本轮不做 K8S rollout；live RabbitMQ ack/DLQ 需要后续启用 dispatcher/provider worker 后验证。
- scatter/gather 的 pending set 最终收敛仍需独立任务。
