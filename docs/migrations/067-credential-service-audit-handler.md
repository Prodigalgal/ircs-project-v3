# 067 credential-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-credential-service` 接入
`ServiceRequestAuditFilter`，并额外验证 credential 相关敏感字段不会进入
`request_audit_logs`。

## 代码范围

```text
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialServiceAuditConfiguration.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialServiceAuditFilterTest.java
services/ircs-credential-service/build.gradle
scripts/smoke/credential-service-audit-02812-smoke.ps1
docs/reports/067-02812-credential-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 复用 shared `ServiceRequestAuditFilter`，不新增 audit 表结构。
- 只写请求元数据，不记录 request body 或 response body。
- 本地测试显式带入 secret body、Authorization、Cookie 和 internal token，并断言 audit 表不包含这些值。
- live smoke 只访问 credential template endpoint，不读取或写入真实 credential。
- live smoke 通过唯一 `X-Trace-Id` 清理本轮 audit fixture。

## 迁移结果

```text
MARK 1/3 credential-service handler request writes request_source
MARK 2/3 credential-service audit excludes body Authorization Cookie and internal token
MARK 3/3 credential-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL in 13s

ircs-credential-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:634f635076842a4a0b4518ef1ea5ca0beaee8980e80544f15c5eebb3ff750e96

trace: 02812-credential-audit-20260608115419
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
