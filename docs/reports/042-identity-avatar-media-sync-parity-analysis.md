# 042 identity avatar media sync parity 分析报告

状态：已完成（本地代码、ARM64 rollout 与 ClusterIP smoke 已闭环）

日期：2026-06-07

## 结论

`POST /api/portal/profile/avatar` 当前仍停留在 `025-19` 的 HTTP contract MVP：
identity-service 校验 multipart 图片后生成 `/media/avatars/{sha256}.{ext}`，然后更新
`members.avatar_url`。该 URL 没有对应的 storage-service 文件落地，也没有 R2/avatar
sync 副作用，因此属于 028 行为级 parity 缺口。

本轮最小可验证方案是：新增 storage-service 内部头像上传能力，复用已有
`LocalObjectStorage`、`FileNormalizationService` 和 `ImageSecurityValidator`，把头像落到
`avatars/` 前缀；identity-service 在更新 `members.avatar_url` 前同步调用该内部接口；
storage-service 增加 `/media/**` 静态资源映射，让返回 URL 可由现有 `/media` 路由服务。

## V1 行为

V1 `MemberProfileServiceImpl.uploadAvatar` 的关键副作用：

- 校验空文件和 2MB 大小限制。
- 使用图片安全校验和文件名校验。
- 通过 `FileNormalizationService` 生成 `avatars/{hash}.{ext}`。
- 使用本地 `ImageStorageService` 写入本地存储。
- 使用 `app.storage.public-path` 生成 `/media/avatars/{hash}.{ext}`。
- 更新 `members.avatar_url`。
- R2 开启时投递 `QueueTopic.SYNC_AVATAR`，由 `AvatarSyncConsumer` 上传 R2 并更新远端 URL。
- `WebConfig` 把 `/media/**` 映射到本地存储目录。

## V3 当前状态

已完成：

- identity-service 有 `POST /api/portal/profile/avatar`。
- 接口支持 multipart raw body 由 BFF 透传，后端解析。
- identity-service 校验空文件、2MB、content type 和基础图片签名。
- 成功时写回 `members.avatar_url`。

缺口：

- identity-service 没有本地或 storage-service 文件写入。
- storage-service 没有头像上传 contract。
- storage-service 当前没有 `/media/**` 静态资源 handler。
- `q.storage.avatar_sync` 仍未实现远端头像同步消费者；但本地 `/media/avatars/**`
  可服务的媒体落地链路已完成。

## 方案选择

采用同步 internal storage upload：

```text
portal/admin BFF raw body
  -> identity-service POST /api/portal/profile/avatar
  -> identity-service 校验 2MB 与图片签名
  -> identity-service POST storage-service /internal/storage/avatars
  -> storage-service 写 /app/storage/avatars/{hash}.{ext}
  -> identity-service 更新 members.avatar_url
  -> BFF GET /media/avatars/{hash}.{ext} 代理到 storage-service
```

选择理由：

- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。
- 不修改 BFF，避免影响 multipart raw body。
- 让媒体文件落在 `/media/**` 所属的 storage-service 边界内，而不是 identity-service
  自己的容器文件系统。
- storage-service 已有本地对象存储和图片安全能力，改动小、可测试。

不采用的方案：

- identity-service 自己落本地盘：K8S 中 `/media/**` 由 storage-service 服务，identity
  本地文件不可被 storage-service 读取。
- 本轮直接实现 R2/avatar worker：涉及凭据、远端副作用和队列调度，超出低资源最小闭环。
- 新增 PVC 或共享卷：改变 dev 资源形态，不符合本轮低资源要求。

## 预期实现边界

storage-service：

- 新增内部 `POST /internal/storage/avatars`，接收 multipart `file`。
- 复用现有图片安全校验、哈希归一化和 `LocalObjectStorage`。
- 返回 `{ "url": "/media/avatars/{hash}.{ext}", "storageKey": "avatars/{hash}.{ext}" }`。
- 增加 `/media/**` 静态资源映射到 `app.storage.base-path`。

identity-service：

- 新增可配置 storage-service base URL，默认 `http://ircs-storage-service:8080`。
- `uploadAvatar` 继续保持原有 2MB 和错误语义。
- storage-service 上传成功后，使用返回 URL 更新 `members.avatar_url`。
- storage-service 失败时抛出 `avatar/storage.sync.failed`，不写 member。

## 风险与后置项

- dev storage-service 使用 `emptyDir`，Pod 重建会丢失本地头像；这是当前低资源约束下的已知
  剩余风险。
- R2 同步和 `q.storage.avatar_sync` 消费者仍未实现，后续可拆独立任务。
- K8S rollout、ClusterIP smoke 和 ResourceQuota 后置检查已由主线程完成。

## 验收标记

```text
MARK 1/2 identity avatar upload persists media through storage-service
MARK 2/2 storage-service serves uploaded avatar through /media/avatars/*
```

## 实施结果

storage-service：

- 新增 `AvatarStorageController`，内部接口为 `POST /internal/storage/avatars`。
- 新增 `AvatarStorageService`，把头像归一化为 `avatars/{hash}.{ext}` 并写入
  `LocalObjectStorage`。
- 新增 `AvatarStorageDtos.AvatarUploadResponse`，返回 `url`、`storageKey`、`mimeType`、
  `fileSize` 和 `fileHash`。
- 新增 `StorageMediaWebConfig`，默认把 `/media/**` 映射到 `app.storage.base-path`。

identity-service：

- 新增 `AvatarStorageClient` 和 `RestClientAvatarStorageClient`。
- 新增配置 `app.identity.storage-service.base-url`，默认
  `http://ircs-storage-service:8080`。
- `MemberProfileService.uploadAvatar` 保留原 2MB、content type、图片签名校验。
- 上传成功路径改为：先 storage-service 落地，再更新 `members.avatar_url`。
- storage-service 调用失败或返回 URL 无效时不更新 member，避免写入不可服务 URL。

## 本地验证

命令：

```text
.\gradlew.bat :services:ircs-storage-service:test
```

结果：

```text
BUILD SUCCESSFUL in 5s
12 actionable tasks: 3 executed, 9 up-to-date
```

命令：

```text
.\gradlew.bat :services:ircs-identity-service:test
```

结果：

```text
BUILD SUCCESSFUL in 7s
10 actionable tasks: 4 executed, 6 up-to-date
```

## 当前边界

- 本轮未改 BFF，`spring.servlet.multipart.enabled=false` 的 raw body 透传边界保持不变。
- 本轮未新增 Pod、Service、HTTPRoute、PVC 或中间件。
- K8S rollout、ClusterIP smoke、`kubectl -n ircs-dev get httproute` 和
  ResourceQuota 检查已由主线程完成。
- R2 sync 与 `q.storage.avatar_sync` 消费者仍是后续 parity 缺口。
- dev storage-service 仍使用 `emptyDir`，本地媒体随 Pod 生命周期丢失。

## 主线程集成验收

2026-06-07 已完成 ARM64 镜像推送、K8S rollout 和 ClusterIP smoke：

```text
ircs-storage-service@sha256:f73fd025c806bd09768b1336647b6806105896fbb3ece4ad19ca2ce689b26e02
ircs-identity-service@sha256:58381fc9c9acc25f82d0ac01a14d7e52623af5ca73e6bcce349e231a862f4456

deployment/ircs-storage-service    Ready 1/1
deployment/ircs-identity-service   Ready 1/1
deployment/ircs-portal-bff         Ready 1/1
```

Smoke：

```text
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
MARK 1/8 identity avatar upload persists media through storage-service
MARK 2/8 storage-service serves uploaded avatar through /media/avatars/*
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```
