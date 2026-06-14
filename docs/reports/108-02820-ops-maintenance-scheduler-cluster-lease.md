# 108 028-20 ops maintenance scheduler cluster lease

## 结论

本轮补上 V3 当前缺失的分布式协调基座第一刀：shared `ClusterLeaseService` 抽象和 Redis 实现已落地，并接入 ops-service maintenance scheduler。

此前 scheduler 只有 Pod 内 `AtomicBoolean running`，多副本时不能阻止不同 Pod 同时执行。本轮改为：

```text
scheduled tick / run-once
  -> scheduler enabled?
  -> local running guard
  -> Redis cluster lease: ircs:cluster-lease:ops-service:maintenance-scheduler
  -> dry-run / execute gate
  -> owner runner
  -> audit + release lease
```

## 行为边界

- Redis lease 只用于跨副本 singleton scheduler 互斥。
- search persistent outbox 继续使用 DB `FOR UPDATE SKIP LOCKED` row lease。
- maintenance write gate 继续表达“某 scope 是否阻断写入”，不混同为互斥锁。
- 未抢到 cluster lease 的副本返回 skipped，不执行 owner runner，也不写 failed audit，避免正常多副本 tick 造成 audit 噪音。
- Redis 不可用时 scheduler 不执行，并写 failed audit 作为运维信号。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests com.prodigalgal.ircs.common.lease.RedisClusterLeaseServiceTest :services:ircs-ops-service:test --tests com.prodigalgal.ircs.ops.MaintenanceSchedulerServiceTest --tests com.prodigalgal.ircs.ops.MaintenanceControllerTest
.\gradlew.bat --no-daemon :services:ircs-ops-service:bootJar
```

结果均为 `BUILD SUCCESSFUL`。

## 后续

- K8S `ircs-dev` 后续需要做 2 replicas scheduler live smoke，确认只有一个 Pod 拿到 lease 并执行。
- 若其他服务出现 singleton runner、cache warmer、provider smoke 等跨副本互斥需求，应复用 shared `ClusterLeaseService`，不要各服务手写 Redis lock。
- 关键数据写入仍需 DB 幂等或 fencing token 兜底，不能只依赖 Redis lock。
