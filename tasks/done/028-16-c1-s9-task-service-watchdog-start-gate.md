# 028-16-C1-S9 task-service watchdog start gate task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `SystemStartupReadinessAspect`
- V1 `DataInitializationOrchestrator`
- V3 `services/ircs-task-service`

## 背景

V1 会通过 `SystemStartupReadinessAspect` 拦截所有 `@Scheduled` 方法；初始化链完成并
`markAsReady()` 之前，scheduler 不会抢跑。V3 `ircs-task-service` 已通过
`APP_TASK_SCHEDULER_ENABLED:false` 默认关闭 collection cron scheduler，但 `TaskWatchdog`
仍是裸 `@Scheduled`，服务启动后会按默认 initial delay 自动扫描和修改 stale active tasks。

本切片只补齐 `TaskWatchdog` 的默认关闭 guard，给 C1 的 worker/scheduler auto-start guard
再增加一个可验证证据点。

## 目标

- 给 `TaskWatchdog` 增加独立启动 gate。
- 默认关闭 watchdog，保持 dev 低资源和初始化完成前不抢跑原则。
- 允许通过 `APP_TASK_WATCHDOG_ENABLED=true` 显式开启。
- 增加测试证明默认属性缺失时不会创建 watchdog bean，开启属性后才创建。
- 回写 C1/089，记录本切片仍不等价于 full readiness orchestrator。

## 非目标

- 不实现跨服务 `SystemReadinessOrchestrator`。
- 不改变 `TaskStartupRecovery` 的启动恢复语义。
- 不改变 collection cron scheduler 已有 `APP_TASK_SCHEDULER_ENABLED:false` gate。
- 不触发真实 scraper、Rabbit 或外部网络。
- 不新增 Pod、Service、HTTPRoute 或改变 task-service 资源配额。

## 输入

```text
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskWatchdog.java
services/ircs-task-service/src/main/resources/application.yaml
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/TaskWatchdogTest.java
deploy/k8s/dev/task-service-dev.yaml
```

## 输出

- `TaskWatchdog` 受 `app.task.watchdog.enabled` 条件控制。
- `application.yaml` 暴露 `APP_TASK_WATCHDOG_ENABLED:false`。
- dev manifest 显式设置 `APP_TASK_WATCHDOG_ENABLED=false`。
- task-service 测试覆盖 disabled default 和 enabled opt-in。

## 影响范围

```text
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskWatchdog.java
services/ircs-task-service/src/main/resources/application.yaml
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/TaskWatchdogTest.java
deploy/k8s/dev/task-service-dev.yaml
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
docs/reports/089-v1-golden-behavior-inventory.md
```

## 依赖

- `028-16-C1-S4` 已完成默认 collection task seed 和 cron scheduler 默认关闭。
- `TaskWatchdog` 已有本地单元测试覆盖 fail stale active tasks 的审计行为。

## 风险

- 默认关闭 watchdog 后，dev 环境不会自动把长时间 ACTIVE 的 collection task 转为 FAILED；
  需要验证该恢复行为时必须显式设置 `APP_TASK_WATCHDOG_ENABLED=true`。
- 本切片只能证明 task-service 一个 scheduler 默认关闭，不代表 V3 已具备 V1 全局 readiness
  orchestrator。

## 验收标准

```text
C1-S9 MARK 1/5 task spec fixes one verifiable watchdog slice
C1-S9 MARK 2/5 TaskWatchdog has default-off ConditionalOnProperty gate
C1-S9 MARK 3/5 application.yaml and dev manifest expose APP_TASK_WATCHDOG_ENABLED=false
C1-S9 MARK 4/5 task-service tests prove disabled default and enabled opt-in
C1-S9 MARK 5/5 C1/089 record implementation result, tests and remaining boundary
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
```

本切片不做 live K8S rollout，不启动真实 scheduler，不执行外部采集。

## 当前状态

2026-06-09：任务创建并完成。

```text
C1-S9 MARK 1/5 task spec fixes one verifiable watchdog slice: done
C1-S9 MARK 2/5 TaskWatchdog has default-off ConditionalOnProperty gate: done
C1-S9 MARK 3/5 application.yaml and dev manifest expose APP_TASK_WATCHDOG_ENABLED=false: done
C1-S9 MARK 4/5 task-service tests prove disabled default and enabled opt-in: done
C1-S9 MARK 5/5 C1/089 record implementation result, tests and remaining boundary: done
```

## 实现结果

2026-06-09：已完成第一刀。

- `TaskWatchdog` 增加 `@ConditionalOnProperty(value = "app.task.watchdog.enabled", havingValue = "true")`。
- `application.yaml` 增加 `app.task.watchdog.enabled`，并映射到
  `APP_TASK_WATCHDOG_ENABLED:false`。
- dev manifest 显式设置 `APP_TASK_WATCHDOG_ENABLED=false`，保持 task-service 默认低资源、
  不在初始化/seed 未确认前自动扫描 stale active tasks。
- `TaskWatchdogTest` 保留原有 watchdog 行为测试，并新增 context guard：
  默认缺失属性时不创建 `TaskWatchdog` bean，显式启用时才创建。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
deployment.apps/ircs-task-service configured (server dry run)
service/ircs-task-service unchanged (server dry run)
```

## 剩余边界

- 本切片不实现 V1 的全局 `SystemReadinessOrchestrator` 或自动放行链条。
- normalization retry watchdog、aggregation scheduler、storage R2 watchdog 等剩余 scheduler
  guard 仍需后续独立切片继续补证据。
- 需要验证 task-service watchdog 行为时，必须显式设置 `APP_TASK_WATCHDOG_ENABLED=true`。
