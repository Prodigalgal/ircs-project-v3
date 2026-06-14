# 025-07 ops-service observability read API MVP

状态：已完成

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/023-ops-service-boundary-analysis.md`
- `docs/reports/032-ops-service-observability-read-api-boundary-analysis.md`
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

## 背景

V1 单体中 `RequestAuditController`、`DlqController` 和 `TrafficMonitorController`
提供了后台运维观测能力。V3 已有 `ircs-ops-service`，但尚未承接这些可观测性读接口。

## 目标

在不增加 K8S 资源对象、不新增 HTTPRoute、不触发运维写命令的前提下，迁移并复现 4 个
只读接口：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

## 非目标

本任务不迁移以下接口：

```text
POST   /api/v1/ops/traffic/reset
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
```

不新增 request audit 采集 filter，不新增 DLQ consumer，不做物理数据库拆分。

## 输入

- V1 Controller、Service、Repository、DTO/VO。
- V3 `ircs-ops-service` 现有 JDBC、Redis、RabbitMQ 配置。
- PostgreSQL 表 `request_audit_logs`、`failed_messages`。
- Valkey/Redis key `traffic:limit:*`。

## 输出

- V3 Controller、Service、Repository、DTO/record。
- 对应单元测试。
- ARM64 镜像与 `ircs-dev` rollout。
- ClusterIP smoke `MARK 1/4` 到 `MARK 4/4`。
- 文档、任务、迁移记录回写。

## 影响范围

包含：

- `services/ircs-ops-service`
- `docs/reports`
- `docs/migrations`
- `docs/requirements`
- `docs/k8s`
- `tasks/in-progress`
- `tasks/done`

不包含：

- 其他业务服务代码。
- K8S Deployment/Service 数量变化。
- RabbitMQ 队列声明变化。
- Valkey/Redis 写入或删除。

## 依赖

- `ircs-ops-service` 当前 Deployment 可用。
- PostgreSQL schema 已包含 `request_audit_logs` 和 `failed_messages`。
- `ircs-dev` 仍使用共享 dev 中间件。
- Harbor ARM64 镜像推送可用。

## 风险

- live dev 数据可能为空，因此 smoke 需要构造最小只读可见数据。
- request audit 列表和 DLQ 列表使用 `PageImpl`，可能出现序列化 warning。
- Redis scan 需要异常降级，避免 Valkey 临时异常导致整个 ops-service 失败。
- 本任务关闭的是查询面，不代表 V3 多服务 request audit 采集策略完成。

## 验收标准

- 4 个目标接口本地单元测试通过。
- 全仓 build 通过。
- ARM64 镜像推送成功。
- `ircs-dev` rollout 成功。
- 每个接口完成真实 HTTP smoke 后按接口粒度标记一次：

```text
MARK 1/4 GET /api/v1/ops/request-audit -> HTTP 200
MARK 2/4 GET /api/v1/ops/request-audit/summary -> HTTP 200
MARK 3/4 GET /api/v1/ops/dlq -> HTTP 200
MARK 4/4 GET /api/v1/ops/traffic/status -> HTTP 200
```

- smoke 后确认没有 HTTPRoute，Pod/Service quota 不新增消耗，RabbitMQ 消息不增加。

## 测试边界

本地：

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
```

K8S：

```text
kubectl -n ircs-dev rollout status deploy/ircs-ops-service --timeout=240s
ClusterIP curl smoke
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota
```

## 关联任务

- 父任务：`tasks/in-progress/025-v1-route-coverage-closeout-parent.md`
- 前置完成：`tasks/done/025-06-storage-service-cover-image-admin-api-lossless.md`

## 当前进度

- 已完成 V1 行为梳理。
- 已完成 V3 代码实现和单元测试。
- 已完成 ARM64 镜像推送与 `ircs-dev` rollout。
- 已完成 4/4 接口级 ClusterIP smoke。
- 已完成 smoke 数据清理与环境后置检查。

## 实现结果

新增 `ircs-ops-service` 观测类只读 API：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

复现标记：

```text
MARK 1/4 GET /api/v1/ops/request-audit -> HTTP 200
MARK 2/4 GET /api/v1/ops/request-audit/summary -> HTTP 200
MARK 3/4 GET /api/v1/ops/dlq -> HTTP 200
MARK 4/4 GET /api/v1/ops/traffic/status -> HTTP 200
```

验证：

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
kubectl -n ircs-dev rollout status deploy/ircs-ops-service --timeout=240s
```

部署：

```text
image:  harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
digest: sha256:c78353aee97fb3013b129bea99d94c1cc6558cbfaf7c4b7555b1ef6cc26aa7ac
ready:  1/1
route:  no HTTPRoute
quota:  pods=23/24, services=19/20
```

清理：

```text
request_audit_logs smoke row: 0
failed_messages smoke row:   0
Valkey smoke key deleted:    1
RabbitMQ queues/DLQs:        0 messages
```

## 后续任务

- DLQ retry/discard 写接口单独拆分，并加入重复投递和状态变更保护。
- traffic reset 单独拆分，并加入 key 白名单或确认机制。
- V3 多服务 request audit 采集策略单独设计。
