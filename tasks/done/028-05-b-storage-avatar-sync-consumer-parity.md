# 028-05-B storage avatar sync consumer parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md`
- `shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java`
- V1 `AvatarSyncConsumer`
- V1 `MemberProfileServiceImpl.uploadAvatar`

## 背景

`028-02` 已完成 V3 本地头像媒体链路：portal 上传头像后 storage-service 写本地文件，
identity-service 更新 `members.avatar_url`，storage-service 服务 `/media/avatars/**`。
但 V1 还有 R2 远端同步队列：上传成功后发布 `SYNC_AVATAR`，消费者把本地头像上传 R2，
再把 `members.avatar_url` 改成 R2 public URL 并删除本地文件。该远端同步路径在 V3 仍缺失。

## 目标

- 为 `ircs-storage-service` 增加 `q.storage.avatar_sync` consumer。
- 复现 V1 的 R2 disabled/inactive skip 语义。
- 复现 V1 的本地头像读取、R2 上传、R2 URL 回写和本地文件清理。
- 为 `ircs-identity-service` 上传头像成功路径补 `SYNC_AVATAR` producer。
- 补充单元测试、K8S smoke 脚本和迁移记录。

## 非目标

- 不新增 K8S Pod、Service、PVC、HTTPRoute 或中间件。
- 不打开 dev 环境真实 R2 写入。
- 不修改 portal/admin UI。
- 不改头像上传 HTTP 同步返回语义。
- 不实现 V1 `AvatarSyncScheduler` watchdog；该项可后续单拆。

## 输入

- RabbitMQ `q.storage.avatar_sync` 消息，payload 为 `memberId` UUID 字符串或 JSON 字符串。
- `members.avatar_url` 当前值。
- 本地 storage path，例如 `avatars/{hash}.png`。
- R2 配置：`APP_STORAGE_R2_ENABLED`、bucket、endpoint/account、access key、secret key、
  `APP_STORAGE_R2_PUBLIC_DOMAIN`。

## 输出

- `services/ircs-storage-service/.../AvatarSyncConsumer.java`
- `services/ircs-storage-service/.../AvatarSyncService.java`
- `services/ircs-storage-service/.../AvatarSyncMemberRepository.java`
- `services/ircs-identity-service/.../AvatarSyncPublisher.java`
- `MemberProfileService.uploadAvatar` producer 接入
- storage/identity 单元测试
- `scripts/smoke/storage-avatar-sync-02805b-smoke.ps1`
- `docs/migrations/048-storage-avatar-sync-consumer-parity.md`

## 影响范围

包含：

```text
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/**
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/**
services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/**
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/**
scripts/smoke/storage-avatar-sync-02805b-smoke.ps1
deploy/k8s/dev/storage-service-dev.yaml
deploy/k8s/dev/identity-service-dev.yaml
docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md
tasks/done/028-05-b-storage-avatar-sync-consumer-parity.md
```

不包含：

```text
services/ircs-portal-bff/**
services/ircs-admin-bff/**
portal/admin UI
production namespace
public HTTPRoute
```

## 依赖

- `QueueTopic.SYNC_AVATAR` 已在 shared contracts 中定义。
- RabbitMQ topology 已由 `shared:ircs-messaging` 提供。
- identity-service 已有 RabbitMQ starter 和 RabbitMQ dev env。
- storage-service dev Deployment 已启用 listener。
- dev 环境 `APP_STORAGE_R2_ENABLED=false`，真实上传由单元测试 mock 证明。

## 风险

- dev 环境没有真实 R2 凭据，K8S smoke 不能证明外部对象存储实际写入。
- storage-service 读取 `members` 表是迁移期 bridge，后续物理拆库需要再收敛。
- 如果上游发送非 UUID payload，`StorageUuidMessageParser` 会 reject 并进入 DLQ；本轮保持
  V1 UUID payload 兼容。

## 验收标准

- `AvatarSyncConsumer` 监听 `q.storage.avatar_sync`。
- R2 disabled/inactive 时消息被 ack，member avatar URL 不变，DLQ 不增长。
- R2 active 且本地文件存在时，消费者上传 R2、回写 R2 public URL、删除本地文件。
- 非本地头像 URL 或 member 不存在时安全跳过。
- `MemberProfileService.uploadAvatar` 在本地头像持久化并更新 member 后发布 `SYNC_AVATAR`。
- `.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test` 通过。
- K8S smoke 返回 `MARK 4/4`，后置检查无 HTTPRoute、ResourceQuota 不增加 Pod/Service。

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test --rerun-tasks
```

K8S 后置：

```text
.\scripts\smoke\storage-avatar-sync-02805b-smoke.ps1
kubectl -n ircs-dev rollout status deploy/ircs-storage-service --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-identity-service --timeout=240s
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota ircs-dev-quota
```

## 当前状态

2026-06-07：

- 已完成 task spec 与分析报告。
- 已实现 storage consumer、identity producer、R2 inactive skip、R2 active unit path、
  本地文件删除和 member URL 回写。
- 已新增 `scripts/smoke/storage-avatar-sync-02805b-smoke.ps1`。
- 已完成本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL
```

- 已完成合并相关模块测试：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test :services:ircs-metadata-worker:test :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

- 已完成 storage/identity ARM64 镜像推送和 `ircs-dev` rollout。
- 已完成 K8S smoke：

```text
MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 SYNC_AVATAR queues are empty and DLQ has no residue
MARK 4/4 smoke cleanup leaves no DB residue and no HTTPRoute conflict
```

## 完成标记

```text
MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 R2 enabled unit path uploads local avatar, rewrites R2 URL and deletes local copy
MARK 4/4 identity uploadAvatar publishes SYNC_AVATAR after local avatar persistence
```

## 后续独立缺口

- 真实 R2 live smoke 需要在 dev 明确配置 R2 凭据后执行。
- V1 `AvatarSyncScheduler` watchdog 未在本切片实现。
