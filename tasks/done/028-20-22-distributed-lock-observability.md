# 028-20-22 distributed lock observability

## 目标

补齐分布式锁治理层的生产可观测性，让 mutex、时间片预占用、令牌桶在各服务中具备统一 metrics 和 health 信号。

## 范围

- shared `DistributedLockManager` 本身记录 Micrometer 指标。
- Actuator health 暴露 Redis lock backend 状态。
- 不改变现有锁语义、TTL、key 格式和业务调用方。
- 不在 metrics tag 中暴露 lock key、owner、实体 ID、URL 等高基数字段。

## 已完成

- 新增 `DistributedLockMetrics`：
  - `ircs.distributed.lock.operations`
  - `ircs.distributed.lock.operation.duration`
  - `ircs.distributed.lock.wait`
- `RedisDistributedLockManager` 覆盖：
  - mutex acquire / renew / release
  - time-slice reserve
  - token-bucket reserve
  - error result
- 新增 `DistributedLockHealthIndicator`：
  - Redis PING 成功时 UP。
  - Redis template/backend 不可用时 DOWN，并输出 reason。
- `UnavailableClusterLeaseService` 暴露 backend status，避免 Redis bean 缺失时 health 沉默。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*DistributedLock*Test" --tests "*RedisDistributedLockManager*Test" --tests "*RedisClusterLeaseServiceTest"
.\gradlew.bat --no-daemon :services:ircs-scraper-service:bootJar :services:ircs-magnet-service:bootJar :services:ircs-ops-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-search-service:bootJar :services:ircs-normalization-worker:bootJar
```

结果：`BUILD SUCCESSFUL`。

## 遗留

- 尚未在 K8S `ircs-dev` 中执行 `/actuator/health` 与 Prometheus live 查询。
- 多副本 contention smoke 仍需验证 Redis key、skipped/busy 指标、worker audit 的同一窗口证据。
