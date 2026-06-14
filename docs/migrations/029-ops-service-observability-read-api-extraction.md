# 029 ops-service observability read API extraction

状态：已完成

更新时间：2026-06-06

## 背景

本迁移记录承接 `025-07`，将 V1 单体中已经验证过的 ops 可观测性只读接口迁移到
`ircs-ops-service`。本次只迁移查询面，不迁移 DLQ retry/discard、traffic reset 或
request audit 采集器。

## 覆盖接口

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

接口级复现结果：

```text
MARK 1/4 GET /api/v1/ops/request-audit -> HTTP 200
MARK 2/4 GET /api/v1/ops/request-audit/summary -> HTTP 200
MARK 3/4 GET /api/v1/ops/dlq -> HTTP 200
MARK 4/4 GET /api/v1/ops/traffic/status -> HTTP 200
```

## 实现范围

新增：

```text
RequestAuditController
RequestAuditQueryService
RequestAuditRepository
RequestAuditLogResponse
RequestAuditSummaryResponse
DlqController
DlqQueryService
DlqRepository
FailedMessageResponse
TrafficMonitorController
TrafficMonitorService
TrafficStatusResponse
TrafficSlotResponse
CredentialLabelRepository
JdbcPageSorts
OpsApiExceptionHandler
```

测试：

```text
RequestAuditControllerTest
DlqControllerTest
DlqRepositoryTest
TrafficMonitorServiceTest
```

依赖：

```text
org.springframework.data:spring-data-commons
```

## 行为说明

- `request-audit` 列表支持 V1 过滤参数：`username`、`method`、`path`、
  `statusCode`、`statusClass`、`clientIp`、`from`、`to`。
- `request-audit/summary` 统计过去 24 小时总数、错误数、慢请求数和最大耗时。
- `dlq` 列表支持 `status` 和 `queueName`，非法 status 返回 400。
- `traffic/status` 只读扫描 `traffic:limit:*`，按 V1 规则区分 global 和 credential
  limiters。
- `traffic/status` 在 Redis 临时异常时返回空列表，避免观测接口拖垮 ops-service。
- 本切片不新增 HTTPRoute，不新增 K8S Service，不消费或投递 RabbitMQ 消息。

## 部署结果

```text
image:  harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
digest: sha256:c78353aee97fb3013b129bea99d94c1cc6558cbfaf7c4b7555b1ef6cc26aa7ac
svc:    ircs-ops-service, ClusterIP 10.102.243.27
ready:  deployment/ircs-ops-service 1/1
pod:    ircs-ops-service-67d94dc6dd-rqvzt, restarts=0
quota:  pods=23/24, services=19/20
route:  no HTTPRoute in ircs-dev
```

## 验证

本地：

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
```

K8S：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
kubectl -n ircs-dev rollout restart deploy/ircs-ops-service
kubectl -n ircs-dev rollout status deploy/ircs-ops-service --timeout=240s
```

smoke 准备：

- 插入临时 `request_audit_logs` row。
- 插入临时 `failed_messages` row。
- 写入临时 Valkey key `traffic:limit:Provider:OPS_SMOKE_20260606020913`。

smoke 后清理：

```text
request_audit_logs smoke row: 0
failed_messages smoke row:   0
Valkey smoke key deleted:    1
RabbitMQ queues/DLQs:        0 messages
```

## 遗留边界

- `POST /api/v1/ops/traffic/reset` 后续单独迁移。
- `POST /api/v1/ops/dlq/{id}/retry`、`batch-retry`、`discard`、`batch-discard`
  后续单独迁移并加保护。
- V3 多服务 request audit 采集策略尚未完成；本次只恢复查询面。
- `PageImpl` JSON shape 继续沿用当前 V3 已迁移分页接口口径，日志会出现 Spring
  序列化 warning，后续可统一改显式 page DTO。

