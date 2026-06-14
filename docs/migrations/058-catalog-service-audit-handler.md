# 058 catalog-service audit handler

状态：已完成

日期：2026-06-07

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

关联报告：

- `docs/reports/058-02812-catalog-service-audit-first-slice.md`

## 背景

`028-11` 已证明 `ircs-config-service` 可以通过 shared `ServiceRequestAuditFilter`
写入 service handler audit。`028-12` 第一刀继续选一个非 config-service 低风险 HTTP 服务，
把同一采集口径铺到 `ircs-catalog-service`。

## 变更

- `ircs-catalog-service` 新增 audit filter registration。
- 本地测试用 H2 建立最小 `request_audit_logs` 表，验证 `request_source`、method、path、
  status、username、client ip 和 trace id 写入。
- actuator 请求继续由 shared filter 排除。
- `ircs-catalog-service` 增加 H2 test dependency，不影响运行时依赖。

## 行为复现

```text
MARK 1/2 catalog-service handler request writes request_source
MARK 2/2 catalog-service audit filter skips actuator noise
```

## Worker/job Audit 边界

本迁移不修改 worker/job audit 表。后续仍建议独立事件口径：

```text
job_source
job_type
correlation_id
status
duration_ms
error_class
```

该设计不复用 `request_audit_logs`，避免把非 HTTP 执行事件混入 request audit 查询面。

## 验证

```text
.\gradlew.bat :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL in 11s
```

本轮不 rollout，不新增 HTTPRoute。

测试结果：

```text
CatalogServiceAuditFilterTest tests=2, failures=0, errors=0
```

## 剩余边界

本迁移只完成 `ircs-catalog-service` 本地 handler audit 第一刀。其他 HTTP 服务、worker/job
真实事件写入、ops 查询关联增强和 live smoke 继续留在 `028-12` 后续范围。
