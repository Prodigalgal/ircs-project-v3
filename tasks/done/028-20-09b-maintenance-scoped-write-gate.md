# 028-20-09B Maintenance scoped write gate

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：D28 maintenance scheduler 后续决策；运维任务执行期间不做全局停写，按 owner service、resource type、resource scope 做局部写入/consumer gate。

## 目标

- 建立 `maintenance_operations` 持久模型，表达运维任务期间的局部 gate。
- ops-service 提供 gate 创建、查询、关闭和 check API。
- shared common 提供 owner service 后续可复用的 gate 判定器与统一 423 错误语义。

## 范围

- `shared/ircs-contracts` maintenance gate mode/status contract。
- `shared/ircs-common` maintenance gate inspector、decision、locked exception。
- `services/ircs-ops-service` gate repository/service/controller。
- `platform/ircs-migrator` Liquibase SQL。
- focused tests。

## 非目标

- 本切片不把 content/search/storage/credential 写入口全部接入 gate。
- 不引入全局维护模式。
- 不为每个运维任务强制建 gate；只为破坏性/覆盖性任务提供基础能力。

## 验收标准

- active、未过期、匹配 owner/resource/scope 的 gate 能阻断写入判定。
- expired/closed gate 不阻断。
- `QUIESCE_WRITES` / `READ_ONLY` 阻断 WRITE；`PAUSE_CONSUMERS` 阻断 CONSUMER。
- scope 支持精确值与 `*` wildcard。
- gate 必须有 TTL/过期时间，避免异常永久锁。
- ops API 使用统一错误模型，check 命中时返回可追踪 decision。

## 测试方式

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*MaintenanceWriteGateInspectorTest"
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --tests "*MaintenanceGate*"
```

## 完成内容

- `shared:ircs-contracts` 增加 `MaintenanceGateMode` / `MaintenanceGateStatus`。
- `shared:ircs-common` 增加 `MaintenanceWriteGateInspector`、`MaintenanceGateDecision`、`MaintenanceGateLockedException`，统一 423 `maintenance.gate.locked` 错误语义。
- `ops-service` 增加 `/api/v1/ops/maintenance/gates` 创建/查询、`/gates/{id}/close` 关闭、`/gates/check` 判定 API。
- 新增 `maintenance_operations` Liquibase changelog，包含 TTL、status、owner/resource/scope 和 correlation 字段。
- gate 默认 TTL 15m，最大 TTL 6h，可通过 `APP_OPS_MAINTENANCE_GATE_DEFAULT_TTL` / `APP_OPS_MAINTENANCE_GATE_MAX_TTL` 覆盖。

## 验证结果

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*MaintenanceWriteGateInspectorTest"
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --tests "*MaintenanceControllerTest" --tests "*OpsConfigValuesTest" --tests "*MaintenanceGate*"
.\gradlew.bat --no-daemon :services:ircs-ops-service:bootJar
.\gradlew.bat --no-daemon :platform:ircs-migrator:bootJar
```

结果：全部通过。

备注：首次并行跑 shared-common 与 ops-service focused tests 时，Windows/Gradle 增量编译出现 shared-common class file 临时竞争；随后单独重跑 shared-common focused test 通过。

## 遗留边界

- content/search/storage/credential 等 owner service 写入口和 worker consumer 还未逐项接入 `MaintenanceWriteGateInspector`。
- 当前 ops-service 先提供 registry/check 能力，不自动为每个 runner 创建 gate；破坏性 runner 后续按资源范围显式创建。
