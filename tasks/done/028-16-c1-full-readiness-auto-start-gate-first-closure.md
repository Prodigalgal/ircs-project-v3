# 028-16-C1 full readiness orchestrator / auto start gate first closure task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`

## 目标

补齐 C1 剩余 auto-start gate 的 first closure：ENV/K8S/启动参数显式 override 最高优先级，
DB/default seed 只作为最低兜底；危险 runner 默认关闭；ready/gate 状态可观察。

## 范围

- 新增 shared auto-start gate inspector/health indicator。
- 让 normalization retry watchdog 与 storage R2 watchdog 默认关闭，显式开启才创建 bean。
- 将 aggregation-worker dev scheduler gate 从常驻开启改为关闭，后续 smoke 需要时再临时开启。
- 增加 no-write/live K8S gate smoke 脚本。

## 非目标

- 不恢复 V1 单体式全局 `DataInitializationOrchestrator`。
- 不触发真实邮件、R2 写入、provider 抓取、采集任务或批量清理。
- 不改 metadata provider、notification 028-10 runner、storage 业务下载链路、portal/admin/BFF。

## 策略说明

- 运行时注入源识别为 `INJECTED`：command line、system properties、system environment、
  configtree、K8S ConfigMap/Secret 等。
- 非运行时注入或缺失值归为 `DEFAULT`，避免把 `application.yaml` 静态默认误判为高优先级。
- `AutoStartGateHealthIndicator` 暴露 `gateCount`、`enabledCount`、`blockedCount` 和逐 gate
  的 `name/propertyKey/environmentKey/enabled/source/sourceKey/blocked`。
- dev K8S 常驻状态保持危险 gate 关闭：`APP_NORMALIZATION_WATCHDOG_ENABLED=false`、
  `APP_STORAGE_R2_WATCHDOG_ENABLED=false`、`APP_AGGREGATION_SCHEDULER_ENABLED=false`。

## 验收结果

```text
MARK C1-F1 shared auto-start gate state exposes injected/default source: done
MARK C1-F2 normalization/storage dangerous watchdog beans default closed and opt-in only: done
MARK C1-F3 dev K8S manifests keep dangerous runner gates false unless a smoke enables them: done
MARK C1-F4 focused tests cover default-off, opt-in and observable source details: done
MARK C1-F5 readiness smoke reports key gates and no side-effect env: done
```

## 验证

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml -f deploy\k8s\dev\storage-service-dev.yaml -f deploy\k8s\dev\aggregation-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
deployment.apps/ircs-aggregation-worker configured (server dry run)

kubectl apply -f deploy\k8s\dev\normalization-worker-dev.yaml -f deploy\k8s\dev\storage-service-dev.yaml -f deploy\k8s\dev\aggregation-worker-dev.yaml
rollout status: normalization-worker/storage-service/aggregation-worker all successfully rolled out

.\scripts\smoke\readiness-auto-start-gate-02816c1.ps1 -UseCluster
MARK 1/3 local application defaults keep dangerous gates false
MARK 2/3 dev manifests explicitly keep dangerous gates false
MARK 3/3 live deployments keep dangerous gates false
```

## 无副作用证据

- 本轮 K8S apply 只关闭 watchdog/scheduler gate，并等待 existing Deployment Ready。
- 未开启 SMTP、metadata provider、R2、采集 cron、R2 watchdog 或 notification runner。
- smoke 只读取 manifest/deployment env 和 Ready replica，不写 DB、不发 Rabbit 消息、不访问公网。

## 剩余拆分项

C1 父任务不完全关闭。剩余项继续拆分：

- cache warmup/TTL/evict matrix。
- catalog countries/languages 全量 V1 preset 导入。
- resolver 播放链路与真实资源站分类同步 live smoke。
- 如后续确需 V1 风格自动放行，再单独实现 `RabbitListenerEndpointRegistry.start()` orchestration。
