# 070 magnet-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-magnet-service` 接入
`ServiceRequestAuditFilter`，让 admin provider/search 相关请求在
`request_audit_logs` 中带上 `request_source=ircs-magnet-service`。本切片复用 shared
query parameter 脱敏，避免 token 或 password 类参数通过 `query_string` 写入审计表。

## 代码范围

```text
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetServiceAuditConfiguration.java
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetServiceAuditFilterTest.java
services/ircs-magnet-service/build.gradle
scripts/smoke/magnet-service-audit-02812-smoke.ps1
docs/reports/070-02812-magnet-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对 `token`、`password` 等 query 参数做值脱敏。
- 本地测试显式带入 provider write body、Authorization、Cookie 和 query token，并断言
  audit 表不包含这些值。
- live smoke 只访问 provider list endpoint，不触发 provider search 或写入 magnet 数据。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/4 magnet-service handler request writes request_source
MARK 2/4 magnet-service audit redacts query token
MARK 3/4 magnet-service audit excludes body Authorization and Cookie
MARK 4/4 magnet-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s

ircs-magnet-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:8adacd816cd0e71008924b554a9c444ace1878e6d1ed2197ff6edc2ec5ff1ad1

trace: 02812-magnet-audit-20260608150827
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
