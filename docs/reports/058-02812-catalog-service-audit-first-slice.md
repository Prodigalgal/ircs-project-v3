# 058 028-12 catalog-service audit first slice

状态：已完成

日期：2026-06-07

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

上游来源：

- `tasks/done/028-11-per-service-audit-collection-parity.md`
- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/migrations/057-per-service-audit-config-handler.md`

## 背景

`028-11` 已完成 `ircs-config-service` handler audit 第一刀，证明 `request_audit_logs`
可以用 `request_source` 区分服务来源。`028-12` 需要继续按风险顺序铺开非 config-service
HTTP handler audit，同时保持 worker/job audit 的事件语义不污染 HTTP request audit。

本轮选择 `ircs-catalog-service`，原因是它是低风险 HTTP 服务，已有 read/write controller
合同测试，且不涉及 credential、token 或 worker 消费副作用。

## 目标

- 在 `ircs-catalog-service` 注册 `ServiceRequestAuditFilter`。
- 让 catalog-service handler 请求写入 `request_audit_logs.request_source=ircs-catalog-service`。
- 用本地单元测试覆盖写入路径和 actuator 噪音排除。
- 不修改 shared filter，不修改 worker/job audit 表。

## 非目标

- 不 rollout 到 `ircs-dev`。
- 不新增 HTTPRoute。
- 不采集请求体、Authorization、Cookie、credential 或 token。
- 不修改 `services/ircs-storage-service/**`、metadata worker 或 normalization worker。

## 方案

复用 028-11 的 shared `ServiceRequestAuditFilter`：

```text
ircs-catalog-service
  -> CatalogServiceAuditConfiguration
  -> ServiceRequestAuditFilter(enabled, requestSource, jdbcTemplate)
  -> request_audit_logs.request_source = ircs-catalog-service
```

配置默认值沿用服务名：

```text
app.service.audit.enabled=true
app.service.audit.source=${spring.application.name:ircs-catalog-service}
```

## 范围

```text
services/ircs-catalog-service/**
docs/reports/058-02812-catalog-service-audit-first-slice.md
docs/migrations/058-catalog-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险

- audit 写入增加一条轻量 DB insert；本轮只加一个服务，继续保持分批铺开。
- 如果 dev 数据库缺少 `request_source` 列，live 写入会 warning，但不影响业务响应；本轮只跑本地 H2 测试。
- worker/job audit 若复用 HTTP 表会混淆语义，因此继续保留独立事件口径。

## 验收标准

```text
MARK 1/2 catalog-service handler request writes request_source
MARK 2/2 catalog-service audit filter skips actuator noise
```

## 测试边界

本轮只执行本地 Gradle 测试：

```text
.\gradlew.bat :services:ircs-catalog-service:test --rerun-tasks
```

不执行 live smoke，不触碰 HTTPRoute 和 ResourceQuota。

## 当前状态

2026-06-07：第一刀已完成。`ircs-catalog-service` 已接入 service handler audit，本地测试通过。

## 实现结果

- 新增 `CatalogServiceAuditConfiguration`，注册 `ServiceRequestAuditFilter`。
- `requestSource` 默认取 `${spring.application.name:ircs-catalog-service}`，可由
  `app.service.audit.source` 覆盖。
- catalog-service handler 请求可写入 `request_audit_logs.request_source=ircs-catalog-service`。
- actuator 请求继续被 shared filter 排除。
- `ircs-catalog-service` 增加 H2 测试依赖，仅用于本地 audit 表写入测试。

## 验证结果

```text
.\gradlew.bat :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL in 11s
```

测试证据：

```text
CatalogServiceAuditFilterTest tests=2, failures=0, errors=0
writesCatalogServiceHandlerAuditRow()
skipsActuatorNoise()
```

MARK：

```text
MARK 1/2 catalog-service handler request writes request_source
MARK 2/2 catalog-service audit filter skips actuator noise
```

## 未执行项

- 未 rollout 到 `ircs-dev`。
- 未新增 HTTPRoute。
- 未做 DB residue、ResourceQuota 或 ClusterIP smoke；本轮只完成本地低风险切片。

## 剩余项

- 后续继续按风险顺序铺开其他 HTTP service handler audit。
- worker/job audit 仍需独立事件表或 writer 设计，不应复用 HTTP `request_audit_logs`。
- 若后续做 live smoke，应只打 ClusterIP 最小端点，并验证 `ircs-dev` 无 HTTPRoute residue。
