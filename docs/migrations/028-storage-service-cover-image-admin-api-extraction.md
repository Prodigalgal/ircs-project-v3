# 028 storage-service cover image admin API extraction

状态：已完成

更新时间：2026-06-06

## 背景

本迁移承接 `025` V1 路由覆盖闭环中的 storage 缺口。V1
`CoverImageController` 已验证可用，V3 本轮将 7 条封面图片管理接口迁移到
`ircs-storage-service`，保持 `/api/v1/cover-images` 路径不变。

上游文档：

```text
docs/requirements/025-v1-route-coverage-closeout.md
docs/reports/025-v1-v3-route-coverage-audit.md
docs/reports/031-storage-service-cover-image-boundary-analysis.md
tasks/done/025-06-storage-service-cover-image-admin-api-lossless.md
```

## 迁移接口

```text
GET    /api/v1/cover-images
GET    /api/v1/cover-images/{id}
DELETE /api/v1/cover-images/{id}
POST   /api/v1/cover-images/upload
POST   /api/v1/cover-images/fetch
POST   /api/v1/cover-images/{id}/sync-r2
POST   /api/v1/cover-images/{id}/retry
```

## 实现结果

新增和修改范围：

```text
services/ircs-storage-service/build.gradle
services/ircs-storage-service/src/main/resources/application.yaml
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageController.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageAdminService.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageAdminRepository.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageDtos.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageStatus.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageUrlResolver.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/ImageSecurityValidator.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/FileNormalizationService.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/StorageCommandPublisher.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageDownloadService.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/ImageDownloadConsumer.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/LocalObjectStorage.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/R2ObjectStorage.java
```

核心行为：

- `upload` 校验文件名、MIME、10MB 上限，生成 `covers/<sha256>.<ext>` 并写入
  `/app/storage`。
- `fetch` 复用 `source_domains` 迁移桥，按 V1 domain + relative path 方式创建或复用
  `EXTERNAL/UNPROCESSED` 记录。
- `delete` 先标记 `PENDING_DELETE`，再发布 `CMD_IMAGE_UNLINK`，由 content-service
  解绑引用后发布 `EVENT_IMAGE_UNLINKED`，最终由 storage-service 物理删除。
- `retry` 重置为 `EXTERNAL/UNPROCESSED` 并发布 `DOWNLOAD_IMAGE`，本轮补齐
  `ImageDownloadConsumer` 和轻量下载处理。
- `sync-r2` 在 dev R2 disabled 下返回 V1 等价业务错误；R2 active 时会读取本地对象并
  调用 R2 put object。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

镜像：

```text
image:  harbor.mnnu.eu.org/ircs/ircs-storage-service:dev
digest: sha256:061d84a5f1a3f6d77efa71e37faab4bc326f4ab7b616d9ba9007ce2b143a73e4
arch:   linux/arm64
```

K8S rollout：

```text
kubectl -n ircs-dev rollout status deploy/ircs-storage-service
deployment "ircs-storage-service" successfully rolled out

pod: ircs-storage-service-8d49c6b6-jxldq
ready: 1/1
restarts: 0
```

ClusterIP live smoke：

```text
MARK 1/7 POST /api/v1/cover-images/upload -> HTTP 200
MARK 2/7 GET /api/v1/cover-images -> HTTP 200
MARK 3/7 GET /api/v1/cover-images/{id} -> HTTP 200
MARK 4/7 POST /api/v1/cover-images/fetch -> HTTP 200
MARK 5/7 POST /api/v1/cover-images/{id}/sync-r2 -> HTTP 400
MARK 6/7 POST /api/v1/cover-images/{id}/retry -> HTTP 202
MARK 7/7 DELETE /api/v1/cover-images/{id} -> HTTP 204
```

说明：

- `sync-r2` 在 `ircs-dev` 中返回 HTTP 400 是预期结果，因为
  `APP_STORAGE_R2_ENABLED=false`，与 V1 R2 disabled 业务错误一致。
- smoke 从 `.106` 节点直接访问 storage-service ClusterIP，不新增临时 Pod。
- smoke 残留检查返回 `0` 个 `cover-smoke` source domain 和 `0` 个关联
  `cover_images`。

资源与冲突检查：

```text
No HTTPRoute in ircs-dev.
pods:     23 / 24
services: 19 / 20
```

## 路由覆盖影响

```text
V3 service Controller routes: 93 -> 100
Direct missing routes:         121 -> 114
storage module missing:        7 -> 0
```

## 遗留事项

- `source_domains` 仍是共享 PostgreSQL 迁移桥，后续需要按 content/storage ownership
  收敛。
- dev 使用 `emptyDir` 存储 `/app/storage`，生产持久化/R2 策略需要单独切换验证。
- Spring Data `PageImpl` JSON 序列化有框架警告；当前与其他 V3 Page 接口保持一致，
  后续可统一切换到稳定分页 DTO。

