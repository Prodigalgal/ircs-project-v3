# 028-20-14 ops maintenance scheduler cluster lease

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 范围

- 新增 shared `ClusterLeaseService` 抽象。
- 新增 Redis-backed `RedisClusterLeaseService`：
  - acquire 使用 `SET key value NX TTL`。
  - release 使用 Lua compare-and-delete，只有 token 匹配才删除。
  - renew 使用 Lua compare-and-pexpire，只有 token 匹配才续租。
- ops-service maintenance scheduler 在本地 `running` guard 后获取 cluster lease。
- 多副本下未抢到 lease 的 Pod 返回 skipped，不执行 owner runner，不写失败 audit。
- lease owner 使用 `WorkerInstanceIds.resolve(...)`，状态接口暴露 `workerId` 与 `clusterLeaseEnabled`。
- scheduler 默认仍 disabled；一旦启用 scheduler，cluster lease 默认 enabled，TTL 默认 `10m`。

## 非目标

- 不把所有 worker/consumer 机械迁移到 Redis lock。
- 不替代 search outbox 的 DB row lease。
- 不改变 maintenance runner dry-run / execute gate / audit 语义。
- 不执行 K8S live rollout 或 Redis live smoke。

## 输出

- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/lease/ClusterLease.java`
- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/lease/ClusterLeaseService.java`
- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/lease/RedisClusterLeaseService.java`
- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/lease/UnavailableClusterLeaseService.java`
- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/OpsClusterLeaseConfiguration.java`
- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/OpsConfigValues.java`
- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/MaintenanceSchedulerService.java`
- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/MaintenanceSchedulerStatusResponse.java`
- `services/ircs-ops-service/src/main/resources/application.yaml`

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests com.prodigalgal.ircs.common.lease.RedisClusterLeaseServiceTest :services:ircs-ops-service:test --tests com.prodigalgal.ircs.ops.MaintenanceSchedulerServiceTest --tests com.prodigalgal.ircs.ops.MaintenanceControllerTest
.\gradlew.bat --no-daemon :services:ircs-ops-service:bootJar
```

结果：

```text
BUILD SUCCESSFUL
BUILD SUCCESSFUL
```

## 遗留边界

- Redis live failover / timeout 行为未在本轮验证；当前策略是 lease 不可用则 scheduler skipped，并记录 failed audit。
- 需要后续 K8S rollout 后用 2 replicas 验证只有一个 Pod 执行 maintenance scheduler。
- 其他 singleton runner / cache warmer / provider smoke 是否接入 cluster lease，需要逐项按行为风险决定。
