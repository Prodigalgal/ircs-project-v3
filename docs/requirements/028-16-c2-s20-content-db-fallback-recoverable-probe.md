# 028-16-C2-S20 content DB fallback recoverable probe 需求

状态：完成

日期：2026-06-09

关联任务：

- `tasks/done/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

## 背景

C2-S18 已让 content-service config event listener/topology 在 dev live 环境上线，
`q.content.config_changed` 有 consumer。C2-S19 已让 config-service change publisher 上线，
C2-S17 重跑后真实 Rabbit publish / deliver / ack 已通过。

C2-S17 当前仍为 PARTIAL，原因是 content-service live deployment 存在
`APP_CONTENT_SCRAPER_BASE_URL` runtime override。按照 V1/V3 SystemConfig 语义，运行时注入
配置必须高于 DB/default seed，因此不能为了 smoke 让 DB 值覆盖 ENV/K8S runtime override。
本轮需要设计可恢复 live probe，在不破坏优先级原则的前提下补齐 DB fallback read 证据。

## 目标

- 明确 SystemConfig 读取优先级：运行时覆盖源 -> `system_configs` DB -> service fallback/default。
- 保留 `APP_CONTENT_SCRAPER_BASE_URL` 高于 DB 的原则，不修改生产语义来迎合测试。
- 为 C2-S17 smoke 增加可恢复 probe 方案：
  - 优先记录 content-service runtime override 原值。
  - 允许在显式开关下临时移除 content-service 指定 runtime override。
  - rollout 后确认 override 暂时缺席，再用临时 DB 值触发 content read probe。
  - smoke 结束后恢复原 runtime override，并确认恢复。
- 跑通 C2-S17 的 Rabbit publish/deliver/ack 与 content read/fallback probe；如仍失败，记录更精确阻塞项。
- 回写 docs/tasks，记录命令、临时 patch/restore、DB key 清理、Rabbit 证据和残留风险。

## 非目标

- 不降低 runtime override 优先级。
- 不改 metadata-worker/Douban、notification、storage、portal/admin/BFF。
- 不引入长期公开诊断接口。
- 不触碰敏感配置、Secret 值或真实 provider/R2/SMTP 配置。
- 不把临时 probe override 固化为业务默认值。

## 范围

包含：

- `scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`
- `deploy/k8s/dev/content-service-dev.yaml` 中与可恢复 probe 说明或 env 有关的最小必要调整
- `docs/**`
- `tasks/**`

不包含：

- `services/ircs-metadata-worker/**`
- `services/ircs-notification-service/**`
- `services/ircs-storage-service/**`
- `portal/**`、`admin/**`、BFF 模块

## 方案

采用显式 live probe 开关，而不是改变业务优先级：

1. C2-S17 smoke 默认仍检测 runtime override；没有显式 probe 开关时保持 PARTIAL/BLOCKED 行为。
2. 加入可恢复开关后，脚本读取并记录 `APP_CONTENT_INTERNAL_SCRAPER_BASE_URL` /
   `APP_CONTENT_SCRAPER_BASE_URL` 原值。
3. 只在原 override 是直接 `value` 且可恢复时，临时从 live deployment 移除这些 env。
4. 等待 content-service rollout，确认 runtime override 缺席。
5. 写入临时 DB config，观察 Rabbit publish/deliver/ack。
6. 调用 content-service 低副作用 refetch probe，观察错误响应是否包含临时 DB base URL。
7. finally 中优先恢复 content-service runtime override，再恢复/清理 DB config，并回收 port-forward。

## 风险

- 临时移除 runtime override 会让 content-service 在短窗口内按 DB/fallback 解析 scraper base URL；
  需要使用显式开关、短执行窗口、finally 恢复和恢复确认。
- 如果 live deployment 中目标 env 改为 `valueFrom` 或并行 worker 改写同一 env，脚本必须拒绝 patch
  或只恢复自己记录的目标 env，不覆盖其他 env。
- 如果 content-service 无 raw video fixture，read probe 可能无法触发，需要记录为 fixture 阻塞。
- Rabbit outage / missed-message TTL fallback 不是本轮目标，仍留在 C2 父任务。

## 验收标准

- C2-S17 smoke 输出包含 runtime override 原值摘要和恢复确认。
- Rabbit temporary update 与 restore 阶段仍有 publish/deliver/ack 证据。
- content read probe 能观察临时 DB fallback 值，或输出比
  `CONTENT_READ_BLOCKED_BY_RUNTIME_OVERRIDE` 更准确的阻塞原因。
- DB 中不保留 C2-S17 临时 key/value，port-forward 无残留。
- docs/tasks 记录命令、结果、恢复证据、C2-S17 MARK 状态和残留风险。

## 完成记录

2026-06-09：已完成。C2-S17 smoke 使用显式
`-TemporarilyRemoveContentRuntimeOverride -ProbeContentRead -CreateTemporaryRawVideoFixture`
跑通 `MARK 7/7`。原 runtime override：
`APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080`；
probe 后已恢复。`system_configs` 临时值、`C2-S17-PROBE-*` raw fixture、port-forward 均无残留。
详见 `docs/reports/095-02816c2s20-content-db-fallback-recoverable-probe.md`。
