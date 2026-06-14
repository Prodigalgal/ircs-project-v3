# 028-20-15 distributed lock manager multi-service rollout

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 目标

落实“所有有可能冲突的、需要锁的服务，应上尽上分布式锁”的拍板结果，将 Redis 分布式锁抽象从 ops 第一刀扩展到 V3 当前主要冲突入口。

## 范围

- shared common 统一分布式锁抽象：
  - mutex lease；
  - time-slice reservation；
  - token bucket。
- 服务接入：
  - scraper-service datasource fetch time-slice；
  - magnet-service provider fetch time-slice；
  - task-service seed/startup recovery/watchdog mutex；
  - search-service outbox relay/reconciliation mutex；
  - aggregation-worker scheduler mutex；
  - normalization-worker LLM cleaning/retry watchdog mutex；
  - config-service default initializer mutex；
  - catalog-service default seed mutex；
  - content-service resolver preset seed mutex；
  - credential-service runtime initializer mutex；
  - storage-service R2 watchdog mutex；
  - notification-worker mail send history cleanup mutex。

## 非目标

- 不把 RabbitMQ consumer 全局串行化。
- 不用 Redis lock 替代 DB 幂等、唯一约束、row lease 或业务状态机。
- 不在本轮执行 K8S 多副本 live lock smoke。

## 验收

- 冲突入口默认启用 cluster lock 或 traffic reservation。
- 每个接入服务保留 ENV/K8S override。
- focused tests 与 affected bootJar 通过。
- docs/report 和 task index 入账。

## 验证

```text
scraper-service focused tests + bootJar: BUILD SUCCESSFUL
task-service focused tests + bootJar: BUILD SUCCESSFUL
search-service focused tests + bootJar: BUILD SUCCESSFUL
magnet-service focused tests + bootJar: BUILD SUCCESSFUL
aggregation-worker focused tests + bootJar: BUILD SUCCESSFUL
normalization-worker focused tests + bootJar: BUILD SUCCESSFUL
config-service focused tests + bootJar: BUILD SUCCESSFUL
catalog-service focused tests + bootJar: BUILD SUCCESSFUL
credential-service focused tests + bootJar: BUILD SUCCESSFUL
storage-service focused tests + bootJar: BUILD SUCCESSFUL
notification-worker focused tests + bootJar: BUILD SUCCESSFUL
```

## 风险和后续

- K8S Redis live / 多副本 contention smoke 后置到统一测试窗口。
- 长运行 runner 需要继续观察 TTL，必要时接入 renew。
- Rabbit consumer 的幂等性和 dead-letter 行为仍需在后续统一测试中单独审计。
