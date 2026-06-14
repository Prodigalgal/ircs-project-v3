# 048 metadata provider fan-out parity 分析

状态：已完成（fan-out routing 本地测试已闭环，真实 provider worker 后续单拆）

更新时间：2026-06-07

## 背景

`028-05` 队列消费者深层副作用审计已标记 `ENRICH_METADATA`、`FETCH_METADATA_DOUBAN`、
`FETCH_METADATA_TMDB`、`FETCH_METADATA_RT` 仍未完全闭环。V1 单体中 metadata enrich 是
典型 scatter/gather 流程：`ENRICH_METADATA` 读取 raw video，按 provider 开关和已有外部 ID
决定待抓取 provider，写入 pending set，再 fan-out 到各 provider fetch queue。V3 已拆出
`ircs-metadata-worker`，但需要单独固定 provider fan-out parity 的最小可验证边界。

## V1 证据

V1 源码：

- `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\scraper\metadata\MetadataEnrichmentService.java`
- `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\scraper\metadata\worker\MetadataProviderWorker.java`
- `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\infrastructure\queue\QueueTopic.java`

关键行为：

- `MetadataEnrichmentService` 监听 `QueueTopic.Names.PROCESS_Q_ENRICH`。
- `dispatch` 构建 `MetadataSearchContext`，包含 `videoId`、标题、季、年份、分类 slug 和已有
  `doubanId`、`tmdbId`、`imdbId`、`rottenTomatoesId`。
- `shouldFetch` 按 `SystemConfigKey.TMDB_ENABLED`、`DOUBAN_ENABLED`、`RT_ENABLED`
  和对应 ID 是否缺失决定 provider。
- 有待抓取 provider 时写 Redis pending set，并分别发布到：
  `FETCH_METADATA_DOUBAN`、`FETCH_METADATA_TMDB`、`FETCH_METADATA_RT`。
- `MetadataProviderWorker` 分别通过三个 `@RabbitListener` 消费
  `FETCH_Q_DOUBAN`、`FETCH_Q_TMDB`、`FETCH_Q_RT`，再把结果发布到 `ENRICHMENT_RESULT`。

## V3 当前实现

V3 源码：

- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataDispatchConsumer.java`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataDispatchService.java`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataProviderTaskPublisher.java`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataProviderRoutingProperties.java`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderWorker.java`
- `services/ircs-metadata-worker/src/main/resources/application.yaml`

已具备：

- `MetadataDispatchConsumer` 监听 `q.enrich.metadata`，受
  `app.metadata.dispatcher.enabled=true` 控制。
- `MetadataDispatchService` 查询 raw video enrichment record，跳过 `SUCCESS` /
  `PERMANENT_FAILURE`，按 provider 配置和已有 ID 决定待抓取 provider。
- `MetadataPendingRegistry` 注册 pending provider set。
- `MetadataProviderTaskPublisher` 已将 `DOUBAN`、`TMDB`、`ROTTEN_TOMATOES`
  分别映射到 `FETCH_METADATA_DOUBAN`、`FETCH_METADATA_TMDB`、`FETCH_METADATA_RT`。
- `application.yaml` 默认保持 dev-safe：dispatcher、listener 和 provider worker 均关闭。
- `MetadataProviderWorker` 当前只暴露 TMDB listener；Douban/RT 实际 provider worker 尚未补齐。

## 差异判断

| 项目 | V1 | V3 当前 | 本切片判断 |
| --- | --- | --- | --- |
| `ENRICH_METADATA` consumer | batch listener | 单消息 listener | 可接受，待 K8S smoke |
| provider fan-out 决策 | 系统配置 + ID 缺失 | properties + ID 缺失 | 语义基本对齐 |
| provider topic 映射 | 三个 fetch topic | 三个 fetch topic | 已有实现，缺多 provider 证据 |
| pending set | Redis set + TTL | `MetadataPendingRegistry` + TTL | 已有实现 |
| TMDB provider worker | 有 listener 和 provider | 有 listener 和 provider | 部分对齐 |
| Douban provider worker | 有 listener 和 provider | 缺实际 listener/provider | 后续任务，不在本切片补公网 provider |
| RT provider worker | 有 listener 和 provider | 缺实际 listener/provider | 后续任务，不在本切片补公网 provider |

## 本切片范围

包含：

```text
docs/reports/048-metadata-provider-fanout-parity-analysis.md
tasks/done/028-05-c-metadata-provider-fanout-parity.md
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/dispatch/**
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/**
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/**
```

不包含：

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java
deploy/**
docs/index.md
tasks/index.md
docs/k8s/current-environment*
Douban/RT 真实公网 provider 实现
K8S rollout
```

## 最小方案

先不改 `QueueTopic`，不启用公网 provider。用单元测试增强证明：

- 当 `tmdb-enabled=true` 且视频缺 `tmdbId` 时，`ENRICH_METADATA` dispatch 会发布
  `ProviderType.TMDB`，对应生产代码路由到 `FETCH_METADATA_TMDB`。
- 当 `douban-enabled=true`、`tmdb-enabled=true`、`rotten-tomatoes-enabled=true`
  且三个 provider ID 都缺失时，dispatch 会按 `DOUBAN`、`TMDB`、`ROTTEN_TOMATOES`
  fan-out 并注册同一 pending set。
- 当某 provider 已有外部 ID 时，不再 fan-out 到该 provider。
- `MetadataProviderTaskPublisher` topic mapping 使用 RabbitTemplate mock 验证：
  `DOUBAN -> metadata.fetch.douban`，`TMDB -> metadata.fetch.tmdb`，
  `ROTTEN_TOMATOES -> metadata.fetch.rt`。

## 验收标准

- 本地 task spec 已固定并关联本报告。
- `MetadataDispatchServiceTest` 覆盖 provider 配置 fan-out 的多 provider 场景。
- `MetadataProviderTaskPublisherTest` 覆盖 provider 到 fetch routing key 的映射。
- `.\gradlew.bat :services:ircs-metadata-worker:test` 通过。
- 无 `QueueTopic` 改动、无 K8S rollout、无 docs/tasks index 改动。

## 遗留风险

- 该切片只证明 dev-safe fan-out 和 topic routing，不证明 RabbitMQ live 消费、ack/DLQ 或
  Kubernetes 环境变量启用后的真实队列排空。
- Douban/RT 实际 provider worker 仍未补齐；本切片只证明 dispatcher 能把任务路由过去。
- 真实 provider credential、rate limit、公网失败分类和图片字段合并仍属于后续 provider
  parity / enrichment result parity 范围。

## 当前验证

2026-06-07 已完成。

实现结果：

- 增强 `MetadataDispatchServiceTest`：
  - 覆盖三 provider 同时启用时，缺失 provider ID 的 raw video 会注册
    `DOUBAN`、`TMDB`、`ROTTEN_TOMATOES` pending set。
  - 使用真实 `MetadataProviderTaskPublisher` 和 mocked `RabbitTemplate`，证明 dispatch 会发布到
    `metadata.fetch.douban`、`metadata.fetch.tmdb`、`metadata.fetch.rt`。
  - 覆盖已有 `tmdbId` 时跳过 TMDB fan-out，仅保留缺失的 Douban/RT。
- 新增 `MetadataProviderTaskPublisherTest`：
  - 覆盖 `DOUBAN`、`TMDB`、`ROTTEN_TOMATOES` 到专属 fetch routing key 的映射。
  - 覆盖 `IMDB` 无 fetch queue 时拒绝发布。
- 未修改 `QueueTopic`、deploy、K8S 文档、docs/tasks index。

本地测试：

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 11s
12 actionable tasks: 12 executed
```

后续仍需主 agent 或后续任务处理：

- 如需 live smoke，再由主 agent 决定是否在 `ircs-dev` 启用 dispatcher/provider worker 环境变量。
- Douban/RT provider worker 仍是功能缺口；本切片只证明 fan-out 和 routing，不证明实际抓取。
- 未验证真实 RabbitMQ ack/DLQ、pending set 过期和 provider result gather 全链路。
