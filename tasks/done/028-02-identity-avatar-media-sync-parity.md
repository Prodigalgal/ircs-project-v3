# 028-02 identity avatar media persistence/sync parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/migrations/041-identity-service-portal-avatar-upload-extraction.md`
- `tasks/done/025-19-identity-service-portal-avatar-upload-contract-mvp.md`

## 背景

`025-19` 已补齐 `POST /api/portal/profile/avatar` HTTP contract：identity-service 能接收
multipart `file`，校验图片并更新 `members.avatar_url`。但该切片明确不落盘、不接 R2、
不新增 avatar sync worker，返回的 `/media/avatars/{sha256}.{ext}` 只具备 URL 形状，
还不能证明媒体文件真实存在或可通过 storage-service 服务。

V1 行为中，头像上传会把文件落到本地存储 `avatars/` 前缀，返回 `/media/avatars/...`，
并在 R2 开启时投递 `q.storage.avatar_sync` 做后续同步。V3 当前 `/media/**` 的服务边界
归属 storage-service，所以 028-02 需要把头像上传从“只写 member 字段”推进到
“媒体在 storage-service 边界内落地并可服务”。

## 目标

- 上传头像时，identity-service 将头像文件同步写入 storage-service 管理的本地对象存储。
- 只有 storage-service 确认头像落地后，identity-service 才更新 `members.avatar_url`。
- 返回值继续保持 V1/025-19 兼容的 `{ "url": "/media/avatars/{hash}.{ext}" }`。
- storage-service 提供 `/media/**` 静态资源映射，使 `/media/avatars/...` 可由现有 BFF
  `/media` 代理路径访问。
- 保持低资源：不新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 非目标

- 不修改 portal-bff/admin-bff，避免破坏 multipart raw body 透传。
- 不在本切片实现 R2 凭据、真实 R2 上传或异步 avatar sync worker 完整调度。
- 不新增物理数据库表或严格数据所有权拆分。
- 不改变 cover image 管理接口的既有 contract。
- 不调整 `docs/index.md`、`tasks/index.md`、`docs/k8s/current-environment-2026-06-02.md`。

## 输入

- V1 `MemberProfileServiceImpl.uploadAvatar`、`AvatarSyncConsumer`、`WebConfig`。
- V3 `MemberProfileService.uploadAvatar` 当前 URL 生成逻辑。
- V3 storage-service `LocalObjectStorage`、`FileNormalizationService`、`ImageSecurityValidator`。
- 现有 BFF `/media` 路由到 storage-service 的边界。

## 输出

- 本地分析报告：`docs/reports/042-identity-avatar-media-sync-parity-analysis.md`。
- storage-service 头像落地内部接口、服务和 `/media/**` 静态资源映射。
- identity-service storage avatar client 与上传事务调整。
- 相关单元测试和 Gradle 验证结果。

## 影响范围

允许写入：

```text
tasks/in-progress/028-02-identity-avatar-media-sync-parity.md
docs/reports/042-identity-avatar-media-sync-parity-analysis.md
services/ircs-identity-service/**
services/ircs-storage-service/**
```

不包含：

```text
services/ircs-portal-bff/**
services/ircs-admin-bff/**
deploy/k8s/**
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
```

## 依赖

- `ircs-storage-service` 现有 ClusterIP 服务可被 identity-service 访问。
- storage-service 现有 `APP_STORAGE_BASE_PATH=/app/storage` 与 `emptyDir` 本地卷继续可用。
- portal/admin BFF 继续保持 raw body 代理，multipart 由 identity-service 解析。

## 风险

- storage-service 失败后，identity-service 需要避免写入不可服务的 `avatar_url`。
- storage-service 使用 `emptyDir`，Pod 重建后本地头像会丢失；这与当前低资源 dev 约束冲突，
  本切片只记录风险，不新增 PVC。
- HTTP 调用 storage-service 是同步链路，需保持 2MB 文件限制和低资源行为。
- R2 同步仍未实现，V1 的异步远端同步能力只作为后续增强项保留。

## 验收标准

- `POST /api/portal/profile/avatar` 成功时，storage-service 先落 `avatars/{hash}.{ext}`，
  identity-service 再写回同一 `/media/avatars/{hash}.{ext}`。
- storage-service 提供 `/media/avatars/{hash}.{ext}` 静态服务映射。
- storage-service 落地失败时，identity-service 不更新 `members.avatar_url`。
- 相关 Gradle 测试通过。
- 无新增 Pod、Service、HTTPRoute 或 BFF multipart 变更。

## 测试边界

本任务至少运行：

```text
.\gradlew.bat :services:ircs-storage-service:test
.\gradlew.bat :services:ircs-identity-service:test
```

本切片不执行 K8S rollout；主 agent 可在集成阶段执行 ClusterIP smoke：

```text
POST /api/portal/profile/avatar
GET  /media/avatars/{hash}.{ext}
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev get resourcequota
```

## 子任务

- `[x] 028-02-A`：完成 task spec 与分析报告。
- `[x] 028-02-B`：storage-service 增加头像本地落地和 `/media/**` 服务。
- `[x] 028-02-C`：identity-service 上传链路接入 storage-service 同步落地。
- `[x] 028-02-D`：补充测试并运行相关 Gradle 验证。
- `[x] 028-02-E`：回写实施结果、测试结果和剩余风险。

## 当前状态

2026-06-07：

- 已确认 `025-19` 只生成 URL 并更新 `members.avatar_url`，没有真实媒体落地。
- 已确认 V3 `/media/**` 路由边界归属 storage-service，identity-service 本地落盘不能保证
  BFF 后续 GET `/media/...` 可访问。
- 本轮采用 identity-service 同步调用 storage-service 内部头像上传接口的低资源方案。

## 实施结果

2026-06-07：

- storage-service 新增 `POST /internal/storage/avatars`，接收 multipart `file`。
- storage-service 新增 `AvatarStorageService`，复用 `ImageSecurityValidator`、
  `FileNormalizationService` 和 `LocalObjectStorage`，将头像写入 `avatars/{hash}.{ext}`。
- storage-service 新增 `/media/**` 静态资源映射，默认映射到 `app.storage.base-path`。
- identity-service 新增 `AvatarStorageClient` 和 `RestClientAvatarStorageClient`，默认调用
  `http://ircs-storage-service:8080/internal/storage/avatars`。
- `MemberProfileService.uploadAvatar` 改为先校验图片，再调用 storage-service，storage 成功后
  才更新 `members.avatar_url`。
- storage-service 返回无效或调用失败时，identity-service 返回 `avatar/storage.sync.failed`
  或 `avatar/storage.response.invalid`，不会更新 `members.avatar_url`。
- 本轮未修改 portal-bff/admin-bff，未影响 multipart raw body 透传。
- 本轮未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 测试结果

命令：

```text
.\gradlew.bat :services:ircs-storage-service:test
```

结果：

```text
BUILD SUCCESSFUL in 5s
12 actionable tasks: 3 executed, 9 up-to-date
```

覆盖：

- 头像上传写入 `avatars/{hash}.png` 并返回 `/media/avatars/{hash}.png`。
- 已存在同 hash 文件时不重复写入。
- 超过 2MB 返回 BAD_REQUEST。
- 非安全文件名返回 BAD_REQUEST。
- `/media/**` 默认映射口径为 `/media/**`。

命令：

```text
.\gradlew.bat :services:ircs-identity-service:test
```

结果：

```text
BUILD SUCCESSFUL in 7s
10 actionable tasks: 4 executed, 6 up-to-date
```

覆盖：

- 成功上传时先调用 storage client，再写回 `members.avatar_url`。
- 空文件、超过 2MB、不支持 content type 时不调用 storage client。
- storage sync 失败时不更新 `members.avatar_url`。

## 验收标记

```text
MARK 1/2 identity avatar upload persists media through storage-service
MARK 2/2 storage-service serves uploaded avatar through /media/avatars/*
```

## 剩余风险与交接

- dev storage-service 仍使用 `emptyDir`，Pod 重建后本地头像会丢失；本轮按低资源要求不新增
  PVC。
- R2/avatar async sync 仍未实现，`q.storage.avatar_sync` 的完整 V1 parity 需要后续单独拆分。
- 主 agent 集成阶段已完成 K8S rollout、ClusterIP smoke、HTTPRoute 和 ResourceQuota
  后置检查。

## 主线程集成验收

2026-06-07：

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL

ircs-storage-service@sha256:f73fd025c806bd09768b1336647b6806105896fbb3ece4ad19ca2ce689b26e02
ircs-identity-service@sha256:58381fc9c9acc25f82d0ac01a14d7e52623af5ca73e6bcce349e231a862f4456

deployment/ircs-storage-service    Ready 1/1
deployment/ircs-identity-service   Ready 1/1
deployment/ircs-portal-bff         Ready 1/1
```

K8S smoke：

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
