# 028-16-C1-S4 task-service default collection task seed task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `infrastructure/dataInit/impl/TaskInitializer.java`
- V3 `services/ircs-task-service`

## 背景

V1 `DataInitializationOrchestrator` 会按 `InitTask.getOrder()` 执行初始化任务。
`TaskInitializer` 的 order 为 60，依赖 data source seed 已完成后，对每个 `data_sources`
生成两条默认 `collection_tasks`：`自动采集 - {sourceName}` 与 `全量采集 - {sourceName}`。

V3 目前 task-service 已有 CRUD、runner、cron scheduler、startup recovery 和 watchdog，
但空库只完成 catalog-service 的 data source preset/default_mapping seed。若 task-service
不派生默认任务，portal/admin 虽然能看到接口和 data source，但缺少 V1 默认采集入口，
行为 parity 仍不完整。

## 目标

- 在 `ircs-task-service` 中新增低优先级、幂等的默认 collection task seed。
- 对每个现有 data source 创建 V1 等价的两条任务：
  - `自动采集 - {sourceName}`：cron `0 0 3 * * ?`，`filterHours=48`。
  - `全量采集 - {sourceName}`：无 cron，`filterHours=null`。
- 复刻 V1 默认字段：enabled、status、timeZone、BY_PAGE、start/end page、random delay、
  timeout、retry 和 random UA。
- 不覆盖同名既有任务，避免用户已编辑任务被初始化逻辑重写。
- seed 完成后刷新 task-service cron scheduler，让新增自动任务进入当前进程调度表。
- 增加 task cron scheduler 显式启动 gate：默认不自动调度 cron，后续 smoke 或受控环境通过
  `APP_TASK_SCHEDULER_ENABLED=true` 打开。

## 非目标

- 不新增或修改 data source preset；该范围由 `028-16-C1-S2` 负责。
- 不实现 admin/ops 手动重新 seed 入口。
- 不实际触发 scraper 抓取或真实网络调用。
- 不处理多副本生产级 leader election；当前仍按 dev 单副本调度语义。
- 不新增 Pod、Service、HTTPRoute 或 K8S 资源。

## 输入

- `data_sources.id`
- `data_sources.name`
- V1 `TaskInitializer.ensureTask()` 默认值。

## 输出

- task-service 启动时幂等补齐 `collection_tasks`。
- 本地测试覆盖 empty-db、幂等、不覆盖既有任务和 scheduler refresh。
- docs/tasks 索引与 089 总账更新。

## 影响范围

```text
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/**
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 依赖

- `028-16-C1-S2` 已完成 data source preset/default_mapping seed。
- `collection_tasks.name` 具有唯一约束，可作为 V1 同名幂等边界。
- `CollectionTaskCronScheduler.refresh()` 已存在，可用于 seed 后刷新调度。

## 风险

- dev 环境已有用户或 smoke 临时 task，seed 必须只按同名缺失插入，不覆盖。
- 若 data source 名称后续被修改，V1 同样按 name 生成新任务；本切片先保持 V1 语义。
- 新增自动任务有 cron，但本切片不在 K8S 中触发真实抓取，避免 dev 资源和外部流量突增。
- cron scheduler 默认关闭会改变“启动即注册 cron”的本地默认行为；start/resume 手动执行不受影响，
  需要 cron 行为的 smoke 必须显式打开 `APP_TASK_SCHEDULER_ENABLED`。

## 验收标准

```text
MARK 1/5 V1 TaskInitializer facts documented in task spec: done
MARK 1/5 V1 TaskInitializer facts documented in task spec: done
MARK 2/5 task-service startup runner creates auto/full tasks for each data source: done
MARK 3/5 default field values match V1 TaskInitializer: done
MARK 4/5 seed is idempotent and does not overwrite existing named tasks: done
MARK 5/5 cron scheduler gate defaults closed and task-service tests/docs indexes updated: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
```

本切片只做本地单元测试；不启动真实 scraper，不执行 live K8S smoke。

## 当前状态

2026-06-09：已创建 task spec，并固定 V1 默认值：

```text
auto task name       = 自动采集 - {dataSource.name}
auto cron            = 0 0 3 * * ?
auto filterHours     = 48
full task name       = 全量采集 - {dataSource.name}
full cron            = null
full filterHours     = null
enabled              = true
status               = IDLE
taskType             = BY_PAGE
timeZone             = Asia/Shanghai
startPage            = 1
endPage              = 0
requestDelayType     = RANDOM
fixedDelayMs         = 0
randomDelayMinMs     = 3000
randomDelayMaxMs     = 8000
timeoutMs            = 15000
maxRetries           = 3
enableRandomUa       = true
```

## 实现结果

2026-06-09：已完成第一刀。

- `DefaultCollectionTaskSeeder` 会在 task-service 启动时和低频 schedule 中读取
  `data_sources(id, name)`，为每个 source 幂等补齐 `自动采集 - {name}` 与
  `全量采集 - {name}`。
- seed 只按 `collection_tasks.name` 判断存在性；同名存在时跳过，不覆盖用户已编辑字段，
  与 V1 `TaskInitializer.ensureTask()` 一致。
- 新增任务通过 `JdbcCollectionTaskRepository.create()` 插入，继承 task-service 现有
  `IDLE` 初始状态、统计字段初始值和 schema 约束。
- `CollectionTaskCronScheduler` 增加 `APP_TASK_SCHEDULER_ENABLED:false` gate；默认不注册 cron，
  避免 dev 环境在任务 seed 后自动对外抓取。
- dev manifest 显式设置 `APP_TASK_DEFAULT_SEED_ENABLED=true` 和
  `APP_TASK_SCHEDULER_ENABLED=false`，不新增 Pod、Service 或 HTTPRoute。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
deployment.apps/ircs-task-service configured (server dry run)
service/ircs-task-service unchanged (server dry run)
```

## 复现标记

```text
task-service default collection task seed: 1/1
```
