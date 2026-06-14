# 028-20-09D Maintenance gate versioned event + Redis hot cache

状态：完成

日期：2026-06-11

## 背景

`028-20-09B/09C` 已建立 `maintenance_operations` 持久 gate 与 content-service raw/unified video 写入口拦截。
后续需要回答 gate 开关如何通知 owner service，并把 gate 等热门治理数据放入 L1/L2/L3 缓存层：
Redis/Valkey 只做热路径缓存，DB 仍是最后兜底和唯一真相。

## 范围

- shared contract：新增版本化 `MaintenanceGateChangedEvent`。
- ops-service：gate create/close 后发布 best-effort domain event。
- content-service：新增 gate changed listener、Rabbit topology、readiness gate。
- content-service：gate 判定改为 L1 service local blocked cache + L2 Redis blocked cache + L3 DB fallback。
- dev K8S：content-service 接入 Valkey env，并显式启用 maintenance gate listener。

## 非目标

- 不把 Redis 作为 gate 事实源。
- 不缓存 allow decision，避免新建 gate 后被旧 allow 缓存误放行。
- 不在本切片中接入 search/storage/credential 等其他 owner service。
- 不处理 worker consumer 的 `PAUSE_CONSUMERS`，后续按 owner service 单独切。

## 实现

- `MaintenanceGateChangedEvent.ROUTING_KEY = maintenance.gate.changed`，事件包含 `eventId`、`operationId`、`operationKey`、`action`、`ownerService`、`resourceType`、`resourceScope`、`mode`、`status`、`revision`、`changedAt`、`expiresAt`、`correlationId`。
- `MaintenanceGateResponse` 增加 `version`，由 `maintenance_operations.version` 映射为事件 `revision`。
- `MaintenanceGateChangePublisher` 发布到 `x.domain.events`，Rabbit/JSON 失败只 warn，不影响 DB 写入。
- `MaintenanceGateChangedListener` 消费 `q.content.maintenance_gate_changed`，默认由 `APP_CONTENT_MAINTENANCE_GATE_LISTENER_ENABLED=false` gate 控制。
- `ContentMaintenanceGate` 判定顺序：
  1. L1 service local blocked cache。
  2. L2 Redis blocked cache。
  3. L3 DB `maintenance_operations` 查询。
  4. DB 命中 blocked 后回填 L2/L1，短 TTL。
- event 失效会清 content L1 cache，并删除 Redis scoped key；wildcard scope 通过 prefix scan 清理。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-contracts:compileJava
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --tests "*MaintenanceGate*" --tests "*MaintenanceControllerTest"
.\gradlew.bat --no-daemon :services:ircs-content-service:test --tests "*ContentMaintenanceGateTest" --tests "*MaintenanceGateChangedListenerTest" --tests "*ContentMaintenanceGateEventTopologyTest" --tests "*ContentAutoStartGateHealthConfigurationTest" --tests "*RawVideoAdminServiceTest" --tests "*UnifiedVideoAdminServiceTest"
.\gradlew.bat --no-daemon :services:ircs-ops-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-content-service:bootJar
```

结果：全部通过。

## 遗留

- search/storage/credential owner service 写入口仍需接入同一 scoped gate 与 Redis hot cache 口径。
- worker consumer 的 `PAUSE_CONSUMERS` 仍需独立实现。
- 如果未来 wildcard gate key 数量大幅增长，可把 Redis prefix scan 升级为 revision key 或 set-index 失效。
