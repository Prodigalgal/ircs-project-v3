# 028-16-C2-S15 config event live matrix smoke harness task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` 的 P0-02 SystemConfig 默认值与缓存
- `docs/reports/089-v1-golden-behavior-inventory.md` 的 P1-05 Cache TTL/evict
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

## 背景

`028-16-C2-S5` 到 `028-16-C2-S14` 已经为 search、interaction、portal、storage、
normalization、aggregation、notification、metadata、ops 和 content 建立了多批
SystemConfig consumer：运行时注入优先、DB fallback、本地 config cache，以及
`SystemConfigChangedEvent` 按 key evict。当前缺口不再是新增业务 consumer，而是需要一个
小而可靠的验证切片，证明：

- config-service 发布 payload 不携带 secret。
- 已接 listener 的 consumer 能按 key evict。
- live Rabbit smoke / cache TTL-evict 全矩阵还有哪些服务未闭环。

## 目标

- 建立一个 no-write 的 `SystemConfigChangedEvent` live matrix smoke helper，默认只做 dry-run
  和拓扑/命令预览，不写 DB、不发真实 Rabbit 消息、不污染真实配置。
- 选择 content-service 作为代表 consumer，补充 focused tests，证明 listener 收到事件后按 key
  evict，下一次读取可以从 DB fallback 取到新值。
- 补强 config-service publisher 测试，显式证明敏感 payload 不包含配置值字段，也不包含示例
  secret 文本。
- 在本 task spec 写清已接 listener 的矩阵、代表切片证据和剩余服务/风险。

## 非目标

- 不新增业务 consumer，不迁移 content audit、resolver seed、media URL fallback 等更多配置。
- 不完成全服务 live Rabbit smoke。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089-v1-golden-behavior-inventory.md`
  或 `docs/migrations/089`。
- 不触碰 BFF C3、normalization genre A、R2/SMTP。

## 输入

- `SystemConfigChangedEvent` contract：`x.domain.events` / `config.system.changed`
- `services/ircs-config-service/**` 中既有 publisher 测试
- `services/ircs-content-service/**` 中既有 content config cache 与 listener
- C2-S5..S14 已记录的 consumer 队列和开关

## 输出

- no-write smoke helper：`scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1`
- config-service payload safety regression test
- content-service representative cache evict regression test
- 本 task spec 的矩阵、验证结果和剩余项

## 影响范围

包含：

- `scripts/smoke/*config*`
- `services/ircs-config-service/src/test/java/com/prodigalgal/ircs/config/**`
- `services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/**`
- `tasks/in-progress/028-16-c2-s15-live-matrix-smoke-harness.md`

不包含：

- 业务运行时代码。
- K8S manifest。
- 全局索引、089 汇总或迁移总账。
- BFF C3、normalization genre A、R2/SMTP。

## 依赖

- 本地 Gradle 测试环境。
- PowerShell parser 可用于脚本语法检查。
- 可选 dev K8S/Rabbit 环境；本轮脚本默认 dry-run，不要求 live cluster 在线。

## 风险

- dry-run helper 不能替代真正 live Rabbit publish/consume；它只把 live smoke 步骤、queue
  readiness 和 no-write 命令固定下来。
- content-service 只是代表 consumer，不能证明所有服务的 listener 均已被 live Rabbit 触发。
- 当前各服务没有统一 TTL cache 抽象，矩阵需要区分“event evict 已有证据”和“TTL 策略待定义”。

## 验收标准

- config-service 测试覆盖敏感 payload 不带 secret value。
- content-service 测试覆盖事件 evict 后下一次读取 DB 新值。
- smoke helper 默认 dry-run，不执行写 DB 或 Rabbit publish；PowerShell parser check 通过。
- smoke helper no-write dry-run 可以输出矩阵和待执行命令。
- 本 task spec 回写测试结果、矩阵表和剩余矩阵项。

## 测试边界

自动测试：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-content-service:test --rerun-tasks
```

脚本语法与 dry-run：

```text
powershell -NoProfile -Command "$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile('scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1 -DryRun
```

手工检查点：

- `rg -n "secret-value|effectiveValue|configValue" services/ircs-config-service/src/test/java/com/prodigalgal/ircs/config/SystemConfigChangePublisherTest.java`
- `rg -n "config_changed|APP_.*CONFIG_LISTENER_ENABLED" scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1`

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/done/028-16-c2-s5-search-cover-url-config-parity.md`
- `tasks/done/028-16-c2-s6-interaction-cover-url-config-parity.md`
- `tasks/done/028-16-c2-s7-portal-cover-url-config-parity.md`
- `tasks/done/028-16-c2-s8-storage-media-url-config-parity.md`
- `tasks/done/028-16-c2-s9-normalization-retry-config-parity.md`
- `tasks/done/028-16-c2-s10-aggregation-batch-config-parity.md`
- `tasks/done/028-16-c2-s11-notification-mail-config-parity.md`
- `tasks/done/028-16-c2-s12-metadata-provider-routing-config-parity.md`
- `tasks/done/028-16-c2-s13-ops-maintenance-reindex-config-parity.md`
- `tasks/done/028-16-c2-s14-content-scraper-base-url-config-parity.md`

## 当前状态

2026-06-09：已完成 `028-16-C2-S15 config event live matrix smoke harness`。

代表切片：

- publisher：config-service `SystemConfigChangePublisher`
- consumer：content-service `q.content.config_changed`
- key：`app.content.internal.scraper-base-url`

实现结果：

- 新增 no-write smoke helper：
  `scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1`。
- smoke helper 默认 dry-run，只输出矩阵、payload 安全形态和后续 no-write 检查命令；不写 DB、
  不 publish Rabbit、不 patch deployment。
- smoke helper 支持可选 `-CheckClusterReadOnly`，只读采集 Rabbit queue messages/consumers，
  仍不做写入。
- config-service `SystemConfigChangePublisherTest` 增加 payload shape guard，固定事件 JSON 只允许
  `key/action/effectiveSource/sensitive/changedAt` 五个字段。
- config-service 敏感 key 测试显式拒绝 payload 出现 `configValue`、`effectiveValue`、
  `db-secret-value` 和 `runtime-secret-value`。
- content-service `SystemConfigRepositoryTest` 增加代表回归：缓存命中旧 DB 值，收到
  `SystemConfigChangedEvent` 后 evict，再次读取拿到新 DB fallback 值。

初始矩阵：

| 服务 | 队列 | 代表 key | 当前证据 | 本轮状态 |
| --- | --- | --- | --- | --- |
| search-service | `q.search.config_changed` | `app.storage.r2.public-domain` / `app.storage.public-path` | S5 单测 | 后续 live smoke |
| interaction-service | `q.interaction.config_changed` | `app.storage.r2.public-domain` / `app.storage.public-path` | S6 单测 | 后续 live smoke |
| portal-service | `q.portal.config_changed` | `app.storage.r2.public-domain` / `app.storage.public-path` | S7 单测 | 后续 live smoke |
| storage-service | `q.storage.config_changed` | `app.storage.r2.public-domain` / `app.storage.public-path` | S8 单测 | 后续 live smoke |
| normalization-worker | `q.normalization.config_changed` | `normalization.max-retries` | S9 单测 | 后续 live smoke |
| aggregation-worker | `q.aggregation.config_changed` | `aggregation.batch-size` | S10 单测 | 后续 live smoke |
| notification-worker | `q.notification.config_changed` | `app.mail.enabled` | S11 单测 | 后续 live smoke |
| metadata-worker | `q.metadata.config_changed` | `app.metadata.tmdb.enabled` | S12 单测 | 后续 live smoke |
| ops-service | `q.ops.config_changed` | `app.ops.maintenance.reindex.dev-limit` | S13 单测 | 后续 live smoke |
| content-service | `q.content.config_changed` | `app.content.internal.scraper-base-url` | S14 单测 + S15 evict/read refresh | 本轮完成代表切片 |

验证结果：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL

powershell parser check
PARSER_OK

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-live-matrix-02816c2-smoke.ps1 -DryRun
MARK 1/4 config event matrix loaded without write operations
MARK 2/4 publisher payload shape is value-free by contract
MARK 3/4 no-write follow-up commands prepared
MARK 4/4 dry-run completed; no DB/Rabbit/deployment writes were attempted
status: SUCCESS
reason: DRY_RUN_MATRIX_ONLY
```

剩余矩阵项：

- live Rabbit publish/consume：需要在 dev 环境用临时非敏感 key 或专用 smoke endpoint 证明消息
  真正进入各服务队列并触发 evict。
- TTL 策略：当前多服务是 event-driven evict + process-local cache，未统一 TTL；后续需要决定
  是否为缺失事件或 Rabbit outage 增加短 TTL 兜底。
- 启动绑定配置：仍需逐项标注“重启生效”或迁到动态 config consumer。

后续服务列表：

- 第一批 live-safe smoke 建议从 content-service 开始，使用
  `app.content.internal.scraper-base-url` 这类非敏感、内部 URL key，避免触碰 R2/SMTP。
- 第二批扩展到 search、portal、interaction、storage 的媒体 URL 类 key。
- 第三批扩展到 normalization、aggregation、metadata、notification、ops 的 worker knob。
- 涉及 MAIL credential、R2 credential、SMTP live、provider HTTP 参数的验证必须保持在各自独立
  C3/R2/SMTP/provider 切片里，本任务不吸收。
