# 025-04 task-service 生命周期 API MVP 子任务

状态：完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/029-task-service-lifecycle-boundary-analysis.md`
- `docs/migrations/013-task-service-readonly-extraction.md`
- `docs/migrations/026-task-service-lifecycle-api-extraction.md`

## 背景

V3 `ircs-task-service` 原先只覆盖 V1 `CollectionTaskController` 的 list/detail。
完整路由覆盖还缺 create、update、delete、start、resume、pause、stop 和 logs。

## 目标

- 接入 `POST /api/v1/collection-tasks`。
- 接入 `PUT /api/v1/collection-tasks/{id}`。
- 接入 `DELETE /api/v1/collection-tasks/{id}`。
- 接入 `POST /api/v1/collection-tasks/{id}/start`。
- 接入 `POST /api/v1/collection-tasks/{id}/resume`。
- 接入 `POST /api/v1/collection-tasks/{id}/pause`。
- 接入 `POST /api/v1/collection-tasks/{id}/stop`。
- 接入 `GET /api/v1/collection-tasks/{id}/logs`。
- 复用现有 `ircs-task-service` Deployment/Service，不新增 HTTPRoute。

## 非目标

- 不迁移 V1 `StreamTaskExecutor`。
- 不新增 task execution RabbitMQ topic 或 consumer。
- 不新增 scheduler/watchdog。
- 不新增数据库 migration。
- 不修改 `scraper-service`。

## 实现结果

- 已实现 task command DTO、command service、log service。
- 已扩展 JDBC repository，支持 create/update/delete 和生命周期状态流转。
- 已接入 Valkey `task:logs:{taskId}` 读取/清理，并兼容 UTF-8 BOM 日志值。
- 已为 task-service dev Deployment 注入 `SPRING_DATA_REDIS_HOST`。

## 验证结果

```text
.\gradlew.bat :services:ircs-task-service:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
ARM64 Jib push -> harbor.mnnu.eu.org/ircs/ircs-task-service:dev
kubectl rollout status deployment/ircs-task-service -n ircs-dev --timeout=240s -> successfully rolled out
```

ClusterIP smoke：

```text
create/update/start/pause/resume/stop/logs/delete ok
remaining task/source after cleanup: 0|0
task:logs:* after cleanup: 0
```

部署结果：

```text
image digest: sha256:ce13e231cc4a1ef058d83a6429ede0a2ccd40dfdc72afe3115174dc01f80b214
ResourceQuota: pods=23/24, services=19/20
ircs-dev HTTPRoute: none
```

## 遗留问题

- 本切片只关闭 HTTP command/log/state 缺口。
- 真实 task runner、scheduler 和 scraper execution contract 拆入
  `025-09 task-service task-runner contract and scheduler execution`。
