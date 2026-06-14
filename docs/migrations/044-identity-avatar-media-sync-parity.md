# 044 identity avatar media sync parity

状态：已完成

日期：2026-06-07

## 范围

本记录承接 `028-02`，把 `POST /api/portal/profile/avatar` 从 `025-19` 的 HTTP
contract MVP 推进到行为 parity：头像上传不再只更新 `members.avatar_url`，而是先由
identity-service 同步调用 storage-service 内部头像落地接口，storage-service 写入
`avatars/{hash}.{ext}` 后，再由 identity-service 写回 `/media/avatars/{hash}.{ext}`。

## 实现

- storage-service 新增 `POST /internal/storage/avatars`。
- storage-service 复用 `ImageSecurityValidator`、`FileNormalizationService` 和
  `LocalObjectStorage`，把头像写入 dev `emptyDir` 的 `/app/storage/avatars/`。
- storage-service 新增 `/media/**` 静态资源映射。
- identity-service 新增 `AvatarStorageClient` 和 `RestClientAvatarStorageClient`。
- `MemberProfileService.uploadAvatar` 改为 storage-service 成功后才更新 member。
- 修复 `RestClientAvatarStorageClient` Spring 构造器选择问题，并新增 context 测试。

## 镜像与部署

```text
ircs-storage-service@sha256:f73fd025c806bd09768b1336647b6806105896fbb3ece4ad19ca2ce689b26e02
ircs-identity-service@sha256:58381fc9c9acc25f82d0ac01a14d7e52623af5ca73e6bcce349e231a862f4456

deployment/ircs-storage-service    Ready 1/1
deployment/ircs-identity-service   Ready 1/1
deployment/ircs-portal-bff         Ready 1/1
```

未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-identity-service:test --rerun-tasks
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
```

Smoke 标记：

```text
MARK 1/8 identity avatar upload persists media through storage-service
MARK 2/8 storage-service serves uploaded avatar through /media/avatars/*
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 剩余边界

- dev storage-service 仍使用 `emptyDir`，Pod 重建会丢本地头像。
- R2/avatar async sync 与 `q.storage.avatar_sync` consumer 仍需后续独立 parity 切片。
