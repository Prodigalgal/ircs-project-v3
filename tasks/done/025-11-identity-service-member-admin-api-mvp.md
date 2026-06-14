# 025-11 identity-service member admin API MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/037-identity-service-member-admin-boundary-analysis.md`
- `docs/decisions/003-formal-portal-admin-bff-split.md`

## 背景

`025-10` 已经让 admin 门户可以通过 `admin-bff` 登录 V3，但会员管理页仍缺少真实
`/api/v1/members/**` 后端。V1 会员后台接口和 admin 前端调用已稳定，本任务把该管理面
拆入 `ircs-identity-service`，让 admin 门户可以继续作为 V1/V3 路由迁移验收入口。

## 目标

- 在 `ircs-identity-service` 新增 V1 兼容后台会员管理 API。
- 复现 V1 会员列表、收藏、历史、更新、状态变更和删除 6 条接口。
- 复用 admin-bff 已有 `/api/v1/members/** -> identity-service` 路由。
- 复用 `MemberStatusCacheService`，状态变更和删除后同步缓存。
- 继续使用低资源部署，不新增 K8S 资源种类或副本。

## 非目标

- 不修改 V1 admin 前端。
- 不新增数据库 migration。
- 不做物理数据库拆分。
- 不新增会员批量操作。
- 不改变 portal member auth/profile 既有语义。

## 输入

V1：

```text
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/controller/MemberAdminController.java
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/service/impl/MemberAdminServiceImpl.java
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/model/command/MemberAdminUpdateCmd.java
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/interaction/model/dto/HistoryRecordDTO.java
ircs-frontend/src/features/members/membersApi.js
```

V3：

```text
services/ircs-identity-service
services/ircs-admin-bff
services/ircs-interaction-service
deploy/k8s/dev/identity-service-dev.yaml
deploy/k8s/dev/frontend-bff-dev.yaml
```

## 输出

- `ircs-identity-service` member admin controller/service/repository/DTO。
- 单元测试、迁移记录、报告、任务状态和 K8S 快照回写。
- ARM64 dev 镜像与 admin-bff smoke 结果。
- 6 条接口复现标记。

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

- `admin-bff` 已将 `/api/v1/members/**` 路由到 `ircs-identity-service`。
- `025-10` 已让 admin token 可被 identity-service 识别为 `ROLE_ADMIN`。
- PostgreSQL 仍保留 V1 迁移表结构。
- `Valkey` 可用于会员状态缓存。

## 风险

- 收藏/历史数据读取跨 interaction ownership，当前按共享数据库迁移桥处理。
- 删除会员如果被其他业务表引用，可能需要后续补更完整的依赖清理策略。
- `sort` 参数必须白名单映射，避免 SQL 注入。
- smoke 创建临时会员和视频数据，必须在完成后清理。

## 验收标准

- `GET /api/v1/members` 通过 `admin-bff` 返回分页会员列表。
- `GET /api/v1/members/{id}/favorites` 通过 `admin-bff` 返回分页收藏。
- `GET /api/v1/members/{id}/history` 通过 `admin-bff` 返回分页历史。
- `PUT /api/v1/members/{id}` 可更新 V1 兼容字段。
- `PUT /api/v1/members/{id}/status` 可更新状态并同步缓存。
- `DELETE /api/v1/members/{id}` 返回 204 并清理收藏/历史。
- 六条 V1 member admin API 完整迁移并 live smoke 复现，标记 `6/6`。
- 本地测试、全仓 build、ARM64 rollout、无 HTTPRoute 和 ResourceQuota 检查通过。

## 测试边界

计划执行：

```text
.\gradlew.bat :services:ircs-identity-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml
ARM64 image build and push
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s
ClusterIP smoke through admin-bff for /api/v1/members/**
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota ircs-dev-quota
```

## 实现结果

- `ircs-identity-service` 已新增 V1 兼容后台会员管理 API。
- `admin-bff` 现有 `/api/v1/members/**` 路由无需修改，已代理到真实 V3 实现。
- 会员列表支持 V1 admin 前端使用的分页、keyword、status 和 sort 参数。
- 收藏/历史查询返回 V1 `HistoryRecordDTO` 兼容字段。
- 更新接口支持 V1 `MemberAdminUpdateCmd` 对应字段，并可重置密码。
- 状态变更和删除会同步会员状态缓存。
- 删除会员前清理收藏和观看历史。

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
MARK 1/6 GET /api/v1/members -> 200 total=1
MARK 2/6 GET /api/v1/members/{id}/favorites -> 200 total=1 title=Codex Member Admin Smoke
MARK 3/6 GET /api/v1/members/{id}/history -> 200 total=1 episode=Episode 1
MARK 4/6 PUT /api/v1/members/{id} -> 200 email=codex-member-smoke-updated-*.example.com points=99
MARK 5/6 PUT /api/v1/members/{id}/status -> 200 status=BANNED
MARK 6/6 DELETE /api/v1/members/{id} -> 204
```

smoke 临时数据已清理：

```text
post_cleanup members|favorites|history|unified|temp_config=0|0|0|0|0
```

`ircs-dev` 仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。

## 复现标记

```text
MARK 1/6 GET    /api/v1/members
MARK 2/6 GET    /api/v1/members/{id}/favorites
MARK 3/6 GET    /api/v1/members/{id}/history
MARK 4/6 PUT    /api/v1/members/{id}
MARK 5/6 PUT    /api/v1/members/{id}/status
MARK 6/6 DELETE /api/v1/members/{id}
```

## 当前状态

已完成。后续继续从 `docs/reports/025-v1-v3-route-coverage-audit.md` 拆分。
