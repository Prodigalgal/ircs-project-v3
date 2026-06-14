# 058 storage R2 watchdog recovery

状态：完成第一刀

更新时间：2026-06-07

关联任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

上游来源：

- `docs/reports/057-storage-r2-watchdog-02807-analysis.md`
- `docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md`
- `docs/reports/053-storage-image-download-parity-analysis.md`

## 迁移内容

本切片补齐 storage-service 的 R2 watchdog/recovery 最小行为：

- R2 inactive 时 watchdog 跳过，不写 DB、不发队列消息。
- R2 active 时扫描本地头像 URL，并重投 `q.storage.avatar_sync`。
- R2 active 时扫描 due `LOCAL_STORED` cover image，并尝试上传 R2。
- cover upload 成功后 finalize 为远端存储状态。
- cover upload 失败时写回 retry/backoff，且不中断 watchdog batch。

## 代码范围

```text
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/StorageR2Watchdog.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageR2SyncService.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/StorageCommandPublisher.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/AvatarSyncMemberRepository.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageAdminRepository.java
services/ircs-storage-service/src/main/resources/application.yaml
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/*R2*Test.java
```

## 验证

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 14s
```

MARK：

```text
MARK 1/4 R2 inactive watchdog skips avatar and cover recovery
MARK 2/4 R2 active watchdog republishes local avatar members to q.storage.avatar_sync
MARK 3/4 R2 active watchdog uploads due LOCAL_STORED cover image to R2
MARK 4/4 R2 upload failure records retry/backoff without aborting watchdog batch
```

dev rollout：

```text
deployment/ircs-storage-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:1355d90561d00e412d333bcf95566987697cd93f4861f27d4bdd17a9a57b7172
ResourceQuota: pods=28/30, services=24/26
HTTPRoute in ircs-dev: No resources found
```

## 遗留边界

- 本切片不包含真实 R2 credential smoke。
- avatar R2 live 上传、R2 URL 回写、本地清理和 R2 对象 cleanup 仍留在 `028-07`
  后续切片。
- cover image 真实 R2 live smoke 需要在 dev R2 credential 配置明确后执行。
