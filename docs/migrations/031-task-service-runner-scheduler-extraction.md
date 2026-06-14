# 031 task-service runner/scheduler extraction

状态：已完成

日期：2026-06-06

关联任务：

- `tasks/done/025-09-task-service-runner-scheduler-mvp.md`

关联报告：

- `docs/reports/035-task-service-runner-scheduler-boundary-analysis.md`

## 背景

`026-task-service-lifecycle-api-extraction` 已迁移 V1 `CollectionTaskController`
的 lifecycle/log/state HTTP 路由，但 `start/resume` 只会进入 `QUEUED`。本轮继续迁移
V1 `CollectionEngineService`、`StreamTaskExecutor`、`CollectionTaskScheduler`、
`TaskCleanupRunner` 和 `TaskWatchdog` 的运行时契约，让 V3 `task-service` 能真实提交
执行并维护状态、日志和定时任务。

## 迁移结果

- `ircs-task-service` 新增低并发 `CollectionTaskRunnerService`。
- `ircs-task-service` 在 create/update/delete/start/resume 后发布任务事件并刷新 runner/scheduler。
- `ircs-task-service` 新增 `CollectionTaskCronScheduler`，启动时注册 enabled cron task。
- `ircs-task-service` 新增启动自愈，把异常 `QUEUED/RUNNING/STOPPING` 任务修复为 `PAUSED`。
- `ircs-task-service` 新增 `TaskWatchdog`，对超时 active task 进入失败终态。
- `ircs-scraper-service` 新增内部 `POST /internal/v1/scraper/task-executions` 执行契约。
- `ircs-scraper-service` 复用现有 manual scrape/publish 逻辑，不把抓取细节复制回 task-service。
- `ircs-admin-bff` 过滤 Java `HttpClient` restricted header `Expect`，保证 admin 门户 smoke 链路不被代理层 500 阻断。

## 边界

```text
admin-bff/gateway
  -> ircs-task-service
       -> runner/scheduler/status/log
       -> internal HTTP
  -> ircs-scraper-service
       -> data source scrape or direct item publish
       -> q.ingest.video
  -> ircs-ingestion-worker
       -> raw_videos
```

本切片不新增 Pod、Service、PVC、HTTPRoute 或 RabbitMQ topic。cron scheduler 是单副本 dev 语义，未来多副本生产形态仍需要 leader election 或外部 scheduler。

## 镜像与 rollout

```text
ircs-task-service:    harbor.mnnu.eu.org/ircs/ircs-task-service:dev sha256:677ee7eab5514d7e2e81ed9e8a0b4d390007bff50d8c7cc109fa43da294d38e6
ircs-scraper-service: harbor.mnnu.eu.org/ircs/ircs-scraper-service:dev sha256:271e249c2c477520595391572067ff3097a111e247169f06e8114ad44d60ff98
ircs-admin-bff:       harbor.mnnu.eu.org/ircs/ircs-admin-bff:dev sha256:3bbc9cde8b8486f5e1148bbb132aa690ea7c8dc95be2d5c796d98aaaa6a10edc
```

`ircs-dev` 当前状态：

```text
ircs-task-service      Ready 1/1
ircs-scraper-service   Ready 1/1
ircs-admin-bff         Ready 1/1
HTTPRoute              none
ResourceQuota          pods=28/30, services=24/26
```

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-admin-bff:test :services:ircs-task-service:test :services:ircs-scraper-service:test
.\gradlew.bat build
```

K8S 验证：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\scraper-service-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-admin-bff --timeout=240s
```

ClusterIP smoke 通过 `admin-bff` 创建临时 collection task 并启动：

```text
POST /api/v1/collection-tasks            -> created
POST /api/v1/collection-tasks/{id}/start -> 202
statusLine                               -> COMPLETED|1|1|0
rawVideoCount                            -> 1
RabbitMQ queue/DLQ messages              -> 0
```

任务日志：

```text
Task runner started
Task execution accepted by scraper-service
Published direct task item
Task execution finished: published=1, failed=0
Task completed
```

smoke 临时 `collection_tasks`、`data_sources` 和 `raw_videos` 数据已清理。

## 复现标记

本切片关闭的不是新增公开 HTTP route，而是 `start/resume -> runner -> scraper internal -> ingestion`
运行时契约。按用户要求“接口完整迁移且复现才标记 1 次”，本轮标记：

```text
task runner/scheduler runtime contract: 1/1
```

公共 V1/V3 route count 不因本切片新增；`025` 父任务的 public route audit 后续继续按缺口列表拆分。
