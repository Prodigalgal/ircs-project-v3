# 036 identity-service admin auth API 边界分析

状态：已完成

更新时间：2026-06-06

## 背景

portal/admin 门户和 `portal-bff`/`admin-bff` 已部署完成，admin API 流量已经通过
`admin-bff` 路由到各 V3 服务。当前 admin 门户登录链路仍暴露真实缺口：

```text
GET /api/v1/auth/pow -> 403
```

V1 admin 前端 `ircs-frontend` 的认证 API 只依赖：

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

V1 后端 `AuthController` 使用 `system_configs` 中的管理员账号、BCrypt 密码和 JWT secret：

```text
security.admin.username
security.admin.password
security.jwt.secret
security.jwt.iat-floor
```

## 目标

本切片实现 `025-10 identity-service admin auth API`：

- 在 `ircs-identity-service` 承接 V1 admin auth 三条路由。
- 复用现有 `PoWService`，让 admin login 与 portal login 使用同一 PoW contract。
- 复用 `system_configs`，不新增数据库表。
- admin login 成功后签发 admin JWT，兼容 V1 subject 为 admin username 的语义。
- `JwtAuthenticationFilter` 同时识别 member token 和 admin token。
- admin change-password 修改 `security.admin.password` 并刷新 `security.jwt.iat-floor`。
- 通过 `admin-bff` 对 admin 门户认证链路做 ClusterIP smoke。

## 非目标

- 不实现 `/api/v1/members/**` 后台会员管理。
- 不迁移完整 RBAC、用户表或多管理员账号。
- 不改变 V1 admin 前端代码。
- 不新增 Pod、Service、HTTPRoute、PVC 或 RabbitMQ topic。
- 不把 admin auth 接回 V1 单体。

## API 契约

```text
GET /api/v1/auth/pow
  -> PoWChallenge { id, difficulty }

POST /api/v1/auth/login
  body { username, password, powVerification }
  -> { token }

POST /api/v1/auth/change-password
  body { oldPassword, newPassword }
  -> { message }
```

`change-password` 继续按 V1 兼容口径校验旧密码；后续如果要收紧为必须携带 admin token，需要单独决策并同步前端契约。

## 边界图

```text
admin frontend
  -> frontend gateway
  -> admin-bff
  -> ircs-identity-service
       -> PoWService / Valkey
       -> system_configs
       -> JwtTokenService
```

## 风险

- dev 集群若没有 `security.admin.password`，login smoke 需要先读取或临时写入测试 admin 密码，完成后恢复。
- `change-password` 会刷新 JWT iat floor，smoke 必须避免破坏现有开发登录口令。
- V1 允许 `/api/v1/auth/**` 匿名访问；本切片按兼容优先保留该行为。
- admin token 引入后，identity-service 的 `/api/v1/**` 应按 `ROLE_ADMIN` 保护，避免未来 `/api/v1/members/**` 裸露。

## 验收标准

- 本地 task spec 已建立，并挂到 `025` 父任务。
- `GET /api/v1/auth/pow` 经 `admin-bff` 返回 200。
- `POST /api/v1/auth/login` 能校验 PoW、管理员账号和 BCrypt 密码，成功返回 token。
- admin token 能通过 identity-service JWT 过滤器识别为 `ROLE_ADMIN`。
- `POST /api/v1/auth/change-password` 能校验旧密码、写入新 BCrypt hash、刷新 iat floor。
- 单元测试覆盖 login 成功/失败、change-password 成功/旧密码失败、admin JWT 解析。
- `.\gradlew.bat :services:ircs-identity-service:test` 和 `.\gradlew.bat build` 通过。
- ARM64 镜像 rollout 后 `ircs-identity-service` Ready 1/1。
- ClusterIP smoke 通过 `admin-bff` 覆盖 `pow/login/change-password`，并清理或恢复临时配置。
- `ircs-dev` 仍无 HTTPRoute，Pod/Service quota 不增加。

## 完成结果

本切片已完成并归档到：

```text
docs/migrations/032-identity-service-admin-auth-api-extraction.md
tasks/done/025-10-identity-service-admin-auth-api-mvp.md
```

验证摘要：

```text
.\gradlew.bat :services:ircs-identity-service:test -> PASS
.\gradlew.bat build -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
ircs-identity-service rollout -> Ready 1/1
identity-service image digest -> sha256:cc5114036b97b5416a5298fe43d071c6519df5b3b8dd4673bc97a3d3997a931d
admin-bff smoke pow/login/change-password -> PASS
temporary config residue count -> 0
HTTPRoute in ircs-dev -> none
ResourceQuota -> pods=28/30, services=24/26
```
