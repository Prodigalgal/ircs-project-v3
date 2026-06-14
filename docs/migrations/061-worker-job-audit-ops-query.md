# 061 worker/job audit ops 查询迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 增加 `ops-service` 只读查询面，使已经写入
`worker_job_audit_events` 的 queue consumer / worker / runner 执行事件可以被
admin/ops 查询。

## 代码范围

```text
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/*WorkerJobAudit*.java
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/OpsApiExceptionHandler.java
services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops/*WorkerJobAudit*.java
services/ircs-ops-service/build.gradle
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 数据库变更

不新增表结构。复用已存在的专用表：

```text
worker_job_audit_events
```

## 风险控制

- 查询 API 只读。
- 排序字段使用白名单。
- 时间参数转为 `java.sql.Timestamp`，避免 PostgreSQL driver 类型推断漂移。
- 不查询敏感正文，只返回执行元数据与已脱敏错误摘要。

## 验证

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
exit code: 0
```

```text
MARK 1/3 ops-service exposes worker/job audit page query
MARK 2/3 ops-service filters worker/job audit by source/status/correlation/time
MARK 3/3 ops-service exposes worker/job audit 24h summary
```

## Dev 资源调整

为执行 live schema migration，`deploy/k8s/dev/migrator-job-dev.yaml` 的 CPU 配额从
`request=50m, limit=500m` 调整为 `request=25m, limit=250m`，避免在当前
`ircs-dev` ResourceQuota `limits.cpu=7650m/8` 下因一次性 Job 无法创建 Pod。

## Dev rollout

已推送 ARM64 镜像并 rollout：

```text
ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:34a42fe11f08a78ba2ae1b042b6cbe2b32bf04e42e6b9711affc1cc57600d01a

ircs-migrator imageID:
  harbor.mnnu.eu.org/ircs/ircs-migrator@sha256:aaaf00ef4567fb47daeda23d85eaf92f0bc5bde702a8bdac43cf1b2a38ddace0
```

Live 查询 smoke：

```text
trace=02812-ops-query-20260608094520
pageTotalElements=1
summaryTotalLast24h=1
residue=0
```

Live MARK：

```text
MARK 1/4 worker_job_audit_events live table accepts fixture event
MARK 2/4 ops worker-job audit page query filters live fixture
MARK 3/4 ops worker-job audit summary returns live totals
MARK 4/4 live fixture cleanup leaves no worker_job_audit_events residue
```
