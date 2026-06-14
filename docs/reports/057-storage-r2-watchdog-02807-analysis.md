# 057 storage R2 watchdog 02807 分析

状态：完成第一刀

更新时间：2026-06-07

关联任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

上游来源：

- `docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md`
- `docs/reports/053-storage-image-download-parity-analysis.md`
- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`

## 背景

`028-05-B` 已完成 avatar `SYNC_AVATAR` consumer，`028-05-H` 已完成 cover `DOWNLOAD_IMAGE`
本地下载链路。但 V1 还有 watchdog/recovery 语义：系统启动和周期运行时会发现仍停留在本地
storage 的头像或图片，并重新触发 R2 同步。当前 V3 如果错过队列消息、R2 临时不可用或
`next_retry_time` 到期，缺少这一层恢复。

## 本轮切片

第一刀只做 dev-safe watchdog 语义，不打开真实 R2：

```text
R2 inactive:
  watchdog no-op

R2 active:
  scan members.avatar_url like local media prefix
  -> publish q.storage.avatar_sync

  scan cover_images where storage_type=LOCAL and status=LOCAL_STORED and next_retry_time due
  -> upload to R2
  -> finalize REMOTE_STORED
  -> on failure mark retry/backoff
```

## 非目标

- 不写入真实 R2 credential。
- 不新增 K8S 资源或公共 HTTPRoute。
- 不做大批量迁移。
- 不把 cover image 本地副本删除作为本轮要求。

## MARK 设计

```text
MARK 1/4 R2 inactive watchdog skips avatar and cover recovery
MARK 2/4 R2 active watchdog republishes local avatar members to q.storage.avatar_sync
MARK 3/4 R2 active watchdog uploads due LOCAL_STORED cover image to R2
MARK 4/4 R2 upload failure records retry/backoff without aborting watchdog batch
```

## 测试边界

本轮只要求本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
```

真实 R2 live smoke 后续仍需在 dev credential 明确配置后单独执行。

## 实现结果

- 新增 `StorageR2Watchdog`，按小批量扫描本地头像和 due cover image。
- 新增 `CoverImageR2SyncService`，在 R2 active 时将 `LOCAL_STORED` cover image 上传到 R2，
  成功后 finalize，失败时写回 retry/backoff。
- `StorageCommandPublisher` 增加 avatar sync 发布方法，用于 watchdog 重投
  `q.storage.avatar_sync`。
- `AvatarSyncMemberRepository` 增加本地头像 member 扫描。
- `CoverImageAdminRepository` 增加 due `LOCAL_STORED` cover image 扫描。
- storage-service application config 增加 watchdog 开关、初始延迟、固定延迟和 batch size。

## 验证结果

本地测试：

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

ARM64 镜像与 dev rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-storage-service
kubectl -n ircs-dev rollout status deployment/ircs-storage-service --timeout=240s
```

结果：

```text
deployment/ircs-storage-service successfully rolled out
pod: ircs-storage-service-d77d864c7-9tb78 Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:1355d90561d00e412d333bcf95566987697cd93f4861f27d4bdd17a9a57b7172
APP_STORAGE_R2_ENABLED=false
ResourceQuota: pods=28/30, services=24/26
HTTPRoute in ircs-dev: No resources found
```

## 剩余项

- 本报告形成时，`028-07` 总任务仍保持进行中。
- avatar R2 live 上传、R2 URL 回写、本地清理和真实 R2 对象 cleanup 仍需在 dev R2
  credential 明确可用后单独 smoke。
- cover image 真实 R2 live smoke 仍未执行；本轮只证明 watchdog/retry 的本地行为和
  R2 inactive dev 安全路径。

2026-06-09 收敛更新：本报告的剩余项已被后续 `028-07-C` 到 `028-07-J` 逐项关闭；
`028-07` 父任务当前已完成，最新总账见 `docs/reports/093-v1-v3-parity-progress-ledger.md`。
