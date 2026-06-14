# 025-09 task-service runner/scheduler MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/029-task-service-lifecycle-boundary-analysis.md`
- `docs/reports/035-task-service-runner-scheduler-boundary-analysis.md`
- `docs/migrations/026-task-service-lifecycle-api-extraction.md`

## 背景

`025-04` 已经补齐 V1 `CollectionTaskController` 的 lifecycle/log/state HTTP 路由，
但 `start/resume` 当前只进入 `QUEUED`，没有真实执行。V1 中真实执行依赖
`CollectionEngineService`、`StreamTaskExecutor`、`CollectionTaskScheduler`、
`TaskCleanupRunner` 和 `TaskWatchdog`。V3 需要按微服务边界迁移这些运行时契约。

## 目标

- 在 `ircs-task-service` 增加低并发 runner，承接 start/resume 后的实际执行提交。
- 在 `ircs-task-service` 增加 cron scheduler 和启动自愈。
- 在 `ircs-scraper-service` 增加内部 batch execution API，让 task runner 不复制 scraper 逻辑。
- 保持 `task-service` 负责状态、日志、scheduler，`scraper-service` 负责抓取和投递。
- 修复 smoke 发现的 `admin-bff` restricted header 透传问题，保证门户/BFF 后续可持续验证。
- 不新增 K8S Pod/Service/HTTPRoute。

## 非目标

- 不完整迁移 V1 `StreamTaskExecutor` 的所有 retry/backoff/cancellation 能力。
- 不新增 task execution RabbitMQ topic。
- 不实现多副本 leader election。
- 不迁移新的前端页面。
- 不依赖外部资源站在线作为唯一验收依据。

## 输入

- V1：
  - `CollectionEngineServiceImpl`
  - `CollectionTaskScheduler`
  - `TaskCleanupRunner`
  - `TaskWatchdog`
  - `GracefulShutdownManager`
  - `StreamTaskExecutor`
- V3：
  - `services/ircs-task-service`
  - `services/ircs-scraper-service`
  - `shared/ircs-contracts`
  - `deploy/k8s/dev/task-service-dev.yaml`
  - `deploy/k8s/dev/scraper-service-dev.yaml`

## 输出

- task-service runner/scheduler 代码和测试。
- scraper-service internal task execution contract 代码和测试。
- 迁移记录、报告、任务状态和 K8S 环境快照回写。
- ARM64 dev 镜像与 ClusterIP smoke 结果。

## 影响范围

计划写入：

```text
services/ircs-task-service
services/ircs-scraper-service
services/ircs-admin-bff
docs/reports
docs/migrations
docs/k8s
tasks
```

不计划写入：

```text
frontend
portal-bff
database migration
new Kubernetes resource kinds
```

## 依赖

- `ircs-scraper-service` 已 Ready，并能向 `q.ingest.video` 投递。
- `ircs-ingestion-worker` 已 Ready。
- PostgreSQL `collection_tasks`、`data_sources` schema 与 V1 兼容。
- Valkey 可用于 `task:logs:{taskId}`。
- `ircs-dev` 仍保持低资源 profile。

## 风险

- 内部 HTTP 执行超时会使任务失败，需要日志中保留错误。
- pause/stop 不能强行中断 scraper-service 正在执行的 HTTP 请求，本轮只保证状态与终态保护。
- cron 以单副本内存 scheduler 实现，不适合未来多副本生产形态。
- 若真实数据源不可用，ClusterIP smoke 需要用 direct item contract 验证提交链路。

## 验收标准

- `task-service` start/resume 提交 runner 后能推进到 `RUNNING` 和终态。
- runner 成功时写日志并 `COMPLETED`，失败时写错误日志并 `FAILED`。
- scheduler 能注册、刷新、取消 cron task。
- 启动自愈能把异常活跃任务改为 `PAUSED`。
- scraper internal task execution API 能返回 published/failed stats 与日志。
- admin-bff 不再把 Java HttpClient restricted header 透传到上游服务。
- 不新增 Pod、Service、HTTPRoute。
- 单元测试、全仓 build、ARM64 rollout 和 ClusterIP smoke 通过。

## 测试边界

计划执行：

```text
.\gradlew.bat :services:ircs-task-service:test :services:ircs-scraper-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\scraper-service-dev.yaml
ARM64 image build and push on VPS or Jib ARM64 path
kubectl rollout status deployment/ircs-task-service -n ircs-dev
kubectl rollout status deployment/ircs-scraper-service -n ircs-dev
ClusterIP smoke through admin-bff/gateway or internal service access
kubectl -n ircs-dev get httproute
kubectl -n ircs-dev describe resourcequota ircs-dev-quota
```

## 实现结果

- `ircs-task-service` 已新增 runner、cron scheduler、启动自愈和 watchdog。
- `ircs-task-service` start/resume 后会发布队列事件并提交 runner。
- `ircs-task-service` 通过 `ScraperTaskExecutionClient` 调用 `ircs-scraper-service` 内部执行契约。
- `ircs-scraper-service` 已新增 `POST /internal/v1/scraper/task-executions`。
- `ircs-scraper-service` 支持 direct item dev smoke，并复用 ingestion publish。
- `ircs-admin-bff` 已过滤 `Expect`，避免 restricted header 透传导致验证链路 500。

## 验证结果

```text
.\gradlew.bat :services:ircs-admin-bff:test :services:ircs-task-service:test :services:ircs-scraper-service:test -> PASS
.\gradlew.bat build -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\scraper-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
kubectl -n ircs-dev rollout status deployment/ircs-admin-bff --timeout=240s -> PASS
```

ClusterIP smoke 通过 `admin-bff` 验证：

```text
taskId=0b81421f-2986-4375-834e-56c7ae924ddc
startStatus=202
statusLine=COMPLETED|1|1|0
rawVideoCount=1
RabbitMQ queue/DLQ messages=0
```

日志复现：

```text
Task runner started
Task execution accepted by scraper-service
Published direct task item
Task execution finished: published=1, failed=0
Task completed
```

smoke 临时数据已清理，`ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为
`pods=28/30`、`services=24/26`。

## 复现标记

```text
task runner/scheduler runtime contract: 1/1
```

## 当前状态

已完成。后续 public route 缺口继续从
`docs/reports/025-v1-v3-route-coverage-audit.md` 拆分。
