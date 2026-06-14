# 025-10 identity-service admin auth API MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/036-identity-service-admin-auth-boundary-analysis.md`
- `docs/decisions/003-formal-portal-admin-bff-split.md`

## 背景

admin 门户已经通过 `admin-bff` 接入 V3，但 `/api/v1/auth/**` 仍没有在
`ircs-identity-service` 中复现。V1 admin 前端登录依赖 `pow/login/change-password`
三条接口，当前 admin 登录页只能暴露缺口，无法作为持续后台验证入口。

## 目标

- 在 `ircs-identity-service` 新增 V1 兼容 admin auth API。
- 让 `admin-bff` 现有 `/api/v1/auth/**` 路由直接可用。
- 复用 `system_configs` 中的 admin username/password/JWT 配置。
- 复用 `PoWService` 和 `JwtTokenService`。
- 保持低资源部署，不新增任何 K8S 资源。

## 非目标

- 不实现 `/api/v1/members/**`。
- 不实现多管理员、RBAC 或用户表。
- 不修改 V1 admin 前端。
- 不新增数据库 migration。
- 不改变 portal member auth/profile 既有语义。

## 输入

V1：

```text
modules/system/controller/AuthController.java
infrastructure/security/filter/JwtAuthenticationFilter.java
common/enums/SystemConfigKey.java
ircs-frontend/src/features/auth/authApi.js
```

V3：

```text
services/ircs-identity-service
services/ircs-admin-bff
deploy/k8s/dev/identity-service-dev.yaml
deploy/k8s/dev/frontend-bff-dev.yaml
```

## 输出

- `ircs-identity-service` admin auth controller/service/DTO。
- `JwtTokenService` 和 `JwtAuthenticationFilter` admin token 支持。
- `SystemConfigRepository` admin password/iAT floor 更新能力。
- 单元测试、迁移记录、报告、任务状态和 K8S 快照回写。
- ARM64 dev 镜像与 admin-bff smoke 结果。

## 影响范围

计划写入：

```text
services/ircs-identity-service
docs/reports
docs/migrations
docs/k8s
tasks
```

不计划写入：

```text
services/ircs-admin-bff
frontend
database migration
new Kubernetes resource kinds
```

## 依赖

- `admin-bff` 已将 `/api/v1/auth/**` 路由到 `ircs-identity-service`。
- `system_configs` 已包含或可写入 `security.admin.username`、`security.admin.password`。
- `Valkey` 可用于 PoW challenge。
- `ircs-dev` 仍使用低资源 Java profile。

## 风险

- change-password smoke 可能影响开发管理员密码，必须使用临时配置或恢复原值。
- 如果 `security.admin.password` 缺失，login 只能返回 401，不能误标完成。
- JWT 过滤器同时支持 member/admin 后，必须保留 BANNED member 拒绝逻辑。

## 验收标准

- `GET /api/v1/auth/pow` 通过 `admin-bff` 返回 200。
- `POST /api/v1/auth/login` 通过 `admin-bff` 返回 token。
- admin token 可访问 identity-service 未来 `/api/v1/**` admin 保护面。
- `POST /api/v1/auth/change-password` 完成旧密码校验、新 hash 写入、iat floor 更新。
- 三条 V1 admin auth API 完整迁移并 live smoke 复现，标记 `3/3`。
- 本地测试、全仓 build、ARM64 rollout、无 HTTPRoute 和 ResourceQuota 检查通过。

## 测试边界

计划执行：

```text
.\gradlew.bat :services:ircs-identity-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml
ARM64 image build and push
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s
ClusterIP smoke through admin-bff for pow/login/change-password
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota ircs-dev-quota
```

## 实现结果

- `ircs-identity-service` 已新增 V1 兼容 admin auth API。
- `admin-bff` 现有 `/api/v1/auth/**` 路由无需修改，已可代理到真实 V3 实现。
- admin login 复用 PoW、`system_configs`、BCrypt 和 JWT secret。
- admin token subject 为 admin username，role 为 `ROLE_ADMIN`。
- `JwtAuthenticationFilter` 已支持 admin token，同时保留 member token 的 BANNED 拒绝逻辑。
- change-password 会写入新 BCrypt hash 并刷新 `security.jwt.iat-floor`。

## 验证结果

```text
.\gradlew.bat :services:ircs-identity-service:test -> PASS
.\gradlew.bat build -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s -> PASS
```

ClusterIP smoke 通过 `admin-bff` 验证：

```text
GET  /api/v1/auth/pow             -> 200
POST /api/v1/auth/login           -> token returned
POST /api/v1/auth/change-password -> 200
POST /api/v1/auth/login           -> token returned with changed password
```

smoke 临时写入的 admin 配置已恢复：

```text
temporary config residue count = 0
```

`ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为 `pods=28/30`、`services=24/26`。

## 复现标记

```text
MARK 1/3 GET  /api/v1/auth/pow
MARK 2/3 POST /api/v1/auth/login
MARK 3/3 POST /api/v1/auth/change-password
```

## 当前状态

已完成。后续 `/api/v1/members/**` 后台会员管理继续从
`docs/reports/025-v1-v3-route-coverage-audit.md` 拆分。
