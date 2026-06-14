# 026 task-service 生命周期 API 抽取记录

状态：已完成

更新时间：2026-06-05

## 范围

本次在已部署的 `ircs-task-service` 上补齐 V1 `CollectionTaskController`
剩余 8 条 HTTP 路由：

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

## 实现

- 新增 `TaskCreateRequest`、`TaskUpdateRequest`、`TaskIdResponse` 和
  `TaskItemLogResponse`。
- 新增 `TaskCommandService`，实现 create/update/delete 与
  start/resume/pause/stop 状态流转。
- 扩展 `JdbcCollectionTaskRepository`，补齐 `collection_tasks` 写入、部分更新、
  状态机更新、data source 存在性和 busy 检查。
- 新增 `TaskLogService`，读取并清理 Valkey `task:logs:{taskId}` list。
- 为 task-service 增加 Redis starter，并在 dev Deployment 中注入
  `SPRING_DATA_REDIS_HOST`。

## 明确非目标

- `start/resume` 按 V1 状态机进入 `QUEUED`，但不复制单体内 `StreamTaskExecutor`。
- 不新增 RabbitMQ topic、consumer、scheduler 或 task-runner worker。
- 不新增 Pod、Service、HTTPRoute 或数据库 migration。

## 验证

```text
.\gradlew.bat :services:ircs-task-service:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml -> server dry run passed
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-task-service' -ImageName ircs-task-service -Tag dev -Architecture arm64 -Os linux -> pushed
kubectl rollout status deployment/ircs-task-service -n ircs-dev --timeout=240s -> successfully rolled out
```

最终部署：

```text
image digest: sha256:ce13e231cc4a1ef058d83a6429ede0a2ccd40dfdc72afe3115174dc01f80b214
ircs-task-service Ready 1/1
ResourceQuota: pods=23/24, services=19/20
ircs-dev HTTPRoute: none
```

ClusterIP smoke：

```text
create/update/start/pause/resume/stop/logs/delete ok
task/source cleanup: 0|0
task log keys: 0
```

## 遗留

- 真实异步采集执行仍需后续 `025-09 task-service task-runner contract and scheduler execution`
  处理。
- 当前 lifecycle API 是 command/log/state 迁移桥，不代表任务已被 runner 消费。
