# 003 正式拆分 portal-bff 与 admin-bff

状态：通过

日期：2026-06-06

## 背景

`ircs-dev` 已部署 V1 `huawai` portal 门户、V1 `ircs-frontend` admin 门户和
`ircs-frontend-gateway`。初始 gateway 只承担路径反向代理，不具备 BFF 能力。用户明确要求
直接正式拆分 `portal-bff` 和 `admin-bff`，让门户验证入口具备稳定后端边界。

## 决策

新增两个独立 Spring Boot 服务和两个独立 K8S Pod/Service：

```text
ircs-portal-bff
ircs-admin-bff
```

`ircs-frontend-gateway` 继续只做 edge 路由：

```text
portal 页面请求 -> ircs-portal-bff -> V3 portal/identity/search/interaction/storage
admin 页面请求  -> ircs-admin-bff  -> V3 identity/content/catalog/task/ops/storage/magnet/scraper/interaction
```

## BFF 职责

- 承接 portal/admin 前端契约，不让前端直接感知多个微服务地址。
- 按页面域分流到 V3 微服务。
- 透传 Authorization、Cookie、Content-Type、请求体和响应状态。
- 为后续页面级聚合、V1 响应兼容、菜单/权限聚合和错误降级预留边界。
- 不接回 V1 monolith 做兜底，不伪造未迁移接口成功。

## 当前 MVP 边界

第一阶段 BFF 以“薄代理 + 明确 ownership”为主：

```text
portal-bff:
  /api/backend/api/portal/** -> normalize to /api/portal/**
  /api/portal/auth/**        -> identity-service
  /api/portal/profile/**     -> identity-service
  /api/portal/interaction/** -> interaction-service
  /api/portal/feedback/**    -> interaction-service
  /api/portal/search/**      -> search-service
  /api/portal/**             -> portal-service
  /media/**                  -> storage-service

admin-bff:
  /api/v1/auth/**             -> identity-service
  /api/v1/members/**          -> identity-service
  /api/v1/dashboard/**        -> ops-service
  /api/v1/ops/**              -> ops-service
  /api/v1/raw-videos/**       -> content-service
  /api/v1/unified-videos/**   -> content-service
  /api/v1/playlists/**        -> content-service
  /api/v1/actors/**           -> content-service
  /api/v1/directors/**        -> content-service
  /api/v1/source-domains/**   -> content-service
  /api/v1/resolvers/**        -> content-service
  /api/v1/cover-images/**     -> storage-service
  /api/v1/collection-tasks/** -> task-service
  /api/v1/catalog/**          -> catalog-service
  dictionary routes           -> catalog-service
  /api/v1/configs/**          -> config-service
  /api/v1/common/**           -> config-service
  /api/v1/credentials/**      -> credential-service
  /api/v1/magnets/**          -> magnet-service
  /api/v1/magnet-providers/** -> magnet-service
  /api/v1/scraper/manual/**   -> scraper-service
  /api/v1/debug/**            -> ops-service
  /api/v1/messages/**         -> interaction-service
  /media/**                   -> storage-service
```

未迁移接口仍返回目标服务真实状态，例如 admin `/api/v1/auth/**` 当前会暴露缺口。

## 执行结果

```text
local build: .\gradlew.bat build -> SUCCESS
images:
  harbor.mnnu.eu.org/ircs/ircs-portal-bff:dev sha256:6ec945cf90eff8cbcb2bb83b808df8bd3d55e6083c265d776e614a220058d47e
  harbor.mnnu.eu.org/ircs/ircs-admin-bff:dev  sha256:bb36dbc33885b85737f11654625cadc8bf34fab97fb06b04f096915e5f54122a
k8s:
  ircs-portal-bff Ready 1/1
  ircs-admin-bff Ready 1/1
  no HTTPRoute in ircs-dev
smoke:
  portal-bff service-info 200
  admin-bff service-info 200
  portal metadata/home/auth-pow via gateway 200
  admin dashboard/ops via gateway 200
  admin auth-pow via gateway 403, kept as V3 identity-service gap
```

## 资源决策

由于新增 2 个 BFF Pod 和 2 个 Service，`ircs-dev` ResourceQuota 小幅提高：

```text
pods:     30
services: 26
```

目标部署后仍保留 2 个 Pod 和 2 个 Service 缓冲。

## 后果

- frontend gateway 配置更简单，业务路径不继续堆在 Nginx。
- portal/admin 可以独立演进页面聚合和兼容逻辑。
- BFF 数量增加会消耗少量资源；开发环境采用低资源 Java profile。
- 完整接口迁移仍由 `025` 路由覆盖闭环负责，BFF 只负责暴露和承接前端契约。
