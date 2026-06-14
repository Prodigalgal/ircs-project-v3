# 028-16-C2-S10 aggregation batch config parity

状态：已完成

日期：2026-06-09

## 背景

`028-16-C2` 正在把 V1 `SystemConfigService` 的运行时配置读取、缓存和变更失效语义迁到
V3 各微服务。前序 C2-S5/C2-S6/C2-S7/C2-S8/C2-S9 已覆盖 search、interaction、
portal、storage 和 normalization 的一批配置消费者。

本轮审计发现 aggregation-worker 仍有一处明确偏差：

- V1 `AggregationScheduler.doRunBatch()` 通过 `AdaptiveWorkloadManager.getOptimalBatchSize(AGGREGATION)`
  获取批次。
- V1 `AdaptiveWorkloadManager` 在静态批次路径读取
  `SystemConfigKey.AGGREGATION_BATCH_SIZE`，canonical key 为 `aggregation.batch-size`，默认值为 `200`。
- V1 adaptive 路径还读取 `aggregation.batch-size.min=50` 与 `aggregation.batch-size.max=500`。
- V3 `AggregationScheduler` 当前使用 `@Value("${app.aggregation.batch-size:1}")` 启动期绑定，
  key 与默认值均不等价，并且不会响应 config-service 的 system config DB 更新和
  `SystemConfigChangedEvent`。

## 目标

把 aggregation-worker scheduler 的 batch size 迁到 V1 风格的
`Runtime injected -> system_configs DB -> V1 default seed` 读取链路，并接入 config change
event evict。

## 非目标

- 不在本轮补完整 V1 `AdaptiveWorkloadManager` 的负载感知、min/max 自适应计算和 backoff。
- 不在本轮补 V1 aggregation scheduler 的 readiness orchestrator、maintenance active-session gate、
  transaction claim/update 细节；这些继续归属 C1/C/B 后续切片。
- 不迁 `app.aggregation.stuck-timeout-minutes` 到 SystemConfig；V1 stuck timeout 是 scheduler 内
  30 分钟硬编码，本轮保留 V3 启动期部署参数。
- 不迁 `@Scheduled` 的 fixed delay。
- 不做 live Rabbit smoke；本轮先用 listener/topology 单测和 K8S server dry-run 固定契约。

## 上游来源

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- V1:
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/common/enums/SystemConfigKey.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion/scheduler/AggregationScheduler.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/infrastructure/adaptive/AdaptiveWorkloadManager.java`

## 输入

- V1 SystemConfig key:
  - `aggregation.batch-size`
- V3 alias:
  - `app.aggregation.batch-size`

## 输出

- aggregation-worker service-local config repository/cache。
- aggregation-worker runtime injection source detector。
- aggregation scheduler config value resolver。
- `q.aggregation.config_changed` event listener/topology。
- 更新 aggregation-worker application.yaml 和 dev K8S manifest。
- 单元测试、K8S server dry-run 和 docs/tasks 回写。

## 影响范围

- `services/ircs-aggregation-worker/**`
- `deploy/k8s/dev/aggregation-worker-dev.yaml`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`
- `docs/index.md`

## 依赖

- `shared:ircs-contracts` 中的 `SystemConfigChangedEvent`。
- Rabbit topology 常量 `shared:ircs-contracts` / `shared:ircs-messaging`。
- aggregation-worker 已使用 JDBC，可直接读取 `system_configs`。

## 风险

- V1 默认 `aggregation.batch-size=200` 高于 dev 期资源目标；dev K8S 必须通过 runtime env
  覆盖为低批次，避免扩大资源占用。
- 只做本地 cache event evict 不能证明 live Rabbit broker 路径已通，后续仍需 C2 live smoke。
- V1 adaptive min/max 仍未迁移，不能把本轮视为完整 adaptive workload parity。

## 验收标准

- MARK 1/6 V1 aggregation batch SystemConfig facts documented。
- MARK 2/6 aggregation-worker has dedicated config cache/event listener。
- MARK 3/6 `AggregationScheduler` 使用 env/DB/default 读取 batch size，V1 默认为 200。
- MARK 4/6 dev K8S 通过 runtime env 覆盖低批次，不扩大资源占用。
- MARK 5/6 aggregation-worker tests and K8S dry-run pass without real Rabbit。
- MARK 6/6 docs/tasks indexes and 089 inventory updated。

## 测试边界

- 本轮运行：
  - `.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks`
  - `kubectl apply --dry-run=server -f deploy\k8s\dev\aggregation-worker-dev.yaml`
- 不运行真实 Rabbit live smoke，不触发真实大批量 aggregation。

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 关联任务

- 父任务：`tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- 上游父任务：`tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`

## 实现结果

- aggregation-worker 新增 service-local `RuntimeInjectedConfig`，只把 command line、system
  properties、system environment、`SPRING_APPLICATION_JSON`、K8S ConfigMap/Secret/configtree
  和显式测试 source 视为最高优先级运行时覆盖源。
- aggregation-worker 新增 service-local `SystemConfigRepository`，按 key 缓存
  `system_configs.config_value`，并提供按 key evict。
- 新增 `AggregationConfigValues`，`AggregationScheduler.runBatch()` 每轮按
  `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取
  `aggregation.batch-size`，默认值恢复为 V1 `200`，并兼容 V3 alias
  `app.aggregation.batch-size` / `APP_AGGREGATION_BATCH_SIZE`。
- 新增 `q.aggregation.config_changed` listener、DLQ topology 和独立
  `APP_AGGREGATION_CONFIG_LISTENER_ENABLED` 开关，收到 `SystemConfigChangedEvent` 后按 key
  失效本地 cache。
- dev K8S manifest 保持 `APP_AGGREGATION_BATCH_SIZE=1`，显式用运行时 env 覆盖 V1 高默认值，
  避免开发环境扩大批处理资源占用。

## 验证结果

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\aggregation-worker-dev.yaml
deployment.apps/ircs-aggregation-worker configured (server dry run)
```

## MARK 结果

- MARK 1/6 V1 aggregation batch SystemConfig facts documented：done 2026-06-09。
- MARK 2/6 aggregation-worker has dedicated config cache/event listener：done 2026-06-09。
- MARK 3/6 `AggregationScheduler` 使用 env/DB/default 读取 batch size，V1 默认为 200：done 2026-06-09。
- MARK 4/6 dev K8S 通过 runtime env 覆盖低批次，不扩大资源占用：done 2026-06-09。
- MARK 5/6 aggregation-worker tests and K8S dry-run pass without real Rabbit：done 2026-06-09。
- MARK 6/6 docs/tasks indexes and 089 inventory updated：done 2026-06-09。

## 遗留问题

- V1 `AdaptiveWorkloadManager` 的 `aggregation.batch-size.min/max` 和动态批量计算未在本轮迁移。
- V1 scheduler readiness orchestrator、maintenance gate、adaptive workload 和完整 start gate 仍归属
  `028-16-C1/C2/C` 后续切片。
- config change event 尚未做 live Rabbit smoke；本轮以 listener/topology 单测和 K8S server
  dry-run 固定契约。

## 当前状态

已完成。文件待移动到 `tasks/done/028-16-c2-s10-aggregation-batch-config-parity.md`。
