# 047 storage avatar sync consumer parity 分析报告

状态：已完成（consumer、identity producer、ARM64 rollout 与 dev-safe RabbitMQ smoke 已闭环）

更新时间：2026-06-07

## 背景

`028-05` 队列消费者审计确认 `SYNC_AVATAR` 的 owner 是 `ircs-storage-service`，
队列名为 `q.storage.avatar_sync`。`028-02` 已经完成 V3 本地头像上传、`members.avatar_url`
回写和 `/media/avatars/**` 静态访问链路，但 V1 的远端 R2 同步消费者仍未复现。

这意味着 V3 当前能把头像存到本地 storage-service，却不能通过异步队列把本地头像同步到
R2、回写 R2 public URL，也不能通过队列 smoke 证明远端同步路径可用。

## 上游来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`
- `shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java`
- V1 `AvatarSyncConsumer`
- V1 `AvatarSyncScheduler`
- V1 `MemberProfileServiceImpl.uploadAvatar`
- V3 `services/ircs-storage-service`
- V3 `services/ircs-identity-service`

## V1 行为摘要

V1 行为由上传入口、消费者和 watchdog 组成：

```text
MemberProfileServiceImpl.uploadAvatar
  -> localStorage.store(...)
  -> members.avatar_url = STORAGE_PUBLIC_PATH + "/" + storageKey
  -> when R2_ENABLED publish QueueTopic.SYNC_AVATAR(memberId)

AvatarSyncConsumer.onMessage(memberId)
  -> R2 disabled or inactive: skip
  -> load members.avatar_url
  -> only process avatar_url with local STORAGE_PUBLIC_PATH prefix
  -> localStorage.retrieve(relativePath)
  -> r2Storage.store(data, relativePath, mimeType)
  -> members.avatar_url = R2_PUBLIC_DOMAIN + "/" + relativePath
  -> localStorage.delete(relativePath)

AvatarSyncScheduler.scheduleSync
  -> when system ready and R2 active
  -> find members whose avatar_url still uses local prefix
  -> publish QueueTopic.SYNC_AVATAR(memberId)
```

## V3 当前状态

已完成：

- `ircs-identity-service` `MemberProfileService.uploadAvatar` 会调用 storage-service 存本地头像。
- `ircs-storage-service` `AvatarStorageService` 会把头像写入 `LocalObjectStorage`，返回
  `/media/avatars/{hash}.{ext}`。
- `deploy/k8s/dev/storage-service-dev.yaml` 已设置 `APP_STORAGE_LISTENER_ENABLED=true`。
- `shared:ircs-messaging` 已声明 `QueueTopic.SYNC_AVATAR` ownership 和 RabbitMQ topology。

本轮前缺口：

- `ircs-storage-service` 没有 `@RabbitListener(queues = q.storage.avatar_sync)`。
- 没有按 memberId 查询和更新 `members.avatar_url` 的 storage 侧 repository。
- 没有本地头像读取、R2 上传、R2 URL 回写、本地清理的 service。
- `ircs-identity-service` 上传成功后没有发布 `SYNC_AVATAR`。
- dev 环境 `APP_STORAGE_R2_ENABLED=false`，因此 K8S smoke 需要验证安全 no-op 和队列清理；
  R2 enabled 的实际上传用单元测试覆盖，避免在无凭据 dev 环境触发外部写入。

本轮完成后状态：

- `ircs-storage-service` 已新增 `AvatarSyncConsumer`，监听 `q.storage.avatar_sync`。
- `AvatarSyncService` 已复现 R2 inactive/disabled skip、读取本地头像、上传 R2、回写 R2 URL
  和删除本地副本的成功路径。
- `AvatarSyncMemberRepository` 已在迁移期 bridge 中读取和更新 `members.avatar_url`。
- `ircs-identity-service` 已新增 `AvatarSyncPublisher`，头像上传成功后发布
  `QueueTopic.SYNC_AVATAR(memberId)`。
- `scripts/smoke/storage-avatar-sync-02805b-smoke.ps1` 已验证 dev R2 disabled 路径会 ack
  消息、不改 member URL、不产生 DLQ 和 HTTPRoute 冲突。

## 本轮方案

最小 1:1 切片：

```text
identity uploadAvatar
  -> store local avatar through storage-service
  -> members.avatar_url = /media/avatars/...
  -> publish QueueTopic.SYNC_AVATAR(memberId)

RabbitMQ q.storage.avatar_sync
  -> AvatarSyncConsumer.onMessage(memberId)
  -> AvatarSyncService.sync(memberId)
  -> if R2 inactive: skip
  -> if member/avatar missing or not local: skip
  -> localObjectStorage.retrieve(relativePath)
  -> r2ObjectStorage.store(data, relativePath, mimeType)
  -> members.avatar_url = R2 public URL
  -> localObjectStorage.deleteIfExists(relativePath)
```

实现边界：

- storage-service 增加 `AvatarSyncConsumer`、`AvatarSyncService`、`AvatarSyncMemberRepository`。
- identity-service 增加轻量 `AvatarSyncPublisher`，复用现有 RabbitMQ 配置。
- producer 默认发布 memberId 字符串，与 V3 `StorageUuidMessageParser` 和 V1 UUID payload 对齐。
- R2 inactive 时消费者 ack 后安全跳过，不写 DB、不进 DLQ。
- local avatar 不存在时记录 warn 后跳过，避免 dev smoke 因无 fixture 文件制造失败消息。
- R2 enabled 的成功路径通过单元测试 mock `R2ObjectStorage`、`LocalObjectStorage` 和 repository 验证。

## 非目标

- 不新增 Pod、Service、PVC、HTTPRoute 或中间件。
- 不打开 dev 环境真实 R2 写入，不写入外部对象存储。
- 不把头像上传接口改为异步最终一致；HTTP 上传仍同步存本地并回写本地 URL。
- 不实现完整 `AvatarSyncScheduler`，watchdog 另拆 follow-up，避免本切片跨入调度/自适应负载。
- 不修改 portal/admin 前端。

## 风险

- dev 环境 R2 关闭，只能用 K8S smoke 证明 consumer 消费、跳过和队列清理；真实 R2 上传需要
  凭据开启后再做 live smoke。
- identity 上传后无条件发布会增加少量 RabbitMQ 消息；storage consumer 在 R2 inactive 时
  会立即 ack 并 no-op，资源影响可控。
- storage-service 需要读取 `members` 表，这是当前共享库迁移阶段的 bridge；后续物理拆库时要
  改为 identity/storage 事件或内部 API。

## 验收标准

```text
MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 R2 enabled unit path uploads local avatar, rewrites R2 URL and deletes local copy
MARK 4/4 identity uploadAvatar publishes SYNC_AVATAR after local avatar persistence
```

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL
```

合并相关模块测试：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test :services:ircs-metadata-worker:test :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

K8S rollout：

```text
ircs-storage-service ARM64 rollout: success
ircs-identity-service ARM64 rollout: success
```

K8S smoke：

```text
.\scripts\smoke\storage-avatar-sync-02805b-smoke.ps1

MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 SYNC_AVATAR queues are empty and DLQ has no residue
MARK 4/4 smoke cleanup leaves no DB residue and no HTTPRoute conflict
```

完成标记：

```text
MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 R2 enabled unit path uploads local avatar, rewrites R2 URL and deletes local copy
MARK 4/4 identity uploadAvatar publishes SYNC_AVATAR after local avatar persistence
```

## 剩余边界

- dev 环境仍保持 `APP_STORAGE_R2_ENABLED=false`，未对真实 R2 凭据执行 live 上传。
- V1 `AvatarSyncScheduler` watchdog 尚未实现；后续需要单独补充本地头像滞留扫描并重投
  `SYNC_AVATAR` 的任务。
- storage-service 读取 `members` 表仍是共享库迁移期 bridge，后续物理拆库时要改为事件或
  identity 内部 API。

## 测试边界

必须运行：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test
```

K8S 后置 smoke：

```text
publish SYNC_AVATAR fixture
verify q.storage.avatar_sync has consumer
verify dev R2 disabled message is acked and DLQ remains 0
verify temporary member avatar_url is unchanged
verify q.storage.avatar_sync and q.storage.avatar_sync.dlq are empty after cleanup
kubectl -n ircs-dev get httproute remains empty
ResourceQuota unchanged or reduced impact
```
