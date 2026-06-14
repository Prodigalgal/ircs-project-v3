# 028-16-C2-S4 SystemConfigKey catalog parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/common/enums/SystemConfigKey.java`
- V3 `services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config/SystemConfigDefaults.java`

## 背景

V1 `SystemConfigInitializer` 会按 `SystemConfigKey.values()` 补齐完整默认配置。V3 已经实现
`ENV/K8S Secret/ConfigMap/configtree/启动参数 -> DB -> 默认 seed` 的运行时优先级护栏，
但本任务开始前 `SystemConfigDefaults` 只覆盖 V1 key 的高风险子集。空库时缺失的默认 key 会导致
admin/config、后续消费者迁移、cache/event 和 provider/runner 配置对齐缺少统一 catalog。

## 目标

- 将 V1 `SystemConfigKey` 的 133 个 key 全量纳入 V3 `SystemConfigDefaults`。
- 保持 V1 默认 seed 为最低优先级兜底，不覆盖 ENV/K8S/启动参数/DB 管理值。
- 保留 `security.admin.password` 与 `security.jwt.secret` 的 V3 随机生成语义。
- 保留敏感 key redaction 与 `effectiveSource` 语义。
- 用测试锁定 V1/V3 default catalog 数量、关键默认值、required/sensitive key 和缺失 diff。

## 非目标

- 不把所有服务一次性改成动态 config client。
- 不实现 LLM cleaning、provider live smoke、cache TTL/evict 矩阵。
- 不把 ENV/K8S 注入值写回 `system_configs`。
- 不改变现有 config change event 拓扑。

## 影响范围

```text
services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config/SystemConfigDefaults.java
services/ircs-config-service/src/test/java/com/prodigalgal/ircs/config/SystemConfigDefaultsTest.java
tasks/in-progress/028-16-c2-system-config-cache-event-parity.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
tasks/done/028-16-c2-s4-system-config-key-catalog-parity.md
```

## 验收标准

```text
MARK 1/6 V1 SystemConfigKey current catalog diff documented: done 2026-06-09
MARK 2/6 SystemConfigDefaults contains all 133 V1 keys: done 2026-06-09
MARK 3/6 V1 required/sensitive keys remain protected and generated/redacted correctly: done 2026-06-09
MARK 4/6 existing env/K8S injection -> DB -> default seed priority tests still pass: done 2026-06-09
MARK 5/6 config-service tests pass: done 2026-06-09
MARK 6/6 docs/tasks indexes updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
```

## 完成状态

2026-06-09：本任务开始时整体审视确认 V1 `SystemConfigKey` 共有 133 个 key，V3
`SystemConfigDefaults` 原覆盖 56 个 key，缺口集中在 batch min/max、metadata timeout/cron、
rate-limit rules、mail rate limit、adaptive scheduling、maintenance/search/storage batch、
LLM cleaning cron/min/max、traffic wait/proxy/provider delay、trend/search cron 等配置。

已完成实现与验证：

- `SystemConfigDefaults` 已纳入 V1 `SystemConfigKey` 全量 133 个 key；直接 diff 结果为
  `V1=133 V3=133 Missing=0 Extra=0`。
- `security.admin.password` 与 `security.jwt.secret` 仍保留 V3 随机生成语义；
  `app.storage.r2.bucket-name` 与 `app.storage.r2.public-domain` 被显式标记为 required。
- LLM prompt 默认值补回 V1 raw-preservation 约束，要求 raw 精确保留、只返回 JSON，
  并保留 `{standardItems}` / `{rawItems}` 占位符。
- 既有 `RuntimeInjectedConfig -> DB -> default seed` 优先级与 `upsertDefault()` 不覆盖
  `config_value` 的合约未改变；V1 默认初始化继续只作为最低优先级空库 seed。
- 新增 `SystemConfigDefaultsTest.includesFullV1SystemConfigKeyCatalog()`，
  用 133 个 V1 key 集合锁定 full catalog；同时覆盖 required/sensitive 边界、关键默认值、
  prompt raw-preservation 和敏感默认值非空生成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留边界：

- 本任务不包含 metadata/storage/notification/normalization/aggregation/ops 等更多消费者的
  env-first 动态读取迁移。
- 本任务不包含 config change event live Rabbit smoke、cache TTL/evict 全矩阵或 LLM
  cleaning/provider live smoke。
