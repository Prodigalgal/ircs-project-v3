# 028-20-11C storage media R2 canonical cleanup health

状态：完成

日期：2026-06-11

## 背景

D23 决策要求 media storage 保留本地文件 + R2 双轨，其中 R2 public object 是 canonical，local 只作为缓存、临时源或 dev fallback。
V1 在 R2 上传成功后，如果图片本地下载功能关闭，会自动清理本地临时文件；V1 也提供 R2 health indicator 暴露 R2 gate/config 状态。

## 范围

- `CoverImageR2SyncService` 成功上传并 finalize R2 metadata 后，根据 `app.storage.image.download-enabled` 判断是否清理本地临时副本。
- `download-enabled=false`：本地文件视为 R2 上传中转文件，成功后 `deleteIfExists(storagePath)`。
- `download-enabled=true`：保留本地副本，继续作为 local cache / dev fallback。
- 新增 `StorageR2HealthIndicator`，复刻 V1 disabled/up、enabled inactive/down、active/up 的 health 语义。

## 非目标

- 不执行真实 R2 live smoke。
- 不修改 cover image schema。
- 不改变 storage-service R2 credential 来源。
- 不新增 PDF/Office/text extraction 类库。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests "*CoverImageR2SyncServiceTest" --tests "*CoverImageDownloadServiceTest" --tests "*StorageR2WatchdogTest" --tests "*CoverImagePhysicalDeletionServiceTest" --tests "*StorageR2HealthIndicatorTest" :services:ircs-storage-service:bootJar
```

结果：通过。

## 遗留

- 统一 K8S smoke 窗口确认 `/actuator/health` 中 R2 component 和真实 R2 object path 可见性。
- 其他 library follow-up 继续按 V1 行为缺口逐项判断。
