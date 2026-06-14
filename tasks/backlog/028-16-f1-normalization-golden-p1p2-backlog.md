# 028-16-F1 normalization golden P1/P2 backlog

状态：partial implementation closure，live/production-like 统一测试后置

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`

## 背景

089/093 总账显示 normalization 主链路已完成多轮 first slice：canonical rawMetadata、文本标准化、
description NER/HanLP prewarm、area/language 复合别名、genre policy、title alias/season/subtitle
以及 A9 本地 first closure。但 golden inventory 仍保留 P1/P2 缺口：完整 title handler pipeline、
location NLP、raw category 兜底推断、CharTable ASCII case 差异、真实老数据重洗和 live normalize
smoke 尚未统一关闭；P2 中 normalization retry/watchdog 的 PROCESSING reset、queue depth guard 和
adaptive workload 也需在本组明确是否继续迁移。

## 目标

- 收敛完整 title handler chain：噪音、结构、双语、数字、季数、副标题、别名去重和 main title 输出。
- 补齐 location NLP、genre synonym/suffix/protected-term 与 raw category fallback 的 golden fixture。
- 明确 CharTable/OpenCC/HanLP 差异中哪些按 V1 迁移、哪些作为 V3 improved replacement 保留。
- 明确 normalization retry/watchdog 的 PROCESSING reset、queue depth guard、adaptive workload 是否迁移。
- 为后续统一测试批次准备 normalization fixture matrix 和 live rerun 输入。

## 本轮实现范围

2026-06-10：028-16-F1/A11 worker 已把 backlog 中可本地关闭的 P1/P2 normalization
细节推进到 implementation-first closure：

- title handler chain：补 release group 前缀剥离、`S02`、`Part II`、`Vol.2` season
  识别，并保持现有 title/alias/season/subtitle lock 语义。
- location NLP first closure：在 `RawRelationAliasPolicy` 中对东京/大阪/首尔/曼谷/巴黎/伦敦/
  纽约/洛杉矶/台北等高置信地名追加标准地区 raw token；同时对 `中国香港` 等复合地区保留原 token
  并追加可命中 alias。
- genre NLP first closure：补 `推理 -> 悬疑`、`惊栗 -> 惊悚`、`动画片/卡通 -> 动画`、
  `综艺节目 -> 综艺`、`伦理片 -> 伦理`、`情色片 -> 情色` 等保守同义词，继续保留原 token。
- raw category fallback：缺少 `rawTypeId/rawTypeName` 时，可从 `categoryId/categoryCode/typeId`
  和 `categoryName/category/type/videoType` 兜底绑定 raw category；只有 category name 时用 name
  作为 sourceCode，仍依赖 dataSourceId。
- retry/watchdog 状态机：到期 `FAILED` 单独重投；stale `PENDING/PROCESSING` 只在
  `q.normalize.video` 队列为空或不可探测时 rescue；重投前统一把可恢复状态置回 `PENDING`
  并清空 `next_normalization_retry_time`。
- adaptive workload：`normalization.batch-size.min/max` 已纳入 watchdog batch clamp；但
  runtime/K8S `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE` 仍高于 DB/default seed，可继续用于 dev 低资源覆盖。
- failure reason/redaction：normalization failure log 只输出稳定 reason code 和 error class，
  不输出 raw metadata 或异常 message。

## 非目标

- 不在本 backlog spec 中修改业务代码。
- 不做全量历史数据重洗。
- 不触发真实外部 provider 或大批量队列。

## 影响范围

```text
services/ircs-normalization-worker/**
services/ircs-scraper-service/**
services/ircs-metadata-worker/**
docs/reports/089-v1-golden-behavior-inventory*.md
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/in-progress/028-16-a*.md
```

## 依赖

- A1-A9 当前实现和 fixture 结论。
- catalog 标准字典、raw dictionary 与 locked_fields 当前语义。
- 后续统一测试窗口允许运行 normalization-worker focused tests 与 dev K8S live smoke。

## 风险

- title/location/genre 规则过宽可能改变已有 portal/search 展示和聚合匹配输入。
- V1 CharTable 小写 ASCII 行为可能与 V3 现有展示预期冲突，需先用 fixture 固化取舍。
- live normalize smoke 需保证 DB/queue/ES fixture cleanup。

## 验收标准

```text
MARK 1/6 title handler chain golden fixture matrix completed: done 2026-06-10 by focused normalization-worker tests
MARK 2/6 location NLP and raw category fallback policy implemented or explicitly accepted: done 2026-06-10
MARK 3/6 genre synonym/suffix/protected-term regression matrix completed: done 2026-06-10
MARK 4/6 CharTable/OpenCC/HanLP and retry/watchdog compatibility decisions recorded with tests: partial done; CharTable ASCII case still kept as V3 improved replacement
MARK 5/6 focused normalization/scraper/metadata tests pass: partial done; normalization-worker focused tests passed, scraper/metadata unchanged and not rerun
MARK 6/6 dev live normalize smoke passes with DB/queue/ES cleanup: deferred to UTB-02816-P1P2-01
```

## 统一测试边界

纳入统一测试批次 `UTB-02816-P1P2-01`。先完成实现与 fixture，再统一运行 focused Gradle tests、
`q.normalize.video` live smoke、ES raw index residue 检查和 ResourceQuota/HTTPRoute 后置检查。

本轮轻量验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 30s
69 tests completed, 0 failed
```

2026-06-12 UTB-01 关闭：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1 -FailOnSkip
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
```

结果：

```text
A9 live smoke: MARK 5/5 PASSED
UTB-01 normalization A9: PASSED
UTB-01 normalization A12 focused regression: PASSED
q.normalize.video/q.search.sync.raw and DLQs: empty
DB fixture residue: 0
```

本轮修复 A9 smoke harness 两处问题：`kubectl` transient stderr 不再误触发 terminating error，
单行 SQL scalar 输出强制数组化，避免中文最后一字被误当状态。业务日志确认 normalization-worker
可将 fixture raw 处理为 `READY`。

仍未执行：全量历史数据重洗、真实老数据矩阵、scraper/metadata 非本批次 focused tests。
