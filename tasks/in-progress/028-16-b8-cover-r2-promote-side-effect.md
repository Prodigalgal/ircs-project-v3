# 028-16-B8 cover R2 promote side effect task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-b7-raw-source-pipeline-cover-image.md`
- V1 `CoverImagePipelineHandler`
- V1 `CoverImageServiceImpl.promoteToR2()`
- V3 `CoverImageR2SyncService`
- V3 `StorageR2Watchdog`

## 背景

`028-16-B7` 已完成 V1 `CoverImagePipelineHandler` 的 cover 选择规则，但刻意没有实现
`coverImageService.promoteToR2(sourceImage.getId())` 的跨服务副作用。V1 `promoteToR2()`
不是同步执行 R2 上传，而是在 R2 enabled 且 cover image 处于 `LOCAL` + `LOCAL_STORED`
且 `nextRetryTime == null` 时，把 `nextRetryTime` 设置为 `now()`，交给后续 upload
processor/watchdog 处理。V3 已有 `CoverImageR2SyncService` 与 `StorageR2Watchdog`，因此
B8 第一刀应恢复“由 aggregation 触发、storage-service 承接”的副作用，而不是让
aggregation-worker 直接接触对象存储。

## 目标

- 新增 storage command topic，用于表达“promote cover image to R2”。
- aggregation-worker 在 B7 cover pipeline 选中符合 V1 条件的 source cover 后发布该命令。
- storage-service 消费该命令，并按 V1 语义只对 `LOCAL` + `LOCAL_STORED` cover 安排
  `next_retry_time = now()`；R2 inactive 时跳过，不修改 DB。
- 不把真实 R2 upload 放进 aggregation-worker；真实上传仍由 storage-service 的现有
  sync/watchdog 边界负责。
- 增加本地单元测试固定 queue ownership、publisher、consumer 和 repository side effect。

## 非目标

- 不在本切片执行真实 R2 外呼或 K8S live smoke。
- 不实现 pipeline handler order/failure isolation runtime。
- 不实现完整 JGraphT raw/raw cluster。
- 不调整 dev R2 bucket 配置或真实凭据。

## 输入

```text
V1 CoverImagePipelineHandler -> coverImageService.promoteToR2(sourceImage.getId())
V1 CoverImageServiceImpl.promoteToR2()
V3 B7 rebuildUnifiedPipelineCoverImage()
V3 CoverImageAdminRepository.markUploadScheduled()
V3 CoverImageR2SyncService.sync()
V3 StorageUuidMessageParser
```

## 输出

- shared queue topic 与 ownership 更新。
- aggregation-worker cover R2 promote command publisher 与调用点。
- storage-service promote consumer/service。
- repository side effect 测试和服务/publisher/consumer 测试。
- docs/tasks/migration/index 回写。

## 影响范围

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java
shared/ircs-messaging/src/main/java/com/prodigalgal/ircs/messaging/QueueOwnership.java
services/ircs-aggregation-worker/**
services/ircs-storage-service/**
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 依赖

- B7 cover 选择规则已经稳定，并能返回 selected cover id 或至少可查询待 promote 的 selected cover。
- storage-service listener queue 可由 shared Rabbit topology 自动声明。
- dev 环境可以继续保持 listener 开关现状，本切片只做本地测试。

## 风险

- 如果 aggregation-worker 在事务未提交前发命令，storage-service 可能读到旧 cover 状态；
  第一刀使用现有 best-effort Rabbit publisher，后续如需严格 afterCommit 可再抽事务 publisher。
- 如果误用 `DOWNLOAD_IMAGE` topic，会把“下载外部图片”和“本地图片安排 R2 upload”混为一谈。
- V1 只在 `nextRetryTime == null` 时设置；V3 若无条件更新时间可能改变重试节奏。
- R2 inactive 时必须跳过，不应把本地图片强制标记为 due。

## 验收标准

```text
B8 MARK 1/5 storage queue topic and ownership for cover R2 promote are declared
B8 MARK 2/5 aggregation-worker publishes promote command only for selected LOCAL/LOCAL_STORED cover
B8 MARK 3/5 storage-service consumes promote command and schedules due upload only when R2 active
B8 MARK 4/5 ineligible cover or inactive R2 does not mutate next_retry_time
B8 MARK 5/5 local aggregation/storage/messaging tests pass and docs record remaining JGraphT/order/live gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-aggregation-worker:test :services:ircs-storage-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。V3 新增 `PROMOTE_COVER_R2` storage command，aggregation-worker
在 cover pipeline 选中 `LOCAL` + `LOCAL_STORED` cover 时发布 command；storage-service
消费后按 V1 `promoteToR2()` 语义只安排 due upload，不直接执行真实 R2 upload。

实现结果：

- `QueueTopic.PROMOTE_COVER_R2` 使用 `x.storage` / `image.cover.r2_promote` /
  `q.storage.cover_r2_promote`，ownership 为 `ircs-storage-service`。
- `JdbcAggregationRepository.rebuildUnifiedPipelineCoverImage()` 返回 V1 会触发
  `promoteToR2()` 的 cover ids；`AggregationService` 通过
  `AggregationStorageCommandPublisher` 发布 storage command。
- `CoverImageR2PromoteConsumer` 消费 UUID payload；`CoverImageR2PromoteService` 在
  R2 inactive 时跳过，不修改 DB。
- `CoverImageAdminRepository.scheduleR2UploadIfEligible()` 只更新 `storage_type='LOCAL'`、
  `status='LOCAL_STORED'` 且 `next_retry_time is null` 的 cover，写入
  `next_retry_time=now()` 与 `last_error=null`。
- 本切片不执行真实 R2 upload，真实上传继续由 `CoverImageR2SyncService` /
  `StorageR2Watchdog` 承接。

MARK：

```text
B8 MARK 1/5 storage queue topic and ownership for cover R2 promote are declared: done 2026-06-09
B8 MARK 2/5 aggregation-worker publishes promote command only for selected LOCAL/LOCAL_STORED cover: done 2026-06-09
B8 MARK 3/5 storage-service consumes promote command and schedules due upload only when R2 active: done 2026-06-09
B8 MARK 4/5 ineligible cover or inactive R2 does not mutate next_retry_time: done 2026-06-09
B8 MARK 5/5 local aggregation/storage/messaging tests pass and docs record remaining JGraphT/order/live gaps: done 2026-06-09
```

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-aggregation-worker:test :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未完成：

- `AggregationPipeline` handler order/failure isolation 的框架化 runtime。
- 完整 JGraphT raw/raw cluster、graph scoring 细节和 K8S live smoke。
