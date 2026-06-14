# 028-01 BFF request audit 采集行为 parity

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `tasks/done/025-07-ops-service-observability-read-api-mvp.md`
- `docs/decisions/003-formal-portal-admin-bff-split.md`

## 背景

V1 单体通过 request audit 记录后台可观察请求。V3 已经把 request audit 查询接口迁移到
`ircs-ops-service`，但 portal/admin BFF 作为当前统一入口尚未写入 `request_audit_logs`。
这导致 admin 门户能打开审计页面，却看不到 V3 门户和后台入口产生的真实请求轨迹。

## 目标

- 在 `ircs-portal-bff` 和 `ircs-admin-bff` 增加 request audit 采集。
- 采集代理请求的 method、path、query、status、duration、client IP、user agent、username。
- 默认排除 actuator、审计查询自身和静态资源路径，避免自噪音。
- 写入失败不影响业务请求。
- 通过现有 `ops-service` request audit 查询接口完成 smoke。

## 非目标

- 不在每个后端业务服务都加 audit filter。
- 不新增 Kafka/RabbitMQ 审计事件。
- 不新增 Pod、Service、HTTPRoute 或中间件。
- 不改变 BFF 路由表 ownership。
- 不采集请求体、token 明文或敏感 header。

## 输入

- BFF 现有 servlet proxy。
- PostgreSQL 表 `request_audit_logs`。
- K8S `ircs-dev` PostgreSQL configMap/secret。
- `ops-service` request audit 查询接口。

## 输出

- portal/admin BFF JDBC audit writer。
- 单元测试或切片测试。
- BFF dev YAML datasource env。
- smoke 脚本。
- 迁移记录和任务回写。

## 影响范围

包含：

- `services/ircs-portal-bff`
- `services/ircs-admin-bff`
- `deploy/k8s/dev/frontend-bff-dev.yaml`
- `scripts/smoke`
- `docs/migrations`
- `docs/reports`
- `docs/requirements`
- `tasks`

不包含：

- `ircs-ops-service` 查询接口语义。
- 后端业务服务 Controller。
- 数据库 schema 变更。

## 依赖

- `request_audit_logs` 表已存在。
- BFF 能连接共享 dev PostgreSQL。
- `ops-service` 查询接口可用。

## 风险

- BFF 增加 datasource 后启动可能受 DB 连接影响；需要设置低 Hikari pool 并保持连接数小。
- 审计采集可能记录自身查询造成噪音；需要路径排除。
- multipart 上传已依赖 `spring.servlet.multipart.enabled=false`，审计实现不能读取 request body。

## 验收标准

```text
MARK 1/2 portal-bff proxied request writes request_audit_logs
MARK 2/2 admin-bff proxied request writes request_audit_logs
```

验证还需确认：

```text
portal-bff Ready 1/1
admin-bff Ready 1/1
ops-service GET /api/v1/ops/request-audit 可查询到 BFF smoke row
kubectl -n ircs-dev get httproute -> no resources
ResourceQuota Pod/Service 对象数量不增加
```

## 测试边界

本地：

```text
.\gradlew.bat :services:ircs-portal-bff:test :services:ircs-admin-bff:test
.\gradlew.bat build
```

K8S：

```text
kubectl -n ircs-dev rollout status deploy/ircs-portal-bff --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-admin-bff --timeout=240s
ClusterIP smoke
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota
```

## 当前进度

- 已确认 HTTP route coverage `missing=0`，本任务只处理行为副作用。
- 已确认 `ops-service` 具备 request audit 查询接口。
- 已实现 BFF audit 采集。
- 已完成本地测试、全仓 build、ARM64 镜像推送、K8S rollout 和 live smoke。

## 实现结果

新增共享轻量审计 writer：

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/BffRequestAuditWriter.java
```

接入：

```text
services/ircs-portal-bff
services/ircs-admin-bff
```

Smoke 结果：

```text
MARK 1/2 portal-bff proxied request writes request_audit_logs
MARK 2/2 admin-bff proxied request writes request_audit_logs
```

验证：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test
.\gradlew.bat build
.\scripts\smoke\bff-02801-request-audit-smoke.ps1
```

部署：

```text
ircs-portal-bff Ready 1/1
ircs-admin-bff  Ready 1/1

ircs-portal-bff imageID:
  harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:d051d3f2d2391de6a7ff2f01408ba4bbc79d63ac1f87931029e69c7e0d22fa52
ircs-admin-bff imageID:
  harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:4e80119902baef1cb59f50f5a53d05d42aac3e39b3ff8f26c99764f21d0c6854
```

后置检查：

```text
request_audit_logs smoke rows after cleanup: 0
HTTPRoute in ircs-dev: none
ResourceQuota: pods=28/30, services=24/26
```

关联迁移记录：

```text
docs/migrations/043-bff-request-audit-collection-parity.md
```
