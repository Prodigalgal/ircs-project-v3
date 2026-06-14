# 032 identity-service admin auth API extraction

状态：已完成

日期：2026-06-06

关联任务：

- `tasks/done/025-10-identity-service-admin-auth-api-mvp.md`

关联报告：

- `docs/reports/036-identity-service-admin-auth-boundary-analysis.md`

## 背景

V1 admin 门户 `ircs-frontend` 登录链路依赖：

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

portal/admin 前端和 BFF 已在 `ircs-dev` 部署完成，但 `/api/v1/auth/**` 在 V3
`ircs-identity-service` 中尚未承接，admin 登录页只能暴露 403 缺口。本切片把 V1
admin auth API 迁移到 identity-service，并通过 admin-bff 做 live smoke。

## 迁移结果

- `ircs-identity-service` 新增 `AdminAuthController`。
- `ircs-identity-service` 新增 `AdminAuthService`。
- `IdentityDtos` 新增 admin login、change-password 和 token response DTO。
- `IdentityConfigKey` 新增 `security.admin.username` 和 `security.admin.password`。
- `SystemConfigRepository` 支持 upsert `system_configs`。
- `JwtTokenService` 新增 admin JWT 签发，token subject 为 admin username，role 为 `ROLE_ADMIN`。
- `JwtAuthenticationFilter` 同时识别 member token 和 admin token。
- `SecurityConfig` 放行 `/api/v1/auth/**`，并对 identity-service 的 `/api/v1/**` 使用 `ROLE_ADMIN` 保护。

## 覆盖路由

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

## 镜像与 rollout

```text
ircs-identity-service: harbor.mnnu.eu.org/ircs/ircs-identity-service:dev sha256:cc5114036b97b5416a5298fe43d071c6519df5b3b8dd4673bc97a3d3997a931d
```

`ircs-dev` 当前状态：

```text
ircs-identity-service Ready 1/1
ircs-admin-bff        Ready 1/1
HTTPRoute             none
ResourceQuota         pods=28/30, services=24/26
```

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-identity-service:test -> PASS
.\gradlew.bat build -> PASS
```

K8S 验证：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s -> PASS
```

ClusterIP smoke 通过 `admin-bff` 完成：

```text
GET  /api/v1/auth/pow             -> 200
POST /api/v1/auth/login           -> token returned
POST /api/v1/auth/change-password -> 200
POST /api/v1/auth/login           -> token returned with changed password
```

smoke 使用临时 admin username/password hash 写入 `system_configs`，结束后恢复原
`security.admin.username`、`security.admin.password` 和 `security.jwt.iat-floor`。
恢复校验结果：

```text
temporary config residue count -> 0
```

## 复现标记

三条 V1 admin auth API 均已完整迁移并经 live smoke 复现：

```text
MARK 1/3 GET  /api/v1/auth/pow
MARK 2/3 POST /api/v1/auth/login
MARK 3/3 POST /api/v1/auth/change-password
```

按 `025` route audit 估算，V3 public Controller route 数从 `108` 增至 `111`，
direct missing routes 从 `106` 降至 `103`。
