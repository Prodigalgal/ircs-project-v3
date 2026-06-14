# 029 task-service 生命周期 API 边界分析

状态：已完成

更新时间：2026-06-05

## 背景

`025` 父任务正在按 V1 Controller 路由关闭 V3 缺口。V3
`ircs-task-service` 已完成 `/api/v1/collection-tasks` 的 list/detail 只读 MVP，
但 V1 `CollectionTaskController` 仍有 8 条路由未接入：

```text
POST   /api/v1/collection-tasks
PUT    /api/v1/collection-tasks/{id}
DELETE /api/v1/collection-tasks/{id}
POST   /api/v1/collection-tasks/{id}/start
POST   /api/v1/collection-tasks/{id}/resume
POST   /api/v1/collection-tasks/{id}/pause
POST   /api/v1/collection-tasks/{id}/stop
GET    /api/v1/collection-tasks/{id}/logs
```

V1 中这些路由分散到 `TaskCommandService`、`CollectionEngineService` 和
`TaskLogService`：写接口操作 `collection_tasks`，生命周期接口会先改变任务状态，
再通过单体内 `StreamTaskExecutor` 投入本地线程池，日志从 Redis
`task:logs:{taskId}` list 读取。

## 当前 V3 约束

- V3 `QueueTopic` 尚无 collection-task 专属 topic。
- V3 `scraper-service` 当前提供 manual scrape SSE，并向 `q.ingest.video` 投递抓取结果。
- `ircs-dev` 当时 ResourceQuota 为 `pods=23/24`、`services=19/20`，本切片必须复用现有
  `ircs-task-service` Deployment/Service。
- 当前 namespace 无 HTTPRoute，本切片继续保持 ClusterIP only。
- live schema 显示 `collection_tasks.name` 唯一，`data_source_id` 外键指向
  `data_sources(id)`，`headers` 为 `jsonb`。
- Valkey 当前无 `task:logs:*` 残留。

## 目标边界

本切片实现 V1 路由覆盖的 command/log/state MVP：

- create：校验 `dataSourceId` 存在，插入 `collection_tasks`，初始状态 `IDLE`。
- update：校验 path/body id 一致，按非空字段部分更新，保持 proxy password 脱敏响应约束。
- delete：删除 task，并清理 `task:logs:{id}`。
- start/resume：按 V1 状态机把任务置为 `QUEUED`，记录执行时间和统计字段。
- pause：仅当状态为 `RUNNING` 或 `QUEUED` 时置为 `PAUSED`。
- stop：仅当状态为 `RUNNING`、`QUEUED` 或 `PAUSED` 时置为 `STOPPING`。
- logs：读取 `task:logs:{id}`，支持 `offset` 和 `limit`，限制最大 page size。

## 非目标边界

- 不在 `task-service` 中复制 V1 `StreamTaskExecutor`。
- 不新增 RabbitMQ topic、consumer 或 task-runner worker。
- 不通过 lifecycle API 调用 `scraper-service` manual scrape SSE。
- 不新增 Deployment、Service、HTTPRoute 或外部入口。
- 不迁移 cron scheduler 和 watchdog。
- 不改变 `collection_tasks` 表结构。

## 架构视图

```text
                 ClusterIP only / ircs-dev

        +--------------------------------------+
        |          ircs-task-service           |
        |--------------------------------------|
        | CollectionTaskController             |
        |  - create/update/delete              |
        |  - start/resume/pause/stop           |
        |  - logs                              |
        +------------------+-------------------+
                           |
             +-------------+-------------+
             |                           |
             v                           v
   +---------------------+      +---------------------+
   | PostgreSQL          |      | Valkey              |
   | collection_tasks    |      | task:logs:{taskId}  |
   | data_sources        |      | list, TTL by writer |
   +---------------------+      +---------------------+

   Deferred execution boundary:

   +---------------------+      +---------------------+
   | future task runner  | ---> | scraper/ingestion   |
   | queue or scheduler  |      | q.ingest.video      |
   +---------------------+      +---------------------+
```

## 风险

- `start/resume` 只进入 `QUEUED`，不会真实启动采集；如果前端依赖实时采集结果，需要后续
  task-runner/scraper 契约切片。
- `STOPPING` 在没有 runner 的情况下不会自动落回 `IDLE` 或 `COMPLETED`，本切片只保持 V1
  命令状态语义。
- `headers` 是 jsonb，非法 JSON 必须返回 400，不能写入坏数据。
- `collection_tasks.name` 唯一，重复名称会返回冲突或数据库错误，需要转换为清晰响应。
- logs 是 Redis/Valkey 兼容 list；V3 当前没有日志写入者，smoke 需要手工写入临时 key 并清理。

## 验收标准

- 8 条 V1 task 缺口路由在 `ircs-task-service` 中可调用。
- create/update/delete 对 `collection_tasks` 的写入可通过 detail/list 复核。
- start/resume/pause/stop 状态流转符合 V1 `CollectionTask` 状态机。
- logs 能读取 Valkey `task:logs:{id}`，offset/limit 生效。
- 单元测试、全仓 build、ARM64 镜像推送、K8S rollout 和 ClusterIP smoke 通过。
- smoke 后 PostgreSQL 临时 task/source 和 Valkey 临时日志 key 清理为 0。

## 实现结果

- 已在 `ircs-task-service` 接入 8 条 V1 task lifecycle/log route。
- 已实现 create/update/delete 与 start/resume/pause/stop 状态流转。
- 已接入 Valkey `task:logs:{taskId}` 读取/清理，并兼容 UTF-8 BOM 日志值。
- 已复用现有 Deployment/Service，不新增 Pod、Service 或 HTTPRoute。

## 验证结果

```text
.\gradlew.bat :services:ircs-task-service:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
ARM64 Jib push -> harbor.mnnu.eu.org/ircs/ircs-task-service:dev
kubectl rollout status deployment/ircs-task-service -n ircs-dev --timeout=240s -> successfully rolled out
ClusterIP smoke -> create/update/start/pause/resume/stop/logs/delete ok
cleanup -> task/source=0|0, task:logs:*=0
image digest -> sha256:ce13e231cc4a1ef058d83a6429ede0a2ccd40dfdc72afe3115174dc01f80b214
```

## 后续切片

```text
025-09 task-service task-runner contract and scheduler execution
```

该后续切片需要明确 `task-service` 与 `scraper-service` 或独立 worker 的执行契约，
再决定是新增 task execution queue，还是让 `scraper-service` 暴露内部 batch execution API。
