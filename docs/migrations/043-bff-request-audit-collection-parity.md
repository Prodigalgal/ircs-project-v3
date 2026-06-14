# 043 BFF request audit collection parity

状态：已完成

更新时间：2026-06-07

## 背景

`025-07` 已经把 request audit 查询面迁移到 `ircs-ops-service`，但 V3 多服务形态下
portal/admin 入口请求没有写入 `request_audit_logs`。这会导致 admin 门户的审计页面只能
查询历史数据或手工数据，无法观察 V3 BFF 产生的真实请求。

本迁移记录对应 `028-01`，属于行为级 1:1 迁移，不计入 HTTP route missing。

## 实现范围

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/BffRequestAuditWriter.java
services/ircs-portal-bff
services/ircs-admin-bff
deploy/k8s/dev/frontend-bff-dev.yaml
scripts/smoke/bff-02801-request-audit-smoke.ps1
```

## 行为

- portal/admin BFF 在代理请求完成后写入 `request_audit_logs`。
- 采集 method、path、query string、status、success、duration、client IP、user agent、
  trace id 和 username。
- 默认排除 actuator、静态资源和 `/api/v1/ops/request-audit`，避免审计页面查询自身造成
  自噪音。
- 审计写入失败只记录 warning，不影响业务响应。
- 不读取 request body，不破坏 multipart raw body proxy。

## 验证

本地：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test
.\gradlew.bat build
```

K8S：

```text
kubectl -n ircs-dev rollout status deploy/ircs-portal-bff --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-admin-bff --timeout=240s
.\scripts\smoke\bff-02801-request-audit-smoke.ps1
```

Smoke：

```text
MARK 1/2 portal-bff proxied request writes request_audit_logs
MARK 2/2 admin-bff proxied request writes request_audit_logs
request_audit_logs smoke rows after cleanup: 0
HTTPRoute in ircs-dev: none
ResourceQuota: pods=28/30, services=24/26
```

## 镜像

```text
ircs-portal-bff:
  harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:d051d3f2d2391de6a7ff2f01408ba4bbc79d63ac1f87931029e69c7e0d22fa52

ircs-admin-bff:
  harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:4e80119902baef1cb59f50f5a53d05d42aac3e39b3ff8f26c99764f21d0c6854
```

## 剩余边界

本次完成的是 BFF 入口采集。后续如果需要每个后端业务服务的内部 handler 级审计，仍需
单独设计 per-service audit 或事件汇聚策略。
