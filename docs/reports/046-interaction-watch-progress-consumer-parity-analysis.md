# 046 interaction watch progress consumer parity 分析报告

状态：已完成（consumer、ARM64 rollout 与 RabbitMQ/DB smoke 已闭环）

更新时间：2026-06-07

## 背景

`028-05` 队列消费者审计确认 `WATCH_PROGRESS` 的 owner 是
`ircs-interaction-service`，队列名为 `q.interaction.watch_progress`。V3 当前已经有
`POST /api/portal/interaction/history` 同步写入观看历史，但缺少 V1 的队列消费者路径。

这意味着 portal 播放器或后续异步 producer 即使把观看进度发布到 RabbitMQ，V3 也不会消费并
upsert `member_watch_histories`。HTTP route 可用不等价于内部功能 1:1。

## 上游来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`
- `shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java`
- V1 `WatchHistoryConsumer`
- V1 `WatchHistoryServiceImpl.batchUpsert`
- V1 `MemberWatchHistoryRepositoryCustomImpl.batchUpsert`
- V3 `services/ircs-interaction-service`

## V1 行为摘要

V1 行为分两段：

```text
PortalInteractionController.reportProgress
  -> WatchHistoryService.reportProgress
  -> publish QueueTopic.WATCH_PROGRESS

WatchHistoryConsumer.onMessageBatch
  -> WatchHistoryService.batchUpsert
  -> 按 memberId:unifiedVideoId 保留 timestamp 最新消息
  -> batch upsert member_watch_histories
  -> FK/DataIntegrityViolation 时 fallback 单条写入，孤儿记录忽略
```

V1 upsert 语义：

- 唯一键 `(member_id, unified_video_id)`。
- 更新 `last_video_id`、`last_episode_id`、`episode_name`、`progress_seconds`、
  `duration_seconds`、`last_watched_at`。
- 重复消息以最新 `timestamp` 为准。
- `episodeName` 为空时使用 `未知剧集`。

## V3 当前状态

已完成：

- `ircs-interaction-service` 有 history/favorite/feedback HTTP route。
- `InteractionCommandService.reportProgress` 会同步校验影片存在、校验 episodeName 并
  upsert `member_watch_histories`。
- `JdbcInteractionRepository.upsertWatchHistory` 已复用 V1 的 on conflict 形态。

本轮前缺口：

- `ircs-interaction-service` 未依赖 `shared:ircs-messaging` 和
  `spring-boot-starter-amqp`。
- 没有 `@RabbitListener(queues = q.interaction.watch_progress)`。
- shared contracts 中没有 V3 统一的 watch progress message DTO。
- 没有批量消息去重、最新 timestamp 选择、空 episodeName fallback 的消费者服务。

## 本轮方案

最小 1:1 切片：

```text
RabbitMQ q.interaction.watch_progress
  -> WatchProgressConsumer.onMessage(WatchProgressMessage)
  -> WatchProgressBatchService.batchUpsert(List<WatchProgressMessage>)
  -> 按 memberId:unifiedVideoId 去重，保留 timestamp 最新消息
  -> 调用 JdbcInteractionRepository.upsertWatchHistoryIfNewer(...)
```

实现边界：

- 新增 `shared/ircs-contracts/.../interaction/WatchProgressMessage.java`。
- `ircs-interaction-service` 增加 AMQP 与 messaging 依赖。
- 新增 `WatchProgressConsumer`，使用单条 listener；批量语义由 service 可测试方法承接。
- 新增 `WatchProgressBatchService`，复用
  `JdbcInteractionRepository.upsertWatchHistoryIfNewer`。
- 对 null/空列表直接忽略；对缺少 `memberId` 或 `unifiedVideoId` 的消息忽略。
- 对 null `timestamp` 使用当前时间；对空 `episodeName` 使用 `未知剧集`。
- 队列消费路径使用 `where excluded.last_watched_at >= member_watch_histories.last_watched_at`
  保护旧消息晚到场景，避免旧播放心跳覆盖新进度。
- 不在本轮把现有 HTTP `POST /history` 改成异步发布，避免改变前端即时写入语义。

## 非目标

- 不新增 HTTP route、Service、Deployment、HTTPRoute、PVC 或中间件。
- 不修改 portal-bff/admin-bff。
- 不改动 V3 当前 history/favorite 查询响应。
- 不实现 batch listener container 或手动 ack/reject 策略；默认 Spring AMQP 失败重试/DLQ
  由现有 Rabbit topology 承接。
- 不把 `POST /history` 从同步写库改为 publish-only。

## 风险

- 单条 listener 与 V1 batch listener 的吞吐不同，但本轮核心 parity 是消费者存在、消息去重
  逻辑可测试、最终 upsert 副作用一致。
- 真实 DLQ 行为已通过 smoke 后队列为空验证；失败重试策略未做破坏性验证。
- 如果上游 producer 发送的是旧 JSON 字段，DTO 字段名必须保持 V1 语义：
  `memberId`、`unifiedVideoId`、`videoId`、`episodeId`、`episodeName`、
  `progressSeconds`、`durationSeconds`、`timestamp`。

## 验收标准

```text
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

## 测试边界

必须运行：

```text
.\gradlew.bat :services:ircs-interaction-service:test
```

K8S 后置 smoke：

```text
publish WATCH_PROGRESS fixture
verify member_watch_histories upsert
publish duplicate older/newer messages and verify latest wins
verify q.interaction.watch_progress and dlq are empty after cleanup
kubectl -n ircs-dev get httproute remains empty
ResourceQuota unchanged or reduced impact
```

## 实施结果

新增实现：

```text
shared/ircs-contracts/.../interaction/WatchProgressMessage.java
services/ircs-interaction-service/.../WatchProgressMessageParser.java
services/ircs-interaction-service/.../WatchProgressConsumer.java
services/ircs-interaction-service/.../WatchProgressBatchService.java
JdbcInteractionRepository.upsertWatchHistoryIfNewer(...)
scripts/smoke/interaction-watch-progress-02805a-smoke.ps1
```

配置变更：

```text
services/ircs-interaction-service/build.gradle
  implementation project(':shared:ircs-messaging')
  implementation 'org.springframework.boot:spring-boot-starter-amqp'

APP_INTERACTION_LISTENER_ENABLED=false by default
deploy/k8s/dev/interaction-service-dev.yaml -> true in ircs-dev
```

## 验收结果

本地测试：

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

K8S rollout：

```text
deployment/ircs-interaction-service Ready 1/1
imageID:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:a70f46ed265944ac06f180e68d8930268542c7c53519f624273b52b7cb296ab4
```

RabbitMQ/DB smoke：

```text
.\scripts\smoke\interaction-watch-progress-02805a-smoke.ps1
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
q.interaction.watch_progress=0 messages, 1 consumer
q.interaction.watch_progress.dlq=0 messages
```
