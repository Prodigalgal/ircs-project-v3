# 077 028 behavior parity parallel subagent slices

状态：已完成

更新时间：2026-06-08

## 背景

`028` 行为级 parity 当前 HTTP Controller / BFF missing 已为 `0`，第一阶段强制行为缺口为 `0`。
后续重点是继续补内部业务逻辑、异步队列、任务 runner、外部 provider、request audit、R2/SMTP
等深层副作用。用户要求把模块任务拆给多个 subagent，并让每个 subagent 使用 `gpt-5.5` +
`xhigh`。

本报告固定本轮并行切片的 ownership，避免多个 agent 修改同一服务目录或同一公共索引。

## 当前代码侧 handler audit 覆盖

```text
已接入 ServiceRequestAuditFilter:
ircs-catalog-service
ircs-config-service
ircs-content-service
ircs-credential-service
ircs-identity-service
ircs-interaction-service
ircs-magnet-service
ircs-portal-service
ircs-search-service
ircs-storage-service
ircs-task-service
ircs-ops-service
ircs-scraper-service

未接入 ServiceRequestAuditFilter:
none
```

## 并行 subagents

```text
Ohm      gpt-5.5 xhigh  worker   scraper-service handler audit
Ampere   gpt-5.5 xhigh  worker   ops-service handler audit
Descartes gpt-5.5 xhigh explorer  provider/R2/SMTP/worker audit 剩余硬缺口拆分
```

## 写入边界

### Ohm：scraper-service handler audit

允许写入：

```text
services/ircs-scraper-service/**
scripts/smoke/scraper-service-audit-02812-smoke.ps1
docs/reports/075-02812-scraper-service-audit-handler.md
docs/migrations/075-scraper-service-audit-handler.md
tasks/in-progress/028-12-075-scraper-service-audit-handler.md
```

不得写入：

```text
docs/index.md
tasks/index.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/k8s/current-environment-2026-06-02.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
services/ircs-ops-service/**
```

### Ampere：ops-service handler audit

允许写入：

```text
services/ircs-ops-service/**
scripts/smoke/ops-service-audit-02812-smoke.ps1
docs/reports/076-02812-ops-service-audit-handler.md
docs/migrations/076-ops-service-audit-handler.md
tasks/in-progress/028-12-076-ops-service-audit-handler.md
```

不得写入：

```text
docs/index.md
tasks/index.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/k8s/current-environment-2026-06-02.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
services/ircs-scraper-service/**
```

### Descartes：剩余硬缺口拆分

只读分析，不写文件。输出应能继续拆成独立 worker task spec。

## 主线程集成边界

主线程负责：

- 审查 worker 改动并修复集成冲突。
- 统一更新公共索引、父任务、K8S 快照和 `028` MARK。
- 推送 ARM64 镜像并 rollout 到 `ircs-dev`。
- 执行 live smoke、trace residue、HTTPRoute 和 ResourceQuota 后置检查。

## 验收口径

```text
每个 service handler audit 切片至少需要：
MARK 1/n handler request writes request_source
MARK 2/n sensitive query is redacted
MARK 3/n Authorization/Cookie/body/response content are excluded
MARK 4/n actuator/static noise is skipped
live MARK: pod Ready, read-only or dev-safe request success, audit row queryable, cleanup residue=0
```

## 集成结果

```text
Ohm/scraper-service:
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
live MARK 4/4 with invalid stream request, residue=0

Ampere/ops-service:
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
live MARK 4/4 with worker-job-audit summary request, residue=0

Descartes/remaining hard gaps:
TMDB live queue 已完成；R2 credential-only object smoke 已完成；后续优先级转向
magnet real provider runner、MAIL credential adapter/fake SMTP sink、ops runner registry
和 storage-service R2 path smoke。
```

## 当前状态

已完成。主线程已完成 worker 结果集成、ARM64 rollout、live smoke、公共索引、父任务、K8S 快照
和 MARK 回写；后续并行切片应从 provider/R2/SMTP/runner 剩余硬缺口重新拆分。
