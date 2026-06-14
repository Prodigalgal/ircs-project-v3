# 028-16-C2-S13 ops maintenance reindex config parity

状态：已完成

日期：2026-06-09

## 背景

`028-16-C2` 已把 V1 `SystemConfigService` 的默认 seed、运行时覆盖优先级、config change
event 和多服务消费者逐步迁入 V3。C2-S5 到 C2-S12 已覆盖 search、interaction、portal、
storage、normalization、aggregation、notification 和 metadata 的一批配置消费者。

本轮继续审计 ops-service：

- V1 `SystemConfigKey` 包含 `app.maintenance.search.reindex-batch-size`、多组
  `app.maintenance.*.batch-size` 和 min/max key。
- V1 实际 `MaintenanceServiceImpl.initRawReindexSession()` 与
  `initUnifiedReindexSession()` 并未直接读取 `app.maintenance.search.reindex-batch-size`，
  而是通过 `AdaptiveWorkloadManager.WorkloadType.ADMIN_BATCH_OP` 取得静态
  `200 / 20 / 1000` 批量范围。
- V3 ops-service 当前只开放 dev-safe `search-reindex-unified` runner，并用
  `app.ops.maintenance.reindex.dev-limit` / `OPS_MAINTENANCE_REINDEX_DEV_LIMIT` 静态
  `@Value` 限制单次入队数量，默认值为 `5`，这是 V3 为开发环境新增的安全限制，不是 V1
  full maintenance reindex 的替代实现。
- 为符合本目标的配置优先级原则，V3-only dev limit 也不能只停留在启动期 `@Value`：
  运行时注入应高于 DB，DB 高于代码 fallback；DB 更新后应可通过
  `SystemConfigChangedEvent` evict 本地 cache。

## 目标

把 ops-service 的 `search-reindex-unified` dev limit 从启动期 `@Value` 迁到
`Runtime injected -> system_configs DB -> V3 dev-safe fallback` 读取链路，并接入
config change event evict。

## 非目标

- 不把 V3-only `app.ops.maintenance.reindex.dev-limit` 加入 config-service 的 V1
  `SystemConfigDefaults` catalog，避免破坏 `V1=133 Missing=0 Extra=0` 证明。
- 不声称完成 V1 full maintenance raw/unified reindex 行为；V1 的 raw/unified 全量重建、
  bulk mode、traffic governor、SSE 进度与 completion mail 仍需后续切片。
- 不迁 `app.maintenance.search.reindex-batch-size` 到当前 dev runner，因为 V1 当前可见代码
  对 raw/unified reindex 实际使用 `ADMIN_BATCH_OP` 静态范围，不直接消费该 key。
- 不执行真实 Rabbit live smoke。

## 上游来源

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- V1:
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/common/enums/SystemConfigKey.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/system/service/impl/MaintenanceServiceImpl.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/infrastructure/adaptive/AdaptiveWorkloadManager.java`

## 输入

- V3 runtime/DB key:
  - `app.ops.maintenance.reindex.dev-limit`
- Runtime aliases:
  - `OPS_MAINTENANCE_REINDEX_DEV_LIMIT`
  - `APP_OPS_MAINTENANCE_REINDEX_DEV_LIMIT`
- Fallback:
  - `5`，保留 V3 dev-safe 默认。
- Clamp:
  - `0..100`，保留既有 dev runner 上限。

## 输出

- ops-service service-local config repository/cache。
- ops-service runtime injection source detector。
- ops-service maintenance config value resolver。
- `q.ops.config_changed` event listener/topology。
- `MaintenanceReindexCommand` 每次运行时读取 dev limit，并保留 0 禁用、100 上限。
- dev K8S manifest 显式开启 ops config listener，并显式设置低资源 dev limit。
- 单元测试、K8S server dry-run 和 docs/tasks 回写。

## 影响范围

- `services/ircs-ops-service/**`
- `deploy/k8s/dev/ops-service-dev.yaml`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`
- `docs/index.md`

## 依赖

- `shared:ircs-contracts` 中的 `SystemConfigChangedEvent`。
- Rabbit topology 常量 `shared:ircs-messaging`。
- ops-service 已有 JDBC/Rabbit runtime。

## 风险

- V3 dev limit 是开发保护，不等于 V1 full reindex batch size；文档必须明确边界。
- 如果 runtime env 设置过大，必须继续 clamp 到 `100`，避免 dev K8S 资源被一次性拉满。
- 如果 DB 未写入 V3-only key，应回退到代码 fallback `5`，不能污染 V1 default catalog。

## 验收标准

- MARK 1/7 V1 maintenance reindex actual config facts documented。
- MARK 2/7 ops-service has dedicated config cache/event listener。
- MARK 3/7 reindex dev limit reads runtime/DB/fallback per execution。
- MARK 4/7 runtime env overrides DB and fallback, invalid values fallback safely。
- MARK 5/7 dev K8S keeps low resource override and config listener enabled。
- MARK 6/7 ops-service tests and K8S dry-run pass without real Rabbit。
- MARK 7/7 docs/tasks indexes and 089 inventory updated。

## 测试边界

- 本轮运行：
  - `.\gradlew.bat --no-daemon :services:ircs-ops-service:test --rerun-tasks`
  - `kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml`
- 不运行真实 Rabbit live smoke。
- 不运行真实 full reindex / bulk mode live smoke。

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 关联任务

- 父任务：`tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- 上游父任务：`tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`

## 当前状态

已完成。

## 实现结果

- ops-service 新增 service-local `RuntimeInjectedConfig`、`SystemConfigRepository` 和
  `OpsConfigValues`，`app.ops.maintenance.reindex.dev-limit` 读取顺序固定为运行时注入 ->
  `system_configs` DB -> V3 dev-safe fallback。
- `MaintenanceReindexCommand` 改为每次执行 `enqueueUnifiedIndex()` 时读取当前 dev limit，
  保留 `0` 禁用、`100` 上限和默认 `5`。
- ops-service 新增 `q.ops.config_changed`、DLQ 绑定和 `APP_OPS_CONFIG_LISTENER_ENABLED`
  独立开关，用于按 key evict 本地 `system_configs` cache。
- dev K8S manifest 显式开启 ops config listener，并设置
  `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`，继续保持低资源 dev runner。
- 顺手补齐 ops-service `QueueTopicDescriptor` 对 `PROMOTE_COVER_R2` 的展示映射，避免共享
  `QueueTopic` 新枚举导致 ops-service dashboard 编译失败。

## 验证结果

```text
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
deployment.apps/ircs-ops-service configured (server dry run)
service/ircs-ops-service unchanged (server dry run)
```

## MARK

- MARK 1/7 V1 maintenance reindex actual config facts documented：done 2026-06-09。
- MARK 2/7 ops-service has dedicated config cache/event listener：done 2026-06-09。
- MARK 3/7 reindex dev limit reads runtime/DB/fallback per execution：done 2026-06-09。
- MARK 4/7 runtime env overrides DB and fallback, invalid values fallback safely：done 2026-06-09。
- MARK 5/7 dev K8S keeps low resource override and config listener enabled：done 2026-06-09。
- MARK 6/7 ops-service tests and K8S dry-run pass without real Rabbit：done 2026-06-09。
- MARK 7/7 docs/tasks indexes and 089 inventory updated：done 2026-06-09。

## 遗留问题

- 本轮没有执行真实 Rabbit live smoke。
- 本轮没有把 V1 full raw/unified reindex、bulk mode、traffic governor、SSE 进度和 completion
  mail 迁入 V3；当前仍只是 dev-safe `search-reindex-unified` runner 的配置读取 parity。
- `app.maintenance.search.reindex-batch-size` 仍未接入当前 dev runner；V1 当前可见 reindex
  实际使用 `ADMIN_BATCH_OP` 静态批量，后续如果实现 full maintenance runner，需要单独设计
  V1 key 与 V3 runner 参数的对应关系。
