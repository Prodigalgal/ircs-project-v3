# 028-12-076 ops-service audit handler task spec

状态：已完成

日期：2026-06-08

## 背景

IRCS V3 正在做 V1 已验证功能到 V3 微服务的行为级 1:1 迁移。`028-12` 当前主任务为
service/worker audit 铺开。`ircs-ops-service` 已提供 request audit、worker job audit、dashboard
和其他 ops 只读查询能力，但自身 handler 请求尚未接入 service audit。本子任务补齐
ops-service 的 handler audit，并确保 request-audit 查询端点不会产生自审计噪音。

## 目标

- 在 `ircs-ops-service` 注册 `ServiceRequestAuditFilter`。
- 默认 `request_source` 为 `ircs-ops-service`。
- 使用本地 MockMvc/H2 测试覆盖 ops handler audit 写入。
- 覆盖 query token/password 脱敏。
- 覆盖 Authorization、Cookie 和 response content 不进入 audit 表。
- 覆盖 actuator 噪音跳过。
- 覆盖 `/api/v1/ops/request-audit` endpoint 不被 audit filter 采集。
- 新增只读 smoke 脚本，供主线程 rollout 后验证。

## 非目标

- 不修改 `task-service`。
- 不修改 `scraper-service`。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/requirements/028-v1-v3-behavior-parity-closeout.md`、
  `docs/k8s/current-environment-2026-06-02.md` 或
  `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`。
- 不 rollout、不 push image。
- 不新增 HTTPRoute，不写业务 fixture。
- 不执行 DLQ retry/discard、traffic reset、maintenance init、dashboard refresh 或其他业务写入。

## 上游来源

- `docs/reports/076-02812-ops-service-audit-handler.md`
- `docs/migrations/076-ops-service-audit-handler.md`
- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 输入

- `services/ircs-ops-service` 当前 controller/service/test/build.gradle/application.yaml。
- 已完成的 service audit configuration、测试和 smoke 模式。
- shared `ServiceRequestAuditFilter` 与 `RequestAuditSanitizer`。

## 输出

- ops-service audit configuration。
- ops-service MockMvc/H2 audit 测试。
- ops-service audit smoke 脚本。
- 本地报告、迁移记录和 task spec 状态回写。

## 影响范围

```text
services/ircs-ops-service/**
scripts/smoke/ops-service-audit-02812-smoke.ps1
docs/reports/076-02812-ops-service-audit-handler.md
docs/migrations/076-ops-service-audit-handler.md
tasks/in-progress/028-12-076-ops-service-audit-handler.md
```

## 依赖

- shared `ServiceRequestAuditFilter` 已可用。
- H2 测试依赖已在 ops-service test classpath。
- 主线程统一处理公共账本、image push、rollout 和 live smoke 集成。

## 风险

- ops-service 自身 request-audit 查询如果被采集，会造成自审计噪音。
- `request_audit_logs.request_source` schema 缺失时，live 环境写入会失败；本地 H2 只验证 handler/filter 行为。
- audit 只记录请求元数据，不记录 response content，不能替代业务查询日志。

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

不运行 rollout，不 push image。smoke 脚本只调用 `GET /api/v1/ops/worker-job-audit/summary`。

## 关联文档

- `docs/reports/076-02812-ops-service-audit-handler.md`
- `docs/migrations/076-ops-service-audit-handler.md`

## 关联任务

- 父任务：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- 子任务：当前文件

## 当前状态

已完成本地实现、主线程 rollout 和 live smoke。本文件保留在 `tasks/in-progress/`，等待
`028-12` 父任务统一归档。

## 实现结果

- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/OpsServiceAuditConfiguration.java`
  注册 `ServiceRequestAuditFilter`。
- `services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops/OpsServiceAuditFilterTest.java`
  覆盖 handler audit 写入、敏感 query 脱敏、Authorization/Cookie/response content 排除、actuator 跳过和
  request-audit endpoint self-audit skip。
- `scripts/smoke/ops-service-audit-02812-smoke.ps1` 只调用只读
  `GET /api/v1/ops/worker-job-audit/summary`，验证 audit row 后清理本轮 trace。

## 验收结果

```text
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
```

## 测试结果

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
BUILD SUCCESSFUL in 10s

ops-service total tests=36, failures=0, errors=0, skipped=0
OpsServiceAuditFilterTest tests=3, failures=0, errors=0, skipped=0
PowerShell parser: syntax ok
```

## rollout/live 结果

```text
imageID: harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:7ec8a32ef43fb6c6bcd4ad165deddfc5490ef3b3d6a01b11ff2d022c81e1c39a
trace: 02812-ops-audit-20260608174902

MARK 1/4 pod Ready
MARK 2/4 worker job audit summary request success
MARK 3/4 audit row redacts query token
MARK 4/4 cleanup leaves no DB residue
```
