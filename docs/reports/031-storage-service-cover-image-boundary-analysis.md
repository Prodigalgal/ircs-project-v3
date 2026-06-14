# 031 storage-service cover image admin API 边界分析

状态：已完成

更新时间：2026-06-06

## 背景

`025` V1 路由覆盖闭环已经完成 search、portal、interaction、task 和 content
切片。当前下一批缺口是 V1 `CoverImageController` 的封面图片管理接口。V1 该能力
已经经过验证，V3 需要按无损迁移口径承接到 `ircs-storage-service`，并保持原有
`/api/v1/cover-images` 路径不变。

## V1 接口边界

来源：

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\storage\controller\CoverImageController.java
```

目标迁移接口共 7 个：

```text
GET    /api/v1/cover-images
GET    /api/v1/cover-images/{id}
DELETE /api/v1/cover-images/{id}
POST   /api/v1/cover-images/upload
POST   /api/v1/cover-images/fetch
POST   /api/v1/cover-images/{id}/sync-r2
POST   /api/v1/cover-images/{id}/retry
```

接口级复现计数规则：

```text
每个接口只有在 V3 完整迁移且 live smoke 复现后，才标记 1/1。
测试准备、清理 SQL、健康检查和辅助 API 调用不计入接口复现次数。
```

## V1 行为摘要

`GET /api/v1/cover-images`：

- 支持 `Pageable`。
- 可按 `status`、`storageType`、`url` 过滤。
- 返回 `Page<CoverImageVO>`。

`GET /api/v1/cover-images/{id}`：

- 按 ID 查询封面图片元数据。
- 不存在返回 `404`。

`DELETE /api/v1/cover-images/{id}`：

- V1 不直接删除 DB 行，而是设置 `status=PENDING_DELETE`、`nextRetryTime=now()`。
- 发布图片 unlink command，由 storage worker 异步物理删除和最终 DB 删除。
- 已经是 `PENDING_DELETE` 或不存在时不抛错。

`POST /api/v1/cover-images/upload`：

- 接收 multipart `file`。
- 校验文件名、真实 MIME、最大 10MB。
- 支持 `image/jpeg`、`image/png`、`image/webp`、`image/gif`、`image/avif`、
  `image/bmp`、`image/x-icon`。
- 以文件内容 SHA-256 生成 `covers/<hash>.<ext>`。
- 本地存储后创建 `LOCAL/LOCAL_STORED` 记录。

`POST /api/v1/cover-images/fetch`：

- 请求体为 `{ "url": "..." }`。
- 创建或复用 `EXTERNAL/UNPROCESSED` 封面记录。
- 复用 `source_domains` 迁移桥，保持 `(original_url, source_domain_id)` 唯一性。
- 对 `FAILED/DEAD` 状态会重置为 `UNPROCESSED` 并清理错误。

`POST /api/v1/cover-images/{id}/sync-r2`：

- R2 未启用时返回业务错误。
- 仅允许 `LOCAL/LOCAL_STORED` 状态进入同步。
- 设置 `nextRetryTime=now()`、`lastError=null`，V1 会立即提交异步 R2 处理。

`POST /api/v1/cover-images/{id}/retry`：

- 仅允许 `FAILED`、`DEAD`、`UNPROCESSED` 状态重试下载。
- 重置为 `EXTERNAL/UNPROCESSED`，清理 retry/error/storage path。
- V1 会立即提交异步下载处理。

## V3 当前状态

`ircs-storage-service` 实现前已有能力：

- `ImageDeletionConsumer` 消费 `q.storage.delete`。
- `CoverImagePhysicalDeletionService` 删除本地/R2 对象并删除 `cover_images` 元数据。
- `LocalObjectStorage` 已有本地路径穿越防护和删除能力。
- `R2ObjectStorage` 已按 dev 配置默认禁用。
- dev Deployment 复用 `emptyDir` 挂载 `/app/storage`，不新增 PVC。

实现前缺口：

- 还没有 `CoverImageController`。
- 还没有封面管理 DTO、分页查询、上传存储、URL fetch、R2 sync/retry command。
- `LocalObjectStorage` 目前只有删除能力，需要补 `store`。

本轮已补齐：

- `CoverImageController`、`CoverImageAdminService`、`CoverImageAdminRepository`
  和 `CoverImageVO` 等价响应 DTO。
- `ImageSecurityValidator`、`FileNormalizationService` 和 `LocalObjectStorage.store/retrieve`。
- `StorageCommandPublisher`、`ImageDownloadConsumer` 和 `CoverImageDownloadService`。
- `R2ObjectStorage.store/exists`，为 R2 enabled 场景保留同步路径。

## 目标设计

目标服务：

```text
ircs-storage-service
```

数据边界：

```text
cover_images      -> storage-service 写边界
source_domains    -> 迁移桥，用于保持 V1 URL/domain 去重行为
RabbitMQ          -> 继续发布 storage delete command
/app/storage      -> dev 本地封面文件落盘路径
```

依赖策略：

- 继续使用现有 shared PostgreSQL 作为迁移桥，不做物理 DB 拆分。
- 继续使用 JDBC，不引入 JPA entity。
- 只补 `spring-data-commons` 支撑 `Pageable/PageImpl`。
- 不新增 Deployment、Service、HTTPRoute、PVC 或中间件。

## ASCII 架构图

```text
        Admin/API caller
              |
              v
  +-------------------------------+
  | ircs-storage-service          |
  | /api/v1/cover-images          |
  +---------------+---------------+
                  |
      +-----------+-----------+
      |                       |
      v                       v
+-------------+        +---------------+
| PostgreSQL  |        | /app/storage  |
| cover_images|        | emptyDir dev  |
| source_domains       | covers/<hash> |
+------+------+        +-------+-------+
       |                       |
       | delete command        |
       v                       v
+-------------------------------+
| RabbitMQ q.storage.delete      |
| ImageDeletionConsumer          |
+-------------------------------+
```

## 非目标

- 不开放公网 HTTPRoute。
- 不迁移到 `.214` 机器。
- 不新增新的 storage worker Pod。
- 不实现生产 R2 凭证配置和真实公网切换。
- 不在本切片迁移 V1 `StorageCleanupScheduler` 或 maintenance 批处理。
- 不把 `source_domains` 物理 ownership 一次性拆分出共享 DB。

## 风险

- `sync-r2` 在 dev 中 R2 默认禁用，live smoke 应以 V1 等价的错误状态复现该接口。
- `retry` 的 V1 立即异步下载处理已由 `DOWNLOAD_IMAGE` queue 和
  `ImageDownloadConsumer` 承接；真实外网下载仍受 dev 网络和 URL 可达性影响。
- `DELETE` 会异步删除 DB 行；smoke 需要隔离测试数据，并确认不误删已有业务数据。
- 上传文件落在 Pod `emptyDir`，重启会丢失，符合 dev 低资源策略但不是生产持久化方案。

## 验收标准

- 7 个目标接口均在 `ircs-storage-service` 暴露。
- `GET` 列表和详情返回字段与 `CoverImageVO` 对齐。
- `upload` 能保存小型测试图片并写入 `LOCAL/LOCAL_STORED` 元数据。
- `fetch` 能创建或复用 `EXTERNAL/UNPROCESSED` 元数据。
- `delete` 能标记 `PENDING_DELETE` 并发布删除命令。
- `sync-r2` 在 dev R2 disabled 场景能返回明确错误。
- `retry` 能按 V1 状态机重置失败记录。
- 本地测试、全仓 build、ARM64 Jib push、K8S rollout 和 ClusterIP live smoke 全部通过。
- live smoke 按接口粒度标记 `1/1`，总计 `7/7`。

## 验收结果

```text
.\gradlew.bat :services:ircs-storage-service:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
Jib linux/arm64 push -> sha256:061d84a5f1a3f6d77efa71e37faab4bc326f4ab7b616d9ba9007ce2b143a73e4
kubectl -n ircs-dev rollout status deploy/ircs-storage-service -> successfully rolled out
ClusterIP live smoke -> 7/7 MARK
smoke residue -> 0 cover-smoke source domains, 0 linked cover_images
HTTPRoute -> none
ResourceQuota -> pods=23/24, services=19/20
```
