# 028-05-E metadata provider worker and enrichment result parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/048-metadata-provider-fanout-parity-analysis.md`
- `docs/reports/050-metadata-provider-worker-enrichment-parity-analysis.md`
- `docs/migrations/049-metadata-provider-fanout-parity.md`
- V1 `MetadataProviderWorker`
- V3 `services/ircs-metadata-worker`

## 背景

`028-05-C` 已经完成 metadata dispatcher fan-out routing 证明，但 worker 层仍只监听 TMDB。
Douban/RT fetch topic 已存在，如果启用 provider fan-out 却没有消费者，会形成队列堆积和
pending enrich 状态，属于接口之后的内部实际功能缺失。

## 目标

- 为 `MetadataProviderWorker` 补 Douban/RT fetch queue listener。
- 对没有 provider 实现的 Douban/RT 生成 terminal `ENRICHMENT_RESULT` failure，而不是静默堆积。
- 补充单元测试证明 missing provider failure 会发布到 result publisher。
- 补充 result collector terminal failure 测试，证明 raw video 会进入 `PERMANENT_FAILURE` 并发布
  raw search sync。

## 非目标

- 不实现真实 Douban/RT 公网 provider。
- 不启用 K8S 环境变量，不做 rollout。
- 不修改 RabbitMQ topology、`QueueTopic` 或 search-service consumer。
- 不实现 pending set scatter/gather 最终聚合。

## 输入

- `q.fetch.metadata.douban`、`q.fetch.metadata.tmdb`、`q.fetch.metadata.rt` 消息。
- `MetadataSearchContext` JSON payload。
- 当前 Spring `List<MetadataProvider>` provider 集合。

## 输出

- `services/ircs-metadata-worker/src/main/java/.../MetadataProviderWorker.java`
- `services/ircs-metadata-worker/src/test/java/.../MetadataProviderWorkerTest.java`
- `services/ircs-metadata-worker/src/test/java/.../MetadataResultCollectorServiceTest.java`
- 本报告和任务回写。

## 影响范围

包含：

```text
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/**
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/**
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/result/**
docs/reports/050-metadata-provider-worker-enrichment-parity-analysis.md
tasks/done/028-05-e-metadata-provider-worker-enrichment-parity.md
```

不包含：

```text
deploy/**
shared/ircs-contracts/**
services/ircs-search-service/**
portal/admin UI
production namespace
```

## 依赖

- `QueueTopic` 已声明三条 fetch queue。
- `MetadataProviderWorker.execute()` 已具备 provider missing -> terminal failure 的核心逻辑。
- `MetadataProviderResultPublisher` 已能发布 `ENRICHMENT_RESULT`。
- `MetadataResultCollectorService` 已能处理 non-retryable failure。

## 风险

- Douban/RT 本轮只做 dev-safe terminal failure，不代表真实 provider 抓取质量。
- 若后续启用三 provider fan-out，缺 provider 会快速形成 `PERMANENT_FAILURE`，这比静默 pending
  更可观察，但仍需真实 provider 任务继续推进。

## 验收标准

- `MetadataProviderWorker` 有 Douban、TMDB、RT 三个 listener 方法。
- Douban/RT 缺 provider 时发布 `PROVIDER_NOT_FOUND`、`retryable=false` 的 failure result。
- `MetadataResultCollectorService` 对 non-retryable failure 写 `PERMANENT_FAILURE` 并发布 raw search
  sync。
- `.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks` 通过。

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
```

K8S smoke 后置，不属于本切片：

```text
启用 dispatcher/provider worker 后发布 ENRICH_METADATA fixture
验证 fetch queue 消费、result queue 清理、DLQ 为 0
```

## 实施结果

2026-06-07：

- `MetadataProviderWorker` 已新增 Douban/RT listener。
- Douban/RT 当前没有真实 provider 实现时，会发布 terminal `ENRICHMENT_RESULT` failure：
  `retryable=false`、`errorCode=PROVIDER_NOT_FOUND`。
- `MetadataProviderWorkerTest` 已覆盖 Douban/RT listener 的 missing-provider failure。
- `MetadataResultCollectorServiceTest` 已覆盖 non-retryable failure 写
  `PERMANENT_FAILURE` 并发布 raw search sync。

本地测试：

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

## 后续独立缺口

- 真实 Douban/RT provider 实现仍需独立任务。
- RabbitMQ live smoke、pending set cleanup 和 scatter/gather 最终状态仍需独立任务。
