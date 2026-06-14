# 028-16-C2-S16 live Rabbit config event safe smoke task spec

状态：进行中

父任务：

- `tasks/done/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`
- `tasks/done/028-16-c2-system-config-cache-event-parity.md`

## 背景

C2-S15 已经完成 no-write matrix smoke harness，并用 content-service 的
`app.content.internal.scraper-base-url` 作为代表 key 固定了 config event payload 安全形态和
content cache evict/read-refresh 单测证据。C2-S16 原计划把该 harness 推进到可控 live Rabbit
publish/consume smoke，但本轮用户明确收缩范围：不执行真实 live 写入，只完成安全脚本、parser
check、默认 dry-run 和 task spec 回写。

## 目标

- 新增一个 live-safe smoke 脚本，默认 dry-run，不写 DB、不 publish Rabbit、不 patch deployment。
- 固定候选 key 为 content-service 的非敏感 `app.content.internal.scraper-base-url`。
- 脚本允许保留显式 live 模式设计，但必须需要 `-ConfirmLiveRabbit` 才能进入真实写入分支。
- dry-run 输出临时 DB 值、config event、content cache evict 验证和 restore/cleanup 的计划。
- 记录当时 Rabbit outage / missed-message TTL 未闭环；该风险后续已由 C2-S21 关闭。

## 非目标

- 本轮不执行真实 DB 写入。
- 本轮不执行真实 Rabbit publish/consume。
- 本轮不新增 content-service 运行时代码、controller 或 smoke endpoint。
- 不触碰敏感 key，不打印 secret。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。
- 不触碰 BFF、normalization、R2、SMTP。

## 输入

- 候选 key：`app.content.internal.scraper-base-url`
- event contract：`x.domain.events` / `config.system.changed`
- content-service consumer queue：`q.content.config_changed`
- S15 no-write harness 与 content-service evict/read-refresh 单测证据

## 输出

- `scripts/smoke/config-event-content-live-02816c2-s16-smoke.ps1`
- 本 task spec 验证结果与剩余风险记录

## 影响范围

包含：

- `scripts/smoke/*config*`
- `tasks/in-progress/028-16-c2-s16-live-rabbit-config-event-safe-smoke.md`

不包含：

- service 运行时代码。
- service 测试代码。
- K8S manifest。
- 全局 docs/tasks index。
- BFF、normalization、R2、SMTP。

## 依赖

- PowerShell parser。
- 可选 dev K8S/Rabbit/PostgreSQL 环境；本轮不要求在线。

## 风险

- dry-run 不能证明 live Rabbit publish/consume 已真实发生。
- 当前 content-service dev manifest 可能通过 `APP_CONTENT_SCRAPER_BASE_URL` 运行时注入覆盖 DB，
  真实 live smoke 前需要先确认 runtime override 不会压过 DB fallback。
- 没有 content-service 只读观测 endpoint 时，脚本只能设计验证步骤，不能自动证明 cache 已 evict。
- Rabbit outage / missed-message TTL 在本轮仍未闭环；该旧风险后续已由 C2-S21 的
  event evict + short TTL read-through 决策和 content-service focused test 覆盖。

## 验收标准

- PowerShell parser check 通过。
- 默认 dry-run 成功，且明确输出没有执行 DB/Rabbit/deployment 写入。
- live 模式必须需要显式 `-ConfirmLiveRabbit`。
- task spec 回写执行模式、测试摘要、是否真的执行 live Rabbit。

## 测试边界

脚本语法与 dry-run：

```text
powershell -NoProfile -Command "$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile('scripts/smoke/config-event-content-live-02816c2-s16-smoke.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/config-event-content-live-02816c2-s16-smoke.ps1
```

Gradle：

```text
本轮不修改 Java/Kotlin 代码，不要求新增 Gradle test；如需回归，可复用 S15 命令：
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-content-service:test --rerun-tasks
```

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- `tasks/done/028-16-c2-system-config-cache-event-parity.md`
- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`

## 当前状态

2026-06-09：任务创建，按用户最新要求收缩为安全脚本、parser check、默认 dry-run 和 task spec
回写；不执行真实 live 写入。

实现结果：

- 新增 `scripts/smoke/config-event-content-live-02816c2-s16-smoke.ps1`。
- 脚本默认 dry-run，不写 DB、不 publish Rabbit、不 patch deployment。
- 脚本固定候选 key 为非敏感 `app.content.internal.scraper-base-url`。
- 脚本记录 live 模式设计，包括临时 DB 值、value-free event payload、runtime override abort
  条件、restore/cleanup 顺序和队列残留检查。
- `-ConfirmLiveRabbit` 分支保留为显式 live 入口，但本轮按用户要求不实现真实写入动作，进入后
  返回 `SKIPPED`。

验证结果：

```text
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile('scripts/smoke/config-event-content-live-02816c2-s16-smoke.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }; 'PARSER_OK'
PARSER_OK

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s16-smoke.ps1
MARK 1/4 live-safe content config event plan prepared without secrets
MARK 2/4 candidate key is non-sensitive app.content.internal.scraper-base-url
MARK 3/4 cleanup and restore plan recorded before any live branch
MARK 4/4 dry-run completed; no DB/Rabbit/deployment writes were attempted
status: SUCCESS
reason: DRY_RUN_ONLY_CONFIRM_LIVE_RABBIT_REQUIRED
mode: dry-run
```

执行模式：

- 真实 live Rabbit：未执行。
- 真实 DB 写入：未执行。
- 真实 deployment patch：未执行。
- Gradle tests：本轮未修改 Java/Kotlin 代码，未执行；S15 已覆盖 config-service 与
  content-service 代表回归。

剩余项：

- live Rabbit publish/consume 后续已由 C2-S17/S19/S20 闭环。
- Rabbit outage / missed-message TTL 兜底后续已由 C2-S21 闭环。
- 若后续要自动证明 content cache evict，需要先增加默认关闭、仅 smoke 开启的只读观测口，或使用
  现有业务路径构造可控观测。
