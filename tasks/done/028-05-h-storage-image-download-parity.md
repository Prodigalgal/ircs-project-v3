# 028-05-H storage image download parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/053-storage-image-download-parity-analysis.md`
- V1 `DOWNLOAD_IMAGE`
- V3 `services/ircs-storage-service`

## 背景

V1 `DOWNLOAD_IMAGE` 消费 `q.storage.image`，只处理 `EXTERNAL` 且状态为
`UNPROCESSED` 或 `FAILED` 的封面图片，并在真实下载中执行 enabled gate、SSRF 校验、
禁止 redirect、10MB 上限、Content-Type allowlist、失败 retry/backoff 和 max retry
到 `DEAD`。V3 storage-service 已有 consumer 和轻量下载服务，但缺这些安全/失败语义的完整覆盖。

## 目标

- 为 `DOWNLOAD_IMAGE` 补最小 dev-safe 真实下载语义。
- 保持 consumer 只承接 `q.storage.image` UUID payload。
- 成功下载后本地存储 cover，并 finalize 为 `LOCAL_STORED`。
- 失败下载记录 `FAILED`、`retry_count`、`next_retry_time` 和 `last_error`。
- unsafe URL 或达到 max retry 时不落对象，并进入 `DEAD`。
- 补 storage-service 单元测试覆盖成功、失败、安全阻断和 consumer eligibility。

## 非目标

- 不实现 R2 pending upload 或真实 R2 live。
- 不实现 normalization producer、download watchdog 或生产级 traffic governor。
- 不修改 metadata-worker 或生产级 deploy 拓扑。
- 不保留任何新增 K8S 对象或测试环境变量。

## 输入

- `q.storage.image` UUID message payload。
- `cover_images` 中 `EXTERNAL` + `UNPROCESSED/FAILED` 记录。
- `source_domains.domain_value` 与 `cover_images.original_url` 组合出的远端 URL。
- storage-service 配置项。

## 输出

- `services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/**`
- `services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/**`
- `services/ircs-storage-service/src/main/resources/application.yaml`
- `docs/reports/053-storage-image-download-parity-analysis.md`
- `tasks/done/028-05-h-storage-image-download-parity.md`
- 可选 `docs/migrations/054-storage-image-download-parity.md`

## 影响范围

包含：

```text
services/ircs-storage-service/**
scripts/smoke/storage-image-download-02805h-smoke.ps1
docs/reports/053-storage-image-download-parity-analysis.md
tasks/done/028-05-h-storage-image-download-parity.md
```

不包含：

```text
services/ircs-metadata-worker/**
production deploy topology
public HTTPRoute
external R2/provider/SMTP live credentials
production traffic governor
```

## 依赖

- `CoverImageAdminRepository.markFetching()` 负责过滤 eligible record。
- `FileNormalizationService` 和 `ImageSecurityValidator` 负责 magic bytes 与扩展名归一。
- `LocalObjectStorage` 负责 dev 本地对象存储。
- `R2ObjectStorage.isActive()` 仅影响 finalize 后是否保留下一步上传 retry 时间，本轮不扩 R2。

## 风险

- SSRF 校验和本地真下载测试存在冲突，测试应通过明确的 test-only allow-local 配置隔离。
- redirect 阻断和 Content-Type 预检需要在下载前失败，避免写入对象。
- max retry `DEAD` 依赖 repository 当前 retry 计数读取结果，测试要覆盖边界值。

## 验收标准

```text
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

## 测试边界

本地单元测试：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
```

H-live 允许短暂执行以下 dev 操作：

```text
push ircs-storage-service ARM64 image
rollout restart deployment/ircs-storage-service
temporarily set APP_STORAGE_IMAGE_ALLOW_LOCAL_ADDRESSES=true for in-cluster fixture server
create/delete temporary fixture Pod/Service
publish q.storage.image fixture
verify DB/object/queue/DLQ cleanup
restore storage-service env
```

## 关联文档

- `docs/reports/053-storage-image-download-parity-analysis.md`
- 可选 `docs/migrations/054-storage-image-download-parity.md`

## 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 审计任务：`tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`
- 同批并行任务：`tasks/done/028-05-g-metadata-pending-gather-parity.md`

## 当前状态

2026-06-07 已完成 storage-service 本地实现、单元测试、ARM64 镜像推送、K8S rollout
和 `q.storage.image` live smoke，记录 `MARK 5/5`。本文件已移动到 `tasks/done/`。

## 实现结果

- 新增 `app.storage.image.download-enabled`，关闭时跳过下载且不改元数据。
- 新增 `app.storage.image.allow-local-addresses`，默认 `false`；仅用于测试或明确 dev 场景放行本地地址。
- 下载前执行 http/https、host、user info 和解析后 IP SSRF 校验。
- 禁止 3xx redirect。
- 下载前执行 `Content-Type` allowlist 预检。
- `Content-Length` 和流式读取均执行 10MB 上限。
- 成功下载后本地存储并 finalize `LOCAL_STORED`。
- 失败下载沿用 `markFailed()` 记录 `FAILED`、`retry_count`、`next_retry_time`、`last_error`，
  达到 max retry 后进入 `DEAD`。
- `markFetching()` 只允许 `EXTERNAL` + `UNPROCESSED/FAILED`，不再自动处理 `DEAD`。

## 完成标记

```text
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

## 验证结果

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s
12 actionable tasks: 12 executed
```

主线程集成验证：

```text
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-storage-service:test :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s
```

H-live 集群验证：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
Pushed harbor.mnnu.eu.org/ircs/ircs-storage-service:dev for linux/arm64

kubectl -n ircs-dev rollout restart deployment/ircs-storage-service
kubectl -n ircs-dev rollout status deployment/ircs-storage-service --timeout=240s
deployment "ircs-storage-service" successfully rolled out

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

## 遗留问题

- 未扩展 R2 pending upload、normalization producer/watchdog 或生产级 traffic governor；这些已拆到 `tasks/backlog/` 后续独立处理。
