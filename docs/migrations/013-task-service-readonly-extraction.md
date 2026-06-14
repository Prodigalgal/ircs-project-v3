# 013 task-service 只读采集任务 API 抽取

状态：完成

更新时间：2026-06-03

## 背景

`ircs-task-service` 是 V3 目标服务之一，V1 边界位于
`ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/task`。
V1 `CollectionTaskController` 的核心路由为 `/api/v1/collection-tasks`，
但创建、更新、启动、暂停、停止和日志读取会牵涉调度器、Redis 状态缓存、
采集队列和运行日志。MVP 先抽取只读查询能力，避免提前接管任务执行状态。

## 本轮实现范围

已实现：

- `GET /api/v1/collection-tasks`
- `GET /api/v1/collection-tasks/{id}`
- 查询过滤参数：`name`、`status`、`dataSourceId`、`enabled`
- 读取 `collection_tasks` 和 `data_sources` 作为共享 PostgreSQL 迁移桥
- dev Deployment/Service，ClusterIP only
- `proxyPassword` 在详情响应中显式为 `null`

未实现：

- `POST /api/v1/collection-tasks`
- `PUT /api/v1/collection-tasks/{id}`
- `DELETE /api/v1/collection-tasks/{id}`
- `/start`、`/resume`、`/pause`、`/stop`
- `/logs`
- 采集调度器、Redis task state cache、RabbitMQ producer/consumer
- 独立数据库拆分和表写权限收紧
- HTTPRoute

## 代码范围

```text
services/ircs-task-service/build.gradle
services/ircs-task-service/src/main/resources/application.yaml
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/IrcsTaskServiceApplication.java
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/CollectionTaskController.java
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskQueryService.java
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/JdbcCollectionTaskRepository.java
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskCardSummary.java
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskDetailSummary.java
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/CollectionTaskControllerTest.java
deploy/k8s/dev/task-service-dev.yaml
```

## 数据库边界

读取：

```text
collection_tasks
data_sources
```

写入：

```text
none
```

当前 dev live schema 已确认：

```text
collection_tasks.time_zone exists
collection_tasks.duplicate_strategy absent
```

## K8S 部署

```text
namespace: ircs-dev
deployment: ircs-task-service
service: ircs-task-service
image: harbor.mnnu.eu.org/ircs/ircs-task-service:dev
image digest: sha256:40161467a676ee8391e0dceb41f46e8131a1c3e1b9e4ebbab926d0b903de84fc
arch: linux/arm64
service type: ClusterIP
HTTPRoute: none
resources: request 25m/128Mi, limit 500m/512Mi
strategy: Recreate
```

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-task-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f .\deploy\k8s\dev\task-service-dev.yaml
```

镜像与部署：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-task-service' -ImageName ircs-task-service -Tag dev -Architecture arm64 -Os linux
kubectl apply -f .\deploy\k8s\dev\task-service-dev.yaml
kubectl rollout status deployment/ircs-task-service -n ircs-dev --timeout=240s
```

集群内 smoke：

```text
GET /actuator/health/readiness -> {"status":"UP"}
GET /api/v1/collection-tasks?name=Codex&page=0&size=10 -> totalElements=1
GET /api/v1/collection-tasks/{id} -> proxyPassword=null
GET /api/v1/collection-tasks?status=IDLE&enabled=true&dataSourceId={id} -> totalElements=1
cleanup -> remaining_temp_tasks=0, remaining_temp_sources=0
```

Smoke 使用的临时数据：

```text
data_sources.id:     ad5cb5a5-8364-4f78-b6de-f44ae50edc53
collection_tasks.id: c7f2c6a0-2572-4196-bf01-6616cd91cd8d
```

安全验证：

- 临时任务中写入了假的 proxy password。
- 详情响应中 `proxyPassword` 返回 `null`。
- 列表响应不包含 proxy password 字段。

## 当前资源结果

`ircs-dev` quota 在部署后为：

```text
pods:            14 / 20
services:        15 / 20
requests.cpu:    575m / 4
requests.memory: 2816Mi / 8Gi
limits.cpu:      6750m / 8
limits.memory:   8Gi / 16Gi
```

节点 allocated resources：

```text
cpu requests:    3547m / 4000m
memory requests: 10570Mi
```

## 遗留问题

- 写命令和任务运行命令尚未迁移，前端若调用这些接口仍需后续切片。
- `/logs` 尚未迁移，V1 依赖 Redis task log，需要单独确认日志存储边界。
- 调度器、watchdog、任务状态缓存和采集队列尚未由 `task-service` 接管。
- 当前仍使用共享 PostgreSQL，后续需要应用级 DB role 和表权限收紧。
