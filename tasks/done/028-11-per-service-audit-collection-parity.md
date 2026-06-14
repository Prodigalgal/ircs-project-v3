# 028-11 per-service audit collection parity 后续子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/migrations/043-bff-request-audit-collection-parity.md`

## 背景

`028-01` 已完成 portal-bff/admin-bff request audit collection，能覆盖门户和后台入口。
但后端业务服务内部 handler 级审计仍未设计，服务间调用、worker 内部动作和绕过 BFF
的调试流量仍不能按 V1 运维审计口径完整追踪。

## 目标

- 设计 per-service audit collection 的统一字段和采集边界。
- 明确哪些服务需要 HTTP filter，哪些 worker 只记录 job/queue audit。
- 复用 `request_audit_logs` 或设计轻量扩展表。
- 让 ops-service 能查询 BFF 与服务内部审计差异。

## 非目标

- 不采集敏感请求体。
- 不显著增加数据库连接数。
- 不引入新中间件。

## 输入

- portal/admin BFF audit 当前实现。
- 各服务 HTTP endpoints。
- queue/job execution context。

## 输出

- per-service audit 设计和最小实现切片。
- ops 查询或报告增强。
- smoke 证据和迁移记录。

本轮第一刀输出：

```text
docs/reports/055-per-service-audit-collection-parity-analysis.md
platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-request-audit-source.sql
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/**
services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config/**
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/**
```

## 影响范围

```text
services/ircs-*-service/**
services/ircs-*-worker/**
shared/ircs-observability/**
scripts/smoke/**
docs/reports/**
docs/migrations/**
tasks/**
```

## 依赖

- Hikari pool 仍需维持低资源。
- ops-service 查询接口可复用或扩展。

## 风险

- 全服务 filter 一次性铺开风险高，应分服务小切片推进。
- 审计字段必须避免泄露 credential、token 或密码。

## 验收标准

- 至少一个业务服务内部 handler audit 完成 MARK。
- worker/job audit 有明确设计或首个实现。
- ops-service 能查询并区分 BFF audit 与 service audit。

本轮 MARK：

```text
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource
```

## 测试边界

- 单服务本地测试。
- ClusterIP smoke 只打最小端点。

## 当前状态

2026-06-07：`ircs-config-service` service handler audit 第一刀已完成，`ops-service`
可按 `requestSource=ircs-config-service` 查询并返回来源字段。本任务已归档到
`tasks/done/`。

## 实现结果

- `request_audit_logs` 增加 `request_source` 字段和索引。
- BFF audit writer 与 service audit filter 统一写入来源字段。
- `ircs-config-service` 接入首个服务级 HTTP handler audit。
- `ops-service` request audit API 支持 `requestSource` 过滤和响应字段。
- 新增 live smoke：`scripts/smoke/service-audit-02811-smoke.ps1`。

## Worker/job audit 设计

worker/job audit 不复用 HTTP request audit 行为。后续应以轻量事件记录：

```text
job_source
job_type
correlation_id
status
duration_ms
error_class
```

该剩余范围进入 `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`。

## 验证结果

本地测试：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-config-service:test :services:ircs-ops-service:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test --rerun-tasks
BUILD SUCCESSFUL in 22s
```

集群 smoke：

```text
.\scripts\smoke\service-audit-02811-smoke.ps1
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource
```

后置检查：

```text
ircs-config-service Ready 1/1
ircs-ops-service Ready 1/1
request_audit_logs trace residue: 0
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 遗留问题

- 全服务 handler audit 尚未铺开。
- worker/job audit 尚未落首个运行事件实现。
- 上述范围由 `028-12` 继续推进，不计入本任务完成范围。
