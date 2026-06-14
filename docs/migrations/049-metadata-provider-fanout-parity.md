# 049 metadata provider fan-out parity

状态：已完成

更新时间：2026-06-07

## 来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/048-metadata-provider-fanout-parity-analysis.md`
- `tasks/done/028-05-c-metadata-provider-fanout-parity.md`

## 迁移目标

证明 V3 `ircs-metadata-worker` 的 `ENRICH_METADATA` dispatcher 能按 V1 scatter/gather
语义把待抓取 provider 路由到对应 fetch topic：

```text
ENRICH_METADATA
  -> load raw video enrichment context
  -> decide missing provider ids
  -> register Redis pending set
  -> publish FETCH_METADATA_DOUBAN / TMDB / RT
```

## 实现结果

- 增强 `MetadataDispatchServiceTest`，覆盖三 provider 同时启用时的 pending set 与 fan-out。
- 使用真实 `MetadataProviderTaskPublisher` 和 mocked `RabbitTemplate` 验证 routing key：
  `metadata.fetch.douban`、`metadata.fetch.tmdb`、`metadata.fetch.rt`。
- 新增 `MetadataProviderTaskPublisherTest` 覆盖 provider 到 fetch topic 的映射，以及 `IMDB`
  无 fetch queue 时拒绝发布。

## 验证

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 11s
12 actionable tasks: 12 executed
```

## 剩余边界

- 本迁移只证明 fan-out routing，不证明真实 RabbitMQ live ack/DLQ。
- Douban/RT provider worker 尚未实现，TMDB provider worker 尚未在 dev live smoke 中启用验证。
- provider result gather、pending set 过期、失败分类和字段合并继续进入后续 `028-05-E`。
