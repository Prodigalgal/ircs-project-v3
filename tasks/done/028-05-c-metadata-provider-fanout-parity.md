# 028-05-C metadata provider fan-out parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/048-metadata-provider-fanout-parity-analysis.md`
- V1 `MetadataEnrichmentService`
- V1 `MetadataProviderWorker`
- V3 `services/ircs-metadata-worker`

## 背景

V1 metadata enrich 通过 `ENRICH_METADATA` 做 provider scatter，再由
`FETCH_METADATA_DOUBAN`、`FETCH_METADATA_TMDB`、`FETCH_METADATA_RT` provider workers
执行抓取并回写 `ENRICHMENT_RESULT`。V3 当前 `ircs-metadata-worker` 已有 dispatcher、
pending registry、TMDB provider worker 和 topic publisher，但 fan-out parity 缺少独立
task spec 与 dev-safe 测试证据。

## 目标

- 对比 V1 metadata fan-out/provider workers 与 V3 `ircs-metadata-worker` 当前实现。
- 固定本地分析报告和本 task spec。
- 在不访问公网、不启用 K8S rollout 的情况下，证明 `ENRICH_METADATA` dispatch 能按 provider
  配置 fan-out 到至少一个 provider fetch topic，并尽量覆盖三 provider topic mapping。
- 保持 dev-safe 默认关闭，不改 `QueueTopic`。

## 非目标

- 不实现 Douban/RT 真实公网 provider。
- 不修改 `QueueTopic`。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/k8s/current-environment*`。
- 不执行 K8S rollout、镜像构建或 ClusterIP smoke。
- 不改 metadata result gather 的字段合并语义。

## 输入

- V1 `MetadataEnrichmentService` 与 `MetadataProviderWorker` 源码。
- V3 `MetadataDispatchService`、`MetadataProviderTaskPublisher`、`MetadataProviderWorker`、
  `application.yaml`。
- `QueueTopic` 已有 `ENRICH_METADATA`、`FETCH_METADATA_DOUBAN`、
  `FETCH_METADATA_TMDB`、`FETCH_METADATA_RT`、`ENRICHMENT_RESULT`。

## 输出

- `docs/reports/048-metadata-provider-fanout-parity-analysis.md`
- 本 task spec。
- `services/ircs-metadata-worker` 内最小 dev-safe 测试增强或实现补口。
- 本地测试命令和结果回写。

## 影响范围

包含：

```text
docs/reports/048-metadata-provider-fanout-parity-analysis.md
tasks/done/028-05-c-metadata-provider-fanout-parity.md
services/ircs-metadata-worker/**
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/**
```

不包含：

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java
docs/index.md
tasks/index.md
docs/k8s/current-environment*
deploy/**
scripts/**
```

## 依赖

- `ircs-metadata-worker` 已依赖 `shared:ircs-messaging` 和 Spring AMQP。
- `MetadataProviderTaskPublisher` 已能通过 `QueueTopic` 发布 JSON payload。
- provider worker 默认由 `app.metadata.provider-workers.tmdb-enabled=false` 关闭，符合
  dev-safe 默认。

## 风险

- 单元测试只能证明 Java 层 dispatch 与 routing，不证明真实 RabbitMQ topology 和 listener
  绑定。
- Douban/RT provider worker 缺口会继续保留；如果误开启对应 fetch queue 而没有 consumer，
  队列会堆积。
- V1 batch listener 到 V3 single listener 的并发和吞吐差异未在本切片验证。

## 验收标准

- task spec 和分析报告已创建并说明范围、非目标、风险、验收和测试边界。
- 至少一个 dev-safe 测试证明 `ENRICH_METADATA` 触发后会按 provider 配置发布到 provider
  fetch 路径。
- 多 provider 配置下 pending set 与 publish provider 列表一致。
- provider 到 `FETCH_METADATA_DOUBAN/TMDB/RT` 的 topic mapping 有测试证据。
- `.\gradlew.bat :services:ircs-metadata-worker:test` 通过，或明确记录失败原因。

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-metadata-worker:test
```

后置集成测试由主 agent 或后续任务处理：

```text
启用 dispatcher/provider worker 环境变量
publish ENRICH_METADATA fixture
verify q.fetch.metadata.* routing
verify q.enrich.metadata / q.fetch.metadata.* / DLQ cleanup
```

## 当前状态

2026-06-07：

- 已完成 V1/V3 初步对比。
- 已固定本 task spec 与 `docs/reports/048-metadata-provider-fanout-parity-analysis.md`。
- 已完成最小 dev-safe 测试增强：
  - `MetadataDispatchServiceTest` 覆盖三 provider 配置 fan-out、pending set 注册、
    真实 publisher 到 mocked RabbitTemplate 的 fetch routing。
  - `MetadataProviderTaskPublisherTest` 覆盖 `DOUBAN/TMDB/ROTTEN_TOMATOES` 到
    `metadata.fetch.douban/tmdb/rt` 的映射，以及 `IMDB` 无 fetch queue 的拒绝行为。
- 已完成本地测试：

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 11s
12 actionable tasks: 12 executed
```

- 未修改 `QueueTopic` 和 `docs/k8s/current-environment*`，未执行 K8S rollout。

## 剩余边界

- 本文件已移动到 `tasks/done/` 并同步任务索引。
- Douban/RT 真实 provider worker 与公网抓取能力仍未完成。
- 真实 RabbitMQ、K8S 环境变量、ack/DLQ 和 queue cleanup smoke 未在本切片执行。
