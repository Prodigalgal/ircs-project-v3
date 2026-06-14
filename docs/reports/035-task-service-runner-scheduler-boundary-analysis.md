# 035 task-service runner 与 scheduler 边界分析

状态：已完成

更新时间：2026-06-06

## 背景

`025` 父任务已经完成 `ircs-task-service` 的 `CollectionTaskController`
HTTP lifecycle/log/state 路由迁移。当前 V3 中 `POST /api/v1/collection-tasks/{id}/start`
和 `resume` 会把任务置为 `QUEUED`，但还不会像 V1 一样把任务提交到运行器，也不会在
服务启动后按 `cron_expression` 重新注册定时采集任务。

V1 的真实运行链路是：

```text
CollectionTaskController
  -> CollectionEngineService
  -> StreamTaskExecutor
  -> ListScraperService + ScraperPipelineExecutor
  -> TaskLogService / TaskCommandService stats
```

其中 `StreamTaskExecutor` 同时承担抓取、pipeline、retry/backoff、进度、日志和统计。
在 V3 微服务边界下，这些职责不应整体复制回 `task-service`：

- `task-service` 拥有 collection task lifecycle、scheduler、runner 编排和状态。
- `scraper-service` 拥有 data source 抓取、mapping、ingestion queue publish。
- `ingestion-worker` 继续消费 `q.ingest.video` 并写内容表。

## 目标

本切片实现 `025-09 task-service task-runner contract and scheduler execution`：

- `task-service` 的 start/resume 在状态置为 `QUEUED` 后，提交本地低并发 runner。
- runner 从 `collection_tasks` 读取执行计划，写入 Valkey `task:logs:{taskId}`。
- runner 调用 `scraper-service` 内部 batch execution API，完成抓取/投递边界。
- runner 根据执行结果把任务置为 `COMPLETED` 或 `FAILED`，并同步基础统计字段。
- `task-service` 启动时将异常中断的 `QUEUED/RUNNING/STOPPING` 任务修复为 `PAUSED`。
- `task-service` 启动后注册 `enabled=true` 且有 `cron_expression` 的任务。
- create/update/delete 后刷新本地 scheduler；delete 时取消对应 cron。
- 验证时若经过 `admin-bff`，BFF 必须过滤 Java `HttpClient` 受限请求头，避免门户验证链路被代理层 500 阻断。

## 非目标

- 不新增 Pod、Service、PVC、HTTPRoute 或 RabbitMQ topic。
- 不把 V1 `StreamTaskExecutor` 完整复制到 `task-service`。
- 不在本切片实现跨服务运行中取消；pause/stop 的远程执行中断能力留待后续。
- 不改变 `collection_tasks` 或 `data_sources` 表结构。
- 不要求外部资源站在线才能完成 dev smoke。
- 不改变 admin/portal 前端路由。

## 方案

采用“内部 HTTP batch contract + task-service 本地 runner/scheduler”的方式，不新增资源配额占用。

```text
                         ircs-dev / ClusterIP only

  +-------------------+        +------------------------------+
  | admin-bff/gateway | -----> | ircs-task-service            |
  | existing route    |        |------------------------------|
  +-------------------+        | CollectionTaskController     |
                               | TaskCommandService           |
                               | CollectionTaskRunnerService  |
                               | CollectionTaskScheduler      |
                               +-----------+------------------+
                                           |
                         internal HTTP     |
                                           v
                               +------------------------------+
                               | ircs-scraper-service         |
                               |------------------------------|
                               | /internal/v1/scraper/        |
                               |   task-executions            |
                               | ListScraperClient            |
                               | ScraperMappingService        |
                               | IngestionPublisher           |
                               +-----------+------------------+
                                           |
                                           v
                               +------------------------------+
                               | RabbitMQ q.ingest.video      |
                               +------------------------------+
                                           |
                                           v
                               +------------------------------+
                               | ircs-ingestion-worker        |
                               +------------------------------+

  +-------------------+        +------------------------------+
  | PostgreSQL        | <----> | collection_tasks/data_sources |
  +-------------------+        +------------------------------+

  +-------------------+
  | Valkey            |
  | task:logs:{id}    |
  +-------------------+
```

## 内部契约

`scraper-service` 新增内部接口：

```text
POST /internal/v1/scraper/task-executions
```

请求由 `task-service` 生成，至少包含：

```text
taskId
dataSourceId
keyword
startPage
endPage
userAgent
proxy
headers
fixedDelayMs
forceIngest
```

响应包含：

```text
status
publishedCount
failedCount
log entries
```

## 状态流转

```text
IDLE/PAUSED/COMPLETED/FAILED
        |
        | start/resume
        v
      QUEUED
        |
        | runner picked
        v
      RUNNING
        |
        +---- success ----> COMPLETED
        |
        +---- error ------> FAILED

QUEUED/RUNNING + service restart -> PAUSED
RUNNING/QUEUED + pause command    -> PAUSED
RUNNING/QUEUED/PAUSED + stop      -> STOPPING
```

## 风险

- 内部 HTTP 调用若超时，task 会进入 `FAILED`；需在日志中保留清晰错误。
- 运行中取消需要 scraper-service 支持 task cancellation token，本轮只做提交前和完成前状态检查。
- cron 注册是单副本 dev 语义；若未来多副本，需要 leader election 或外部 scheduler。
- direct item smoke 只能证明 contract 与 ingestion 投递，不等同于真实资源站抓取全链路。
- scheduler 使用内存 `ScheduledFuture`，服务重启会通过启动扫描重建。

## 验收标准

- 本地 task spec 已建立，并挂到 `025` 父任务。
- `scraper-service` 提供内部 batch execution API。
- `task-service` start/resume 能提交 runner，runner 能从 `QUEUED` 推进到终态。
- `task-service` 能启动时修复异常活跃任务为 `PAUSED`。
- `task-service` 能根据 create/update/delete 刷新 cron scheduler。
- 单元测试覆盖 runner 成功/失败、scheduler 注册取消、scraper 内部 contract。
- `.\gradlew.bat :services:ircs-task-service:test :services:ircs-scraper-service:test` 通过。
- `.\gradlew.bat build` 通过。
- ARM64 镜像推送并 rollout `ircs-task-service`、`ircs-scraper-service`。
- ClusterIP smoke 覆盖 start -> runner -> scraper internal -> logs/status。
- smoke 后临时 PostgreSQL、Valkey、RabbitMQ 消息清理完成。
- `ircs-dev` 仍无 HTTPRoute，Pod/Service quota 不增加。
- 通过 `admin-bff` 发起的 smoke 不因 `Expect` 等 restricted header 透传而失败。

## 完成结果

本切片已完成并归档到：

```text
docs/migrations/031-task-service-runner-scheduler-extraction.md
tasks/done/025-09-task-service-runner-scheduler-mvp.md
```

关键结果：

- `task-service` 已能在 `start/resume` 后提交低并发 runner。
- runner 能从 `QUEUED` 推进到 `RUNNING` 和 `COMPLETED/FAILED` 终态。
- runner 写入 `task:logs:{taskId}`，并同步 processed/success/failed 统计。
- `scraper-service` 提供内部 `POST /internal/v1/scraper/task-executions`。
- cron scheduler、配置变更刷新、启动自愈和 watchdog 均已接入。
- `admin-bff` 已过滤 `Expect`，避免 Java `HttpClient` restricted header 造成代理 500。

验证摘要：

```text
.\gradlew.bat :services:ircs-admin-bff:test :services:ircs-task-service:test :services:ircs-scraper-service:test -> PASS
.\gradlew.bat build -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\scraper-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
ircs-task-service / ircs-scraper-service / ircs-admin-bff rollout -> Ready 1/1
admin-bff smoke start -> runner -> scraper internal -> ingestion -> COMPLETED|1|1|0
RabbitMQ queue/DLQ messages -> 0
HTTPRoute in ircs-dev -> none
ResourceQuota -> pods=28/30, services=24/26
```
