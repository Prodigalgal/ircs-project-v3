# 013-01 task-service 只读采集任务 MVP 子任务

状态：完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/migrations/013-task-service-readonly-extraction.md`

## 背景

`ircs-task-service` 尚未接入 `ircs-dev`。V1 任务边界位于
`modules/task`，核心 HTTP 路由为 `/api/v1/collection-tasks`，核心表为
`collection_tasks`。任务创建、启动、暂停、停止会牵涉调度引擎和采集队列，
MVP 先抽取低风险只读查询能力。

## 目标

- 提供 `GET /api/v1/collection-tasks` 分页查询。
- 提供 `GET /api/v1/collection-tasks/{id}` 详情查询。
- 支持 V1 查询过滤参数：`name`、`status`、`dataSourceId`、`enabled`。
- 读取当前 PostgreSQL `collection_tasks` 和 `data_sources` 作为迁移桥。
- 部署 dev Deployment/Service，保持 ClusterIP only。
- 完成本地测试、server dry-run、ARM64 镜像、K8S rollout 和 smoke。

## 非目标

- 不接入 `POST /api/v1/collection-tasks`。
- 不接入 `PUT /api/v1/collection-tasks/{id}`。
- 不接入 `DELETE /api/v1/collection-tasks/{id}`。
- 不接入 `/start`、`/resume`、`/pause`、`/stop` 命令接口。
- 不接入 task logs。
- 不连接采集调度器或生产队列。
- 不新增 HTTPRoute。

## 输入

- V1 `CollectionTaskController`
- V1 `CollectionTask`
- V1 `CollectionTaskDTO`
- V1 `CollectionTaskRepository`
- `collection_tasks` 表
- `data_sources` 表

## 输出

- `ircs-task-service` 只读 JDBC 实现。
- `deploy/k8s/dev/task-service-dev.yaml`。
- 迁移文档和 K8S 快照更新。

## 影响范围

```text
services/ircs-task-service
deploy/k8s/dev/task-service-dev.yaml
docs/migrations
docs/k8s
deploy/k8s/README.md
tasks
```

## 风险

- `collection_tasks` 中包含 proxy password，详情响应必须脱敏。
- 写命令和运行态命令暂不迁移，前端若调用这些接口仍需后续切片。
- 当前仍使用共享 PostgreSQL，后续要逐步收紧表写权限。

## 验收标准

- `GET /api/v1/collection-tasks` 能返回临时任务分页结果。
- `GET /api/v1/collection-tasks/{id}` 能返回临时任务详情。
- `proxyPassword` 在响应中为 `null`。
- 临时数据 smoke 后清理为 0。
- `ircs-task-service` 在 `ircs-dev` Ready 1/1 且无 HTTPRoute。

## 测试边界

```text
.\gradlew.bat :services:ircs-task-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
kubectl rollout status deployment/ircs-task-service -n ircs-dev --timeout=240s
集群内 HTTP smoke
```

## 实现结果

- 已实现 `CollectionTaskController`、`TaskQueryService`、`JdbcCollectionTaskRepository`。
- 已实现列表响应 `TaskCardSummary` 和详情响应 `TaskDetailSummary`。
- 已新增 `deploy/k8s/dev/task-service-dev.yaml`，Service 为 ClusterIP。
- 已移除 MVP 阶段不需要的调度开关，不启动任务调度器。
- 已推送 `linux/arm64` 镜像。

## 验证结果

```text
.\gradlew.bat :services:ircs-task-service:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=server -f .\deploy\k8s\dev\task-service-dev.yaml -> server dry run passed
kubectl rollout status deployment/ircs-task-service -n ircs-dev --timeout=240s -> successfully rolled out
```

集群内 smoke：

```text
readiness -> UP
list by name -> totalElements=1
detail by id -> proxyPassword=null
list by status/enabled/dataSourceId -> totalElements=1
cleanup -> remaining_temp_tasks=0, remaining_temp_sources=0
```

部署结果：

```text
image digest: sha256:40161467a676ee8391e0dceb41f46e8131a1c3e1b9e4ebbab926d0b903de84fc
ircs-task-service Ready 1/1
HTTPRoute: none
```
