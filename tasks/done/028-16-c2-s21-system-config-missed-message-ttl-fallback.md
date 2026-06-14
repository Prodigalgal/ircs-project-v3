# 028-16-C2-S21 SystemConfig Rabbit outage / missed-message TTL fallback task spec

状态：已完成

父任务：

- `tasks/done/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `tasks/done/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `docs/reports/095-02816c2s20-content-db-fallback-recoverable-probe.md`

## 背景

C2-S17/S18/S19/S20 已证明 config-service publisher、content-service listener、
Rabbit publish/deliver/ack 和 content DB fallback read-through 在正常链路下可用。当前唯一
剩余风险是 Rabbit outage 或 missed-message：消费者本地 `SystemConfigRepository` 只依赖
Rabbit event evict，若事件丢失，旧 DB fallback cache 可能无限期滞留。

## 目标

- 明确 SystemConfig missed-message 策略：Rabbit event evict 是快速路径，短 TTL read-through
  是最终一致兜底，不引入定时全量 refresh。
- 在 content-service 代表消费者补最小 TTL fallback，验证 missed-message 后能重新读取 DB。
- 保持读取优先级不变：RuntimeInjectedConfig / ENV / K8S override -> DB fallback cache -> service default。
- 回写 C2 父任务，若 S21 完成则关闭 C2 父任务；否则记录剩余阻塞。

## 非目标

- 不停 RabbitMQ，不做全局 Rabbit outage 演练。
- 不修改 metadata provider、notification、storage、portal/admin/BFF。
- 不一次性改造所有 SystemConfig consumer。
- 不新增配置中心、scheduler 或全量 refresh job。

## 影响文件

- `services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/SystemConfigRepository.java`
- `services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/SystemConfigRepositoryTest.java`
- `services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/ContentConfigValuesTest.java`
- `docs/reports/*02816c2s21*`
- `tasks/done/028-16-c2-system-config-cache-event-parity.md`
- `tasks/done/028-16-c2-s21-system-config-missed-message-ttl-fallback.md`

## 验收标准

- content-service DB fallback cache 在未收到 Rabbit event 时，TTL 到期后会重新查询 DB。
- TTL 到期前仍保持 cache，避免每次读取都打 DB。
- `APP_CONTENT_SCRAPER_BASE_URL` 等 runtime override 继续高于 DB cache 和 default。
- content-service focused tests 通过。
- 文档记录设计决策、测试命令、是否执行 live smoke、清理证据和 C2 父任务状态。

## 测试方式

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
```

## 实施结果

2026-06-09：任务完成。决策为 `Rabbit event evict + short TTL read-through`，不做定时
全量 refresh，也不采用仅事件驱动。理由：

- 仅事件驱动无法覆盖 Rabbit outage / missed-message，进程内 DB fallback cache 可能永久旧值。
- 定时全量 refresh 会扩大消费者实现面，并容易绕开每个服务已有的 runtime override 优先级。
- 短 TTL read-through 只在按需读取时触发；正常 Rabbit 投递仍即时 evict，异常漏消息时最多
  滞后一段可配置 TTL。

代码结果：

- `SystemConfigRepository` 增加 TTL cache entry，默认 `30s`。
- TTL 可由 `app.content.system-config.cache-ttl` /
  `APP_CONTENT_SYSTEM_CONFIG_CACHE_TTL` 覆盖。
- `SystemConfigChangedListener` 的按 key evict 保持不变。
- `ContentConfigValues` 仍按 RuntimeInjectedConfig / ENV / K8S override -> DB fallback cache
  -> service default 读取；runtime override 命中时不触碰 DB cache。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

新增/覆盖测试：

- `SystemConfigRepositoryTest.refreshesAfterTtlWhenConfigChangeEventWasMissed`
- `SystemConfigRepositoryTest.changedEventEvictsCachedValueSoNextReadUsesFreshDbValue`
- `ContentConfigValuesTest.runtimeOverrideSkipsDbFallbackCache`

Live smoke：

- 本轮未执行新的 live Rabbit outage smoke，也未停 RabbitMQ。
- C2-S17/S19/S20 已有真实 Rabbit publish/deliver/ack 与 content DB fallback read probe；
  S21 只需证明 missed-message 后最终一致性，focused test 更可控且不破坏共享 dev Rabbit。

清理证据：

- 未 patch K8S deployment。
- 未写 live DB。
- 未发布 Rabbit 消息。
- 未创建 port-forward。

文档：

- `docs/reports/096-02816c2s21-system-config-missed-message-ttl-fallback.md`

C2 父任务：

- MARK 完成，可关闭。
