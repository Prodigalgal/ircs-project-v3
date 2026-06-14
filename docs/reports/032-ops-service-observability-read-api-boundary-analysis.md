# 032 ops-service observability read API 边界分析报告

状态：已完成（边界分析、task spec、MVP 实现、`ircs-dev` rollout 与 4/4 接口级 smoke 均已完成）

更新时间：2026-06-06

## 背景

`025` 路由覆盖闭环已经完成 search、portal、interaction、task、content、storage
六个切片。本切片开始前的 V3 service Controller route 基线为：

```text
V3 service Controller routes: 100
Direct missing routes:         114
```

本报告承接 `025-07 ops-service request-audit/DLQ/traffic read-first API`，目标是在
不新增 Pod、Service、HTTPRoute、不触发运维写命令的前提下，先恢复 V1 中经过验证的
ops 可观测性只读接口。

## 上游来源

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/023-ops-service-boundary-analysis.md`
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`
- V1 `RequestAuditController`
- V1 `DlqController`
- V1 `TrafficMonitorController`
- V1 `RequestAuditServiceImpl`
- V1 `FailedMessageServiceImpl`
- V1 `TrafficMonitorServiceImpl`

## 目标接口

本切片只覆盖 4 个只读接口：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

验收时按接口粒度计数。只有某个接口完成 V3 实现、部署到 `ircs-dev`，并通过真实
ClusterIP HTTP 调用复现后，才标记一次：

```text
MARK 1/4 ...
MARK 2/4 ...
MARK 3/4 ...
MARK 4/4 ...
```

健康检查、准备数据、清理数据、队列检查、quota 检查不计入接口 MARK。

## 非目标

本切片不迁移以下 5 个带副作用接口：

```text
POST   /api/v1/ops/traffic/reset
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
```

原因：

- `traffic/reset` 会删除 Redis 限流 slot，属于运维写命令。
- `dlq/retry` 和 `batch-retry` 会重新投递消息，可能触发跨服务写入。
- `dlq discard` 会修改 `failed_messages.status`。
- 写接口需要单独保护边界、幂等策略、权限确认和 smoke 数据隔离。

## V1 行为证据

### request audit

V1 路由：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
```

列表过滤参数：

```text
username
method
path
statusCode
statusClass
clientIp
from
to
page
size
sort
```

返回字段来自 `request_audit_logs`：

```text
id
createdAt
updatedAt
version
username
method
path
queryString
statusCode
success
durationMs
clientIp
userAgent
traceId
errorMessage
```

summary 统计过去 24 小时：

```text
totalLast24h
errorsLast24h
slowLast24h
maxDurationMsLast24h
```

慢请求阈值沿用 V1：

```text
3000ms
```

### DLQ

V1 只读路由：

```text
GET /api/v1/ops/dlq
```

过滤参数：

```text
status
queueName
page
size
sort
```

返回字段来自 `failed_messages`：

```text
id
createdAt
updatedAt
version
queueName
routingKey
exchange
payload
exceptionStack
retryCount
status
```

状态枚举：

```text
PENDING
RETRIED
DISCARDED
```

### traffic status

V1 路由：

```text
GET /api/v1/ops/traffic/status
```

行为：

- 扫描 Valkey/Redis `traffic:limit:*`。
- `cred:*` key 归入 credential limiters。
- 其他 key 归入 global limiters。
- Redis value 是下一次可用时间戳，单位为毫秒。
- 拥堵率按 `nextAvailableInMs / 30000` 计算，保留两位小数。
- 过期超过 60 秒且无需等待的旧 key 忽略。

## V3 目标边界

`ircs-ops-service` 已经拥有：

```text
PostgreSQL read access
Valkey/Redis read access
RabbitMQ metrics read access
dashboard read API
maintenance active/stream read API
```

本切片继续复用该 Deployment：

```text
                +----------------------+
                | ircs-ops-service     |
                | ClusterIP only       |
                +----------+-----------+
                           |
          +----------------+----------------+
          |                |                |
+---------v---------+ +----v----------+ +---v----------------+
| request_audit_logs| | failed_messages| | Valkey traffic:*  |
| PostgreSQL        | | PostgreSQL     | | read scan only    |
+-------------------+ +---------------+ +--------------------+
          |                |                |
          +----------------+----------------+
                           |
                +----------v-----------+
                | /api/v1/ops/** read  |
                +----------------------+
```

实现策略：

- 使用 Spring JDBC / `NamedParameterJdbcTemplate`，不引入 JPA。
- 返回 V1 可消费字段 shape。
- 分页返回使用 Spring Data `PageImpl`，保持与已迁移接口一致。
- Redis/Valkey status 只读扫描，不提供 reset。
- 不新增 HTTPRoute，不新增 K8S Service，不新增中间件。

## 风险

- `request_audit_logs.created_at` 是 `TIMESTAMPTZ`，`failed_messages.created_at` 是
  `TIMESTAMP WITHOUT TIME ZONE`；JDBC mapper 需要兼容。
- V1 request audit 由单体 filter 写入；V3 多服务请求审计采集策略仍需后续单独设计。
  本切片只迁移查询面，不新增采集器。
- Redis scan 在 key 很多时有成本；dev 环境只做小批量扫描，并保留 `count 100`。
- Page JSON shape 可能继续触发 Spring `PageImpl` 序列化 warning；这是当前 V3 已迁移
  Page 接口的一致模式，后续可统一改 DTO。

## 验收标准

- 完成本报告与 `025-07` task spec。
- `ircs-ops-service` 新增 4 个只读接口。
- 单元测试覆盖过滤、summary、DLQ status 校验、traffic slot 分类。
- 本地 `:services:ircs-ops-service:test` 通过。
- 全仓 `build` 通过。
- ARM64 Jib 镜像推送到 Harbor。
- `ircs-dev` rollout 成功。
- ClusterIP smoke 输出 `MARK 1/4` 到 `MARK 4/4`。
- smoke 前后不新增 HTTPRoute，不新增 Pod/Service，不投递 RabbitMQ 消息，不执行 DLQ retry/discard 或 traffic reset。

## 实施结果 2026-06-06

`ircs-ops-service` 已完成 observability read API MVP，实现并复现 4 个只读接口：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

部署结果：

```text
image:  harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
digest: sha256:c78353aee97fb3013b129bea99d94c1cc6558cbfaf7c4b7555b1ef6cc26aa7ac
svc:    ircs-ops-service, ClusterIP 10.102.243.27
ready:  deployment/ircs-ops-service 1/1
pod:    ircs-ops-service-67d94dc6dd-rqvzt, restarts=0
quota:  pods=23/24, services=19/20
route:  no HTTPRoute in ircs-dev
```

接口级 smoke：

```text
MARK 1/4 GET /api/v1/ops/request-audit -> HTTP 200
MARK 2/4 GET /api/v1/ops/request-audit/summary -> HTTP 200
MARK 3/4 GET /api/v1/ops/dlq -> HTTP 200
MARK 4/4 GET /api/v1/ops/traffic/status -> HTTP 200
```

验证命令：

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
kubectl -n ircs-dev rollout status deploy/ircs-ops-service --timeout=240s
```

smoke 后置检查：

```text
request_audit_logs smoke row: 0
failed_messages smoke row:   0
Valkey smoke key deleted:    1
RabbitMQ queues/DLQs:        0 messages
HTTPRoute in ircs-dev:       No resources found
```

已知非阻塞现象：

- `GET /api/v1/ops/request-audit` 和 `GET /api/v1/ops/dlq` 继续使用当前 V3
  分页接口的 `PageImpl` JSON 口径，运行日志会出现 Spring 序列化 warning；后续可统一
  改显式 page DTO。
