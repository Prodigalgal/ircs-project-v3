# 027-02 portal/admin BFF 正式拆分 MVP

状态：已完成

上游来源：

- `docs/requirements/027-portal-admin-frontend-deployment.md`
- `docs/reports/034-portal-admin-frontend-deployment-boundary-analysis.md`
- `docs/decisions/003-formal-portal-admin-bff-split.md`
- `tasks/done/027-01-portal-admin-frontend-deployment-mvp.md`

## 背景

用户明确要求直接正式拆分 `portal-bff` 和 `admin-bff`。当前 gateway 只是 Nginx 路径代理，
不能承接页面级契约、聚合、兼容或权限菜单边界。

## 目标

- 新增 `services:ircs-portal-bff` Spring Boot 服务。
- 新增 `services:ircs-admin-bff` Spring Boot 服务。
- 两个 BFF 第一阶段以薄代理方式承接前端路径，并分流到 V3 微服务。
- gateway 改为只把 portal API 发往 `ircs-portal-bff`，把 admin API 发往
  `ircs-admin-bff`。
- K8S 新增两个 BFF Deployment/Service，低资源运行。
- 更新 ResourceQuota 并完成 ARM64 镜像、rollout 和 smoke。

## 非目标

- 不在本任务内实现所有缺失 V1 API。
- 不接回 V1 monolith。
- 不新增公网 HTTPRoute。
- 不把 BFF 做成数据库读写单体。
- 不修改 V1 前端源码。

## 输入

- V1 portal 前端 API 契约：`D:\WorkSpace\Project\ircs\ircs-project-v1\huawai\src\lib\api`
- V1 admin 前端 API 契约：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend\src`
- V3 service inventory：`deploy/k8s/service-inventory.yaml`
- 当前 gateway 清单：`deploy/k8s/dev/frontend-portal-admin-dev.yaml`

## 输出

- `services/ircs-portal-bff`
- `services/ircs-admin-bff`
- `settings.gradle` include 更新。
- `deploy/k8s/dev/frontend-bff-dev.yaml`
- gateway ConfigMap 路由改为 BFF。
- `deploy/k8s/dev/resource-quota-dev.yaml` 更新。
- smoke 结果和文档回写。

## 影响范围

包含：

- `settings.gradle`
- `services/ircs-portal-bff`
- `services/ircs-admin-bff`
- `deploy/k8s/dev`
- `docs/requirements`
- `docs/reports`
- `docs/decisions`
- `docs/k8s`
- `tasks/in-progress`
- `tasks/done`

不包含：

- 现有业务服务代码。
- V1 前端源码。
- V1 monolith。
- 公网 HTTPRoute。

## 依赖

- Java 25 toolchain 和 Gradle build 可用。
- Harbor ARM64 push 可用。
- `ircs-dev` 各后端服务 Ready。
- ResourceQuota 允许新增两个 Pod/Service。

## 风险

- BFF 薄代理必须保留 query/body/header/status，否则会破坏前端契约。
- SSE 路由需要流式复制响应，不能一次性缓冲完整响应。
- 未迁移接口会继续返回真实缺口状态，不能在 BFF 中伪造。
- 新增两个 Java Pod 会增加资源占用，需要低 request/limit。

## 验收标准

- `:services:ircs-portal-bff:test` 通过。
- `:services:ircs-admin-bff:test` 通过。
- 全仓 build 通过。
- 两个 ARM64 镜像推送成功。
- `ircs-dev` 中 `ircs-portal-bff` 和 `ircs-admin-bff` Ready 1/1。
- gateway 经 BFF smoke：

```text
MARK 1/8 portal-bff /internal/service-info -> HTTP 200
MARK 2/8 portal via bff /api/portal/metadata -> HTTP 200
MARK 3/8 portal via bff /api/backend/api/portal/home -> HTTP 200
MARK 4/8 portal via bff /api/backend/api/portal/auth/pow -> HTTP 200
MARK 5/8 admin-bff /internal/service-info -> HTTP 200
MARK 6/8 admin via bff /api/v1/dashboard/statistics -> HTTP 200
MARK 7/8 admin via bff /api/v1/ops/traffic/status -> HTTP 200
MARK 8/8 admin via bff /api/v1/auth/pow -> 真实记录当前 V3 缺口状态
```

- `ircs-dev` 仍无 HTTPRoute。

## 测试边界

本地：

```text
.\gradlew.bat :services:ircs-portal-bff:test
.\gradlew.bat :services:ircs-admin-bff:test
.\gradlew.bat build
```

K8S：

```text
kubectl apply --dry-run=server -f deploy/k8s/dev/resource-quota-dev.yaml
kubectl apply --dry-run=server -f deploy/k8s/dev/frontend-bff-dev.yaml
kubectl apply --dry-run=server -f deploy/k8s/dev/frontend-portal-admin-dev.yaml
kubectl -n ircs-dev rollout status deploy/ircs-portal-bff --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-admin-bff --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-frontend-gateway --timeout=240s
```

## 关联任务

- 前置：`tasks/done/027-01-portal-admin-frontend-deployment-mvp.md`
- 继续，后续已完成：`tasks/done/025-08-interaction-service-admin-message-api-mvp.md`

## 当前进度

- 已完成正式拆分决策。
- 已新增 `services:ircs-portal-bff` 和 `services:ircs-admin-bff`。
- 已将 gateway API 路由切换到 `ircs-portal-bff` / `ircs-admin-bff`。
- 已将 `ircs-portal-frontend` 的 SSR `BACKEND_URL` 切换到 `ircs-portal-bff`。
- 已新增 `deploy/k8s/dev/frontend-bff-dev.yaml`。
- 已将 `deploy/k8s/dev/resource-quota-dev.yaml` 更新为 `pods=30`、`services=26`。
- 已在 ARM64 VPS 构建并推送 BFF 镜像。
- 已完成 `ircs-dev` rollout 和 smoke。

## 实现结果

```text
ircs-portal-bff image:
  harbor.mnnu.eu.org/ircs/ircs-portal-bff:dev
  sha256:6ec945cf90eff8cbcb2bb83b808df8bd3d55e6083c265d776e614a220058d47e

ircs-admin-bff image:
  harbor.mnnu.eu.org/ircs/ircs-admin-bff:dev
  sha256:bb36dbc33885b85737f11654625cadc8bf34fab97fb06b04f096915e5f54122a

ready:
  ircs-portal-bff 1/1
  ircs-admin-bff 1/1
  ircs-portal-frontend 1/1
  ircs-admin-frontend 1/1
  ircs-frontend-gateway 1/1

quota:
  pods 28/30
  services 24/26
```

## 验证结果

本地：

```text
.\gradlew.bat :services:ircs-admin-bff:test :services:ircs-admin-bff:bootJar -> PASS
.\gradlew.bat build -> PASS
```

K8S：

```text
kubectl apply --dry-run=server -f deploy/k8s/dev/resource-quota-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy/k8s/dev/frontend-bff-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy/k8s/dev/frontend-portal-admin-dev.yaml -> PASS
kubectl -n ircs-dev rollout status deploy/ircs-portal-bff -> PASS
kubectl -n ircs-dev rollout status deploy/ircs-admin-bff -> PASS
kubectl -n ircs-dev rollout status deploy/ircs-portal-frontend -> PASS
kubectl -n ircs-dev rollout status deploy/ircs-frontend-gateway -> PASS
kubectl -n ircs-dev get httproute -> No resources found
```

Smoke：

```text
MARK 1/8 portal-bff /internal/service-info -> HTTP 200
MARK 2/8 portal via bff /api/portal/metadata -> HTTP 200
MARK 3/8 portal via bff /api/backend/api/portal/home -> HTTP 200
MARK 4/8 portal via bff /api/backend/api/portal/auth/pow -> HTTP 200
MARK 5/8 admin-bff /internal/service-info -> HTTP 200
MARK 6/8 admin via bff /api/v1/dashboard/statistics -> HTTP 200
MARK 7/8 admin via bff /api/v1/ops/traffic/status -> HTTP 200
MARK 8/8 admin via bff /api/v1/auth/pow -> HTTP 403, 记录为初次验收时的 V3 identity-service 缺口
```

## 遗留问题

- `/api/v1/auth/**` 管理员登录链路已由后续 `025-10` 迁移完成。
- `/api/v1/members/**` 后台会员管理已由后续 `025-11` 迁移完成。
- `/api/v1/actors`、`/api/v1/directors`、`/api/v1/source-domains`、`/api/v1/resolvers`、
  `/api/v1/debug/**` 已固定 BFF ownership，但目标业务接口仍需按后续子任务补齐。

## 复验 2026-06-06

本轮重新验收正式拆分状态：

```text
local tests:
  .\gradlew.bat :services:ircs-portal-bff:test :services:ircs-admin-bff:test -> PASS

k8s:
  ircs-portal-bff         Ready 1/1
  ircs-admin-bff          Ready 1/1
  ircs-frontend-gateway   Ready 1/1
  ircs-portal-frontend    Ready 1/1
  ircs-admin-frontend     Ready 1/1
  ResourceQuota           pods 28/30, services 24/26
  HTTPRoute               No resources found in ircs-dev

smoke:
  portal-bff service-info                         -> HTTP 200
  admin-bff service-info                          -> HTTP 200
  portal /api/portal/metadata via gateway         -> HTTP 200
  portal /api/backend/api/portal/home via gateway -> HTTP 200
  portal /api/backend/api/portal/auth/pow         -> HTTP 200
  admin /api/v1/dashboard/statistics via gateway  -> HTTP 200
  admin /api/v1/ops/traffic/status via gateway    -> HTTP 200
  admin /api/v1/auth/pow via gateway              -> HTTP 200
```
