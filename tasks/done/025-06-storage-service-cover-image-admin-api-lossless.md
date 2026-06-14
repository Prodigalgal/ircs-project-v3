# 025-06 storage-service cover image admin API 无损迁移

状态：已完成

父任务：

```text
tasks/in-progress/025-v1-route-coverage-closeout-parent.md
```

上游来源：

```text
docs/requirements/025-v1-route-coverage-closeout.md
docs/reports/025-v1-v3-route-coverage-audit.md
docs/reports/031-storage-service-cover-image-boundary-analysis.md
docs/migrations/004-storage-service-extraction.md
```

## 背景

V1 `CoverImageController` 提供封面图片管理能力，共 7 个 HTTP 接口。V3
`ircs-storage-service` 当前只承接了图片物理删除 worker，尚未承接 admin API。
由于 V1 功能已经经过验证，本任务按无损迁移口径补齐这些接口。

## 目标

- 在 `ircs-storage-service` 中新增 `/api/v1/cover-images` 管理 API。
- 保持 V1 路径、HTTP method、核心状态机和返回字段兼容。
- 复用现有 PostgreSQL、RabbitMQ 和 `/app/storage`。
- 按接口级 live smoke 标记 `7/7` 复现。

## 非目标

- 不新增 Deployment、Service、HTTPRoute、PVC 或中间件。
- 不迁移 dev 资源到 `.214`。
- 不实现生产 R2 接入切换。
- 不在本任务中迁移 storage cleanup scheduler。
- 不在本任务中迁移 storage cleanup scheduler。

## 输入

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\storage\controller\CoverImageController.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\storage\service\impl\CoverImageServiceImpl.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/
deploy/k8s/dev/storage-service-dev.yaml
```

## 输出

- `ircs-storage-service` 新增 cover image admin Controller、Service、Repository、DTO。
- `LocalObjectStorage` 支持上传写入。
- storage-service 测试覆盖核心查询、upload/fetch/delete/retry/sync-r2 分支。
- ARM64 镜像推送到 `harbor.mnnu.eu.org/ircs/ircs-storage-service:dev`。
- `ircs-dev` rollout 后完成 ClusterIP smoke。
- 迁移记录和任务文档回写。

## 影响范围

代码：

```text
services/ircs-storage-service
```

部署：

```text
deploy/k8s/dev/storage-service-dev.yaml
```

文档：

```text
docs/reports/031-storage-service-cover-image-boundary-analysis.md
docs/migrations/028-storage-service-cover-image-admin-api-extraction.md
docs/reports/025-v1-v3-route-coverage-audit.md
docs/k8s/current-environment-2026-06-02.md
docs/index.md
tasks/index.md
tasks/in-progress/025-v1-route-coverage-closeout-parent.md
```

## 依赖

- `ircs-dev` PostgreSQL、RabbitMQ、storage-service Deployment 已 Ready。
- `APP_STORAGE_BASE_PATH=/app/storage`。
- `APP_STORAGE_R2_ENABLED=false` in dev。
- Hikari dev 连接池已压缩为 `maximumPoolSize=2`。

## 风险

- 删除接口会发布异步删除命令，smoke 必须只使用临时测试记录。
- `sync-r2` 在 dev 环境应复现 R2 disabled 错误，不强行启用 R2。
- multipart 上传需要生成安全的小测试图片，避免大文件和路径穿越。
- `source_domains` 仍是共享表迁移桥，后续需要独立 ownership 收敛。

## 验收标准

目标接口：

```text
1. GET    /api/v1/cover-images
2. GET    /api/v1/cover-images/{id}
3. DELETE /api/v1/cover-images/{id}
4. POST   /api/v1/cover-images/upload
5. POST   /api/v1/cover-images/fetch
6. POST   /api/v1/cover-images/{id}/sync-r2
7. POST   /api/v1/cover-images/{id}/retry
```

每个接口完成迁移并在 live smoke 复现后，标记 `1/1`。本任务总验收为 `7/7`。

## 测试边界

必须执行：

```text
.\gradlew.bat :services:ircs-storage-service:test
.\gradlew.bat build
kubectl -n ircs-dev rollout status deploy/ircs-storage-service
ClusterIP live smoke for 7 target interfaces
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota
```

不适用：

- 不做公网入口测试。
- 不做生产 R2 上传验收。

## 当前状态

```text
已完成。
接口复现标记：7/7。
迁移记录：docs/migrations/028-storage-service-cover-image-admin-api-extraction.md
```

## 实现结果

已新增：

```text
CoverImageController
CoverImageAdminService
CoverImageAdminRepository
CoverImageDtos
CoverImageStatus
CoverImageUrlResolver
ImageSecurityValidator
FileNormalizationService
StorageCommandPublisher
CoverImageDownloadService
ImageDownloadConsumer
```

已增强：

```text
LocalObjectStorage.store/retrieve
R2ObjectStorage.store/exists
services/ircs-storage-service/build.gradle
services/ircs-storage-service/src/main/resources/application.yaml
```

## 验证结果

```text
.\gradlew.bat :services:ircs-storage-service:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=client -f deploy/k8s/dev/storage-service-dev.yaml -> configured (dry run)
Jib linux/arm64 push -> sha256:061d84a5f1a3f6d77efa71e37faab4bc326f4ab7b616d9ba9007ce2b143a73e4
kubectl -n ircs-dev rollout status deploy/ircs-storage-service -> successfully rolled out
kubectl -n ircs-dev get httproute -> No resources found
kubectl -n ircs-dev describe resourcequota -> pods=23/24, services=19/20
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

清理结果：

```text
cover-smoke source_domains -> 0
cover-smoke linked cover_images -> 0
```
