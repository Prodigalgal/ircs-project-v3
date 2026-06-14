# 028-05-A interaction watch-progress consumer parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/046-interaction-watch-progress-consumer-parity-analysis.md`
- `shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java`
- V1 `WatchHistoryConsumer`
- V1 `WatchHistoryServiceImpl.batchUpsert`

## 背景

V3 当前 HTTP history 写入已经可用，但 `q.interaction.watch_progress` 没有消费者。
V1 会把播放进度先发布到 `WATCH_PROGRESS`，再由 interaction consumer 批量去重并 upsert
观看历史。缺少该消费者会导致异步播放心跳消息无人处理。

## 目标

- 为 V3 增加 watch progress message contract。
- 为 `ircs-interaction-service` 接入 RabbitMQ consumer。
- 复现 V1 的按 `memberId:unifiedVideoId` 去重和最新 timestamp 优先语义。
- 复用现有 `member_watch_histories` upsert SQL。
- 补充单元测试并记录完成标记。

## 非目标

- 不改现有 HTTP `POST /api/portal/interaction/history` 同步写入路径。
- 不新增公网 route、K8S Service、Deployment、PVC 或中间件。
- 不修改 portal/admin 前端。
- 不实现完整 batch listener container 调优。
- 不接入远端播放统计、积分、推荐或通知副作用。

## 输入

- RabbitMQ `q.interaction.watch_progress` 消息。
- `WatchProgressMessage` 字段：
  `memberId`、`unifiedVideoId`、`videoId`、`episodeId`、`episodeName`、
  `progressSeconds`、`durationSeconds`、`timestamp`。

## 输出

- `shared/ircs-contracts/.../interaction/WatchProgressMessage.java`
- `services/ircs-interaction-service` AMQP 依赖。
- `WatchProgressConsumer`
- `WatchProgressBatchService`
- 单元测试与本任务/报告回写。

## 影响范围

包含：

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/interaction/**
services/ircs-interaction-service/build.gradle
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/**
services/ircs-interaction-service/src/test/java/com/prodigalgal/ircs/interaction/**
docs/reports/046-interaction-watch-progress-consumer-parity-analysis.md
tasks/done/028-05-a-interaction-watch-progress-consumer-parity.md
```

不包含：

```text
services/ircs-portal-bff/**
services/ircs-admin-bff/**
deploy/**
portal/admin UI
```

## 依赖

- `QueueTopic.WATCH_PROGRESS` 的 queue、exchange、routing key 已在 shared contracts 中定义。
- `JdbcInteractionRepository.upsertWatchHistory` 已具备 V1 on conflict 语义。
- 队列路径使用 `upsertWatchHistoryIfNewer`，避免旧消息晚到覆盖新进度。
- RabbitMQ topology 由 `shared:ircs-messaging` 提供。

## 风险

- 上游 producer 尚未统一，K8S smoke 需要直接 publish fixture。
- 单元测试不覆盖真实 RabbitMQ ack/DLQ，需要后置 ClusterIP smoke。
- 如果引入 AMQP 依赖导致测试 context 自动连接 RabbitMQ，需要通过 slice/unit 测试避免外部依赖。

## 验收标准

- `WatchProgressConsumer` 监听 `q.interaction.watch_progress`。
- 空消息或缺少关键 ID 的消息不会写库。
- 同一 member/video 的重复消息保留 timestamp 最新一条。
- 空 episodeName 写入 `未知剧集`。
- `progressSeconds`、`durationSeconds` 小于 0 时归零。
- `.\gradlew.bat :services:ircs-interaction-service:test` 通过。

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-interaction-service:test
```

K8S 后置：

```text
publish WATCH_PROGRESS fixture
verify member_watch_histories upsert
verify duplicate latest wins
verify queue/dlq cleanup
```

## 当前状态

2026-06-07：

- 已完成 task spec 与分析报告。
- 已完成 `WatchProgressMessage` contract、consumer、parser、batch service 和
  `upsertWatchHistoryIfNewer`。
- 已完成本地测试：

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

- 已完成 ARM64 image push 和 K8S rollout：

```text
deployment/ircs-interaction-service Ready 1/1
imageID:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:a70f46ed265944ac06f180e68d8930268542c7c53519f624273b52b7cb296ab4
```

- 已完成 RabbitMQ/DB smoke：

```text
.\scripts\smoke\interaction-watch-progress-02805a-smoke.ps1
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

- 后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
q.interaction.watch_progress=0 messages, 1 consumer
q.interaction.watch_progress.dlq=0 messages
```
