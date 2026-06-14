# 076 028-12 ops-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- `tasks/in-progress/028-12-076-ops-service-audit-handler.md`

## 背景

`028-12` 正在铺开 V1 到 V3 微服务迁移后的 service/worker audit。HTTP
Controller/BFF missing 已清零，config、catalog、content、credential、identity、
interaction、magnet、storage、search、portal、task service handler audit 已完成，scraper
service 切片由并行 worker 单独推进。`ircs-ops-service` 目前承担 dashboard、system metrics、
request audit、worker job audit、DLQ、traffic 和 maintenance 等运维只读/维护入口，是审计闭环
里查询其他服务 audit 结果的服务，也需要把自身 handler 请求纳入
`request_audit_logs.request_source=ircs-ops-service`。

ops-service 与其他服务的特殊差异是：它自身暴露 `/api/v1/ops/request-audit` 查询端点。shared
`ServiceRequestAuditFilter` 已跳过该路径，本切片必须显式验证该端点不会被 audit filter 采集，避免
ops-service 查询 audit 时产生自审计噪音。

## 目标

- 为 `ircs-ops-service` 注册 `ServiceRequestAuditFilter`。
- 默认写入 `request_audit_logs.request_source=ircs-ops-service`。
- 使用本地 MockMvc + H2 测试证明 ops handler 请求会写入 audit 表。
- 覆盖 query `token` / `password` 脱敏。
- 覆盖 Authorization、Cookie 和 response content 不进入 audit 表。
- 覆盖 actuator 噪音跳过。
- 覆盖 `/api/v1/ops/request-audit` 仍被跳过，避免 request audit 查询自递归。
- 提供只调用只读 ops endpoint 的 smoke 脚本，不执行 K8S rollout、不 push image。

## 非目标

- 不修改 `task-service`。
- 不修改 `scraper-service`。
- 不修改全局索引、公共需求文档、K8S 环境文档或 028-12 父任务公共账本。
- 不执行 K8S rollout、不 push image。
- 不新增 HTTPRoute、controller、service、business fixture 或业务写入流程。
- 不修改 dashboard、request audit、worker job audit、DLQ、traffic、maintenance 的业务语义。
- 不在 audit 中记录 Authorization、Cookie、JWT、request body 或 response body。

## 方案

复用 shared `ServiceRequestAuditFilter` 和 `RequestAuditSanitizer`。在 ops-service 内新增
`OpsServiceAuditConfiguration`，通过 `app.service.audit.enabled` 控制启停，并使用
`app.service.audit.source` 或 `spring.application.name` 解析 request source，默认值为
`ircs-ops-service`。

本地测试采用 standalone MockMvc + H2。选择 `GET /api/v1/ops/worker-job-audit/summary` 作为主要
handler audit 入口，该 endpoint 只读、无需创建业务 fixture，service 层使用 mock 返回 summary，
可验证 response content 不进入 audit 表。额外用 `GET /api/v1/ops/request-audit/summary` 验证
shared filter 的 self-audit skip 仍生效。

## smoke 策略

smoke 脚本只访问 `GET /api/v1/ops/worker-job-audit/summary`，不新增 HTTPRoute，不写业务 fixture，
不触发 DLQ retry/discard、traffic reset、maintenance init 或 dashboard refresh。脚本使用唯一
`X-Trace-Id` 验证 audit row，结束后清理本轮 `request_audit_logs` 记录。

## 影响范围

```text
services/ircs-ops-service/**
scripts/smoke/ops-service-audit-02812-smoke.ps1
docs/reports/076-02812-ops-service-audit-handler.md
docs/migrations/076-ops-service-audit-handler.md
tasks/in-progress/028-12-076-ops-service-audit-handler.md
```

## 风险

- ops-service 的 request-audit endpoint 如果被采集，会造成查询 audit 时持续产生自审计噪音。
- live 环境写入依赖 `request_audit_logs.request_source` schema 已存在。
- service audit 只记录请求元数据，不记录 body，不能用于复原运维查询响应内容。
- 本 worker 不 rollout，live smoke 需在主线程完成镜像发布和 rollout 后再运行。

## 验收标准

```text
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
```

## 测试边界

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
powershell -NoProfile -Command "$tokens = $null; $errors = $null; $null = [System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw 'scripts/smoke/ops-service-audit-02812-smoke.ps1'), [ref]$errors); if ($errors.Count -gt 0) { $errors; exit 1 }"
```

不运行 rollout，不 push image。smoke 脚本可由主线程在 rollout 后执行：

```text
.\scripts\smoke\ops-service-audit-02812-smoke.ps1
```

## 当前状态

已完成。本地实现由 worker 完成；主线程已完成 ARM64 image push、`ircs-dev` rollout、
live smoke 和公共账本回写。

## 实现结果

- 新增 `OpsServiceAuditConfiguration`，为 `ircs-ops-service` 注册 `ServiceRequestAuditFilter`。
- 新增 `OpsServiceAuditFilterTest`，使用 standalone MockMvc + H2 覆盖：
  - ops handler 请求写入 `request_source=ircs-ops-service`。
  - query `token` / `password` 脱敏为 `***`。
  - Authorization、Cookie 和 response content 不进入 audit 表。
  - actuator 噪音跳过。
  - `/api/v1/ops/request-audit/summary` 不被 audit filter 采集，避免 self-audit 噪音。
- 新增 `scripts/smoke/ops-service-audit-02812-smoke.ps1`，只调用只读
  `GET /api/v1/ops/worker-job-audit/summary`，使用唯一 trace 写入并清理
  `request_audit_logs`。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
BUILD SUCCESSFUL in 10s

ops-service total tests=36, failures=0, errors=0, skipped=0
OpsServiceAuditFilterTest tests=3, failures=0, errors=0, skipped=0
```

脚本语法：

```text
powershell parser check scripts/smoke/ops-service-audit-02812-smoke.ps1
syntax ok
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

## MARK

```text
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
```
