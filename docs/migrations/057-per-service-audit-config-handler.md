# 057 per-service audit config handler parity

状态：已完成

日期：2026-06-07

关联任务：

- `tasks/done/028-11-per-service-audit-collection-parity.md`
- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

关联报告：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`

## 背景

`028-01` 已完成 portal/admin BFF request audit collection，但后端业务服务 handler 请求仍无法
按来源区分写入 `request_audit_logs`。本迁移补齐首个服务级 audit 切片，选择低风险的
`ircs-config-service` 作为验证服务。

## 变更

- `request_audit_logs` 增加 `request_source varchar(128)` 和 `idx_req_audit_source`。
- shared common 增加 `ServiceRequestAuditFilter`。
- `ircs-config-service` 接入 service request audit filter。
- `ops-service` 支持 `requestSource` 查询参数并返回 `requestSource` 字段。
- portal/admin BFF 的 audit writer 也开始写入 BFF 来源字段。

## 行为复现

```text
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource
```

## 验证

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-config-service:test :services:ircs-ops-service:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test --rerun-tasks
BUILD SUCCESSFUL in 22s

.\scripts\smoke\service-audit-02811-smoke.ps1
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource
```

## K8S 结果

```text
ircs-config-service Ready 1/1
ircs-ops-service Ready 1/1
config-service imageID:
harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:dea99906f21224ba835b7beffa47eb63e5c6e17cf513e9b0c39a678665c3986c
ops-service imageID:
harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:a41ea0eed59902f975b9ef2a38bb4c29b72534a6c98d98a89f64d80ae92820de
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 剩余边界

本迁移只证明首个业务服务 handler audit。全服务铺开、服务间调用审计和 worker/job audit
继续由 `028-12` 拆分推进。
