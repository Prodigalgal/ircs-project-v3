# 069 interaction-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-interaction-service` 接入
`ServiceRequestAuditFilter`，让 portal/admin 交互链路在 `request_audit_logs` 中带上
`request_source=ircs-interaction-service`。本切片继续复用 shared query parameter 脱敏，
避免 member JWT 或 token 参数通过 `query_string` 写入审计表。

## 代码范围

```text
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/InteractionServiceAuditConfiguration.java
services/ircs-interaction-service/src/test/java/com/prodigalgal/ircs/interaction/InteractionServiceAuditFilterTest.java
services/ircs-interaction-service/build.gradle
scripts/smoke/interaction-service-audit-02812-smoke.ps1
docs/reports/069-02812-interaction-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对 `token` 等 query 参数做值脱敏。
- 本地测试显式带入 feedback body、Authorization、Cookie 和 query token，并断言 audit
  表不包含这些值。
- live smoke 只访问 public feedback wall endpoint，不执行收藏、历史、留言写入或 admin reply。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/4 interaction-service handler request writes request_source
MARK 2/4 interaction-service audit redacts query token
MARK 3/4 interaction-service audit excludes body Authorization and Cookie
MARK 4/4 interaction-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL in 15s

ircs-interaction-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:2cd5b1086233d618ac0354be4a1c0f982a5c75793670acfcd14ca451c0492cb0

trace: 02812-interaction-audit-20260608145509
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
