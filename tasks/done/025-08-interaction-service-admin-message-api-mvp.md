# 025-08 interaction-service admin message API MVP

状态：已完成

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/033-interaction-service-admin-message-boundary-analysis.md`
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

## 背景

V1 `AdminMessageController` 提供后台留言列表、回复、可见性和删除能力。V3
`ircs-interaction-service` 已完成门户反馈/收藏/历史写接口，但后台
`/api/v1/messages/**` 仍未接入。

## 目标

迁移并复现 4 个 V1 后台留言管理接口：

```text
GET    /api/v1/messages
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

## 非目标

- 不改 `/api/portal/feedback/**`。
- 不新增 HTTPRoute、Pod 或 Service。
- 不新增后台认证体系。
- 不做软删除或审计表。
- 不做物理数据库拆分。

## 输入

- V1 `AdminMessageController`、`UserMessageServiceImpl`、`UserMessageDTO`。
- V3 `user_messages`、`members` schema。
- V3 `UserMessageResponse`、`PageResponse`、JDBC repository 风格。

## 输出

- V3 admin message Controller、Service、Repository 方法和请求 DTO。
- 单元测试。
- ARM64 镜像与 `ircs-dev` rollout。
- ClusterIP smoke `MARK 1/4` 到 `MARK 4/4`。
- 文档、任务、迁移记录回写。

## 影响范围

包含：

- `services/ircs-interaction-service`
- `docs/reports`
- `docs/migrations`
- `docs/requirements`
- `docs/k8s`
- `tasks/in-progress`
- `tasks/done`

不包含：

- 其他服务代码。
- K8S Service/Deployment 数量变化。
- RabbitMQ 队列变化。
- 门户反馈接口行为变化。

## 依赖

- `ircs-interaction-service` 当前 Deployment 可用。
- PostgreSQL schema 已包含 `members` 和 `user_messages`。
- Harbor ARM64 镜像推送可用。

## 风险

- live dev 数据可能为空，因此 smoke 需要构造临时 member/message。
- 后台列表需要返回 `memberEmail`，但公开墙仍不能泄漏该字段。
- 删除接口是物理删除，smoke 必须只删除临时数据。

## 验收标准

- 4 个目标接口单元测试通过。
- 全仓 build 通过。
- ARM64 镜像推送成功。
- `ircs-dev` rollout 成功。
- 每个接口完成真实 HTTP smoke 后按接口粒度标记一次：

```text
MARK 1/4 GET /api/v1/messages -> HTTP 200
MARK 2/4 POST /api/v1/messages/{id}/reply -> HTTP 200
MARK 3/4 PUT /api/v1/messages/{id}/visibility -> HTTP 200
MARK 4/4 DELETE /api/v1/messages/{id} -> HTTP 204
```

- smoke 后确认没有 HTTPRoute，Pod/Service quota 不新增消耗，临时 `members` 和
  `user_messages` 数据为 0。

## 测试边界

本地：

```text
.\gradlew.bat :services:ircs-interaction-service:test
.\gradlew.bat build
```

K8S：

```text
kubectl -n ircs-dev rollout status deploy/ircs-interaction-service --timeout=240s
ClusterIP curl smoke
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev get resourcequota
```

## 关联任务

- 父任务：`tasks/in-progress/025-v1-route-coverage-closeout-parent.md`
- 前置完成：`tasks/done/025-07-ops-service-observability-read-api-mvp.md`

## 当前进度

- 已完成 V1 行为梳理。
- 已确认本任务采用 4 个后台消息接口。
- 2026-06-06 用户要求先部署 V1 `huawai` portal 门户和 `ircs-frontend`
  admin 门户，用门户持续验证接口迁移完整性；本任务临时暂停，待
  `tasks/done/027-01-portal-admin-frontend-deployment-mvp.md` 和
  `tasks/done/027-02-portal-admin-bff-split-mvp.md` 完成后继续。
- 2026-06-06 `027-01` 与 `027-02` 已完成，本任务恢复执行。
- 已完成 V3 admin message Controller、Service、Repository 和请求 DTO。
- 已完成 `UserMessageResponse` 的 `isPublic` + `public` 双字段兼容。
- 已完成本地测试、全仓 build、ARM64 镜像推送、`ircs-dev` rollout 和 4/4 smoke。

## 实现结果

```text
service:
  ircs-interaction-service

image:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
  sha256:6b272d19acf269781317b9c59028eb67220601b79f977bbd40efc50fc4cbccd0

rollout:
  ircs-interaction-service Ready 1/1

quota:
  pods 28/30
  services 24/26
```

## 验证结果

本地：

```text
.\gradlew.bat :services:ircs-interaction-service:test -> PASS
.\gradlew.bat build -> PASS
```

K8S：

```text
kubectl -n ircs-dev rollout status deploy/ircs-interaction-service --timeout=240s -> PASS
kubectl -n ircs-dev get httproute -> No resources found
kubectl -n ircs-dev get resourcequota ircs-dev-quota -> pods 28/30, services 24/26
```

Smoke：

```text
MARK 1/4 GET /api/v1/messages -> HTTP 200
MARK 2/4 POST /api/v1/messages/{id}/reply -> HTTP 200
MARK 3/4 PUT /api/v1/messages/{id}/visibility -> HTTP 200
MARK 4/4 DELETE /api/v1/messages/{id} -> HTTP 204
remaining temporary user_messages|members = 0|0
```
