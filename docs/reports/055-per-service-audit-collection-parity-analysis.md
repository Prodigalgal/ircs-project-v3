# 055 per-service audit collection parity 分析

状态：已完成

更新时间：2026-06-07

## 背景

`028-01` 已完成 portal/admin BFF request audit collection，admin 门户可以通过
`ops-service` 查询入口请求。但 V3 微服务拆分后，绕过 BFF 的 ClusterIP 调试请求、服务间
HTTP 调用和后端 handler 级行为仍不会进入 `request_audit_logs`。这会让“接口可访问”
和“服务内部行为可观察”之间继续有缺口。

## 当前缺口

- `request_audit_logs` 只有请求字段，没有来源字段，无法稳定区分 BFF 入口日志和后端
  service handler 日志。
- BFF 使用 `BffRequestAuditWriter` 直写审计表；业务服务尚无统一 servlet filter。
- `ops-service` 查询响应没有返回来源字段。

## 本轮方案

第一刀只覆盖一个低风险业务服务：

```text
ircs-config-service
  -> ServiceRequestAuditFilter
  -> request_audit_logs.request_source = ircs-config-service
  -> ops-service request audit page exposes requestSource
```

同时给 BFF 审计补来源：

```text
ircs-portal-bff -> request_source = ircs-portal-bff
ircs-admin-bff  -> request_source = ircs-admin-bff
```

## 非目标

- 不一次性铺开所有业务服务。
- 不采集请求体、Authorization、Cookie 或敏感 header。
- 不新增中间件。
- 不新增公共 HTTPRoute。
- 不改变 ops-service 现有 request audit 查询路径。

## 验收标准

```text
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource
```

## 测试边界

本地必须执行：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-config-service:test :services:ircs-ops-service:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test --rerun-tasks
```

K8S live smoke 需要先确认 dev PostgreSQL 已应用 `request_source` 列，再对
`ircs-config-service`、`ircs-ops-service` 和 BFF 执行 ARM64 rollout。

## 风险

- 如果 dev 数据库未先加 `request_source` 列，新版本 BFF/service 写入会失败；因此本轮
  增加 Liquibase migration，并在 live rollout 前单独检查 schema。
- 全服务铺开会增加数据库写入，本轮只选 `ircs-config-service` 做最小验证。
- 审计写入失败必须只记录 warning，不影响业务响应。

## 当前状态

已完成 `ircs-config-service` handler audit 第一刀，并通过 `ircs-dev` ClusterIP smoke。

## 实现结果

- 新增 `request_audit_logs.request_source` 字段和索引，用于区分 BFF 入口日志与后端服务日志。
- `BffRequestAuditWriter` 已写入 `request_source`，portal/admin BFF 新版本可标记来源。
- `ServiceRequestAuditFilter` 已进入 shared common，首个接入服务为 `ircs-config-service`。
- `ircs-config-service` 通过 servlet filter 对 handler 请求写入审计行，不读取请求体，不采集敏感 header。
- `ops-service` 的 request audit 查询支持 `requestSource` 过滤，并在响应中返回 `requestSource`。

## Worker/job audit 设计边界

本轮不把 worker/job 执行写入 `request_audit_logs`，避免混淆 HTTP request audit 语义。
后续 worker/job audit 应走独立轻量事件口径：

```text
job_source:        service/worker 名称
job_type:          queue consumer / scheduler / maintenance runner / provider task
correlation_id:    traceId、messageId、sessionId 或 provider task id
status:            started / succeeded / failed / skipped
duration_ms:       执行耗时
error_class:       可选失败分类，不记录 credential、token 或请求体
```

后续铺开范围已拆入：

```text
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 验证结果

本地测试：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-config-service:test :services:ircs-ops-service:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test --rerun-tasks
BUILD SUCCESSFUL in 22s
```

dev schema：

```text
ALTER TABLE request_audit_logs ADD COLUMN IF NOT EXISTS request_source VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_req_audit_source ON request_audit_logs (request_source);
```

ARM64 rollout：

```text
ircs-config-service imageID:
harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:dea99906f21224ba835b7beffa47eb63e5c6e17cf513e9b0c39a678665c3986c

ircs-ops-service imageID:
harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:a41ea0eed59902f975b9ef2a38bb4c29b72534a6c98d98a89f64d80ae92820de
```

ClusterIP smoke：

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

## 剩余项

- 全服务 handler audit 铺开仍未完成。
- worker/job audit 仍只有设计口径，尚未实现首个队列或 runner 事件写入。
- 这些剩余项不放在已完成的 `028-11` 内，后续由 `028-12` 继续推进。
