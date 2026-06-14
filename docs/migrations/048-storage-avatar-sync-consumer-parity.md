# 048 storage avatar sync consumer parity

状态：已完成

更新时间：2026-06-07

## 来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md`
- `tasks/done/028-05-b-storage-avatar-sync-consumer-parity.md`

## 迁移目标

复现 V1 头像上传后的异步同步副作用：

```text
identity uploadAvatar
  -> 本地 storage-service 持久化头像
  -> members.avatar_url = /media/avatars/...
  -> publish SYNC_AVATAR(memberId)

storage AvatarSyncConsumer
  -> consume q.storage.avatar_sync
  -> R2 inactive 时 ack + no-op
  -> R2 active 时上传本地头像到 R2
  -> 回写 members.avatar_url 为 R2 public URL
  -> 删除本地副本
```

## 实现结果

- `ircs-storage-service` 新增 `AvatarSyncConsumer`、`AvatarSyncService`、
  `AvatarSyncMemberRepository`。
- `ircs-identity-service` 新增 `AvatarSyncPublisher`，并在 `MemberProfileService.uploadAvatar`
  成功后发布 `SYNC_AVATAR`。
- 新增 storage/identity 单元测试，覆盖 R2 disabled skip、R2 active 上传回写和 producer 发布。
- 新增 `scripts/smoke/storage-avatar-sync-02805b-smoke.ps1` 验证 dev R2 disabled 路径。

## 验证

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test :services:ircs-metadata-worker:test :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

K8S smoke：

```text
MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 SYNC_AVATAR queues are empty and DLQ has no residue
MARK 4/4 smoke cleanup leaves no DB residue and no HTTPRoute conflict
```

## 剩余边界

- dev 环境未开启真实 R2，因此 R2 live 上传需后续凭据启用后单独验证。
- V1 `AvatarSyncScheduler` watchdog 尚未迁移。
- storage-service 读取 `members` 表是共享库迁移期 bridge。
