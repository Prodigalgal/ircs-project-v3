# 096-02816C2S21 SystemConfig missed-message TTL fallback

日期：2026-06-09

## 结论

V3 SystemConfig cache/event 的最终策略确定为：

```text
RuntimeInjectedConfig / ENV / K8S override
  -> DB fallback cache with short TTL
  -> service default
```

Rabbit `SystemConfigChangedEvent` 仍是正常链路的快速失效路径；Rabbit outage 或
missed-message 下不依赖定时全量 refresh，而由短 TTL read-through 兜底，保证旧 DB fallback
cache 不会无限期滞留。runtime override 始终高于 DB cache 和 default。

## 设计取舍

- 不选仅事件驱动：missed-message 会让进程内 cache 永久停留旧值。
- 不选定时全量 refresh：当前 SystemConfig key 由消费者按需读取，定时全量拉取会扩大 DB 和
  服务实现面，也更难保持每个服务的 runtime override 优先级。
- 选择 event + TTL read-through：正常 Rabbit 链路即时 evict；异常或漏消息时最多滞后一段
  可配置 TTL；读取仍是 on-demand，不引入后台 scheduler。

## 本轮实现

- content-service 代表消费者的 `SystemConfigRepository` 增加 TTL cache entry。
- 默认 TTL 为 `30s`，可用 `app.content.system-config.cache-ttl` 或
  `APP_CONTENT_SYSTEM_CONFIG_CACHE_TTL` 覆盖。
- `SystemConfigChangedListener` 的按 key evict 行为不变。
- `ContentConfigValues` 的 runtime override 优先级不变，命中 ENV/K8S 时不会读取 DB cache。

## 验证

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

新增/覆盖的关键断言：

- TTL 到期前继续使用旧 DB fallback cache，避免每次读取打 DB。
- 未收到 Rabbit event 时，TTL 到期后重新查询 DB 并取得新值。
- 收到 `SystemConfigChangedEvent` 时仍立即 evict，下次读取使用新 DB 值。
- `APP_CONTENT_INTERNAL_SCRAPER_BASE_URL` / `APP_CONTENT_SCRAPER_BASE_URL` runtime override
  高于 DB fallback cache，命中时不调用 repository。

## Live smoke

本轮未执行新的 live Rabbit outage smoke，也未停 RabbitMQ。原因是 C2-S17/S19/S20 已完成真实
Rabbit publish/deliver/ack 与 content DB fallback read probe；S21 需要证明的是 missed-message
语义，使用 focused test 模拟“无 event 但 DB 已变更”更可控，且不会破坏共享 dev Rabbit。

## 清理证据

- 未 patch K8S deployment。
- 未写 live DB。
- 未发布 Rabbit 消息。
- 未创建 port-forward。

## C2 父任务状态

C2 父任务可关闭。剩余的 metadata/storage/notification/ops full runner、provider HTTP 参数、
MAIL rate-limit 等已属于其他后续切片，不再阻塞 SystemConfig cache/event parity 的 C2 目标。
