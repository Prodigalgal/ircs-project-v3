# 028-20-17 storage R2 watchdog claim-first

## 目标

将 `storage-service` R2 watchdog 从外层全局 mutex 下沉到 avatar/cover 行级 claim，允许多副本并发处理不同恢复批次，避免重复 republish/upload。

## 已完成

- `StorageR2Watchdog` 移除外层 `storage:r2-watchdog` Redis mutex，改为直接执行 claim-first batch。
- `AvatarSyncMemberRepository` 新增 `claimLocalAvatarMemberIds` 和 `releaseAvatarSyncClaim`：
  - `members.avatar_sync_claimed_by`
  - `members.avatar_sync_claimed_until`
  - `FOR UPDATE SKIP LOCKED`
- `CoverImageAdminRepository` 新增 `claimDueLocalR2UploadIds`，直接把 due cover claim 为 `UPLOADING`。
- `CoverImageR2SyncService` 允许处理已 claim 的 `UPLOADING` cover，并避免重复 `markUploading`。
- 新增 migrator：`platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-avatar-sync-claim-lease.sql`。
- 新增配置：`app.storage.r2.watchdog.worker-id`、`app.storage.r2.watchdog.claim-ttl`。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests "*StorageR2WatchdogTest" --tests "*AvatarSyncMemberRepositoryTest" --tests "*CoverImageAdminRepositoryTest" --tests "*CoverImageR2SyncServiceTest" :services:ircs-storage-service:bootJar :platform:ircs-migrator:processResources
```

结果：`BUILD SUCCESSFUL`。

## 遗留

- 尚未执行 K8S 多副本 contention live smoke；需要后续用两个 storage-service 副本验证同一批 avatar/cover 不会重复 claim。
- avatar publish 成功后保留 claim 到 TTL，由 consumer 成功更新 `avatar_url` 来自然移出候选集；如果 Rabbit publish 失败会释放 claim。
- 旧的 `app.storage.cluster-lock.*` 配置暂时保留，避免部署值断档；R2 watchdog 已不再使用它做外层互斥。
