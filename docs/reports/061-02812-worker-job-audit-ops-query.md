# 061 028-12 worker/job audit ops 查询 API 报告

状态：已完成

日期：2026-06-08

## 背景

`028-12` 已经完成两条审计切片：

- `ircs-catalog-service` HTTP handler audit 写入 `request_audit_logs.request_source`。
- `ircs-notification-worker` `MailConsumer` 写入专用 `worker_job_audit_events`。

但目前 `worker_job_audit_events` 只有写入端，`ops-service` 尚不能查询 queue consumer、
scheduler 或 runner 的执行证据。这样 admin/ops 门户后续无法用 V3 查询面持续核对 V1
内部异步任务行为。

## 本轮目标

- 在 `ircs-ops-service` 增加 `worker_job_audit_events` 只读分页查询 API。
- 增加 24 小时摘要，区分 total、failed、succeeded 与最大耗时。
- 支持按 `jobSource`、`jobType`、`jobName`、`correlationId`、`status`、`errorClass`、
  `from`、`to` 过滤。
- 保持 worker/job audit 与 HTTP `request_audit_logs` 分表、分 URL，不混淆 request 语义。

## 非目标

- 不新增 HTTPRoute。
- 不新增 Service/Pod 常驻资源。
- 不修改 notification worker 写入逻辑。
- 不记录或查询 credential、token、Cookie、Authorization、邮件正文或请求体。
- 不实现跨表 join；本轮只提供独立 worker/job audit 查询面。

## 验收标准

```text
MARK 1/3 ops-service exposes worker/job audit page query
MARK 2/3 ops-service filters worker/job audit by source/status/correlation/time
MARK 3/3 ops-service exposes worker/job audit 24h summary
```

## 测试边界

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
```

本轮本地测试覆盖 repository/controller；dev live smoke 只验证 ops 查询 API 和
可清理 fixture，不执行 notification worker 真实队列写入 fixture。

## 实现结果

- 新增 `GET /api/v1/ops/worker-job-audit`，返回 `worker_job_audit_events`
  分页结果。
- 新增 `GET /api/v1/ops/worker-job-audit/summary`，返回近 24 小时 total、
  failed、succeeded 和最大耗时。
- 查询过滤支持 `jobSource`、`jobType`、`jobName`、`correlationId`、`status`、
  `errorClass`、`from`、`to`。
- SQL 排序字段复用 `JdbcPageSorts` 白名单，不把 pageable sort 直接透传为任意列名。

## 验证结果

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
exit code: 0
```

测试证据：

```text
WorkerJobAuditControllerTest tests=2, failures=0, errors=0
WorkerJobAuditRepositoryTest tests=2, failures=0, errors=0
RequestAuditControllerTest tests=2, failures=0, errors=0
```

MARK：

```text
MARK 1/3 ops-service exposes worker/job audit page query
MARK 2/3 ops-service filters worker/job audit by source/status/correlation/time
MARK 3/3 ops-service exposes worker/job audit 24h summary
```

## 未执行项

- 未新增 HTTPRoute。
- 未执行 notification worker queue fixture 写入 live smoke。
- 未接入更多 worker、scheduler 或 maintenance runner 写入端。

## Rollout 补充

2026-06-08：为让 ops 查询 API 能被 dev portal/admin 使用，已推送 ARM64
`harbor.mnnu.eu.org/ircs/ircs-ops-service:dev` 并 rollout `ircs-ops-service`。

首次运行 migrator Job 时被 ResourceQuota 拦截：

```text
exceeded quota: ircs-dev-quota, requested: limits.cpu=500m, used: limits.cpu=7650m, limited: limits.cpu=8
```

已将 `deploy/k8s/dev/migrator-job-dev.yaml` 的 dev Job CPU 资源调低为：

```text
requests.cpu=25m
limits.cpu=250m
```

该调整只影响一次性 migrator Job，保持低资源 dev 策略，不新增 Service 或 HTTPRoute。

Rollout 和 migration 结果：

```text
.\gradlew.bat :services:ircs-ops-service:jib "-PjibToImage=harbor.mnnu.eu.org/ircs/ircs-ops-service:dev" -PjibTargetArch=arm64
BUILD SUCCESSFUL

.\gradlew.bat :platform:ircs-migrator:jib "-PjibToImage=harbor.mnnu.eu.org/ircs/ircs-migrator:dev" -PjibTargetArch=arm64
BUILD SUCCESSFUL

kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=180s
deployment "ircs-ops-service" successfully rolled out

kubectl -n ircs-dev wait --for=condition=complete job/ircs-migrator --timeout=180s
job.batch/ircs-migrator condition met
```

Image evidence：

```text
ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:34a42fe11f08a78ba2ae1b042b6cbe2b32bf04e42e6b9711affc1cc57600d01a

ircs-migrator imageID:
  harbor.mnnu.eu.org/ircs/ircs-migrator@sha256:aaaf00ef4567fb47daeda23d85eaf92f0bc5bde702a8bdac43cf1b2a38ddace0
```

Live schema 和 API smoke：

```text
worker_job_audit_events table exists

trace: 02812-ops-query-20260608094520
pageTotalElements: 1
firstJobSource: ircs-smoke-worker
firstStatus: failed
summaryTotalLast24h: 1
residue: 0
```

Live MARK：

```text
MARK 1/4 worker_job_audit_events live table accepts fixture event
MARK 2/4 ops worker-job audit page query filters live fixture
MARK 3/4 ops worker-job audit summary returns live totals
MARK 4/4 live fixture cleanup leaves no worker_job_audit_events residue
```

后置检查：

```text
kubectl -n ircs-dev get httproutes.gateway.networking.k8s.io
No resources found in ircs-dev namespace.

ResourceQuota:
pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi

migrator pods:
No resources found in ircs-dev namespace.
```

最终组合验证：

```text
.\gradlew.bat :services:ircs-ops-service:test :platform:ircs-migrator:processResources --rerun-tasks
BUILD SUCCESSFUL in 10s
```
