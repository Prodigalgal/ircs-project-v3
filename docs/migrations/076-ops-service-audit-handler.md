# 076 ops-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-ops-service` 接入
`ServiceRequestAuditFilter`，让 ops handler 请求在 `request_audit_logs` 中带上
`request_source=ircs-ops-service`。

本切片选择只读 `GET /api/v1/ops/worker-job-audit/summary` 作为本地测试和 live smoke 入口；同时
显式验证 shared filter 对 `/api/v1/ops/request-audit` 的跳过逻辑，避免 ops-service 查询 audit
时产生自审计噪音。

## 代码范围

```text
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/OpsServiceAuditConfiguration.java
services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops/OpsServiceAuditFilterTest.java
scripts/smoke/ops-service-audit-02812-smoke.ps1
docs/reports/076-02812-ops-service-audit-handler.md
docs/migrations/076-ops-service-audit-handler.md
tasks/in-progress/028-12-076-ops-service-audit-handler.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对敏感 query 参数做值脱敏。
- 本地测试显式带入 Authorization、Cookie、query token/password 和 response marker，并断言 audit 表不包含这些值。
- 本地测试显式验证 actuator 和 `/api/v1/ops/request-audit` 跳过。
- smoke 只访问 worker job audit summary，只读且不需要业务 fixture。
- smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit 记录。

## 当前状态

已完成。本地实现由 worker 完成；主线程已完成 ARM64 image push、`ircs-dev` rollout、
live smoke 和公共账本回写。

## 迁移结果

```text
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
```

验证证据：

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
BUILD SUCCESSFUL in 10s

ops-service total tests=36, failures=0, errors=0, skipped=0
OpsServiceAuditFilterTest tests=3, failures=0, errors=0, skipped=0
PowerShell parser: syntax ok
```

主线程 rollout/live 结果：

```text
imageID: harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:7ec8a32ef43fb6c6bcd4ad165deddfc5490ef3b3d6a01b11ff2d022c81e1c39a
pod: ircs-ops-service Ready 1/1
trace: 02812-ops-audit-20260608174902

MARK 1/4 pod Ready
MARK 2/4 worker job audit summary request success
MARK 3/4 audit row redacts query token
MARK 4/4 cleanup leaves no DB residue
```

后置检查：

```text
request_audit_logs ops-service trace residue=0
HTTPRoute absent
ResourceQuota unchanged: pods 28/30, services 24/26
local port-forward residue=none
```
