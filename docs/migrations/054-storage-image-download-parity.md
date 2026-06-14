# 054 storage image download parity

状态：已完成

日期：2026-06-07

关联：

- `docs/reports/053-storage-image-download-parity-analysis.md`
- `tasks/done/028-05-h-storage-image-download-parity.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`

## 背景

`028-05-H` 承接 V1 `DOWNLOAD_IMAGE` 的最小 dev-safe 真实下载/失败语义。V3 既有
`ImageDownloadConsumer` 和 `CoverImageDownloadService`，但缺 enabled gate、SSRF 校验、
redirect 阻断、Content-Type allowlist 预检以及对应单元测试。

## 行为复现

本次迁移完成：

```text
q.storage.image UUID payload
  -> ImageDownloadConsumer
  -> CoverImageDownloadService.process(imageId)
  -> EXTERNAL + UNPROCESSED/FAILED eligibility
  -> download-enabled gate
  -> SSRF / redirect / Content-Type / 10MB safety checks
  -> LOCAL_STORED on success
  -> FAILED retry/backoff or DEAD on failure
```

完成标记：

```text
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

## 实现范围

修改文件：

```text
services/ircs-storage-service/src/main/resources/application.yaml
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageDownloadService.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageAdminRepository.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/CoverImageDownloadServiceTest.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/ImageDownloadConsumerTest.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/CoverImageAdminRepositoryTest.java
```

## 关键约束

- `app.storage.image.allow-local-addresses` 默认 `false`，只用于测试或明确 dev 场景。
- `DEAD` 记录不再被 `markFetching()` 自动消费；需要 admin retry 先 reset 为 `UNPROCESSED`。
- 本轮不实现 R2 pending upload、watchdog、normalization producer 或生产级 traffic governor。
- 本轮不修改生产级 deploy 拓扑；H-live 仅执行 dev ARM64 image rollout 和临时 smoke env。

## 验证

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s
12 actionable tasks: 12 executed
```

```text
.\scripts\smoke\storage-image-download-02805h-smoke.ps1
MARK 1/5 DOWNLOAD_IMAGE consumer listens on q.storage.image
MARK 2/5 DOWNLOAD_IMAGE fixture finalizes cover_images as LOCAL_STORED
MARK 3/5 downloaded cover object exists under storage-service local storage
MARK 4/5 q.storage.image and DLQ are empty after live download
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

后置复核：

```text
fixture Pod/Service/ConfigMap: deleted
q.storage.image=0 messages, 1 consumer
q.storage.image.dlq=0 messages
source_domains/cover_images smoke residue: 0
ResourceQuota: pods=28/30, services=24/26
HTTPRoute: No resources found in ircs-dev namespace
storage-service imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:2fdc14e5fc39f1939ebde89c8d06963a4cc39d720a085469fb22ba678d498df4
```

## 后续

- R2 live、watchdog 和生产级 traffic governor 已拆到 backlog 独立推进。
